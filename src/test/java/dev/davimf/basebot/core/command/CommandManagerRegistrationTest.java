package dev.davimf.basebot.core.command;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandManagerRegistrationTest {

    @Test
    void selectsOnlyCommandsMissingFromGuild() {
        assertEquals(Set.of("setup", "ban"), CommandManager.missingCommandNames(
                Set.of("ping"), Set.of("ping", "setup", "ban")));
    }

    @Test
    void selectsNothingWhenGuildAlreadyHasEveryCommand() {
        assertEquals(Set.of(), CommandManager.missingCommandNames(
                Set.of("ping", "setup"), Set.of("ping", "setup")));
    }

    @Test
    void selectsEverythingForEmptyGuild() {
        assertEquals(Set.of("ping", "setup"), CommandManager.missingCommandNames(
                Set.of(), Set.of("ping", "setup")));
    }
}
