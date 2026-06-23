package dev.davimf.basebot.ratelimit;

import dev.davimf.basebot.ratelimit.ProfileRateLimiter.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfileRateLimiterTest {

    private static final long HOUR = 3_600_000L;
    private static final long T0 = 1_000_000L;

    @Test
    void allowsUpToLimitThenBlocksWithinWindow() {
        ProfileRateLimiter rl = new ProfileRateLimiter(2, HOUR);
        assertTrue(rl.check("name", T0).allowed());
        assertTrue(rl.check("name", T0 + 1_000).allowed());
        Decision third = rl.check("name", T0 + 2_000);
        assertFalse(third.allowed());
        assertTrue(third.retryAfterMillis() > 0);
    }

    @Test
    void allowsAgainOnceWindowHasPassed() {
        ProfileRateLimiter rl = new ProfileRateLimiter(2, HOUR);
        rl.check("name", T0);
        rl.check("name", T0 + 1_000);
        assertTrue(rl.check("name", T0 + HOUR).allowed(), "oldest hit expired -> a slot frees");
    }

    @Test
    void keysAreIndependent() {
        ProfileRateLimiter rl = new ProfileRateLimiter(2, HOUR);
        rl.check("name", T0);
        rl.check("name", T0);
        assertTrue(rl.check("icon", T0).allowed());
    }
}
