package dev.davimf.basebot.modules.base.leveling;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * A semana do ranking de call: segunda 00:00 em {@link #ZONE}, representada pelo epoch millis
 * desse instante. Nenhum job zera nada — a virada da semana apenas passa a escrever noutra linha.
 */
public final class VoiceWeek {

    /** Fuso fixo. O Brasil não tem horário de verão desde 2019, mas o cálculo é zoned mesmo assim. */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private VoiceWeek() {}

    /** Uma fatia de uma janela, já atribuída a uma semana. */
    public record Slice(long weekStart, long from, long to) {
        public long durationMs() {
            return to - from;
        }
    }

    /** Segunda-feira 00:00 (em {@link #ZONE}) da semana que contém {@code epochMillis}. */
    public static long weekStart(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZONE)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZONE)
                .toInstant().toEpochMilli();
    }

    /** Segunda-feira 00:00 seguinte à semana que contém {@code epochMillis}. */
    public static long nextWeekStart(long epochMillis) {
        return Instant.ofEpochMilli(weekStart(epochMillis)).atZone(ZONE)
                .toLocalDate().plusWeeks(1)
                .atStartOfDay(ZONE)
                .toInstant().toEpochMilli();
    }

    /**
     * Divide {@code [from, to)} nas fronteiras de semana. A soma das durações das fatias é
     * exatamente {@code to - from}. Janela vazia ou invertida devolve lista vazia.
     *
     * <p>É a ÚNICA forma de creditar tempo — nenhum outro caminho deve reimplementar isto.
     */
    public static List<Slice> splitByWeek(long from, long to) {
        List<Slice> out = new ArrayList<>();
        long cursor = from;
        while (cursor < to) {
            long boundary = nextWeekStart(cursor);
            long end = Math.min(to, boundary);
            out.add(new Slice(weekStart(cursor), cursor, end));
            cursor = end;
        }
        return out;
    }
}
