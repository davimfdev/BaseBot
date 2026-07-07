package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQLite store da economia por-usuário (migração 029). Deduções atômicas via WHERE (anti double-spend). */
public final class WalletRepository {

    public record Wallet(long cash, long bank) {}
    public record Entry(String userId, long total) {}

    private final SqliteManager sqlite;

    public WalletRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    public Wallet get(String g, String u) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT cash, bank FROM user_wallets WHERE guild_id=? AND user_id=?")) {
            ps.setString(1, g);
            ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Wallet(rs.getLong(1), rs.getLong(2)) : new Wallet(0, 0);
            }
        } catch (SQLException e) {
            throw new RepositoryException("get wallet " + g + "/" + u, e);
        }
    }

    /** Credita a carteira (upsert). Para injetar dinheiro (daily/work/crime-win/admin). */
    public void addCash(String g, String u, long delta) {
        upsert(g, u, "cash", delta);
    }

    public void addBank(String g, String u, long delta) {
        upsert(g, u, "bank", delta);
    }

    private void upsert(String g, String u, String col, long delta) {
        String sql = "INSERT INTO user_wallets (guild_id, user_id, " + col + ") VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET " + col + " = " + col + " + excluded." + col;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setLong(3, delta);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("add " + col + " " + g + "/" + u, e);
        }
    }

    public void setCash(String g, String u, long value) { set(g, u, "cash", Math.max(0, value)); }
    public void setBank(String g, String u, long value) { set(g, u, "bank", Math.max(0, value)); }

    private void set(String g, String u, String col, long value) {
        String sql = "INSERT INTO user_wallets (guild_id, user_id, " + col + ") VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, user_id) DO UPDATE SET " + col + " = excluded." + col;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setLong(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("set " + col + " " + g + "/" + u, e);
        }
    }

    /** Debita a carteira só se houver saldo (atômico). */
    public boolean tryDebitCash(String g, String u, long amount) {
        if (amount <= 0) {
            return true;
        }
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE user_wallets SET cash = cash - ? WHERE guild_id=? AND user_id=? AND cash >= ?")) {
            ps.setLong(1, amount);
            ps.setString(2, g);
            ps.setString(3, u);
            ps.setLong(4, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("debit cash " + g + "/" + u, e);
        }
    }

    public boolean deposit(String g, String u, long amount) { return move(g, u, "cash", "bank", amount); }
    public boolean withdraw(String g, String u, long amount) { return move(g, u, "bank", "cash", amount); }

    private boolean move(String g, String u, String from, String to, long amount) {
        if (amount <= 0) {
            return false;
        }
        String sql = "UPDATE user_wallets SET " + from + " = " + from + " - ?, " + to + " = " + to + " + ? "
                + "WHERE guild_id=? AND user_id=? AND " + from + " >= ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, amount);
            ps.setLong(2, amount);
            ps.setString(3, g);
            ps.setString(4, u);
            ps.setLong(5, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("move " + from + "->" + to + " " + g + "/" + u, e);
        }
    }

    /** Transfere carteira→carteira: debita o pagador (atômico) e credita o recebedor (upsert), numa transação. */
    public boolean transfer(String g, String fromU, String toU, long amount) {
        if (amount <= 0) {
            return false;
        }
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int debited;
                try (PreparedStatement deb = c.prepareStatement(
                        "UPDATE user_wallets SET cash = cash - ? WHERE guild_id=? AND user_id=? AND cash >= ?")) {
                    deb.setLong(1, amount);
                    deb.setString(2, g);
                    deb.setString(3, fromU);
                    deb.setLong(4, amount);
                    debited = deb.executeUpdate();
                }
                if (debited == 0) {
                    c.rollback();
                    return false;
                }
                try (PreparedStatement cred = c.prepareStatement(
                        "INSERT INTO user_wallets (guild_id, user_id, cash) VALUES (?,?,?) "
                        + "ON CONFLICT (guild_id, user_id) DO UPDATE SET cash = cash + excluded.cash")) {
                    cred.setString(1, g);
                    cred.setString(2, toU);
                    cred.setLong(3, amount);
                    cred.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("transfer " + g + " " + fromU + "->" + toU, e);
        }
    }

    public List<Entry> topPage(String g, int limit, int offset) {
        List<Entry> out = new ArrayList<>();
        String sql = "SELECT user_id, (cash + bank) AS total FROM user_wallets WHERE guild_id=? "
                + "ORDER BY total DESC, user_id LIMIT ? OFFSET ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, g);
            ps.setInt(2, limit);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Entry(rs.getString("user_id"), rs.getLong("total")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("topPage " + g, e);
        }
    }

    public int rank(String g, String u) {
        Wallet w = get(g, u);
        long total = w.cash() + w.bank();
        if (total <= 0 && count(g) == 0) {
            return 0;
        }
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM user_wallets WHERE guild_id=? AND (cash + bank) > ?")) {
            ps.setString(1, g);
            ps.setLong(2, total);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("rank " + g + "/" + u, e);
        }
    }

    public int count(String g) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM user_wallets WHERE guild_id=?")) {
            ps.setString(1, g);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("count " + g, e);
        }
    }
}
