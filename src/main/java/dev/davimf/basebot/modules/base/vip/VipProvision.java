package dev.davimf.basebot.modules.base.vip;

/** Decisão pura de provisionamento idempotente por recurso (call, cargo-controle). */
public final class VipProvision {
    private VipProvision() {}
    public enum ResourceAction { REUSE, CREATE, SKIP }

    public static ResourceAction forResource(boolean planWantsIt, String savedId, boolean existsInDiscord) {
        if (!planWantsIt) return ResourceAction.SKIP;
        if (savedId != null && !savedId.isBlank() && existsInDiscord) return ResourceAction.REUSE;
        return ResourceAction.CREATE;
    }
}
