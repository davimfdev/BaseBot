package dev.davimf.basebot.modules.base.vip;

import java.time.Instant;

public record VipPlan(
        String id,
        String guildId,
        String name,
        String discordCategoryId,
        boolean hasCall,
        String vipRoleId,
        boolean useControlRole,
        int xpBonusPct,
        int ecoBonusPct,
        Long defaultDurationMinutes,
        boolean revealDefault,
        boolean enabled,
        int position,
        Instant createdAt,
        Instant updatedAt
) {}
