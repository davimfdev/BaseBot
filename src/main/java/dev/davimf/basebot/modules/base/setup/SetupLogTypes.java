// [OUTLINE START]
// Package: dev.davimf.basebot.modules.base.setup
// 
// Class: SetupLogTypes
// 
// Constructors:
//   - `Constructor` : `private SetupLogTypes()`
// 
// Methods:
//   - `Method` : `public static final List<LogType> ALL = List. of(new LogType(, ,)`
//   - `Method` : `public static String labelFor(String key)`
//   - `Method` : `public static List<List<LogType>> pages(int maxPerPage)`
// 
// Record: LogType
// 
// Record Components:
//   - Record Component : public final String key
//   - Record Component : public final String label
//   - Record Component : public final String module
// [OUTLINE END]



package dev.davimf.basebot.modules.base.setup;

import dev.davimf.basebot.util.Emojis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every log category has its OWN channel (BOTSPECS + user requirement) — never a single
 * "general log". Each entry's {@code key} is stored in {@code guild_config.channels} as
 * the configured channel id for that log. Listeners/commands read the channel by key.
 *
 * <p>Grouped by module for the {@code /setup → Logs} selector (22 types, paginated to
 * stay within the 25 StringSelect option limit per page).
 */
public final class SetupLogTypes {

    /** A single log channel slot. {@code key} is the guild_config.channels key. */
    public record LogType(String key, String label, String module) {}

    public static final List<LogType> ALL = List.of(
            // " + Emojis.of(Emojis.WRENCH, "🛠️") + " Base (logs gerais e moderação)
            new LogType("log-comandos", "Comandos", "Base"),
            new LogType("log-mensagens", "Mensagens (del/edit/fix)", "Base"),
            new LogType("log-entradas", "Entradas (join)", "Base"),
            new LogType("log-saidas", "Saídas (leave)", "Base"),
            new LogType("log-membros", "Membros (apelido/nome/cargos/timeout)", "Base"),
            new LogType("log-voz", "Voz (call)", "Base"),
            new LogType("log-canais", "Canais", "Base"),
            new LogType("log-cargos", "Cargos", "Base"),
            new LogType("log-servidor", "Servidor", "Base"),
            new LogType("log-bans", "Banimentos", "Base"),
            new LogType("log-kicks", "Expulsões", "Base"),
            new LogType("log-moderacao", "Moderação (casos)", "Base"),
            new LogType("log-formularios", "Formulários", "Base"),
            new LogType("log-loja", "Loja", "Base"),
            // " + Emojis.of(Emojis.TICKET, "🎟️") + " Tickets
            new LogType("log-tickets", "Tickets", "Tickets"),
            // " + Emojis.of(Emojis.SALES, "🛒") + " Vendas
            new LogType("log-orcamentos", "Orçamentos", "Vendas"),
            new LogType("log-vendas", "Vendas", "Vendas"),
            // " + Emojis.of(Emojis.WEAPON, "🔫") + " Facs (FiveM)
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
