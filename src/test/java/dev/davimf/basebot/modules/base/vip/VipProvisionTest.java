package dev.davimf.basebot.modules.base.vip;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.davimf.basebot.modules.base.vip.VipProvision.ResourceAction.*;

class VipProvisionTest {
    @Test void skips_when_plan_does_not_want() {
        assertEquals(SKIP, VipProvision.forResource(false, null, false));
        assertEquals(SKIP, VipProvision.forResource(false, "x", true));
    }
    @Test void reuses_when_saved_and_exists() {
        assertEquals(REUSE, VipProvision.forResource(true, "id", true));
    }
    @Test void creates_when_missing_or_gone() {
        assertEquals(CREATE, VipProvision.forResource(true, null, false));
        assertEquals(CREATE, VipProvision.forResource(true, "id", false)); // sumiu no Discord
        assertEquals(CREATE, VipProvision.forResource(true, "", true)); // blank id → CREATE, not REUSE
    }
}
