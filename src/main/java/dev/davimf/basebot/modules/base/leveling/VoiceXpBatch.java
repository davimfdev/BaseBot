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
 * Aplica, numa ÚNICA transação SQL, os créditos de XP de voz de um ciclo do ticker:
 * avança {@code xp_credited_until} de cada sessão e soma o XP elegível em {@code user_levels}.
 * Devolve os totais antigo/novo de quem recebeu XP para o ticker detectar level-up após o commit
 * (as chamadas ao Discord ficam fora da transação).
 */
public final class VoiceXpBatch {

    private VoiceXpBatch() {}

    public record Credit(long sessionId, String guildId, String userId, long xpDelta, long creditedUntil) {}

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
                        "UPDATE voice_sessions SET xp_credited_until=? WHERE id=?");
                 PreparedStatement selXp = c.prepareStatement(
                        "SELECT xp FROM user_levels WHERE guild_id=? AND user_id=?");
                 PreparedStatement addXp = c.prepareStatement(
                        "INSERT INTO user_levels (guild_id, user_id, xp) VALUES (?,?,?) "
                        + "ON CONFLICT (guild_id, user_id) DO UPDATE SET xp = xp + excluded.xp")) {
                for (Credit cr : credits) {
                    advance.setLong(1, cr.creditedUntil());
                    advance.setLong(2, cr.sessionId());
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
