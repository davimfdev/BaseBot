// [OUTLINE START]
// Package: dev.davimf.basebot.database.model
// 
// Record: TicketCategory
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String name
//   - Record Component : public final String emoji
//   - Record Component : public final // channel-name-safe unicode emoji
//   - Record Component : public final or "" / null String description
//   - Record Component : public final String discordCategoryId
//   - Record Component : public final // Discord parent category id List<String> staffRoleIds // roles allowed to handle this ticket type
// [OUTLINE END]



package dev.davimf.basebot.database.model;

import java.util.List;

/**
 * A ticket category/type (BOTSPECS Module 2). A guild can have one or more; each drives
 * a panel button and opens a private channel under {@code discordCategoryId}.
 */
public record TicketCategory(
        String id,
        String guildId,
        String name,
        String emoji,              // channel-name-safe unicode emoji, or "" / null
        String description,
        String discordCategoryId,  // Discord parent category id
        List<String> staffRoleIds  // roles allowed to handle this ticket type
) {}
