package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.database.postgres.PostgresPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

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

    /**
     * Tetos de rodadas por invocacao de {@link #flushOnce}, para drenar o conjunto sujo em
     * lotes sucessivos sem rodar sem limite: uma invocacao processa no maximo
     * {@code BATCH * MAX_ROUNDS} linhas.
     */
    private static final int MAX_ROUNDS = 20;

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

    /**
     * Drena o conjunto sujo em lotes sucessivos de {@code BATCH}, nao so o primeiro: com
     * {@code dirtyRows} sem {@code ORDER BY}, um unico lote sempre pegaria as mesmas linhas de
     * rowid baixo e as de rowid alto nunca subiriam. Cada linha e independente: uma falha nao
     * impede as outras. Para quando o lote vem vazio, vem menor que {@code BATCH} (conjunto
     * esgotado), quando nenhuma linha do lote foi limpa (nada progride, evita girar em vao), ou
     * ao atingir {@link #MAX_ROUNDS} rodadas.
     */
    public static void flushOnce(VoiceTimeRepository repo, Upstream upstream) {
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<VoiceTimeRepository.DirtyRow> batch = repo.dirtyRows(BATCH);
            if (batch.isEmpty()) {
                return;
            }

            int cleared = 0;
            for (VoiceTimeRepository.DirtyRow row : batch) {
                try {
                    upstream.upsert(row.guildId(), row.userId(), row.weekStart(), row.ms());
                    if (repo.clearDirty(row.guildId(), row.userId(), row.weekStart(), row.ms())) {
                        cleared++;
                    }
                } catch (Exception e) {
                    log.warn("Falha ao sincronizar voice_weekly_time {}/{} semana {}",
                            row.guildId(), row.userId(), row.weekStart(), e);
                }
            }

            if (batch.size() < BATCH || cleared == 0) {
                return;
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
