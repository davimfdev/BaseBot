package dev.davimf.basebot.modules.base.economy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JobOutcomeTest {
    @Test
    void rewardStaysInRange() {
        for (long roll = 0; roll < 1000; roll++) {
            long r = JobOutcome.reward(60, 120, roll);
            assertTrue(r >= 60 && r <= 120, "fora do range: " + r);
        }
    }

    @Test
    void rewardIsDeterministicByRoll() {
        assertEquals(JobOutcome.reward(60, 120, 7), JobOutcome.reward(60, 120, 7));
        assertEquals(60, JobOutcome.reward(60, 120, 0));
        assertEquals(120, JobOutcome.reward(60, 120, 60)); // span=61 → roll 60 = max
    }

    @Test
    void rewardHandlesEqualMinMax() {
        assertEquals(100, JobOutcome.reward(100, 100, 999));
    }
}
