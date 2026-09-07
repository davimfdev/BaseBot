package dev.davimf.basebot.modules.base.forms;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQLite store for configurable forms (BOTSPECS Module 1 — /formulario). */
public final class FormRepository {

    /** A form definition: a title plus up to 5 question labels. */
    public record Form(String id, String guildId, String title, List<String> questions) {}

    private final SqliteManager sqlite;

    public FormRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public String create(String guildId, String title, String createdBy, List<String> questions) {
        String id = newId();
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement f = c.prepareStatement(
                        "INSERT INTO forms (id, guild_id, title, created_by) VALUES (?, ?, ?, ?)")) {
                    f.setString(1, id);
                    f.setString(2, guildId);
                    f.setString(3, title);
                    f.setString(4, createdBy);
                    f.executeUpdate();
                }
                try (PreparedStatement q = c.prepareStatement(
                        "INSERT INTO form_questions (id, form_id, position, label) VALUES (?, ?, ?, ?)")) {
                    for (int i = 0; i < questions.size(); i++) {
                        q.setString(1, newId());
                        q.setString(2, id);
                        q.setInt(3, i);
                        q.setString(4, questions.get(i));
                        q.addBatch();
                    }
                    q.executeBatch();
                }
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("create form for " + guildId, e);
        }
    }

    public Optional<Form> find(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM forms WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Form(rs.getString("id"), rs.getString("guild_id"),
                        rs.getString("title"), loadQuestions(c, id)));
            }
        } catch (SQLException e) {
            throw new RepositoryException("find form " + id, e);
        }
    }

    public List<Form> list(String guildId) {
        List<Form> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM forms WHERE guild_id = ? ORDER BY created_at")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Form(rs.getString("id"), rs.getString("guild_id"),
                            rs.getString("title"), loadQuestions(c, rs.getString("id"))));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list forms for " + guildId, e);
        }
    }

    private static List<String> loadQuestions(Connection c, String formId) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT label FROM form_questions WHERE form_id = ? ORDER BY position")) {
            ps.setString(1, formId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString("label"));
                }
            }
        }
        return out;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
