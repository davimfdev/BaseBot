package dev.davimf.basebot.modules.base.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Counts member joins per guild in a sliding window (in-memory). Used to detect raids. */
public final class JoinWindow {

    private final long windowMs;
    private final Map<String, Deque<Long>> joins = new ConcurrentHashMap<>();

    public JoinWindow(long windowMs) {
        this.windowMs = windowMs;
    }

    /** Records one join for {@code guildId}, expires old ones, returns the current count in window. */
    public synchronized int record(String guildId, long nowMs) {
        Deque<Long> q = joins.computeIfAbsent(guildId, k -> new ArrayDeque<>());
        while (!q.isEmpty() && nowMs - q.peekFirst() > windowMs) {
            q.pollFirst();
        }
        q.addLast(nowMs);
        return q.size();
    }
}
