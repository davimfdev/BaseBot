package dev.davimf.basebot.commands;

import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.facs.commands.FarmCommand;
import dev.davimf.basebot.modules.facs.commands.PunirCommand;
import dev.davimf.basebot.modules.facs.commands.ProduzirCommand;
import dev.davimf.basebot.modules.facs.commands.RelatorioCommand;
import dev.davimf.basebot.modules.sales.budget.BudgetCommand;
import dev.davimf.basebot.modules.base.commands.FormularioCommand;
import dev.davimf.basebot.modules.base.commands.MensagemCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Builds {@code data()} for option-heavy commands so JDA's validation runs at test time.
 * Guards against "Cannot add required options after non-required options" (and similar)
 * which otherwise only surfaces at command registration on bot startup. The command
 * definition never touches instance state, so null collaborators are fine here.
 */
class CommandDataValidationTest {

    @Test
    void optionHeavyCommandsBuildValidData() {
        List<SlashCommand> commands = List.of(
                new ProduzirCommand(null, null),
                new RelatorioCommand(null),
                new FarmCommand(null),
                new PunirCommand(null),
                new FormularioCommand(null),
                new BudgetCommand(null),
                new MensagemCommand(null));
        for (SlashCommand cmd : commands) {
            assertDoesNotThrow(cmd::data, "Invalid command data for /" + cmd.name());
        }
    }
}
