package dev.davimf.basebot.modules.base.utility;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registro de ausências (AFK) em memória, por guild+usuário. */
public final class AfkRegistry {

    public record Afk(String reason, long since) {}

    private final ConcurrentHashMap<String, Afk> afk = new ConcurrentHashMap<>();

    private static String key(String guildId, String userId) {
        return guildId + ":" + userId;
    }

    public void set(String guildId, String userId, String reason, long since) {
        afk.put(key(guildId, userId), new Afk(reason, since));
    }

    public Optional<Afk> get(String guildId, String userId) {
        return Optional.ofNullable(afk.get(key(guildId, userId)));
    }

    public Optional<Afk> remove(String guildId, String userId) {
        return Optional.ofNullable(afk.remove(key(guildId, userId)));
    }
}
