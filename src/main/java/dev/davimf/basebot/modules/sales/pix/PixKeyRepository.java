package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite store for Pix keys: multiple per guild+user (BOTSPECS Module 3). */
public final class PixKeyRepository {

    private final SqliteManager sqlite;

    public PixKeyRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public long insert(PixKey k) {
        String sql = "INSERT INTO pix_keys (guild_id, user_id, key_type, key_value, merchant_name) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, k.guildId());
            ps.setString(2, k.userId());
            ps.setString(3, k.keyType());
            ps.setString(4, k.keyValue());
            ps.setString(5, k.merchantName());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RepositoryException("insert pix key " + k.guildId() + "/" + k.userId(), e);
        }
    }

    public void update(long id, String keyValue, String merchantName) {
        String sql = "UPDATE pix_keys SET key_value = ?, merchant_name = ? WHERE id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, keyValue);
            ps.setString(2, merchantName);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("update pix key " + id, e);
        }
    }

    public void delete(long id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM pix_keys WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete pix key " + id, e);
        }
    }

    public Optional<PixKey> find(long id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM pix_keys WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find pix key " + id, e);
        }
    }

    public List<PixKey> list(String guildId, String userId) {
        List<PixKey> out = new ArrayList<>();
        String sql = "SELECT * FROM pix_keys WHERE guild_id = ? AND user_id = ? ORDER BY id";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list pix keys " + guildId + "/" + userId, e);
        }
    }

    public Optional<PixKey> findDefault(String guildId, String userId) {
        String sql = "SELECT * FROM pix_keys WHERE guild_id = ? AND user_id = ? ORDER BY id LIMIT 1";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find default pix key " + guildId + "/" + userId, e);
        }
    }

    private static PixKey map(ResultSet rs) throws SQLException {
        return new PixKey(
                rs.getLong("id"),
                rs.getString("guild_id"),
                rs.getString("user_id"),
                rs.getString("key_type"),
                rs.getString("key_value"),
                rs.getString("merchant_name"));
    }
}
