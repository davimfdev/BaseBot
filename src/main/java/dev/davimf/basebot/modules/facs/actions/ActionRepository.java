package dev.davimf.basebot.modules.facs.actions;

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

/** SQLite store for actions and their participants (BOTSPECS Module 4 — /painel-acoes). */
public final class ActionRepository {

    public static final String CONFIRMED = "CONFIRMED";
    public static final String RESERVE = "RESERVE";

    /**
     * An action event. {@code actionName}/{@code minContingent}/{@code dirtyMoney} are
     * denormalized from the saved action type at creation so history survives type edits.
     * {@code isPast} actions skip the queue (members are backfilled). {@code dueAt} is the
     * epoch-millis schedule for future actions (0 = none); {@code dueNotified} is set once
     * the scheduler reveals the Vitória/Derrota buttons.
     */
    public record Action(String id, String guildId, String channelId, String messageId,
                         String whenText, int capacity, String status, String createdBy,
                         String actionName, int minContingent, int dirtyMoney,
                         boolean isPast, boolean entriesOpen, long dueAt, boolean dueNotified) {}

    /** A participant row. */
    public record Participant(String userId, String kind, int priority, String joinedAt) {}

    private final SqliteManager sqlite;

    public ActionRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public String create(String guildId, String createdBy, String whenText, int capacity,
                         String actionName, int minContingent, int dirtyMoney,
                         boolean isPast, boolean entriesOpen, long dueAt) {
        String id = newId();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO fac_actions
                         (id, guild_id, when_text, capacity, created_by,
                          action_name, min_contingent, dirty_money,
                          is_past, entries_open, due_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, whenText);
            ps.setInt(4, capacity);
            ps.setString(5, createdBy);
            ps.setString(6, actionName);
            ps.setInt(7, minContingent);
            ps.setInt(8, dirtyMoney);
            ps.setInt(9, isPast ? 1 : 0);
            ps.setInt(10, entriesOpen ? 1 : 0);
            ps.setLong(11, dueAt);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create action for " + guildId, e);
        }
    }

    public void setMessage(String id, String channelId, String messageId) {
        update("UPDATE fac_actions SET channel_id = ?, message_id = ? WHERE id = ?",
                ps -> {
                    ps.setString(1, channelId);
                    ps.setString(2, messageId);
                    ps.setString(3, id);
                }, "set action message " + id);
    }

    public void setStatus(String id, String status) {
        update("UPDATE fac_actions SET status = ? WHERE id = ?", ps -> {
            ps.setString(1, status);
            ps.setString(2, id);
        }, "set action status " + id);
    }

    public void setEntriesOpen(String id, boolean open) {
        update("UPDATE fac_actions SET entries_open = ? WHERE id = ?", ps -> {
            ps.setInt(1, open ? 1 : 0);
            ps.setString(2, id);
        }, "set action entries " + id);
    }

    public void setWhen(String id, String whenText, long dueAt) {
        update("UPDATE fac_actions SET when_text = ?, due_at = ?, due_notified = 0 WHERE id = ?", ps -> {
            ps.setString(1, whenText);
            ps.setLong(2, dueAt);
            ps.setString(3, id);
        }, "set action when " + id);
    }

    public void markDueNotified(String id) {
        update("UPDATE fac_actions SET due_notified = 1 WHERE id = ?",
                ps -> ps.setString(1, id), "mark action due " + id);
    }

    public Optional<Action> find(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM fac_actions WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapAction(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find action " + id, e);
        }
    }

    /** Open actions for a guild, newest first (for the "Editar ações" picker). */
    public List<Action> listOpenByGuild(String guildId) {
        String sql = "SELECT * FROM fac_actions WHERE guild_id = ? AND status = 'OPEN' "
                + "ORDER BY created_at DESC";
        List<Action> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapAction(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list open actions " + guildId, e);
        }
    }

    /** Future, still-open actions whose scheduled time has passed and weren't revealed yet. */
    public List<Action> dueForReveal(long now) {
        String sql = "SELECT * FROM fac_actions WHERE status = 'OPEN' AND is_past = 0 "
                + "AND due_notified = 0 AND due_at > 0 AND due_at <= ?";
        List<Action> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapAction(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list due actions", e);
        }
    }

    private static Action mapAction(ResultSet rs) throws SQLException {
        return new Action(rs.getString("id"), rs.getString("guild_id"),
                rs.getString("channel_id"), rs.getString("message_id"), rs.getString("when_text"),
                rs.getInt("capacity"), rs.getString("status"), rs.getString("created_by"),
                rs.getString("action_name"), rs.getInt("min_contingent"), rs.getInt("dirty_money"),
                rs.getInt("is_past") == 1, rs.getInt("entries_open") == 1,
                rs.getLong("due_at"), rs.getInt("due_notified") == 1);
    }

    // --- participants ----------------------------------------------------------

    public Optional<Participant> participant(String actionId, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM fac_action_participants WHERE action_id = ? AND user_id = ?")) {
            ps.setString(1, actionId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapParticipant(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("get participant", e);
        }
    }

    public void put(String actionId, String userId, String kind, int priority) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO fac_action_participants (action_id, user_id, kind, priority)
                     VALUES (?, ?, ?, ?)
                     ON CONFLICT (action_id, user_id) DO UPDATE SET kind = excluded.kind
                     """)) {
            ps.setString(1, actionId);
            ps.setString(2, userId);
            ps.setString(3, kind);
            ps.setInt(4, priority);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("put participant", e);
        }
    }

    public void remove(String actionId, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "DELETE FROM fac_action_participants WHERE action_id = ? AND user_id = ?")) {
            ps.setString(1, actionId);
            ps.setString(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("remove participant", e);
        }
    }

    /** Confirmed list, lowest priority + latest join first (the bump candidate is index 0). */
    public List<Participant> confirmedByBumpOrder(String actionId) {
        return list(actionId, CONFIRMED, "priority ASC, joined_at DESC");
    }

    /** Confirmed list in join order (for display). */
    public List<Participant> confirmed(String actionId) {
        return list(actionId, CONFIRMED, "joined_at ASC");
    }

    /** Reserve list, highest priority + earliest join first (next-promotion is index 0). */
    public List<Participant> reserveByPromotionOrder(String actionId) {
        return list(actionId, RESERVE, "priority DESC, joined_at ASC");
    }

    public int countConfirmed(String actionId) {
        return confirmed(actionId).size();
    }

    private List<Participant> list(String actionId, String kind, String order) {
        String sql = "SELECT * FROM fac_action_participants WHERE action_id = ? AND kind = ? ORDER BY " + order;
        List<Participant> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, actionId);
            ps.setString(2, kind);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapParticipant(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list participants", e);
        }
    }

    private static Participant mapParticipant(ResultSet rs) throws SQLException {
        return new Participant(rs.getString("user_id"), rs.getString("kind"),
                rs.getInt("priority"), rs.getString("joined_at"));
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private void update(String sql, Binder binder, String errorContext) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException(errorContext, e);
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
