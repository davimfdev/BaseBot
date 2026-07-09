package dev.davimf.basebot.modules.base.leveling;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplica, numa ÚNICA transação, os créditos de um ciclo do ticker (ou de um settle disparado por
 * evento): avança as duas watermarks da sessão, soma o XP elegível em {@code user_levels} e o
 * tempo elegível nos baldes de {@code voice_weekly_time}.
 *
 * <p>Devolve os totais antigo/novo de quem recebeu XP para o chamador detectar level-up após o
 * commit — as chamadas ao Discord ficam fora da transação.
 *
 * <p><b>Invariantes:</b> as watermarks avançam para {@code creditedUntil} mesmo quando não houve
 * crédito (senão o período inelegível seria creditado retroativamente depois). E todo crédito de
 * tempo passa por {@link VoiceWeek#splitByWeek}, nunca por uma divisão ad-hoc.
 */
public final class VoiceXpBatch {

    private VoiceXpBatch() {}

    /**
     * @param xpDelta   XP a somar; 0 quando a janela não era elegível a XP
     * @param timeFrom  início da janela de tempo (a watermark anterior)
     * @param timeTo    fim da janela de tempo; {@code <= timeFrom} significa "sem crédito"
     * @param creditedUntil novo valor das duas watermarks
     */
    public record Credit(long sessionId, String guildId, String userId,
                         long xpDelta, long timeFrom, long timeTo, long creditedUntil) {}

    public record Result(String guildId, String userId, long oldXp, long newXp) {}

    public static List<Result> apply(SqliteManager sqlite, List<Credit> credits) {
        List<Result> results = new ArrayList<>();
        if (credits.isEmpty()) {
            return results;
        }
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement advance = c.prepareStatement(
                        "UPDATE voice_sessions SET xp_credited_until=?, time_credited_until=? WHERE id=?");
                 PreparedStatement selXp = c.prepareStatement(
                        "SELECT xp FROM user_levels WHERE guild_id=? AND user_id=?");
                 PreparedStatement addXp = c.prepareStatement(
                        "INSERT INTO user_levels (guild_id, user_id, xp) VALUES (?,?,?) "
                        + "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = xp + excluded.xp");
                 PreparedStatement addTime = c.prepareStatement(
                        "INSERT INTO voice_weekly_time (guild_id, user_id, week_start, ms, dirty) "
                        + "VALUES (?,?,?,?,1) "
                        + "ON CONFLICT (guild_id, user_id, week_start) "
                        + "DO UPDATE SET ms = ms + excluded.ms, dirty = 1")) {

                for (Credit cr : credits) {
                    advance.setLong(1, cr.creditedUntil());
                    advance.setLong(2, cr.creditedUntil());
                    advance.setLong(3, cr.sessionId());
                    advance.executeUpdate();

                    if (cr.xpDelta() > 0) {
                        long old = 0;
                        selXp.setString(1, cr.guildId());
                        selXp.setString(2, cr.userId());
                        try (ResultSet rs = selXp.executeQuery()) {
                            if (rs.next()) {
                                old = rs.getLong(1);
                            }
                        }
                        addXp.setString(1, cr.guildId());
                        addXp.setString(2, cr.userId());
                        addXp.setLong(3, cr.xpDelta());
                        addXp.executeUpdate();
                        results.add(new Result(cr.guildId(), cr.userId(), old, old + cr.xpDelta()));
                    }

                    for (VoiceWeek.Slice slice : VoiceWeek.splitByWeek(cr.timeFrom(), cr.timeTo())) {
                        addTime.setString(1, cr.guildId());
                        addTime.setString(2, cr.userId());
                        addTime.setLong(3, slice.weekStart());
                        addTime.setLong(4, slice.durationMs());
                        addTime.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
            return results;
        } catch (SQLException e) {
            throw new RepositoryException("voice xp batch", e);
        }
    }
}
