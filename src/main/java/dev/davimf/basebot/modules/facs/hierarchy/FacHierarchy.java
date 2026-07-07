// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.hierarchy
// 
// Class: FacHierarchy
// 
// Constructors:
//   - `Constructor` : `private FacHierarchy()`
// 
// Methods:
//   - `Method` : `public static final List<Level> LEVELS = List. of(new Level(,)`
//   - `Method` : `public static List<String> keys()`
// 
// Record: Level
// 
// Record Components:
//   - Record Component : public final String key
//   - Record Component : public final String label
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.hierarchy;

import java.util.List;

/**
 * The FiveM faction chain of command (BOTSPECS Module 4), ordered top → bottom. Each
 * level maps to a server role configured in {@code /setup → Cargos} (its {@code key} is
 * the guild_config.roles key). Rank is the list index: a lower index outranks a higher
 * one. Used by the {@code /hierarquia} panel and rank-gated commands.
 */
public final class FacHierarchy {

    /** One rung of the chain of command. */
    public record Level(String key, String label) {}

    public static final List<Level> LEVELS = List.of(
            new Level("lider", "Líder"),
            new Level("sub-lider", "Sub-Líder"),
            new Level("gerente-geral", "Gerente Geral"),
            new Level("gerente-vendas", "Gerente de Vendas"),
            new Level("gerente-elite", "Gerente de Elite"),
            new Level("gerente-elite-feminina", "Gerente de Elite Feminina"),
            new Level("gerente-recrutamento", "Gerente de Recrutamento"),
            new Level("gerente-farm", "Gerente de Farm"),
            new Level("recrutador", "Recrutador"),
            new Level("elite", "Elite"),
            new Level("elite-feminina", "Elite Feminina"),
            new Level("membro", "Membro"),
            new Level("sem-set", "Sem Set")
    );

    private FacHierarchy() {}

    /** The configured role keys in chain order (top → bottom). */
    public static List<String> keys() {
        return LEVELS.stream().map(Level::key).toList();
    }
}
