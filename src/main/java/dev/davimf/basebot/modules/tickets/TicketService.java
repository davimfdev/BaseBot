package dev.davimf.basebot.modules.tickets;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.crypto.TicketCrypto;
import dev.davimf.basebot.database.model.ActiveTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
