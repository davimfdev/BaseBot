package dev.davimf.basebot.modules.base.setup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every log category has its OWN channel (BOTSPECS + user requirement) — never a single
 * "general log". Each entry's {@code key} is stored in {@code guild_config.channels} as
 * the configured channel id for that log. Listeners/commands read the channel by key.
 *
 * <p>Grouped by module for the {@code /setup → Logs} selector (19 types, within the 25
 * StringSelect option limit).
 */
public final class SetupLogTypes {

    /** A single log channel slot. {@code key} is the guild_config.channels key. */
    public record LogType(String key, String label, String module) {}

    public static final List<LogType> ALL = List.of(
            // 🛠️ Base (logs gerais e moderação)
            new LogType("log-comandos", "Comandos", "Base"),
            new LogType("log-msgdel", "Mensagens deletadas", "Base"),
            new LogType("log-msgedit", "Mensagens editadas", "Base"),
            new LogType("log-entradas", "Entradas (join)", "Base"),
            new LogType("log-saidas", "Saídas (leave)", "Base"),
            new LogType("log-voz", "Voz (call)", "Base"),
            new LogType("log-bans", "Banimentos", "Base"),
            new LogType("log-kicks", "Expulsões", "Base"),
            new LogType("log-formularios", "Formulários", "Base"),
            // 🎟️ Tickets
            new LogType("log-tickets", "Tickets", "Tickets"),
            // 🛒 Vendas
            new LogType("log-orcamentos", "Orçamentos", "Vendas"),
            new LogType("log-vendas", "Vendas", "Vendas"),
            // 🔫 Facs (FiveM)
            new LogType("log-farm", "Farm", "Facs"),
            new LogType("log-punicoes", "Punições", "Facs"),
            new LogType("log-hierarquia", "Hierarquia", "Facs"),
            new LogType("log-financeiro", "Financeiro", "Facs"),
            new LogType("log-acoes", "Ações", "Facs"),
            new LogType("log-pds", "PDs", "Facs"),
            new LogType("log-sets", "Solicitações de Set", "Facs")
    );

    private SetupLogTypes() {}

    public static String labelFor(String key) {
        return ALL.stream().filter(t -> t.key().equals(key))
                .map(LogType::label).findFirst().orElse(key);
    }

    /**
     * Splits the log types into screens, grouped by module (each page belongs to one
     * module), chunked so no page exceeds {@code maxPerPage} selects — keeping every
     * screen within Discord's component limit while showing as many as fit.
     */
    public static List<List<LogType>> pages(int maxPerPage) {
        Map<String, List<LogType>> byModule = new LinkedHashMap<>();
        for (LogType t : ALL) {
            byModule.computeIfAbsent(t.module(), k -> new ArrayList<>()).add(t);
        }
        List<List<LogType>> pages = new ArrayList<>();
        for (List<LogType> group : byModule.values()) {
            for (int i = 0; i < group.size(); i += maxPerPage) {
                pages.add(List.copyOf(group.subList(i, Math.min(group.size(), i + maxPerPage))));
            }
        }
        return pages;
    }
}
