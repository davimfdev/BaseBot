package dev.davimf.basebot.modules.base.economy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Reset diário do /daily à meia-noite de Brasília (puro, sem estado). */
public final class DailyReset {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private DailyReset() {}

    /** Epoch ms da meia-noite local mais recente (início do dia atual em {@link #ZONE}). */
    public static long todayMidnightMillis(long nowMillis) {
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(ZONE);
        return now.toLocalDate().atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    /** Epoch ms do início do próximo dia local (para exibir o "disponível novamente"). */
    public static long nextMidnightMillis(long nowMillis) {
        ZonedDateTime now = Instant.ofEpochMilli(nowMillis).atZone(ZONE);
        LocalDate tomorrow = now.toLocalDate().plusDays(1);
        return tomorrow.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    /** Disponível se a última coleta foi antes da meia-noite de hoje (um /daily por dia-calendário). */
    public static boolean available(long lastTs, long nowMillis) {
        return lastTs < todayMidnightMillis(nowMillis);
    }
}
