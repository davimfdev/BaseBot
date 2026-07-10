package dev.davimf.basebot.modules.base.leveling;

/**
 * A janela de tempo já vivida mas ainda não creditada: {@code [watermark, agora]}.
 *
 * <p>O ticker credita a cada 60s e avança a watermark de toda sessão aberta, então na prática isto
 * nunca passa de ~1 minuto. Existe para o {@code /tempocall} e o {@code /topcall} não ficarem
 * atrasados até um minuto.
 *
 * <p>A janela é recortada no início da semana: quem está em call desde domingo não deve ver o tempo
 * da semana passada somado a esta.
 */
public final class VoicePending {

    private VoicePending() {}

    /** {@code 0} quando o membro não está elegível (mutado, ensurdecido, AFK, fora do escopo). */
    public static long pendingMs(long watermark, long now, long weekStart, boolean eligible) {
        if (!eligible) {
            return 0;
        }
        long from = Math.max(watermark, weekStart);
        return Math.max(0, now - from);
    }
}
