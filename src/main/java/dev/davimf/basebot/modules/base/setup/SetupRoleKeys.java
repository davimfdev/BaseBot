package dev.davimf.basebot.modules.base.setup;

import java.util.List;
import java.util.Map;

/** Logical role slots that {@code /setup} can map to server roles (BOTSPECS Module 1). */
public final class SetupRoleKeys {

    private SetupRoleKeys() {}

    /** key -> human label. Keys are the stable identifiers stored in guild_config.roles. */
    public static final List<Map.Entry<String, String>> OPTIONS = List.of(
            Map.entry("moderador", "Moderador"),
            Map.entry("staff", "Staff"),
            Map.entry("mutado", "Cargo de Mutado"),
            Map.entry("vendedor", "Vendedor (Pix)")
    );

    public static String labelFor(String key) {
        return OPTIONS.stream().filter(e -> e.getKey().equals(key))
                .map(Map.Entry::getValue).findFirst().orElse(key);
    }
}
