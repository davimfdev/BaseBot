package dev.davimf.basebot.modules.base.leveling;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Trava de reconciliação. As sessões de voz sobrevivem a um restart com a watermark de antes da
 * queda; até o {@link VoiceReconciler} reancorá-las em {@code now}, creditar qualquer janela
 * lançaria o período inteiro em que o bot esteve offline no ranking.
 *
 * <p>Os listeners de voz ficam vivos desde o READY, antes de o reconciler rodar, então quem credita
 * — ticker e settler — precisa consultar esta trava.
 */
public final class VoiceGate {

    private final AtomicBoolean reconciled = new AtomicBoolean(false);

    /** {@code true} só depois de o reconciler ter terminado com sucesso. */
    public boolean isReconciled() {
        return reconciled.get();
    }

    public void markReconciled() {
        reconciled.set(true);
    }
}
