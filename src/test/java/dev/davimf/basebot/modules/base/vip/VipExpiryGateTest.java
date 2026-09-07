package dev.davimf.basebot.modules.base.vip;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 60s VIP expiry sweep must NOT query Neon unless something is actually due — otherwise it keeps
 * the serverless compute permanently awake. These cover the pure horizon logic that gates it.
 */
class VipExpiryGateTest {

    private static VipGrant grant(Instant expiresAt) {
        return new VipGrant("id", "g1", "p1", "u1", null, null, false,
                Instant.now(), expiresAt, true, VipProvisionStatus.ACTIVE, null, "by", null, null, Instant.now());
    }

    @Test
    void noTimedGrantsMeansFarFutureHorizonSoSweepStaysIdle() {
        Instant horizon = VipService.soonestExpiry(List.of(grant(null), grant(null)));
        assertEquals(Instant.MAX, horizon);
        assertFalse(VipService.expirySweepDue(Instant.now(), horizon), "sem prazo, nunca consulta o banco");
    }

    @Test
    void emptyGrantsMeansFarFutureHorizon() {
        assertEquals(Instant.MAX, VipService.soonestExpiry(List.of()));
    }

    @Test
    void horizonIsTheEarliestExpiry() {
        Instant soon = Instant.now().plusSeconds(60);
        Instant later = Instant.now().plusSeconds(3600);
        assertEquals(soon, VipService.soonestExpiry(List.of(grant(later), grant(soon), grant(null))));
    }

    @Test
    void sweepIdleBeforeHorizonButDueAfter() {
        Instant now = Instant.now();
        Instant horizon = now.plusSeconds(60);
        assertFalse(VipService.expirySweepDue(now, horizon), "antes do vencimento: no-op em memória");
        assertTrue(VipService.expirySweepDue(now.plusSeconds(61), horizon), "no/após vencimento: varre");
    }

    @Test
    void unloadedHorizonForcesACheck() {
        // Instant.MIN é o estado inicial "ainda não carregado": deve forçar uma consulta.
        assertTrue(VipService.expirySweepDue(Instant.now(), Instant.MIN));
    }
}
