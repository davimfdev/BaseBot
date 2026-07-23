package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.database.postgres.ActionTypeRepository.ActionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quebra as ações salvas em grupos que cabem num select do Discord.
 *
 * <p>O limite é de 25 opções por menu, e uma cidade passa disso somando pequenas e
 * grandes. Em vez de paginar com botões, cada grupo vira seu próprio menu — a
 * categoria já é a divisão natural, e um grupo que um dia estourar 25 se parte em
 * menus extras ("Pequenas · 2/2") sem nenhum estado de página para manter.
 *
 * <p>Nada é descartado: ações sem categoria caem no grupo "Sem categoria" e continuam
 * selecionáveis.
 */
public final class ActionTypeGroups {

    /** Discord allows at most 25 options in a select menu. */
    public static final int MAX_OPTIONS = 25;

    /** Um menu: rótulo já pronto para exibição e as ações que ele lista. */
    public record Group(String label, List<ActionType> types) {}

    private ActionTypeGroups() {}

    /**
     * Agrupa por categoria (Grandes, Pequenas, Sem categoria) e parte cada grupo em
     * blocos de no máximo {@value #MAX_OPTIONS}. Grupos vazios são omitidos.
     */
    public static List<Group> chunked(List<ActionType> all) {
        Map<ActionCategory, List<ActionType>> byCategory = new LinkedHashMap<>();
        for (ActionCategory c : ActionCategory.values()) {
            byCategory.put(c, new ArrayList<>());
        }
        List<ActionType> uncategorized = new ArrayList<>();
        for (ActionType t : all) {
            if (t.category() == null) {
                uncategorized.add(t);
            } else {
                byCategory.get(t.category()).add(t);
            }
        }

        List<Group> out = new ArrayList<>();
        byCategory.forEach((category, types) -> addChunks(out, category.plural(), types));
        addChunks(out, "Sem categoria", uncategorized);
        return out;
    }

    private static void addChunks(List<Group> out, String label, List<ActionType> types) {
        if (types.isEmpty()) {
            return;
        }
        int chunks = (types.size() + MAX_OPTIONS - 1) / MAX_OPTIONS;
        for (int i = 0; i < chunks; i++) {
            List<ActionType> slice =
                    types.subList(i * MAX_OPTIONS, Math.min(types.size(), (i + 1) * MAX_OPTIONS));
            String suffix = chunks == 1 ? "" : " · " + (i + 1) + "/" + chunks;
            out.add(new Group(label + suffix, List.copyOf(slice)));
        }
    }
}
