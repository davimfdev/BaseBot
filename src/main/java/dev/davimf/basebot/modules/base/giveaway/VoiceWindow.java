package dev.davimf.basebot.modules.base.giveaway;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Verifica se uma sessão de voz intersecta a faixa diária [startHour, endHour) no fuso dado. Puro. */
public final class VoiceWindow {

    private VoiceWindow() {}

    public static boolean overlapsDailyWindow(long joinMs, long leaveMs, int startHour, int endHour, ZoneId zone) {
        if (leaveMs <= joinMs || startHour >= endHour) {
            return false;
        }
        LocalDate first = Instant.ofEpochMilli(joinMs).atZone(zone).toLocalDate();
        LocalDate last = Instant.ofEpochMilli(leaveMs).atZone(zone).toLocalDate();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            long wStart = ZonedDateTime.of(d, LocalTime.of(startHour, 0), zone).toInstant().toEpochMilli();
            long wEnd = endHour >= 24
                    ? ZonedDateTime.of(d.plusDays(1), LocalTime.MIDNIGHT, zone).toInstant().toEpochMilli()
                    : ZonedDateTime.of(d, LocalTime.of(endHour, 0), zone).toInstant().toEpochMilli();
            if (joinMs < wEnd && leaveMs > wStart) {
                return true;
            }
        }
        return false;
    }
}
