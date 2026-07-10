package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.postgres.PostgresPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Poda diária dos dados de voz com mais de {@link VoiceRetention#DAYS} dias.
 *
 * <p>Duas guardas: sessões <b>abertas</b> nunca são apagadas, por mais longas que sejam; e baldes
 * semanais ainda <b>sujos</b> nunca são apagados antes de chegarem ao Postgres. Por isso o sweeper
 * é agendado depois do flusher.
 */
public final class VoiceRetentionSweeper {

    private static final Logger log = LoggerFactory.getLogger(VoiceRetentionSweeper.class);

    private final VoiceSessionRepository sessions;
    private final VoiceTimeRepository times;
    private final PostgresPool postgres;

    public VoiceRetentionSweeper(BotContext ctx) {
        this.sessions = new VoiceSessionRepository(ctx.database().sqlite());
        this.times = new VoiceTimeRepository(ctx.database().sqlite());
        this.postgres = ctx.database().postgres();
    }

    public void sweep() {
        long now = System.currentTimeMillis();
        sweepLocal(sessions, times, now);
        sweepPostgres(postgres, VoiceRetention.cutoff(now));
    }

    /** A parte que roda no SQLite. Separada para ser testável. */
    public static void sweepLocal(VoiceSessionRepository sessions, VoiceTimeRepository times, long now) {
        long cutoff = VoiceRetention.cutoff(now);
        int closed = sessions.purgeClosedBefore(cutoff);
        int weeks = times.purgeWeeksBefore(VoiceWeek.weekStart(cutoff));
        if (closed > 0 || weeks > 0) {
            log.info("Retenção de voz: {} sessões e {} baldes semanais podados (>{}d)",
                    closed, weeks, VoiceRetention.DAYS);
        }
    }

    private static void sweepPostgres(PostgresPool pool, long cutoff) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM voice_weekly_time WHERE week_start < ?")) {
            ps.setLong(1, VoiceWeek.weekStart(cutoff));
            ps.executeUpdate();
        } catch (Exception e) {
            // Neon fora do ar: tenta de novo amanhã. Nunca bloqueia a poda local.
            log.warn("Falha ao podar voice_weekly_time no Postgres: {}", e.toString());
        }
    }
}
