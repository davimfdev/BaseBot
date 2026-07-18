package dev.davimf.basebot.modules.base.vip;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VipBonusCacheTest {
    private final Instant now = Instant.parse("2026-07-18T12:00:00Z");
    private VipPlan plan(String id, int xp, int eco) {
        return new VipPlan(id,"g","n",null,true,null,true,xp,eco,null,true,true,0,Instant.EPOCH,Instant.EPOCH);
    }
    private VipGrant grant(String user, String planId, Instant expires) {
        return new VipGrant("gr-"+user,"g",planId,user,null,null,true,
                Instant.EPOCH, expires, true, VipProvisionStatus.ACTIVE, null, null, null, null, Instant.EPOCH);
    }

    @Test void active_grant_maps_to_plan_bonus_capped() {
        var cache = VipService.computeCache(
                List.of(grant("u1","p1", now.plusSeconds(60))),
                Map.of("p1", plan("p1", 250, 30)), now, 100);
        assertEquals(new VipBonusValue(100, 30), cache.get("g").get("u1")); // xp capado em 100
    }

    @Test void expired_grant_excluded_before_sweep() {
        var cache = VipService.computeCache(
                List.of(grant("u1","p1", now.minusSeconds(1))),
                Map.of("p1", plan("p1", 50, 50)), now, 100);
        assertTrue(cache.isEmpty() || cache.getOrDefault("g", Map.of()).isEmpty());
    }

    @Test void missing_plan_yields_no_bonus() {
        var cache = VipService.computeCache(
                List.of(grant("u1","ghost", now.plusSeconds(60))), Map.of(), now, 100);
        assertTrue(cache.getOrDefault("g", Map.of()).isEmpty());
    }
}
