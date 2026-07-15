package dev.davimf.basebot.modules.base.moderation;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Marca mensagens apagadas pelos comandos de limpeza ({@code /purge}, {@code /clear}, {@code /cl})
 * para que o log de mensagens NÃO registre essas deleções — do contrário uma limpeza de dezenas
 * de mensagens floodaria o canal de log. Os IDs expiram sozinhos após uma janela curta, então o
 * mapa não cresce indefinidamente mesmo que algum evento de deleção nunca chegue.
 */
public final class PurgeLogSuppressor {

    /** Janela de validade de um ID marcado (o evento de deleção chega poucos segundos depois). */
    private static final long TTL_MS = 30_000L;

    private final ConcurrentHashMap<Long, Long> purged = new ConcurrentHashMap<>();

    /** Marca IDs que estão prestes a ser apagados por um comando de limpeza. Chamar ANTES do purge. */
    public void mark(Collection<Long> messageIds) {
        long expiry = System.currentTimeMillis() + TTL_MS;
        for (Long id : messageIds) {
            purged.put(id, expiry);
        }
        prune();
    }

    /** {@code true} se a mensagem foi apagada por um comando de limpeza; consome o registro. */
    public boolean claim(long messageId) {
        Long expiry = purged.remove(messageId);
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    /** {@code true} se algum ID do lote veio de um comando de limpeza (usado na deleção em massa). */
    public boolean claimAny(Collection<Long> messageIds) {
        boolean hit = false;
        for (Long id : messageIds) {
            if (claim(id)) {
                hit = true;
            }
        }
        return hit;
    }

    private void prune() {
        if (purged.size() < 1000) {
            return;
        }
        long now = System.currentTimeMillis();
        purged.entrySet().removeIf(e -> e.getValue() < now);
    }
}
