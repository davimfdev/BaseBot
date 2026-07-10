package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GifInteractionsTest {

    @Test
    void messageFollowsActorVerbTargetEmoji() {
        GifInteractions.Spec beijo = new GifInteractions.Spec(
                "beijo", "Beija alguém.", "kiss", "deu um beijo em", "😚");
        assertEquals("<@1> deu um beijo em <@2> 😚", beijo.message("<@1>", "<@2>"));
    }

    @Test
    void catalogHasNoDuplicateCommandNames() {
        List<String> names = GifInteractions.CATALOG.stream().map(GifInteractions.Spec::name).toList();
        assertEquals(names.size(), Set.copyOf(names).size(), "nomes de comando devem ser únicos");
    }

    @Test
    void catalogPreservesTheTwoOriginalCommandsVerbatim() {
        // abracar e toca_aqui foram dobrados neste catálogo; o texto não pode ter mudado.
        var byName = GifInteractions.CATALOG.stream()
                .collect(Collectors.toMap(GifInteractions.Spec::name, s -> s));
        assertEquals("<@a> abraçou <@b> 🤗", byName.get("abracar").message("<@a>", "<@b>"));
        assertEquals("<@a> fez um carinho em <@b> 🥰", byName.get("toca_aqui").message("<@a>", "<@b>"));
    }

    @Test
    void everySpecHasNonBlankNameCategoryAndVerb() {
        for (GifInteractions.Spec s : GifInteractions.CATALOG) {
            assertTrue(!s.name().isBlank() && !s.category().isBlank() && !s.verb().isBlank(),
                    "spec incompleta: " + s.name());
        }
    }

    @Test
    void byNameFindsAKnownActionAndReturnsNullOtherwise() {
        assertEquals("kiss", GifInteractions.byName("beijo").category());
        assertEquals(null, GifInteractions.byName("naoexiste"));
    }

    @Test
    void commandNamesAreDiscordSafe() {
        // Discord: minúsculas, sem espaço; letras/números/hífen/underscore, 1-32 chars.
        for (GifInteractions.Spec s : GifInteractions.CATALOG) {
            assertTrue(s.name().matches("[a-z0-9_-]{1,32}"), "nome inválido: " + s.name());
        }
    }
}
