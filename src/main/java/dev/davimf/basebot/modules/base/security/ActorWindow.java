package dev.davimf.basebot.modules.base.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Conta ações destrutivas por ator numa janela deslizante (em memória). Anti-nuke. */
public final class ActorWindow {

    private final long windowMs;
    private final Map<String, Deque<Long>> actions = new ConcurrentHashMap<>();

    public ActorWindow(long windowMs) {
        this.windowMs = windowMs;
    }

    /** Registra uma ação de {@code actorKey}, expira as antigas, retorna a contagem na janela. */
    public synchronized int record(String actorKey, long nowMs) {
        Deque<Long> q = actions.computeIfAbsent(actorKey, k -> new ArrayDeque<>());
        while (!q.isEmpty() && nowMs - q.peekFirst() > windowMs) {
            q.pollFirst();
        }
        q.addLast(nowMs);
        return q.size();
    }
}
