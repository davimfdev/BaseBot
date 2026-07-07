package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Estado de cadeia/ficha do usuário (migração 035). */
public final class CrimeStateRepository {

    public record State(long presoAte, boolean fichaSuja) {}

    private final SqliteManager sqlite;

    public CrimeStateRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public State get(String g, String u) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT preso_ate, ficha_suja FROM user_crime_state WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, g);
            ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new State(rs.getLong(1), rs.getInt(2) == 1) : new State(0, false);
            }
        } catch (SQLException e) {
            throw new RepositoryException("crimestate get " + g + "/" + u, e);
        }
    }

    public void jail(String g, String u, long ate) { upsert(g, u, "preso_ate", ate); }
    public void release(String g, String u) { upsert(g, u, "preso_ate", 0); }
    public void setFicha(String g, String u, boolean dirty) { upsert(g, u, "ficha_suja", dirty ? 1 : 0); }

    private void upsert(String g, String u, String col, long value) {
        String sql = "INSERT INTO user_crime_state (guild_id, user_id, " + col + ") VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET " + col + " = excluded." + col;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setLong(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("crimestate upsert " + col + " " + g + "/" + u, e);
        }
    }
}
