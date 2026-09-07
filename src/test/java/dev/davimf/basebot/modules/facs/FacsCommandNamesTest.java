package dev.davimf.basebot.modules.facs;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Guards Facs command names with accented characters against JDA validation rejection. */
class FacsCommandNamesTest {

    @Test
    void jdaAcceptsAccentedFacsNames() {
        assertDoesNotThrow(() -> Commands.slash("punições", "Histórico de punições."));
        assertDoesNotThrow(() -> Commands.slash("hierarquia", "Painel de hierarquia."));
    }
}
