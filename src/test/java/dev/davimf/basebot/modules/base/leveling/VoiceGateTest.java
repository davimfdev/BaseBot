package dev.davimf.basebot.modules.base.leveling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A trava de reconciliação começa fechada (nada credita antes do boot reancorar as watermarks),
 * abre quando o reconciler termina com sucesso, e permanece aberta depois.
 */
class VoiceGateTest {

    @Test
    void startsClosed() {
        VoiceGate gate = new VoiceGate();
        assertFalse(gate.isReconciled());
    }

    @Test
    void markReconciledOpensTheGate() {
        VoiceGate gate = new VoiceGate();
        gate.markReconciled();
        assertTrue(gate.isReconciled());
    }

    @Test
    void staysOpenOnceMarked() {
        VoiceGate gate = new VoiceGate();
        gate.markReconciled();
        gate.markReconciled();
        assertTrue(gate.isReconciled());
    }
}
