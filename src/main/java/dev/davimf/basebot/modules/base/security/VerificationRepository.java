package dev.davimf.basebot.modules.base.security;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Estado local da verificação: quem já passou (por servidor) e a fila de pendências
 *  (uma por pessoa). Ver migração 038_verification.sql. */
public final class VerificationRepository {

    /** Pedido pendente na fila de aprovação. */
    public record Pending(String guildId, String userId, String messageId, String answers, long createdAt) {}

    private final SqliteManager sqlite;

    public VerificationRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public boolean isVerified(String guildId, String userId) {
        String sql = "SELECT 1 FROM verified_members WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("isVerified " + guildId + "/" + userId, e);
        }
    }

    public void markVerified(String guildId, String userId, long atMillis) {
        String sql = "INSERT INTO verified_members (guild_id, user_id, verified_at) VALUES (?, ?, ?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET verified_at = excluded.verified_at";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setLong(3, atMillis);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("markVerified " + guildId + "/" + userId, e);
        }
    }

    public void forget(String guildId, String userId) {
        exec("DELETE FROM verified_members WHERE guild_id = ? AND user_id = ?", guildId, userId,
                "forget verified");
    }

    public boolean hasPending(String guildId, String userId) {
        String sql = "SELECT 1 FROM verification_requests WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("hasPending " + guildId + "/" + userId, e);
        }
    }

    /** Insere o pedido; retorna false (sem inserir) se já existe pendência para o usuário. */
    public boolean openRequest(String guildId, String userId, String answers, long createdAt) {
        String sql = "INSERT OR IGNORE INTO verification_requests "
                + "(guild_id, user_id, message_id, answers, created_at) VALUES (?, ?, NULL, ?, ?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.setString(3, answers);
            ps.setLong(4, createdAt);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("openRequest " + guildId + "/" + userId, e);
        }
    }

    public void attachMessage(String guildId, String userId, String messageId) {
        String sql = "UPDATE verification_requests SET message_id = ? WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, messageId);
            ps.setString(2, guildId);
            ps.setString(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("attachMessage " + guildId + "/" + userId, e);
        }
    }

    public Optional<Pending> findPending(String guildId, String userId) {
        String sql = "SELECT message_id, answers, created_at FROM verification_requests "
                + "WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Pending(guildId, userId,
                        rs.getString("message_id"), rs.getString("answers"), rs.getLong("created_at")));
            }
        } catch (SQLException e) {
            throw new RepositoryException("findPending " + guildId + "/" + userId, e);
        }
    }

    public void closeRequest(String guildId, String userId) {
        exec("DELETE FROM verification_requests WHERE guild_id = ? AND user_id = ?", guildId, userId,
                "closeRequest");
    }

    private void exec(String sql, String guildId, String userId, String what) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(what + " " + guildId + "/" + userId, e);
        }
    }
}
