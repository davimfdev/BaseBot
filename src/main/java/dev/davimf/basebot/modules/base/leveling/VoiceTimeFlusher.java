package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.postgres.PostgresPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Sobe os baldes semanais sujos do SQLite para o Postgres, de onde o site lê o ranking.
 *
 * <p>O upsert manda o <b>total absoluto</b> da semana, nunca um delta, então reenviar a mesma
 * linha após um timeout parcial não duplica tempo. E {@code dirty} só é limpo se {@code ms} não
 * mudou durante o round-trip — senão o incremento do ticker se perderia.
 *
 * <p>Neon fora do ar não afeta a contagem: a linha continua suja e sobe na próxima rodada.
 */
public final class VoiceTimeFlusher {

    private static final Logger log = LoggerFactory.getLogger(VoiceTimeFlusher.class);

    /** Quantas linhas sujas por rodada. */
    public static final int BATCH = 500;

    /** Destino do flush. Extraído para testar a reconciliação sem um Postgres de verdade. */
    public interface Upstream {
        void upsert(String guildId, String userId, long weekStart, long ms) throws Exception;
    }

    private final VoiceTimeRepository repo;
    private final Upstream upstream;

    public VoiceTimeFlusher(BotContext ctx) {
        this.repo = new VoiceTimeRepository(ctx.database().sqlite());
        this.upstream = postgresUpstream(ctx.database().postgres());
    }

    public void flush() {
        flushOnce(repo, upstream);
    }

    /** Uma rodada. Cada linha é independente: uma falha não impede as outras. */
    public static void flushOnce(VoiceTimeRepository repo, Upstream upstream) {
        for (VoiceTimeRepository.DirtyRow row : repo.dirtyRows(BATCH)) {
            try {
                upstream.upsert(row.guildId(), row.userId(), row.weekStart(), row.ms());
                repo.clearDirty(row.guildId(), row.userId(), row.weekStart(), row.ms());
            } catch (Exception e) {
                log.warn("Falha ao sincronizar voice_weekly_time {}/{} semana {}: {}",
                        row.guildId(), row.userId(), row.weekStart(), e.toString());
            }
        }
    }

    private static Upstream postgresUpstream(PostgresPool pool) {
        String sql = "INSERT INTO voice_weekly_time (guild_id, user_id, week_start, ms, updated_at) "
                + "VALUES (?,?,?,?, now()) "
                + "ON CONFLICT (guild_id, user_id, week_start) "
                + "DO UPDATE SET ms = EXCLUDED.ms, updated_at = now()";
        return (guildId, userId, weekStart, ms) -> {
            try (Connection c = pool.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, guildId);
                ps.setString(2, userId);
                ps.setLong(3, weekStart);
                ps.setLong(4, ms);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new SQLException("upsert voice_weekly_time", e);
            }
        };
    }
}
