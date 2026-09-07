package dev.davimf.basebot.modules.sales.budget;

import dev.davimf.basebot.database.model.Budget;
import dev.davimf.basebot.database.model.BudgetItem;
import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SQLite store for budgets (orçamentos) and their line items (BOTSPECS Module 3). */
public final class BudgetRepository {

    private final SqliteManager sqlite;

    public BudgetRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public String createDraft(String guildId, String sellerId, String clientId) {
        String id = newId();
        String sql = """
                INSERT INTO budgets (id, guild_id, seller_id, client_id, status)
                VALUES (?, ?, ?, ?, 'DRAFT')
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, sellerId);
            ps.setString(4, clientId);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create draft budget for " + guildId, e);
        }
    }

    public void addItem(String budgetId, String productName, long unitPriceCents, int quantity) {
        String sql = """
                INSERT INTO budget_items (id, budget_id, product_name, unit_price_cents, quantity)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newId());
            ps.setString(2, budgetId);
            ps.setString(3, productName);
            ps.setLong(4, unitPriceCents);
            ps.setInt(5, quantity);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("add item to budget " + budgetId, e);
        }
    }

    public List<BudgetItem> listItems(String budgetId) {
        String sql = "SELECT * FROM budget_items WHERE budget_id = ? ORDER BY rowid";
        List<BudgetItem> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, budgetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new BudgetItem(
                            rs.getString("id"),
                            rs.getString("budget_id"),
                            rs.getString("product_name"),
                            rs.getLong("unit_price_cents"),
                            rs.getInt("quantity")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list items for budget " + budgetId, e);
        }
    }

    public Optional<Budget> find(String id) {
        String sql = "SELECT * FROM budgets WHERE id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find budget " + id, e);
        }
    }

    public void setStatus(String id, String status) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE budgets SET status = ? WHERE id = ?")) {
            ps.setString(1, status);
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("set status for budget " + id, e);
        }
    }

    /** Marks a budget PENDING and records where its approval message lives + when it expires. */
    public void markSent(String id, String channelId, String messageId, String expiresAtIso) {
        String sql = """
                UPDATE budgets
                   SET status = 'PENDING', channel_id = ?, message_id = ?,
                       sent_at = datetime('now'), expires_at = ?
                 WHERE id = ?
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, channelId);
            ps.setString(2, messageId);
            ps.setString(3, expiresAtIso);
            ps.setString(4, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("mark budget sent " + id, e);
        }
    }

    /** PENDING budgets whose {@code expires_at} is at or before {@code nowIso}. */
    public List<Budget> listPendingExpired(String nowIso) {
        String sql = "SELECT * FROM budgets WHERE status = 'PENDING' AND expires_at IS NOT NULL "
                + "AND expires_at <= ?";
        List<Budget> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, nowIso);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list expired budgets", e);
        }
    }

    private static Budget map(ResultSet rs) throws SQLException {
        return new Budget(
                rs.getString("id"),
                rs.getString("guild_id"),
                rs.getString("seller_id"),
                rs.getString("client_id"),
                rs.getString("status"),
                rs.getString("channel_id"),
                rs.getString("message_id"),
                rs.getString("created_at"),
                rs.getString("sent_at"),
                rs.getString("expires_at"));
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
