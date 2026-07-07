package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JokenpoResultTest {
    @Test
    void ties() {
        for (JokenpoResult.Choice c : JokenpoResult.Choice.values()) {
            assertEquals(JokenpoResult.Outcome.TIE, JokenpoResult.decide(c, c));
        }
    }

    @Test
    void wins() {
        assertEquals(JokenpoResult.Outcome.WIN_A,
                JokenpoResult.decide(JokenpoResult.Choice.PEDRA, JokenpoResult.Choice.TESOURA));
        assertEquals(JokenpoResult.Outcome.WIN_A,
                JokenpoResult.decide(JokenpoResult.Choice.PAPEL, JokenpoResult.Choice.PEDRA));
        assertEquals(JokenpoResult.Outcome.WIN_A,
                JokenpoResult.decide(JokenpoResult.Choice.TESOURA, JokenpoResult.Choice.PAPEL));
        assertEquals(JokenpoResult.Outcome.WIN_B,
                JokenpoResult.decide(JokenpoResult.Choice.TESOURA, JokenpoResult.Choice.PEDRA));
    }
}
