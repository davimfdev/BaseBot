package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQLite store das compras da loja (migração 034): sweep de temp, limite por usuário, histórico. */
public final class ShopPurchaseRepository {

    public record Purchase(long id, String guildId, long itemId, String userId, String roleId,
                           Long expiresAt, long pricePaid, long createdAt) {}

    private final SqliteManager sqlite;

    public ShopPurchaseRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public long insert(Purchase p) {
        String sql = "INSERT INTO shop_purchases (guild_id, item_id, user_id, role_id, expires_at, "
                + "price_paid, created_at) VALUES (?,?,?,?,?,?,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.guildId());
            ps.setLong(2, p.itemId());
            ps.setString(3, p.userId());
            ps.setString(4, p.roleId());
            if (p.expiresAt() == null) { ps.setNull(5, java.sql.Types.INTEGER); } else { ps.setLong(5, p.expiresAt()); }
            ps.setLong(6, p.pricePaid());
            ps.setLong(7, p.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RepositoryException("insert purchase " + p.guildId(), e);
        }
    }

    /** Compras ativas do item para o usuário: perm/custom (expires NULL) + temp não expirado. */
    public int countActiveByUserItem(String g, String u, long itemId, long nowMs) {
        String sql = "SELECT COUNT(*) FROM shop_purchases WHERE guild_id=? AND user_id=? AND item_id=? "
                + "AND (expires_at IS NULL OR expires_at > ?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setLong(3, itemId);
            ps.setLong(4, nowMs);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count active " + g + "/" + u + "/" + itemId, e);
        }
    }

    /** Compra temporária ativa desse item para o usuário (para estender), ou null. */
    public Purchase activeTemp(String g, String u, long itemId, long nowMs) {
        String sql = "SELECT * FROM shop_purchases WHERE guild_id=? AND user_id=? AND item_id=? "
                + "AND expires_at IS NOT NULL AND expires_at > ? ORDER BY expires_at DESC LIMIT 1";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setLong(3, itemId);
            ps.setLong(4, nowMs);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("active temp " + g + "/" + u + "/" + itemId, e);
        }
    }

    public boolean extend(long purchaseId, long newExpiresAt) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE shop_purchases SET expires_at=? WHERE id=?")) {
            ps.setLong(1, newExpiresAt);
            ps.setLong(2, purchaseId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("extend purchase " + purchaseId, e);
        }
    }

    /** Compras de cargo temporário vencidas (para o sweep remover o cargo). */
    public List<Purchase> due(long nowMs) {
        List<Purchase> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM shop_purchases WHERE expires_at IS NOT NULL AND expires_at <= ?")) {
            ps.setLong(1, nowMs);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("due purchases", e);
        }
    }

    /** Claim-by-delete: só o primeiro sweep a deletar "ganha" a compra (evita remoção dupla). */
    public boolean claim(long purchaseId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM shop_purchases WHERE id=?")) {
            ps.setLong(1, purchaseId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RepositoryException("claim purchase " + purchaseId, e);
        }
    }

    private static Purchase map(ResultSet rs) throws SQLException {
        long exp = rs.getLong("expires_at");
        Long expires = rs.wasNull() ? null : exp;
        return new Purchase(
                rs.getLong("id"), rs.getString("guild_id"), rs.getLong("item_id"),
                rs.getString("user_id"), rs.getString("role_id"), expires,
                rs.getLong("price_paid"), rs.getLong("created_at"));
    }
}
