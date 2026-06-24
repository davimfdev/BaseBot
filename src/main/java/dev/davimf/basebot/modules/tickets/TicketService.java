package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.crypto.TicketCrypto;
import dev.davimf.basebot.database.model.ActiveTicket;
import dev.davimf.basebot.database.model.TicketCategory;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Encapsulates the ticket closure &amp; transcript pipeline (BOTSPECS §Closure & Transcript):
 *
 * <ol>
 *   <li>Fetch message history by the saved text-channel id.</li>
 *   <li>AES-encrypt the transcript + generate a one-time password.</li>
 *   <li>POST the encrypted bundle to davimf.dev ({@code /api/ticket-store}).</li>
 *   <li>Delete BOTH the text and voice channels by their saved ids.</li>
 *   <li>Send the closure embed (with password + transcript link) to #log-tickets and the user's DM.</li>
 * </ol>
 *
 * <p>Steps 2–3 (the encryption + ingest, which must match the dashboard byte-for-byte)
 * are implemented in {@link #buildTranscriptLink}. The JDA-side history fetch, channel
 * deletion and embed delivery are scaffolded for the UI layer to call.
 */
public final class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final BotContext ctx;

    public TicketService(BotContext ctx) {
        this.ctx = ctx;
    }

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
            channel.sendMessageComponents(TicketView.dashboard(ticketId, header)).useComponentsV2().queue();
            event.getHook().sendMessage("Ticket criado: " + channel.getAsMention()).queue();
        }, err -> event.getHook().sendMessage("Falha ao criar o ticket: " + err.getMessage()).queue());
    }

    /**
     * Basic close: marks the ticket closed and deletes its channel after a short delay.
     * TODO(Module 2): render + AES-encrypt the transcript, POST it to davimf.dev, and send
     * the closure embed (password + link) to the ticket log channel and the creator's DM
     * (see {@link #buildTranscriptLink}).
     */
    public void closeTicket(ButtonInteractionEvent event, String ticketId) {
        Optional<ActiveTicket> maybe = ctx.database().tickets().findById(ticketId);
        if (maybe.isEmpty() || event.getGuild() == null) {
            event.reply("Ticket não encontrado.").setEphemeral(true).queue();
            return;
        }
        ActiveTicket t = maybe.get();
        ctx.database().tickets().setStatus(ticketId, ActiveTicket.CLOSED);
        ctx.database().actionLogs().log(t.guildId(), event.getUser().getId(), t.creatorId(),
                "TICKET_CLOSE", ticketId);
        event.reply("🔒 Ticket fechado por " + event.getUser().getAsMention()
                + ". O canal será removido em 5 segundos.").queue();

        TextChannel channel = event.getGuild().getTextChannelById(t.textChannelId());
        if (channel != null) {
            channel.delete().reason("Ticket fechado por " + event.getUser().getName())
                    .queueAfter(5, TimeUnit.SECONDS);
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Encrypts a rendered transcript, stores it on the dashboard, and returns the
     * one-time password + public link to show the closer and DM the creator.
     *
     * @param ticket           the active ticket being closed
     * @param guildName        guild display name (for the transcript row)
     * @param channelName      ticket channel name (for display)
     * @param transcriptJson   the fully-rendered transcript as a JSON string
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

    // TODO(Module 2): renderTranscript(textChannelId) -> JSON, deleteChannels(ticket),
    // sendClosureEmbed(logChannel, creatorDm, result). These call JDA and the saved IDs.

    /** Password (shown once) + public transcript URL for the closure embed/DM. */
    public record ClosureResult(String password, String transcriptUrl) {}
}
