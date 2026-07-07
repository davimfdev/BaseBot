package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VoiceEligibilityTest {
    @Test
    void eligibleWithTwoHumansNotDeafNotAfk() {
        assertTrue(VoiceEligibility.isEligible(false, 2, false, false));
    }

    @Test
    void notEligibleAlone() {
        assertFalse(VoiceEligibility.isEligible(false, 1, false, false));
    }

    @Test
    void notEligibleWhenBotDeafOrAfk() {
        assertFalse(VoiceEligibility.isEligible(true, 5, false, false));
        assertFalse(VoiceEligibility.isEligible(false, 5, true, false));
        assertFalse(VoiceEligibility.isEligible(false, 5, false, true));
    }
}
