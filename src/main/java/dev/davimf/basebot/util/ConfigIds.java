package dev.davimf.basebot.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lê as listas de IDs separadas por vírgula guardadas em {@code GuildConfig.settings}.
 * Tolerante ao que o dashboard e os selects da JDA produzem: {@code null}, vazio, espaços,
 * vírgulas soltas e duplicatas.
 */
public final class ConfigIds {

    private ConfigIds() {}

    /** Ordem de inserção preservada, sem duplicatas, sem vazios. */
    public static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
