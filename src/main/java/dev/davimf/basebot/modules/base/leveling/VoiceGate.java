package dev.davimf.basebot.modules.base.leveling;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trava de reconciliação, por guild. As sessões de voz sobrevivem a um restart com a watermark de
 * antes da queda; até o {@link VoiceReconciler} reancorá-la em {@code now}, creditar qualquer
 * janela lançaria o período inteiro em que o bot esteve offline no ranking.
 *
 * <p>Os listeners de voz ficam vivos desde o READY, antes de o reconciler rodar, então quem credita
 * — ticker e settler — precisa consultar esta trava.
 *
 * <p>É por guild: uma guild que falhe ao reconciliar não impede as outras de contar. A trava de uma
 * guild só abre depois de ELA ter sido reconciliada com sucesso (falha fechada).
 */
public final class VoiceGate {

    private final Set<String> reconciled = ConcurrentHashMap.newKeySet();

    /** {@code true} só depois de esta guild ter sido reconciliada com sucesso. */
    public boolean isReconciled(String guildId) {
        return reconciled.contains(guildId);
    }

    public void markReconciled(String guildId) {
        reconciled.add(guildId);
    }
}
