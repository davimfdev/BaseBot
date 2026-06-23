package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.database.model.ActiveTicket;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * SQLite persistence for active tickets (Module 2). Channel IDs are stored, never
 * names, so the closure pipeline can delete the exact text and voice channels.
 */
public final class TicketRepository {

    private final SqliteManager sqlite;

    public TicketRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void create(ActiveTicket t) {
        String sql = """
                INSERT INTO active_tickets
                    (id, guild_id, text_channel_id, voice_channel_id,
                     creator_id, assigned_staff_id, suffix, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, t.id());
            ps.setString(2, t.guildId());
            ps.setString(3, t.textChannelId());
            ps.setString(4, t.voiceChannelId());
            ps.setString(5, t.creatorId());
            ps.setString(6, t.assignedStaffId());
            ps.setString(7, t.suffix());
            ps.setString(8, t.status() == null ? ActiveTicket.OPEN : t.status());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("create ticket " + t.id(), e);
        }
    }

    public Optional<ActiveTicket> findByTextChannel(String textChannelId) {
        return queryOne("text_channel_id", textChannelId);
    }

    public Optional<ActiveTicket> findById(String id) {
        return queryOne("id", id);
    }

    public void setVoiceChannel(String id, String voiceChannelId) {
        update("UPDATE active_tickets SET voice_channel_id = ? WHERE id = ?", voiceChannelId, id);
    }

    public void assignStaff(String id, String staffId) {
        update("UPDATE active_tickets SET assigned_staff_id = ? WHERE id = ?", staffId, id);
    }

    public void setStatus(String id, String status) {
        update("UPDATE active_tickets SET status = ? WHERE id = ?", status, id);
    }

    public void rename(String id, String suffix) {
        update("UPDATE active_tickets SET suffix = ? WHERE id = ?", suffix, id);
    }

    private void update(String sql, String value, String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("update ticket " + id, e);
        }
    }

    private Optional<ActiveTicket> queryOne(String column, String value) {
        String sql = "SELECT * FROM active_tickets WHERE " + column + " = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(map(rs));
            }
        } catch (SQLException e) {
            throw new RepositoryException("query ticket by " + column, e);
        }
    }

    private ActiveTicket map(ResultSet rs) throws SQLException {
        return new ActiveTicket(
                rs.getString("id"),
                rs.getString("guild_id"),
                rs.getString("text_channel_id"),
                rs.getString("voice_channel_id"),
                rs.getString("creator_id"),
                rs.getString("assigned_staff_id"),
                rs.getString("suffix"),
                rs.getString("status")
        );
    }
}
