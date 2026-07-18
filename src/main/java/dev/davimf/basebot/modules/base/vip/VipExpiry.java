package dev.davimf.basebot.modules.base.vip;

import java.time.Instant;
import java.util.List;

/** Decisões puras de expiração/validade de grants. */
public final class VipExpiry {
    private VipExpiry() {}

    public static boolean isExpired(VipGrant g, Instant now) {
        return g.active() && g.expiresAt() != null && !g.expiresAt().isAfter(now);
    }

    /** Grant vale para bônus? (ativo e não vencido, mesmo que o sweep ainda não rodou.) */
    public static boolean effective(VipGrant g, Instant now) {
        return g.active() && !isExpired(g, now);
    }

    public static List<VipGrant> due(List<VipGrant> grants, Instant now) {
        return grants.stream().filter(g -> isExpired(g, now)).toList();
    }
}
