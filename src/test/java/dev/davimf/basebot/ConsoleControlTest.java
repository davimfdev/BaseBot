package dev.davimf.basebot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleControlTest {

    @Test
    void recognizesPterodactylShutdownAndRestartCommands() {
        assertTrue(ConsoleControl.isShutdownCommand("stop"));
        assertTrue(ConsoleControl.isShutdownCommand(" shutdown "));
        assertTrue(ConsoleControl.isShutdownCommand("RESTART"));
        assertTrue(ConsoleControl.isShutdownCommand("reiniciar"));
        assertTrue(ConsoleControl.isShutdownCommand("desligar"));
        assertTrue(ConsoleControl.isShutdownCommand("exit"));
        assertFalse(ConsoleControl.isShutdownCommand("status"));
    }
}
