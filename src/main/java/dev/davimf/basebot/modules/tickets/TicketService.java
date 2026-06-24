package dev.davimf.basebot.modules.tickets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.crypto.TicketCrypto;
import dev.davimf.basebot.database.model.ActiveTicket;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.util.EmbedColor;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
     * Creation flow (BOTSPECS Module 2): create a private text channel under the
     * category's Discord parent, granting access to the creator + staff roles only,
     * save it to SQLite, and post the dashboard whose first message pings creator + staff.
     * The triggering select interaction must already be deferred (ephemeral).
     */
    public void openTicket(StringSelectInteractionEvent event, TicketCategory cat) {
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
                    null, creator.getId(), null, cat.name(), ActiveTicket.OPEN));
            ctx.database().actionLogs().log(guild.getId(), creator.getId(), channel.getId(),
                    "TICKET_OPEN", cat.name());

            String staffMentions = cat.staffRoleIds().stream()
                    .map(r -> "<@&" + r + ">").collect(Collectors.joining(" "));
            String emoji = (cat.emoji() != null && !cat.emoji().isBlank()) ? cat.emoji() + " " : "";
            String header = "## " + emoji + cat.name() + "\n"
                    + creator.getAsMention() + (staffMentions.isBlank() ? "" : " " + staffMentions) + "\n\n"
                    + (cat.description() == null || cat.description().isBlank()
                            ? "Descreva seu pedido e a equipe irá atendê-lo." : cat.description());
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
        event.reply("🙋 " + event.getUser().getAsMention() + " assumiu o atendimento.").queue();
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

        event.deferReply().queue();
        action.reason("Call do ticket " + ticketId).queue(vc -> {
            ctx.database().tickets().setVoiceChannel(ticketId, vc.getId());
            ctx.database().actionLogs().log(t.guildId(), event.getUser().getId(), t.creatorId(),
                    "TICKET_CALL", vc.getId());
            event.getHook().sendMessage("🔊 Call criada: " + vc.getAsMention()).queue();
        }, err -> event.getHook().sendMessage("Falha ao criar a call: " + err.getMessage()).queue());
    }

    /** "Notificar": ping the ticket creator to request their attention. */
    public void notifyCreator(ButtonInteractionEvent event, String ticketId) {
        ActiveTicket t = lookup(event, ticketId);
        if (t == null) {
            return;
        }
        if (isCreator(event, t)) {
            ephemeral(event, "Apenas a equipe pode notificar o autor.");
            return;
        }
        ctx.database().actionLogs().log(t.guildId(), event.getUser().getId(), t.creatorId(),
                "TICKET_NOTIFY", ticketId);
        event.reply("🔔 <@" + t.creatorId() + ">, a equipe solicita sua atenção neste ticket.").queue();
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
                ok -> event.getHook().sendMessage("Ticket renomeado para `" + suffix + "`.").queue(),
                err -> event.getHook().sendMessage("Falha ao renomear: " + err.getMessage()).queue());
    }

    // --- Closure & transcript --------------------------------------------------

    /**
     * Full closure (BOTSPECS §Closure & Transcript): render the history to JSON,
     * encrypt + POST it to the dashboard, deliver the closure embed (password + link)
     * to {@code #log-tickets} and the creator's DM, then delete the text + voice
     * channels. If the transcript step fails the channels are kept and the ticket
     * stays open so nothing is lost.
     */
    public void closeTicket(ButtonInteractionEvent event, String ticketId) {
        ActiveTicket t = lookup(event, ticketId);
        if (t == null) {
            return;
        }
        if (!ActiveTicket.OPEN.equals(t.status())) {
            ephemeral(event, "Este ticket já está sendo fechado.");
            return;
        }
        Guild guild = event.getGuild();
        TextChannel channel = guild.getTextChannelById(t.textChannelId());
        if (channel == null) {
            ctx.database().tickets().setStatus(ticketId, ActiveTicket.CLOSED);
            ephemeral(event, "O canal já não existe; ticket marcado como fechado.");
            return;
        }

        ctx.database().tickets().setStatus(ticketId, ActiveTicket.CLOSING);
        String closerId = event.getUser().getId();
        event.reply("🔒 Fechando o ticket e gerando o transcript…").queue();

        String guildName = guild.getName();
        String channelName = channel.getName();
        renderTranscript(channel, t, guildName)
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
                    deliverClosure(guild, t, channel, channelName, closerId, result);
                });
    }

    private void deliverClosure(Guild guild, ActiveTicket t, TextChannel channel,
                                String channelName, String closerId, ClosureResult result) {
        var closure = TicketView.closure(accent(t.guildId()), t.suffix(), t.creatorId(),
                closerId, result.transcriptUrl(), result.password());

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
                .flatMap(net.dv8tion.jda.api.entities.User::openPrivateChannel)
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

    /** Pulls up to {@link #TRANSCRIPT_LIMIT} messages and renders them to a JSON string. */
    private CompletableFuture<String> renderTranscript(TextChannel channel, ActiveTicket t, String guildName) {
        return channel.getIterableHistory().takeAsync(TRANSCRIPT_LIMIT).thenApply(messages -> {
            ObjectNode root = JSON.createObjectNode();
            root.put("ticketId", t.id());
            root.put("guildId", t.guildId());
            root.put("guildName", guildName);
            root.put("channelName", channel.getName());
            root.put("category", t.suffix());
            root.put("creatorId", t.creatorId());
            root.put("assignedStaffId", t.assignedStaffId());
            root.put("closedAt", java.time.Instant.now().toString());
            ArrayNode arr = root.putArray("messages");
            // takeAsync returns newest-first; render oldest-first for a readable transcript.
            for (int i = messages.size() - 1; i >= 0; i--) {
                Message m = messages.get(i);
                ObjectNode mn = arr.addObject();
                mn.put("id", m.getId());
                mn.put("authorId", m.getAuthor().getId());
                mn.put("authorName", m.getAuthor().getName());
                mn.put("bot", m.getAuthor().isBot());
                mn.put("timestamp", m.getTimeCreated().toString());
                mn.put("content", m.getContentDisplay());
                ArrayNode at = mn.putArray("attachments");
                m.getAttachments().forEach(a -> at.add(a.getUrl()));
            }
            return root.toString();
        });
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

    private static void ephemeral(ButtonInteractionEvent event, String msg) {
        event.reply(msg).setEphemeral(true).queue();
    }

    private static void ephemeral(EntitySelectInteractionEvent event, String msg) {
        event.reply(msg).setEphemeral(true).queue();
    }

    private static void ephemeral(ModalInteractionEvent event, String msg) {
        event.reply(msg).setEphemeral(true).queue();
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
