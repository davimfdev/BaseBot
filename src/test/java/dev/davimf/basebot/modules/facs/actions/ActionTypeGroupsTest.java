package dev.davimf.basebot.modules.facs.actions;

import dev.davimf.basebot.database.postgres.ActionTypeRepository.ActionType;
import dev.davimf.basebot.modules.facs.actions.ActionTypeGroups.Group;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionTypeGroupsTest {

    private static ActionType type(String name, ActionCategory category) {
        return new ActionType(name.toLowerCase(), "g1", name, 10, 5, 1000, category);
    }

    private static List<ActionType> many(int count, ActionCategory category) {
        List<ActionType> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(type("Ação " + i, category));
        }
        return out;
    }

    @Test
    void splitsByCategoryWithGrandesFirst() {
        List<Group> groups = ActionTypeGroups.chunked(List.of(
                type("Banco Central", ActionCategory.GRANDE),
                type("Fast Food", ActionCategory.PEQUENA),
                type("Cinema", ActionCategory.GRANDE)));

        assertEquals(2, groups.size());
        assertEquals("Grandes", groups.get(0).label());
        assertEquals(2, groups.get(0).types().size());
        assertEquals("Pequenas", groups.get(1).label());
        assertEquals(1, groups.get(1).types().size());
    }

    @Test
    void omitsEmptyCategories() {
        List<Group> groups = ActionTypeGroups.chunked(List.of(type("Fast Food", ActionCategory.PEQUENA)));

        assertEquals(1, groups.size());
        assertEquals("Pequenas", groups.get(0).label());
    }

    @Test
    void keepsUncategorizedInTheirOwnGroupLast() {
        List<Group> groups = ActionTypeGroups.chunked(List.of(
                type("Sem porte", null),
                type("Banco Central", ActionCategory.GRANDE)));

        assertEquals(2, groups.size());
        assertEquals("Grandes", groups.get(0).label());
        assertEquals("Sem categoria", groups.get(1).label());
    }

    /** O bug original: 26+ ações num único select fazem a JDA lançar. */
    @Test
    void neverExceedsTheDiscordOptionLimit() {
        List<Group> groups = ActionTypeGroups.chunked(many(60, ActionCategory.PEQUENA));

        assertEquals(3, groups.size());
        groups.forEach(g -> assertTrue(g.types().size() <= ActionTypeGroups.MAX_OPTIONS,
                g.label() + " tem " + g.types().size() + " opções"));
        assertEquals(List.of("Pequenas · 1/3", "Pequenas · 2/3", "Pequenas · 3/3"),
                groups.stream().map(Group::label).toList());
    }

    @Test
    void everyActionSurvivesTheSplit() {
        List<ActionType> all = new ArrayList<>(many(30, ActionCategory.PEQUENA));
        all.addAll(many(28, ActionCategory.GRANDE));
        all.add(type("Sem porte", null));

        List<ActionType> flattened = ActionTypeGroups.chunked(all).stream()
                .flatMap(g -> g.types().stream())
                .toList();

        assertEquals(all.size(), flattened.size());
        assertTrue(flattened.containsAll(all));
    }

    @Test
    void chunkLabelHasNoSuffixWhenItFitsInOneMenu() {
        List<Group> groups = ActionTypeGroups.chunked(many(ActionTypeGroups.MAX_OPTIONS, ActionCategory.GRANDE));

        assertEquals(1, groups.size());
        assertEquals("Grandes", groups.get(0).label());
    }
}
