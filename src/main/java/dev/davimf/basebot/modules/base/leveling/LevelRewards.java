package dev.davimf.basebot.modules.base.leveling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Coleta os cargos de recompensa para todos os níveis cruzados num level-up (puro). */
public final class LevelRewards {

    private LevelRewards() {}

    /** Cargos de {@code rewards} para os níveis {@code (from, to]} (destino incluído). */
    public static List<String> rolesForCrossedLevels(Map<Integer, String> rewards, int from, int to) {
        List<String> out = new ArrayList<>();
        for (int level = from + 1; level <= to; level++) {
            String roleId = rewards.get(level);
            if (roleId != null && !roleId.isBlank()) {
                out.add(roleId);
            }
        }
        return out;
    }
}
