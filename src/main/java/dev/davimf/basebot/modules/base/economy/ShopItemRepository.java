package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Postgres store dos itens da loja (config). O contador vendido fica no SQLite ({@link ShopStockRepository}). */
public final class ShopItemRepository {

    private final PostgresPool pool;

    public ShopItemRepository(PostgresPool pool) { this.pool = pool; }

    public long insert(ShopItem it) {
        String sql = "INSERT INTO shop_items (guild_id, type, role_id, name, description, price, "
                + "duration_s, stock, per_user, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, it.guildId());
            ps.setString(2, it.type().name());
            ps.setString(3, it.roleId());
            ps.setString(4, it.name());
            ps.setString(5, it.description());
            ps.setLong(6, it.price());
            setNullableLong(ps, 7, it.durationS());
            setNullableInt(ps, 8, it.stock());
            setNullableInt(ps, 9, it.perUser());
            ps.setLong(10, it.createdAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new RepositoryException("insert shop item " + it.guildId(), e);
        }
    }

    /** Itens ativos (enabled) do servidor. {@code sold} vem zerado — preencher via ShopStockRepository. */
    public List<ShopItem> list(String g) {
        List<ShopItem> out = new ArrayList<>();
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM shop_items WHERE guild_id=? AND enabled = true ORDER BY price, id")) {
            ps.setString(1, g);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list shop items " + g, e);
        }
    }

    public int count(String g) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM shop_items WHERE guild_id=? AND enabled = true")) {
            ps.setString(1, g);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count shop items " + g, e);
        }
    }

    /** Busca por id (sem filtrar enabled — compras/expiração precisam achar item desativado). */
    public ShopItem find(String g, long id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM shop_items WHERE guild_id=? AND id=?")) {
            ps.setString(1, g);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("find shop item " + g + "/" + id, e);
        }
    }

    /** Soft-delete: desativa o item (histórico de compras continua válido). */
    public boolean delete(String g, long id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE shop_items SET enabled = false WHERE guild_id=? AND id=? AND enabled = true")) {
            ps.setString(1, g);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("delete shop item " + g + "/" + id, e);
        }
    }

    private static ShopItem map(ResultSet rs) throws SQLException {
        return new ShopItem(
                rs.getLong("id"),
                rs.getString("guild_id"),
                ShopItem.Type.valueOf(rs.getString("type")),
                rs.getString("role_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("price"),
                nullableLong(rs, "duration_s"),
                nullableInt(rs, "stock"),
                nullableInt(rs, "per_user"),
                0,                       // sold preenchido pelo ShopStockRepository na camada de serviço
                rs.getLong("created_at"));
    }

    private static void setNullableLong(PreparedStatement ps, int i, Long v) throws SQLException {
        if (v == null) { ps.setNull(i, java.sql.Types.BIGINT); } else { ps.setLong(i, v); }
    }

    private static void setNullableInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) { ps.setNull(i, java.sql.Types.INTEGER); } else { ps.setInt(i, v); }
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer nullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
