// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: ActiveTicket
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String textChannelId
//   - Record Component : public final String voiceChannelId
//   - Record Component : public final // nullable until "Criar Call" String creatorId
//   - Record Component : public final String assignedStaffId
//   - Record Component : public final // nullable until "Assumir Atendimento" String suffix
//   - Record Component : public final String status
//   - Record Component : public final // OPEN | CLOSING | CLOSED String reason
//   - Record Component : public final // reason the member gave when opening (asked via modal) String emoji // category emoji
//   - Record Component : public final kept in the channel name + header
// 
// Fields:
//   - `Field` : `public static final String OPEN`
//   - `Field` : `public static final String CLOSING`
//   - `Field` : `public static final String CLOSED`
// [OUTLINE END]



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
        String status,           // OPEN | CLOSING | CLOSED
        String reason,           // reason the member gave when opening (asked via modal)
        String emoji             // category emoji, kept in the channel name + header
) {
    public static final String OPEN = "OPEN";
    public static final String CLOSING = "CLOSING";
    public static final String CLOSED = "CLOSED";
}
