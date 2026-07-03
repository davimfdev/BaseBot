package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Operational sold-counter for shop items (SQLite). The item config (price, stock limit,
 * per-user) lives in Postgres; only this fast-changing counter stays local so Neon never
 * takes an operational write per purchase. Reservation is atomic within SQLite; the stock
 * limit is passed in from the Postgres-backed {@link ShopItemRepository}.
 */
public final class ShopStockRepository {

    private final SqliteManager sqlite;

    public ShopStockRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    /** Reserva 1 unidade atomicamente. {@code true} = reservado; {@code false} = esgotado. */
    public boolean reserveStock(long itemId, Integer stockLimit) {
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT OR IGNORE INTO shop_stock (item_id, sold) VALUES (?, 0)")) {
                    ins.setLong(1, itemId);
                    ins.executeUpdate();
                }
                String sql = "UPDATE shop_stock SET sold = sold + 1 "
                        + "WHERE item_id = ? AND (? IS NULL OR sold < ?)";
                try (PreparedStatement up = c.prepareStatement(sql)) {
                    up.setLong(1, itemId);
                    if (stockLimit == null) {
                        up.setNull(2, java.sql.Types.INTEGER);
                        up.setNull(3, java.sql.Types.INTEGER);
                    } else {
                        up.setInt(2, stockLimit);
                        up.setInt(3, stockLimit);
                    }
                    boolean ok = up.executeUpdate() == 1;
                    c.commit();
                    return ok;
                }
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("reserve stock " + itemId, e);
        }
    }

    /** Estorna 1 unidade reservada (piso 0). */
    public void releaseStock(long itemId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE shop_stock SET sold = sold - 1 WHERE item_id = ? AND sold > 0")) {
            ps.setLong(1, itemId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("release stock " + itemId, e);
        }
    }

    /** Quantidade vendida do item (0 quando não há linha). */
    public int soldOf(long itemId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT sold FROM shop_stock WHERE item_id = ?")) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("soldOf " + itemId, e);
        }
    }
}
