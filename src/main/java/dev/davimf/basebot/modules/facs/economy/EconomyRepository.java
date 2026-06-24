package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.database.model.FacTransaction;
import dev.davimf.basebot.database.model.StockEntry;
import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite store for the faction treasury, transaction log and raw-material stock. */
public final class EconomyRepository {

    private final SqliteManager sqlite;

    public EconomyRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    // --- treasury --------------------------------------------------------------

    public long getBalance(String guildId) {
        String sql = "SELECT balance_cents FROM fac_finance WHERE guild_id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("get balance for " + guildId, e);
        }
    }

    /** Applies a signed delta to the balance and records a transaction. Returns new balance. */
    public long adjust(String guildId, String type, long deltaCents, String actorId, String note) {
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement up = c.prepareStatement("""
                        INSERT INTO fac_finance (guild_id, balance_cents) VALUES (?, ?)
                        ON CONFLICT (guild_id) DO UPDATE SET balance_cents = balance_cents + ?
                        """)) {
                    up.setString(1, guildId);
                    up.setLong(2, deltaCents);
                    up.setLong(3, deltaCents);
                    up.executeUpdate();
                }
                try (PreparedStatement tx = c.prepareStatement("""
                        INSERT INTO fac_transactions (id, guild_id, type, amount_cents, actor_id, note)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)) {
                    tx.setString(1, newId());
                    tx.setString(2, guildId);
                    tx.setString(3, type);
                    tx.setLong(4, deltaCents);
                    tx.setString(5, actorId);
                    tx.setString(6, note);
                    tx.executeUpdate();
                }
                long balance;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT balance_cents FROM fac_finance WHERE guild_id = ?")) {
                    sel.setString(1, guildId);
                    try (ResultSet rs = sel.executeQuery()) {
                        balance = rs.next() ? rs.getLong(1) : 0L;
                    }
                }
                c.commit();
                return balance;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("adjust balance for " + guildId, e);
        }
    }

    public List<FacTransaction> listTransactions(String guildId, String sinceIso) {
        String sql = "SELECT * FROM fac_transactions WHERE guild_id = ? AND created_at >= ? "
                + "ORDER BY created_at DESC";
        List<FacTransaction> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, sinceIso);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new FacTransaction(
                            rs.getString("id"), rs.getString("guild_id"), rs.getString("type"),
                            rs.getLong("amount_cents"), rs.getString("actor_id"),
                            rs.getString("note"), rs.getString("created_at")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list transactions for " + guildId, e);
        }
    }

    // --- stock -----------------------------------------------------------------

    /** Adds {@code delta} to an item's stock (delta may be negative). Returns the new qty. */
    public long addStock(String guildId, String item, long delta) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO fac_stock (guild_id, item, quantity) VALUES (?, ?, ?)
                     ON CONFLICT (guild_id, item) DO UPDATE SET quantity = quantity + ?
                     """)) {
            ps.setString(1, guildId);
            ps.setString(2, item);
            ps.setLong(3, delta);
            ps.setLong(4, delta);
            ps.executeUpdate();
            return getStock(guildId, item);
        } catch (SQLException e) {
            throw new RepositoryException("add stock " + item + " for " + guildId, e);
        }
    }

    public long getStock(String guildId, String item) {
        String sql = "SELECT quantity FROM fac_stock WHERE guild_id = ? AND item = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new RepositoryException("get stock " + item + " for " + guildId, e);
        }
    }

    /** Deducts {@code qty} only if enough is in stock; returns false otherwise (atomic). */
    public boolean deductStock(String guildId, String item, long qty) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     UPDATE fac_stock SET quantity = quantity - ?
                     WHERE guild_id = ? AND item = ? AND quantity >= ?
                     """)) {
            ps.setLong(1, qty);
            ps.setString(2, guildId);
            ps.setString(3, item);
            ps.setLong(4, qty);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("deduct stock " + item + " for " + guildId, e);
        }
    }

    public List<StockEntry> listStock(String guildId) {
        String sql = "SELECT * FROM fac_stock WHERE guild_id = ? AND quantity != 0 ORDER BY item";
        List<StockEntry> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StockEntry(rs.getString("guild_id"), rs.getString("item"),
                            rs.getLong("quantity")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list stock for " + guildId, e);
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
