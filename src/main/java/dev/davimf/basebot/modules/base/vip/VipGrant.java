package dev.davimf.basebot.modules.base.vip;

import java.time.Instant;

public record VipGrant(
        String id,
        String guildId,
        String planId,
        String userId,
        String callChannelId,
        String controlRoleId,
        boolean revealOnOccupancy,
        Instant grantedAt,
        Instant expiresAt,
        boolean active,
        VipProvisionStatus provisionStatus,
        String provisionError,
        String grantedBy,
        Instant revokedAt,
        String revokedBy,
        Instant updatedAt
) {}
