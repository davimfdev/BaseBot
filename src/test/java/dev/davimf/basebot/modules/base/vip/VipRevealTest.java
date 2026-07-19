package dev.davimf.basebot.modules.base.vip;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.davimf.basebot.modules.base.vip.VipReveal.RevealAction.*;

class VipRevealTest {
    @Test void reveals_when_first_allowed_human_joins() {
        assertEquals(REVEAL, VipReveal.decide(true, 1, false));
    }
    @Test void hides_when_last_leaves() {
        assertEquals(HIDE, VipReveal.decide(true, 0, true));
    }
    @Test void noop_when_already_in_target_state() {
        assertEquals(NONE, VipReveal.decide(true, 2, true));
        assertEquals(NONE, VipReveal.decide(true, 0, false));
    }
    @Test void hides_if_reveal_disabled_but_currently_revealed() {
        assertEquals(HIDE, VipReveal.decide(false, 3, true));
        assertEquals(NONE, VipReveal.decide(false, 3, false));
    }
}
