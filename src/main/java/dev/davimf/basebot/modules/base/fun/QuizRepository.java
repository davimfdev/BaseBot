package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Postgres store das perguntas de quiz personalizadas (config editável pelo dashboard). */
public final class QuizRepository {

    private final PostgresPool pool;

    public QuizRepository(PostgresPool pool) { this.pool = pool; }

    public String add(String guildId, String question, String correct, String w1, String w2, String w3) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String sql = "INSERT INTO quiz_questions (id, guild_id, question, correct, wrong1, wrong2, wrong3) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, question);
            ps.setString(4, correct);
            ps.setString(5, w1);
            ps.setString(6, w2);
            ps.setString(7, w3);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("quiz add " + guildId, e);
        }
    }

    public void remove(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM quiz_questions WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("quiz remove " + id, e);
        }
    }

    public List<QuizQuestion> list(String guildId) {
        List<QuizQuestion> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM quiz_questions WHERE guild_id=? ORDER BY id")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("quiz list " + guildId, e);
        }
    }

    public Optional<QuizQuestion> random(String guildId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM quiz_questions WHERE guild_id=? ORDER BY random() LIMIT 1")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("quiz random " + guildId, e);
        }
    }

    private static QuizQuestion map(ResultSet rs) throws SQLException {
        return new QuizQuestion(rs.getString("id"), rs.getString("guild_id"), rs.getString("question"),
                rs.getString("correct"), rs.getString("wrong1"), rs.getString("wrong2"), rs.getString("wrong3"));
    }
}
