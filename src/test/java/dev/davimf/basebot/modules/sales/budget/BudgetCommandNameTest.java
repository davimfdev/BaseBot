// [OUTLINE START]
// Package: dev.davimf.basebot.modules.sales.budget
// 
// Class: BudgetCommandNameTest
// [OUTLINE END]



package dev.davimf.basebot.modules.sales.budget;

import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards against the bot failing to start because JDA rejects the accented command name
 * {@code /orçamento} (BOTSPECS spells it with a cedilla). Discord allows lowercase
 * unicode letters; this proves JDA's client-side validation agrees.
 */
class BudgetCommandNameTest {

    @Test
    void jdaAcceptsAccentedCommandName() {
        assertDoesNotThrow(() ->
                Commands.slash("orçamento", "Cria um orçamento interativo para um cliente."));
    }

    @Test
    void nameMatchesCommandData() {
        assertEquals("orçamento",
                Commands.slash("orçamento", "x").getName());
    }
}
