package dev.davimf.basebot.modules.base.leveling;

import java.util.concurrent.TimeUnit;

/** Janela de retenção de dados de voz. Único lugar onde os 90 dias existem. */
public final class VoiceRetention {

    public static final int DAYS = 90;

    private VoiceRetention() {}

    /** Instante antes do qual os dados podem ser podados. */
    public static long cutoff(long now) {
        return now - TimeUnit.DAYS.toMillis(DAYS);
    }
}
