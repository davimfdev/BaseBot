package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.modules.facs.hierarchy.FacHierarchy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Logical role slots that {@code /setup} can map to server roles (BOTSPECS Module 1 + 4). */
public final class SetupRoleKeys {

    private SetupRoleKeys() {}

    /** key -> human label. Keys are the stable identifiers stored in guild_config.roles. */
    public static final List<Map.Entry<String, String>> OPTIONS = build();

    private static List<Map.Entry<String, String>> build() {
        List<Map.Entry<String, String>> all = new ArrayList<>(List.of(
                Map.entry("moderador", "Moderador"),
                Map.entry("staff", "Staff"),
                Map.entry("mutado", "Cargo de Mutado"),
                Map.entry("nao-verificado", "Não-verificado"),
                Map.entry("vendedor", "Vendedor (Pix)")
        ));
        // Module 4 FiveM chain of command (top -> bottom), configured here as role slots.
        for (FacHierarchy.Level level : FacHierarchy.LEVELS) {
            all.add(Map.entry(level.key(), level.label()));
        }
        return List.copyOf(all);
    }

    public static String labelFor(String key) {
        return OPTIONS.stream().filter(e -> e.getKey().equals(key))
                .map(Map.Entry::getValue).findFirst().orElse(key);
    }
}
