package dev.davimf.basebot.modules.base.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts AutoMod violations per user in a sliding window; fires when a user reaches
 * {@code warnPer} within {@code windowMs}. In-memory (short windows — losing state on a
 * restart is acceptable, unlike the voice tracker). When {@code windowMs <= 0} every
 * {@code warnPer}-th violation fires immediately (no expiry).
 */
public final class ViolationWindow {

    private final int warnPer;
    private final long windowMs;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public ViolationWindow(int warnPer, long windowMs) {
        this.warnPer = Math.max(1, warnPer);
        this.windowMs = windowMs;
    }

    /** Records one violation for {@code userId}; returns true when a warn should be applied
     *  (limit reached). Resets that user's counter when it fires. */
    public synchronized boolean record(String userId, long nowMs) {
        Deque<Long> q = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        if (windowMs > 0) {
            while (!q.isEmpty() && nowMs - q.peekFirst() > windowMs) {
                q.pollFirst();
            }
        }
        q.addLast(nowMs);
        if (q.size() >= warnPer) {
            q.clear();
            return true;
        }
        return false;
    }
}
