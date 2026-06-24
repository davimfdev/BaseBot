package dev.davimf.basebot.modules.tickets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.crypto.TicketCrypto;
import dev.davimf.basebot.database.sqlite.TicketRepository;
import dev.davimf.basebot.database.model.ActiveTicket;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Drives the whole ticket lifecycle (BOTSPECS Module 2): creation, the in-channel
 * dashboard actions (Assumir, Criar Call, Membro, Notificar, Renomear) and the
 * closure &amp; transcript pipeline.
 *
 * <p>Closure renders the channel history to JSON, AES-encrypts it (byte-for-byte
 * matching the dashboard via {@link TicketCrypto}), POSTs the bundle to davimf.dev,
 * then posts the closure embed (password + link) to {@code #log-tickets} and the
 * creator's DM before deleting both the text and voice channels.
 */
public final class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Cap on the number of messages pulled into a transcript. */
    private static final int TRANSCRIPT_LIMIT = 1000;

    /** guild_config key for the ticket log channel (falls back to the typed field). */
    private static final String TICKET_LOG_KEY = "log-tickets";

    private final BotContext ctx;

    public TicketService(BotContext ctx) {
        this.ctx = ctx;
    }

    // --- Creation --------------------------------------------------------------

    /**
     * Creation flow (BOTSPECS Module 2): triggered by the open-reason modal. Creates a
     * private text channel under the category's Discord parent (creator + staff only),
     * stores the ticket + the member's reason, and posts the dashboard whose first
     * message pings creator + staff and shows the reason. The modal must already be
     * deferred (ephemeral).
     */
    public void openTicket(ModalInteractionEvent event, TicketCategory cat, String reason) {
        Guild guild = event.getGuild();
        Member creator = event.getMember();
        if (guild == null || creator == null) {
            event.getHook().sendMessage("Não foi possível abrir o ticket.").queue();
            return;
        }
        Category parent = guild.getCategoryById(cat.discordCategoryId());
        if (parent == null) {
            event.getHook().sendMessage("A categoria do Discord configurada não existe mais. "
                    + "Avise um administrador.").queue();
            return;
        }

        List<Permission> allow = List.of(
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY);
        ChannelAction<TextChannel> action = guild
                .createTextChannel(TicketChannelName.of(cat.emoji(), cat.name(), creator.getUser().getName()), parent)
                .addRolePermissionOverride(guild.getPublicRole().getIdLong(), List.of(), List.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(creator.getIdLong(), allow, List.of());
        for (String roleId : cat.staffRoleIds()) {
            try {
                action = action.addRolePermissionOverride(Long.parseLong(roleId), allow, List.of());
            } catch (NumberFormatException ignored) {
                // skip malformed role id
            }
        }

        action.reason("Ticket " + cat.name() + " por " + creator.getUser().getName()).queue(channel -> {
            String ticketId = newId();
            ctx.database().tickets().create(new ActiveTicket(ticketId, guild.getId(), channel.getId(),
                    null, creator.getId(), null, cat.name(), ActiveTicket.OPEN, reason));
            ctx.database().tickets().addEvent(ticketId,
                    "📝 Ticket aberto por " + creator.getAsMention() + " · Motivo: " + reason,
                    System.currentTimeMillis());
            ctx.database().actionLogs().log(guild.getId(), creator.getId(), channel.getId(),
                    "TICKET_OPEN", cat.name());

            String staffMentions = cat.staffRoleIds().stream()
                    .map(r -> "<@&" + r + ">").collect(Collectors.joining(" "));
            String emoji = (cat.emoji() != null && !cat.emoji().isBlank()) ? cat.emoji() + " " : "";
            String header = "## " + emoji + cat.name() + "\n"
                    + creator.getAsMention() + (staffMentions.isBlank() ? "" : " " + staffMentions) + "\n\n"
                    + "**📝 Motivo:** " + reason;
            // V2 text mentions DO ping — intended here (creator + staff).
            channel.sendMessageComponents(TicketView.dashboard(accent(guild.getId()), ticketId, header))
                    .useComponentsV2().queue();
            event.getHook().sendMessage("Ticket criado: " + channel.getAsMention()).queue();
        }, err -> event.getHook().sendMessage("Falha ao criar o ticket: " + err.getMessage()).queue());
    }

    // --- Dashboard actions -----------------------------------------------------

    /** "Assumir Atendimento": a staff member claims the ticket. */
    public void assume(ButtonInteractionEvent event, String ticketId) {
        ActiveTicket t = lookup(event, ticketId);
        if (t == null) {
            return;
        }
        if (isCreator(event, t)) {
            ephemeral(event, "O criador do ticket não pode assumir o atendimento.");
            return;
        }
        if (t.assignedStaffId() != null) {
            ephemeral(event, "Este ticket já foi assumido por <@" + t.assignedStaffId() + ">.");
            return;
        }
        ctx.database().tickets().assignStaff(ticketId, event.getUser().getId());
        ctx.database().actionLogs().log(t.guildId(), event.getUser().getId(), t.creatorId(),
                "TICKET_ASSIGN", ticketId);
        // Reveal the full action set + show who is handling it (BOTSPECS Module 2).
        event.editComponents(TicketView.dashboard(accent(t.guildId()), ticketId,
                        headerFor(t), event.getUser().getId()))
                .useComponentsV2().queue();
        announce(event.getChannel(), t.guildId(), ticketId,
                "🙋 " + event.getUser().getAsMention() + " assumiu o atendimento.");
    }

    /** "Criar Call": open a private voice channel mirroring the ticket's access. */
    public void createCall(ButtonInteractionEvent event, String ticketId) {
        ActiveTicket t = lookup(event, ticketId);
        if (t == null) {
            return;
        }
        if (isCreator(event, t)) {
            ephemeral(event, "Apenas a equipe pode criar a call.");
            return;
        }
        if (t.voiceChannelId() != null && event.getGuild().getVoiceChannelById(t.voiceChannelId()) != null) {
            ephemeral(event, "A call já existe: <#" + t.voiceChannelId() + ">.");
            return;
        }
        Guild guild = event.getGuild();
        TextChannel text = guild.getTextChannelById(t.textChannelId());
        if (text == null) {
            ephemeral(event, "O canal do ticket não existe mais.");
            return;
        }
        Category parent = text.getParentCategory();
        List<Permission> allow = List.of(
                Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_SPEAK);
        long everyone = guild.getPublicRole().getIdLong();

        ChannelAction<VoiceChannel> action = guild
                .createVoiceChannel("call-" + TicketChannelName.slug(t.suffix()), parent)
                .addRolePermissionOverride(everyone, List.of(), List.of(Permission.VIEW_CHANNEL))
                .addMemberPermissionOverride(Long.parseLong(t.creatorId()), allow, List.of());
        // Mirror the staff role overrides the text channel already grants.
        for (PermissionOverride ov : text.getRolePermissionOverrides()) {
            if (ov.getIdLong() != everyone) {
                action = action.addRolePermissionOverride(ov.getIdLong(), allow, List.of());
            }
        }

        event.deferReply(true).queue();
        action.reason("Call do ticket " + ticketId).queue(vc -> {
            ctx.database().tickets().setVoiceChannel(ticketId, vc.getId());
            ctx.database().actionLogs().log(t.guildId(), event.getUser().getId(), t.creatorId(),
                    "TICKET_CALL", vc.getId());
            announce(event.getChannel(), t.guildId(), ticketId,
                    "🔊 Call criada por " + event.getUser().getAsMention() + ": " + vc.getAsMention());
            event.getHook().sendMessage("🔊 Call criada: " + vc.getAsMention()).queue();
        }, err -> event.getHook().sendMessage("Falha ao criar a call: " + err.getMessage()).queue());
    }

    /** "Notificar": DM the ticket creator with a jump-to-channel button (BOTSPECS Module 2). */
    public void notifyCreator(ButtonInteractionEvent event, String ticketId) {
        ActiveTicket t = lookup(event, ticketId);
        if (t == null) {
            return;
        }
        if (isCreator(event, t)) {
            ephemeral(event, "Apenas a equipe pode notificar o autor.");
            return;
        }
        Guild guild = event.getGuild();
        String channelUrl = "https://discord.com/channels/" + guild.getId() + "/" + t.textChannelId();
        event.deferReply(true).queue();
        guild.getJDA().retrieveUserById(t.creatorId())
                .flatMap(User::openPrivateChannel)
                .flatMap(dm -> dm.sendMessageComponents(
                        TicketView.notifyDm(accent(t.guildId()), guild.getName(), channelUrl)).useComponentsV2())
                .queue(ok -> {
                    ctx.database().actionLogs().log(t.guildId(), event.getUser().getId(), t.creatorId(),
                            "TICKET_NOTIFY", ticketId);
                    announce(event.getChannel(), t.guildId(), ticketId,
                            "🔔 <@" + t.creatorId() + "> foi notificado por " + event.getUser().getAsMention() + ".");
                    event.getHook().sendMessage("🔔 O autor foi notificado por DM.").queue();
                }, err -> event.getHook().sendMessage(
                        "Não foi possível enviar DM ao autor (DMs fechadas?).").queue());
    }

    /** "Membro": prompt a staff member to pick a user to add to the ticket. */
    public void promptAddMember(ButtonInteractionEvent event, String ticketId) {
        ActiveTicket t = lookup(event, ticketId);
        if (t == null) {
            return;
        }
        if (isCreator(event, t)) {
            ephemeral(event, "Apenas a equipe pode adicionar membros.");
            return;
        }
        event.replyComponents(TicketView.addMemberPrompt(accent(t.guildId()), ticketId))
                .useComponentsV2().setEphemeral(true).queue();
    }

    /** Resolves the user picked in the "Membro" select and grants channel access. */
    public void addMember(EntitySelectInteractionEvent event, String ticketId) {
        ActiveTicket t = ctx.database().tickets().findById(ticketId).orElse(null);
        if (t == null || event.getGuild() == null || event.getValues().isEmpty()) {
            ephemeral(event, "Ticket não encontrado.");
            return;
        }
        Guild guild = event.getGuild();
        TextChannel text = guild.getTextChannelById(t.textChannelId());
        if (text == null) {
            ephemeral(event, "O canal do ticket não existe mais.");
            return;
        }
        String userId = event.getValues().get(0).getId();
        event.deferReply(true).queue();
        guild.retrieveMemberById(userId).queue(member -> text.upsertPermissionOverride(member)
                .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                .reason("Adicionado ao ticket " + ticketId)
                .queue(ok -> {
                    ctx.database().actionLogs().log(t.guildId(), event.getUser().getId(), userId,
                            "TICKET_MEMBER_ADD", ticketId);
                    announce(event.getChannel(), t.guildId(), ticketId, "👤 " + member.getAsMention()
                            + " foi adicionado ao ticket por " + event.getUser().getAsMention() + ".");
                    event.getHook().sendMessage("Adicionado " + member.getAsMention() + " ao ticket.").queue();
                }, err -> event.getHook().sendMessage("Falha ao adicionar: " + err.getMessage()).queue()),
                err -> event.getHook().sendMessage("Não foi possível encontrar esse membro.").queue());
    }

    /** "Renomear": open a modal to rename the ticket channel. */
    public void promptRename(ButtonInteractionEvent event, String ticketId) {
        ActiveTicket t = lookup(event, ticketId);
        if (t == null) {
            return;
        }
        if (isCreator(event, t)) {
            ephemeral(event, "Apenas a equipe pode renomear o ticket.");
            return;
        }
        event.replyModal(TicketView.renameModal(ticketId, t.suffix())).queue();
    }

    /** Applies the new name from the rename modal to the channel + stored suffix. */
    public void rename(ModalInteractionEvent event, String ticketId) {
        ActiveTicket t = ctx.database().tickets().findById(ticketId).orElse(null);
        if (t == null || event.getGuild() == null) {
            ephemeral(event, "Ticket não encontrado.");
            return;
        }
        String raw = event.getValue("nome") == null ? "" : event.getValue("nome").getAsString();
        String suffix = TicketChannelName.slug(raw);
        TextChannel text = event.getGuild().getTextChannelById(t.textChannelId());
        if (text == null) {
            ephemeral(event, "O canal do ticket não existe mais.");
            return;
        }
        ctx.database().tickets().rename(ticketId, suffix);
        ctx.database().actionLogs().log(t.guildId(), event.getUser().getId(), t.creatorId(),
                "TICKET_RENAME", suffix);
        event.deferReply(true).queue();
        text.getManager().setName(suffix).reason("Ticket renomeado").queue(
                ok -> {
                    announce(event.getChannel(), t.guildId(), ticketId, "✏️ Ticket renomeado para `" + suffix
                            + "` por " + event.getUser().getAsMention() + ".");
                    event.getHook().sendMessage("Ticket renomeado para `" + suffix + "`.").queue();
                },
                err -> event.getHook().sendMessage("Falha ao renomear: " + err.getMessage()).queue());
    }

    // --- Closure & transcript --------------------------------------------------

    /** "Fechar": prompt for a closure reason before running the transcript pipeline. */
    public void promptClose(ButtonInteractionEvent event, String ticketId) {
        ActiveTicket t = lookup(event, ticketId);
        if (t == null) {
            return;
        }
        if (!ActiveTicket.OPEN.equals(t.status())) {
            ephemeral(event, "Este ticket já está sendo fechado.");
            return;
        }
        event.replyModal(TicketView.closeReasonModal(ticketId)).queue();
    }

    /**
     * Full closure (BOTSPECS §Closure & Transcript): render the history to JSON,
     * encrypt + POST it to the dashboard, deliver the closure embed (reason + password
     * + link) to {@code #log-tickets} and the creator's DM, then delete the text + voice
     * channels. If the transcript step fails the channels are kept and the ticket
     * stays open so nothing is lost.
     */
    public void closeTicket(ModalInteractionEvent event, String ticketId) {
        if (event.getGuild() == null) {
            ephemeral(event, "Use isto em um servidor.");
            return;
        }
        ActiveTicket t = ctx.database().tickets().findById(ticketId).orElse(null);
        if (t == null) {
            ephemeral(event, "Ticket não encontrado.");
            return;
        }
        if (!ActiveTicket.OPEN.equals(t.status())) {
            ephemeral(event, "Este ticket já está sendo fechado.");
            return;
        }
        String reason = event.getValue("motivo") == null ? null : event.getValue("motivo").getAsString();
        Guild guild = event.getGuild();
        TextChannel channel = guild.getTextChannelById(t.textChannelId());
        if (channel == null) {
            ctx.database().tickets().setStatus(ticketId, ActiveTicket.CLOSED);
            ephemeral(event, "O canal já não existe; ticket marcado como fechado.");
            return;
        }

        ctx.database().tickets().setStatus(ticketId, ActiveTicket.CLOSING);
        String closerId = event.getUser().getId();
        String closerName = event.getUser().getEffectiveName();
        event.reply("🔒 Fechando o ticket e gerando o transcript…").queue();

        String guildName = guild.getName();
        String channelName = channel.getName();
        guild.retrieveMemberById(t.creatorId()).submit()
                .handle((member, ex) -> member == null ? null : member.getEffectiveName())
                .thenCompose(openerName -> renderTranscript(channel, t, reason,
                        openerName == null ? "Usuário" : openerName, closerName))
                .thenApplyAsync(json -> buildTranscriptLink(t, guildName, channelName, json),
                        ctx.scheduler().executor())
                .whenComplete((result, err) -> {
                    if (err != null) {
                        log.error("Transcript pipeline failed for ticket {}", ticketId, err);
                        ctx.database().tickets().setStatus(ticketId, ActiveTicket.OPEN);
                        channel.sendMessage("⚠️ Falha ao gerar/enviar o transcript: "
                                + rootMessage(err) + "\nO ticket **não** foi fechado.").queue();
                        return;
                    }
                    deliverClosure(guild, t, channel, channelName, closerId, reason, result);
                });
    }

    private void deliverClosure(Guild guild, ActiveTicket t, TextChannel channel,
                                String channelName, String closerId, String reason, ClosureResult result) {
        var closure = TicketView.closure(accent(t.guildId()), channelName, t.creatorId(),
                closerId, reason, result.transcriptUrl(), result.password());

        // 1) Post to the ticket log channel (per spec the password lives here + in the DM).
        String logId = ticketLogChannelId(t.guildId());
        if (logId != null) {
            TextChannel logChannel = guild.getTextChannelById(logId);
            if (logChannel != null) {
                logChannel.sendMessageComponents(closure).useComponentsV2().queue();
            }
        }

        // 2) DM the creator with the transcript link + one-time password.
        guild.getJDA().retrieveUserById(t.creatorId())
                .flatMap(User::openPrivateChannel)
                .flatMap(dm -> dm.sendMessageComponents(closure).useComponentsV2())
                .queue(ok -> {}, err -> log.debug("Could not DM ticket creator {}: {}",
                        t.creatorId(), err.getMessage()));

        // 3) Delete the voice channel (if any), then the text channel.
        ctx.database().tickets().setStatus(t.id(), ActiveTicket.CLOSED);
        if (t.voiceChannelId() != null) {
            VoiceChannel vc = guild.getVoiceChannelById(t.voiceChannelId());
            if (vc != null) {
                vc.delete().reason("Ticket fechado").queue(null, e -> { });
            }
        }
        channel.delete().reason("Ticket fechado por " + closerId)
                .queueAfter(5, TimeUnit.SECONDS);
        log.info("Ticket {} closed; transcript at {}", t.id(), result.transcriptUrl());
    }

    /**
     * Pulls up to {@link #TRANSCRIPT_LIMIT} messages and renders them to the JSON shape
     * the davimf.dev transcript viewer expects ({@code categoryLabel/openerName/openedAt/
     * closedByName/closedAt/closeReason/messages[]} with per-message {@code embeds}).
     */
    private CompletableFuture<String> renderTranscript(TextChannel channel, ActiveTicket t,
                                                       String closeReason, String openerName,
                                                       String closerName) {
        String botName = channel.getJDA().getSelfUser().getName();
        String botAvatar = channel.getJDA().getSelfUser().getEffectiveAvatarUrl();
        int accent = accent(t.guildId());
        return channel.getIterableHistory().takeAsync(TRANSCRIPT_LIMIT).thenApply(messages -> {
            ObjectNode root = JSON.createObjectNode();
            root.put("categoryLabel", t.suffix());
            root.put("openerName", openerName);
            root.put("openedAt", fmt(channel.getTimeCreated()));
            root.put("closedByName", closerName);
            root.put("closedAt", fmt(OffsetDateTime.now()));
            if (closeReason == null || closeReason.isBlank()) {
                root.putNull("closeReason");
            } else {
                root.put("closeReason", closeReason);
            }

            // Build a unified timeline: real conversation messages + persisted action
            // events (which always survive, even when MESSAGE_CONTENT strips embeds from
            // history). Bot messages with no text are dropped — they are the dashboard and
            // the live action embeds, which the stored events re-add cleanly.
            List<Entry> entries = new java.util.ArrayList<>();
            for (Message m : messages) {
                String content = m.getContentDisplay();
                if (m.getAuthor().isBot() && content.isBlank() && m.getAttachments().isEmpty()) {
                    continue;
                }
                ObjectNode mn = JSON.createObjectNode();
                mn.put("authorName", m.getMember() != null
                        ? m.getMember().getEffectiveName() : m.getAuthor().getName());
                mn.put("avatarUrl", m.getAuthor().getEffectiveAvatarUrl());
                mn.put("timestampMillis", m.getTimeCreated().toInstant().toEpochMilli());
                mn.put("content", content);
                mn.put("bot", m.getAuthor().isBot());
                mn.put("edited", m.isEdited());
                ArrayNode at = mn.putArray("attachments");
                m.getAttachments().forEach(a -> at.add(a.getFileName()));
                ArrayNode embeds = mn.putArray("embeds");
                for (MessageEmbed e : m.getEmbeds()) {
                    embeds.add(renderEmbed(e));
                }
                entries.add(new Entry(m.getTimeCreated().toInstant().toEpochMilli(), mn));
            }
            for (TicketRepository.TicketEvent ev : ctx.database().tickets().listEvents(t.id())) {
                ObjectNode mn = JSON.createObjectNode();
                mn.put("authorName", botName);
                mn.put("avatarUrl", botAvatar);
                mn.put("timestampMillis", ev.createdAtMillis());
                mn.put("content", "");
                mn.put("bot", true);
                mn.put("edited", false);
                mn.putArray("attachments");
                ObjectNode embed = JSON.createObjectNode();
                embed.put("description", ev.text());
                embed.put("color", accent);
                mn.putArray("embeds").add(embed);
                entries.add(new Entry(ev.createdAtMillis(), mn));
            }
            entries.sort(java.util.Comparator.comparingLong(Entry::millis));

            ArrayNode arr = root.putArray("messages");
            entries.forEach(e -> arr.add(e.node()));
            return root.toString();
        });
    }

    /** A timeline item (a real message or a stored action event) keyed by timestamp. */
    private record Entry(long millis, ObjectNode node) {}

    /** Maps a JDA embed to the viewer's embed shape. */
    private static ObjectNode renderEmbed(MessageEmbed e) {
        ObjectNode en = JSON.createObjectNode();
        if (e.getAuthor() != null && e.getAuthor().getName() != null) {
            en.put("authorName", e.getAuthor().getName());
        }
        if (e.getTitle() != null) {
            en.put("title", e.getTitle());
        }
        if (e.getDescription() != null) {
            en.put("description", e.getDescription());
        }
        en.put("color", e.getColorRaw());
        ArrayNode fields = en.putArray("fields");
        for (MessageEmbed.Field f : e.getFields()) {
            ObjectNode fn = fields.addObject();
            fn.put("name", f.getName());
            fn.put("value", f.getValue());
            fn.put("inline", f.isInline());
        }
        if (e.getFooter() != null && e.getFooter().getText() != null) {
            en.put("footer", e.getFooter().getText());
        }
        if (e.getThumbnail() != null) {
            en.put("thumbnailUrl", e.getThumbnail().getUrl());
        }
        if (e.getImage() != null) {
            en.put("imageUrl", e.getImage().getUrl());
        }
        return en;
    }

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static String fmt(OffsetDateTime when) {
        return when.format(TS_FMT);
    }

    /**
     * Encrypts a rendered transcript, stores it on the dashboard, and returns the
     * one-time password + public link to show the closer and DM the creator.
     */
    public ClosureResult buildTranscriptLink(ActiveTicket ticket,
                                             String guildName,
                                             String channelName,
                                             String transcriptJson) {
        TicketCrypto.Result enc = ctx.ticketCrypto().encrypt(transcriptJson);
        String url = ctx.ticketIngest().store(ticket.id(), guildName, channelName, enc.bundle());

        ctx.database().actionLogs().log(
                ticket.guildId(), ticket.assignedStaffId(), ticket.creatorId(),
                "TICKET_CLOSED", ticket.id());

        log.info("Stored transcript for ticket {} -> {}", ticket.id(), url);
        return new ClosureResult(enc.password(), url);
    }

    // --- helpers ---------------------------------------------------------------

    private ActiveTicket lookup(ButtonInteractionEvent event, String ticketId) {
        if (event.getGuild() == null) {
            ephemeral(event, "Use isto em um servidor.");
            return null;
        }
        Optional<ActiveTicket> maybe = ctx.database().tickets().findById(ticketId);
        if (maybe.isEmpty()) {
            ephemeral(event, "Ticket não encontrado.");
            return null;
        }
        return maybe.get();
    }

    private static boolean isCreator(ButtonInteractionEvent event, ActiveTicket t) {
        return event.getUser().getId().equals(t.creatorId());
    }

    /** Minimal dashboard header reconstructed from stored ticket data (used on edit). */
    private static String headerFor(ActiveTicket t) {
        return "## " + t.suffix() + "\n<@" + t.creatorId() + ">";
    }

    private static void ephemeral(ButtonInteractionEvent event, String msg) {
        event.reply(msg).setEphemeral(true).queue();
    }

    private static void ephemeral(EntitySelectInteractionEvent event, String msg) {
        event.reply(msg).setEphemeral(true).queue();
    }

    private static void ephemeral(ModalInteractionEvent event, String msg) {
        event.reply(msg).setEphemeral(true).queue();
    }

    /**
     * Posts a public action notice as a classic embed (members see it) AND persists it as
     * a ticket event so it always lands in the transcript — even with MESSAGE_CONTENT off,
     * when the bot can't read embeds back from channel history.
     */
    private void announce(MessageChannel channel, String guildId, String ticketId, String text) {
        ctx.database().tickets().addEvent(ticketId, text, System.currentTimeMillis());
        channel.sendMessageEmbeds(TicketView.actionEmbed(accent(guildId), text)).queue(ok -> {}, e -> {});
    }

    private int accent(String guildId) {
        return EmbedColor.resolve(config(guildId));
    }

    private GuildConfig config(String guildId) {
        return ctx.database().guildConfig().findOrEmpty(guildId);
    }

    private String ticketLogChannelId(String guildId) {
        GuildConfig cfg = config(guildId);
        String byKey = cfg.channel(TICKET_LOG_KEY);
        return byKey != null ? byKey : cfg.ticketLogChannelId();
    }

    private static String rootMessage(Throwable err) {
        Throwable cause = err;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /** Password (shown once) + public transcript URL for the closure embed/DM. */
    public record ClosureResult(String password, String transcriptUrl) {}
}
