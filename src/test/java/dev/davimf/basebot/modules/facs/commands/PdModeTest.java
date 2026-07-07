package dev.davimf.basebot.modules.facs.commands;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PdModeTest {

    @Test
    void memberWhenUserPresent() {
        assertEquals(PdCommand.Mode.MEMBER, PdCommand.mode(true, null, null));
        assertEquals(PdCommand.Mode.MEMBER, PdCommand.mode(true, "123", "Fulano"));
    }

    @Test
    void externalWhenGameIdAndRpNamePresent() {
        assertEquals(PdCommand.Mode.EXTERNAL, PdCommand.mode(false, "123", "Fulano"));
        assertEquals(PdCommand.Mode.EXTERNAL, PdCommand.mode(false, " 123 ", " Fulano "));
    }

    @Test
    void invalidWhenIncomplete() {
        assertEquals(PdCommand.Mode.INVALID, PdCommand.mode(false, null, null));
        assertEquals(PdCommand.Mode.INVALID, PdCommand.mode(false, "123", null));
        assertEquals(PdCommand.Mode.INVALID, PdCommand.mode(false, null, "Fulano"));
        assertEquals(PdCommand.Mode.INVALID, PdCommand.mode(false, "  ", "  "));
    }
}
