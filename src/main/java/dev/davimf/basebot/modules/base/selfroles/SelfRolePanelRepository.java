package dev.davimf.basebot.modules.base.selfroles;

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

/** Postgres store para painéis de self-role (config editável pelo dashboard). */
public final class SelfRolePanelRepository {

    private final PostgresPool pool;

    public SelfRolePanelRepository(PostgresPool pool) {
        this.pool = pool;
    }

    public String createPanel(String guildId, String title, String description, String style, boolean unique) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO self_role_panels (id, guild_id, title, description, style, unique_choice) "
                     + "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, title);
            ps.setString(4, description);
            ps.setString(5, style);
            ps.setBoolean(6, unique);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create self-role panel for " + guildId, e);
        }
    }

    public void updatePanel(String id, String title, String description, String style, boolean unique) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE self_role_panels SET title=?, description=?, style=?, unique_choice=? WHERE id=?")) {
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setString(3, style);
            ps.setBoolean(4, unique);
            ps.setString(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("update self-role panel " + id, e);
        }
    }

    public void setOptions(String panelId, List<SelfRolePanel.Option> options) {
        try (Connection c = pool.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM self_role_options WHERE panel_id=?")) {
                    del.setString(1, panelId);
                    del.executeUpdate();
                }
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO self_role_options (panel_id, role_id, label, emoji, position) VALUES (?,?,?,?,?)")) {
                    for (SelfRolePanel.Option o : options) {
                        ins.setString(1, panelId);
                        ins.setString(2, o.roleId());
                        ins.setString(3, o.label());
                        ins.setString(4, o.emoji());
                        ins.setInt(5, o.position());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("set options for panel " + panelId, e);
        }
    }

    public void setPublished(String panelId, String channelId, String messageId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE self_role_panels SET channel_id=?, message_id=? WHERE id=?")) {
            ps.setString(1, channelId);
            ps.setString(2, messageId);
            ps.setString(3, panelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("publish panel " + panelId, e);
        }
    }

    public void delete(String panelId) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE self_role_panels SET enabled = false WHERE id=?")) {
            ps.setString(1, panelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete panel " + panelId, e);
        }
    }

    public Optional<SelfRolePanel> find(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM self_role_panels WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(c, rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find panel " + id, e);
        }
    }

    public List<SelfRolePanel> list(String guildId) {
        List<SelfRolePanel> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM self_role_panels WHERE guild_id=? AND enabled = true ORDER BY created_at")) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(c, rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list panels for " + guildId, e);
        }
    }

    private static SelfRolePanel map(Connection c, ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        return new SelfRolePanel(id, rs.getString("guild_id"), rs.getString("title"),
                rs.getString("description"), rs.getString("style"), rs.getBoolean("unique_choice"),
                rs.getString("channel_id"), rs.getString("message_id"), loadOptions(c, id));
    }

    private static List<SelfRolePanel.Option> loadOptions(Connection c, String panelId) throws SQLException {
        List<SelfRolePanel.Option> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT role_id, label, emoji, position FROM self_role_options WHERE panel_id=? ORDER BY position")) {
            ps.setString(1, panelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SelfRolePanel.Option(rs.getString("role_id"), rs.getString("label"),
                            rs.getString("emoji"), rs.getInt("position")));
                }
            }
        }
        return out;
    }
}
