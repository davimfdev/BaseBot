package dev.davimf.basebot.modules.base.leveling;

/**
 * Traduz um estado de voz que NÃO está contando tempo no motivo, para o {@code /tempocall} explicar
 * em vez de mostrar um zero inexplicável.
 *
 * <p>Vários motivos podem valer ao mesmo tempo; a ordem abaixo é fixa e escolhe o mais acionável
 * pelo próprio membro primeiro.
 *
 * <p><b>Pré-condição: o snapshot não é de um bot.</b> {@code bot} não é consultado aqui, então
 * {@code of(s) == null} <em>não</em> equivale a {@link VoiceEligibility#timeEligible} para um bot —
 * este devolve {@code false}, aquele devolve {@code null}. Os chamadores partem de
 * {@code voice_sessions}, que nunca contém bots.
 */
public final class VoicePauseReason {

    private VoicePauseReason() {}

    /** {@code null} quando o tempo está sendo contado. Ver a pré-condição na classe. */
    public static String of(VoiceStateSnapshot s) {
        if (s.selfMuted()) {
            return "microfone fechado";
        }
        if (s.deafened()) {
            return "ensurdecido";
        }
        if (s.afkChannel()) {
            return "canal AFK";
        }
        if (!s.inScope()) {
            return "canal fora do escopo";
        }
        return null;
    }
}
