package dev.davimf.basebot.database.sqlite;

import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Arquivo das mensagens recentes (retenção via purga). Usado pelos logs de mensagem para
 *  recuperar o conteúdo de mensagens apagadas e o "antes" de edições. Os anexos são
 *  re-hospedados no cofre (vault) — guardamos o canal+mensagem do cofre para buscar URLs frescas. */
public final class MessageArchiveRepository {

    public record Archived(String messageId, String guildId, String channelId,
                           String authorId, String content, String attachments,
                           long createdAt, long updatedAt,
                           String vaultChannelId, String vaultMessageId) {}

    private final SqliteManager sqlite;

    public MessageArchiveRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void upsert(String messageId, String guildId, String channelId, String authorId,
                       String content, String attachments, long createdAt) {
        String sql = "INSERT INTO message_archive"
                + " (message_id, guild_id, channel_id, author_id, content, attachments, created_at, updated_at)"
                + " VALUES (?,?,?,?,?,?,?,?)"
                + " ON CONFLICT(message_id) DO UPDATE SET content=excluded.content,"
                + " attachments=excluded.attachments, updated_at=excluded.updated_at";
        try (Connection c = sqlite.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, guildId);
            ps.setString(3, channelId);
            ps.setString(4, authorId);
            ps.setString(5, content);
            ps.setString(6, attachments);
            ps.setLong(7, createdAt);
            ps.setLong(8, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("upsert message_archive", e);
        }
    }

    /** Records the vault location of a message's re-hosted attachments. */
    public void setVault(String messageId, String vaultChannelId, String vaultMessageId) {
        String sql = "UPDATE message_archive SET vault_channel_id=?, vault_message_id=? WHERE message_id=?";
        try (Connection c = sqlite.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, vaultChannelId);
            ps.setString(2, vaultMessageId);
            ps.setString(3, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("setVault message_archive", e);
        }
    }

    public Archived find(String messageId) {
        String sql = "SELECT * FROM message_archive WHERE message_id=?";
        try (Connection c = sqlite.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Archived(rs.getString("message_id"), rs.getString("guild_id"),
                        rs.getString("channel_id"), rs.getString("author_id"),
                        rs.getString("content"), rs.getString("attachments"),
                        rs.getLong("created_at"), rs.getLong("updated_at"),
                        rs.getString("vault_channel_id"), rs.getString("vault_message_id"));
            }
        } catch (SQLException e) {
            throw new RepositoryException("find message_archive", e);
        }
    }

    public void updateContent(String messageId, String newContent, long updatedAt) {
        String sql = "UPDATE message_archive SET content=?, updated_at=? WHERE message_id=?";
        try (Connection c = sqlite.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newContent);
            ps.setLong(2, updatedAt);
            ps.setString(3, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("updateContent message_archive", e);
        }
    }

    public int purgeOlderThan(long cutoffMs) {
        String sql = "DELETE FROM message_archive WHERE created_at < ?";
        try (Connection c = sqlite.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, cutoffMs);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("purge message_archive", e);
        }
    }
}
