package dev.davimf.basebot.database.model;

/**
 * A live ticket tracked in SQLite. Holds the mapped Discord IDs needed to drive the
 * dashboard and the closure pipeline (text + voice channel deletion by ID).
 */
public record ActiveTicket(
        String id,
        String guildId,
        String textChannelId,
        String voiceChannelId,   // nullable until "Criar Call"
        String creatorId,
        String assignedStaffId,  // nullable until "Assumir Atendimento"
        String suffix,
        String status            // OPEN | CLOSING | CLOSED
) {
    public static final String OPEN = "OPEN";
    public static final String CLOSING = "CLOSING";
    public static final String CLOSED = "CLOSED";
}
