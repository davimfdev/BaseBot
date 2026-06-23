package dev.davimf.basebot.modules.sales.pix;

import dev.davimf.basebot.database.model.PixKey;
import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** SQLite store for Pix keys, scoped per guild + user (per person; BOTSPECS Module 3). */
public final class PixKeyRepository {

    private final SqliteManager sqlite;

    public PixKeyRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public void upsert(PixKey k) {
        String sql = """
                INSERT INTO pix_keys
                    (guild_id, user_id, key_type, key_value, merchant_name, merchant_city)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (guild_id, user_id) DO UPDATE SET
                    key_type      = excluded.key_type,
                    key_value     = excluded.key_value,
                    merchant_name = excluded.merchant_name,
                    merchant_city = excluded.merchant_city
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, k.guildId());
            ps.setString(2, k.userId());
            ps.setString(3, k.keyType());
            ps.setString(4, k.keyValue());
            ps.setString(5, k.merchantName());
            ps.setString(6, k.merchantCity());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("upsert pix key " + k.guildId() + "/" + k.userId(), e);
        }
    }

    public Optional<PixKey> findByUser(String guildId, String userId) {
        String sql = "SELECT * FROM pix_keys WHERE guild_id = ? AND user_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new PixKey(
                        rs.getString("guild_id"),
                        rs.getString("user_id"),
                        rs.getString("key_type"),
                        rs.getString("key_value"),
                        rs.getString("merchant_name"),
                        rs.getString("merchant_city")
                ));
            }
        } catch (SQLException e) {
            throw new RepositoryException("find pix key " + guildId + "/" + userId, e);
        }
    }
}
