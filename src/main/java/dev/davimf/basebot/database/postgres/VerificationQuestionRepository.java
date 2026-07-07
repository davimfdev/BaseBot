package dev.davimf.basebot.database.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Perguntas de verificação (config editável pelo /setup e dashboard). Uma linha por
 *  pergunta, ordenadas por position; máx. 5 por guild (imposto na UI). */
public final class VerificationQuestionRepository {

    public record Question(String id, String guildId, int position, String prompt, boolean required) {}

    private final PostgresPool pool;

    public VerificationQuestionRepository(PostgresPool pool) {
        this.pool = pool;
    }

    public List<Question> listByGuild(String guildId) {
        String sql = "SELECT * FROM verification_questions WHERE guild_id = ? ORDER BY position";
        List<Question> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list verification questions " + guildId, e);
        }
    }

    public int count(String guildId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM verification_questions WHERE guild_id = ?")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("count verification questions " + guildId, e);
        }
    }

    public void add(Question q) {
        String sql = "INSERT INTO verification_questions (id, guild_id, position, prompt, required) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, q.id());
            ps.setString(2, q.guildId());
            ps.setInt(3, q.position());
            ps.setString(4, q.prompt());
            ps.setBoolean(5, q.required());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("add verification question " + q.id(), e);
        }
    }

    public void delete(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM verification_questions WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete verification question " + id, e);
        }
    }

    private static Question map(ResultSet rs) throws SQLException {
        return new Question(rs.getString("id"), rs.getString("guild_id"),
                rs.getInt("position"), rs.getString("prompt"), rs.getBoolean("required"));
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
