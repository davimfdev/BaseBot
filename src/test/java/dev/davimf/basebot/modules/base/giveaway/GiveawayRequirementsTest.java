package dev.davimf.basebot.modules.base.giveaway;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GiveawayRequirementsTest {
    private Giveaway g(String role, int days, int hours, int ws, int we) {
        return new Giveaway("id", "g", "c", null, "p", 0, 1, 0, false, role, days, hours, ws, we);
    }

    @Test
    void allInactivePasses() {
        assertNull(GiveawayRequirements.firstUnmet(g(null, 0, 0, -1, -1), false, 0, 0, false, 1000));
    }

    @Test
    void roleUnmet() {
        assertNotNull(GiveawayRequirements.firstUnmet(g("r1", 0, 0, -1, -1), false, 0, 0, false, 1000));
        assertNull(GiveawayRequirements.firstUnmet(g("r1", 0, 0, -1, -1), true, 0, 0, false, 1000));
    }

    @Test
    void daysUnmet() {
        long now = 10L * 86_400_000L;
        assertNotNull(GiveawayRequirements.firstUnmet(g(null, 5, 0, -1, -1), true, now - 2L * 86_400_000L, 0, false, now));
        assertNull(GiveawayRequirements.firstUnmet(g(null, 5, 0, -1, -1), true, now - 6L * 86_400_000L, 0, false, now));
    }

    @Test
    void voiceHoursUnmet() {
        assertNotNull(GiveawayRequirements.firstUnmet(g(null, 0, 5, -1, -1), true, 0, 4L * 3_600_000L, false, 1000));
        assertNull(GiveawayRequirements.firstUnmet(g(null, 0, 5, -1, -1), true, 0, 5L * 3_600_000L, false, 1000));
    }

    @Test
    void windowUnmet() {
        assertNotNull(GiveawayRequirements.firstUnmet(g(null, 0, 0, 20, 23), true, 0, 0, false, 1000));
        assertNull(GiveawayRequirements.firstUnmet(g(null, 0, 0, 20, 23), true, 0, 0, true, 1000));
    }
}
