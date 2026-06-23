package dev.davimf.basebot.ratelimit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory rolling-window rate limiter for global bot-profile changes. Discord caps
 * username/avatar updates at 2 per hour (BOTSPECS §API Limitations); this enforces that
 * locally and reports how long until the next slot frees so the user can be informed.
 */
public final class ProfileRateLimiter {

    /** Outcome of a {@link #check}: whether it was allowed and, if not, the wait in ms. */
    public record Decision(boolean allowed, long retryAfterMillis) {}

    private final int maxPerWindow;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new HashMap<>();

    public ProfileRateLimiter(int maxPerWindow, long windowMillis) {
        this.maxPerWindow = maxPerWindow;
        this.windowMillis = windowMillis;
    }

    /** Consumes a slot for {@code key} when allowed; otherwise reports the retry-after. */
    public synchronized Decision check(String key, long nowMillis) {
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!window.isEmpty() && nowMillis - window.peekFirst() >= windowMillis) {
            window.pollFirst();
        }
        if (window.size() >= maxPerWindow) {
            long retryAfter = windowMillis - (nowMillis - window.peekFirst());
            return new Decision(false, Math.max(0, retryAfter));
        }
        window.addLast(nowMillis);
        return new Decision(true, 0L);
    }
}
