package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.modules.base.economy.ActionStatus.Kind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActionStatusTest {

    @Test
    void jailedBeatsEverythingWhenNotExempt() {
        // preso, sem equipamento, em cooldown — mesmo assim JAILED (não isento)
        ActionStatus s = ActionStatus.resolve(1000, 0, 3600, false, true, false);
        assertEquals(Kind.JAILED, s.kind());
        assertEquals(0, s.readyAt());
    }

    @Test
    void exemptActionIgnoresJail() {
        // /daily é isento: mesmo preso, cai nas regras normais (aqui: READY)
        ActionStatus s = ActionStatus.resolve(10_000, 0, 1, true, true, true);
        assertEquals(Kind.READY, s.kind());
    }

    @Test
    void lockedBeatsCooldown() {
        // sem equipamento vence cooldown ativo
        ActionStatus s = ActionStatus.resolve(1000, 1000, 3600, false, false, false);
        assertEquals(Kind.LOCKED, s.kind());
        assertEquals(0, s.readyAt());
    }

    @Test
    void cooldownWhenActiveElseReady() {
        // ativo: now < lastTs + cd*1000
        ActionStatus active = ActionStatus.resolve(1000, 1000, 60, true, false, false);
        assertEquals(Kind.COOLDOWN, active.kind());
        assertEquals(1000 + 60_000, active.readyAt());
        // expirado
        ActionStatus ready = ActionStatus.resolve(1000 + 60_001, 1000, 60, true, false, false);
        assertEquals(Kind.READY, ready.kind());
        assertEquals(0, ready.readyAt());
    }

    @Test
    void readyWhenNeverUsed() {
        // "nunca usado" = lastTs 0; com now realista (epoch), 0 + cd*1000 já passou → READY.
        ActionStatus s = ActionStatus.resolve(10_000_000L, 0, 3600, true, false, false);
        assertEquals(Kind.READY, s.kind());
    }
}
