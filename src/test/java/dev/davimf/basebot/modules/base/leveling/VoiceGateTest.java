package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trava de reconciliação é por guild: começa fechada para qualquer guild (nada credita antes do
 * boot reancorar as watermarks daquela guild), abre quando o reconciler termina aquela guild com
 * sucesso, e permanece aberta depois — sem afetar outras guilds ainda não reconciliadas.
 */
class VoiceGateTest {

    @Test
    void startsClosedForAnyGuild() {
        VoiceGate gate = new VoiceGate();
        assertFalse(gate.isReconciled("g1"));
        assertFalse(gate.isReconciled("g2"));
    }

    @Test
    void markReconciledOpensOnlyThatGuild() {
        VoiceGate gate = new VoiceGate();
        gate.markReconciled("g1");
        assertTrue(gate.isReconciled("g1"));
        assertFalse(gate.isReconciled("g2"));
    }

    @Test
    void staysOpenOnceMarked() {
        VoiceGate gate = new VoiceGate();
        gate.markReconciled("g1");
        gate.markReconciled("g1");
        assertTrue(gate.isReconciled("g1"));
    }
}
