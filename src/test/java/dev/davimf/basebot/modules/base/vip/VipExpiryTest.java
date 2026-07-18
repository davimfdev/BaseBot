package dev.davimf.basebot.modules.base.vip;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class VipExpiryTest {
    private VipGrant g(boolean active, Instant expires) {
        return new VipGrant("id","g","p","u",null,null,true,
                Instant.EPOCH, expires, active, VipProvisionStatus.ACTIVE, null, null, null, null, Instant.EPOCH);
    }
    private final Instant now = Instant.parse("2026-07-18T12:00:00Z");

    @Test void permanent_never_expires() { assertFalse(VipExpiry.isExpired(g(true, null), now)); }
    @Test void past_expiry_is_expired() { assertTrue(VipExpiry.isExpired(g(true, now.minusSeconds(1)), now)); }
    @Test void future_expiry_not_expired() { assertFalse(VipExpiry.isExpired(g(true, now.plusSeconds(60)), now)); }
    @Test void inactive_never_expired() { assertFalse(VipExpiry.isExpired(g(false, now.minusSeconds(1)), now)); }

    @Test void effective_ignores_expired_before_sweep() {
        assertTrue(VipExpiry.effective(g(true, now.plusSeconds(60)), now));
        assertFalse(VipExpiry.effective(g(true, now.minusSeconds(1)), now));
    }

    @Test void due_filters_expired() {
        var live = g(true, now.plusSeconds(60));
        var dead = g(true, now.minusSeconds(1));
        assertEquals(List.of(dead), VipExpiry.due(List.of(live, dead), now));
    }
}
