package dev.davimf.basebot.modules.base.giveaway;

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

/** SQLite store dos sorteios (migração 030). */
public final class GiveawayRepository {

    private final SqliteManager sqlite;

    public GiveawayRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    /** Cria o sorteio com um id gerado; retenta se colidir. Retorna o id. */
    public String create(Giveaway g) {
        String sql = "INSERT INTO giveaways (id, guild_id, channel_id, prize, coin_reward, winners, ends_at, "
                + "req_role_id, req_min_days, req_min_voice_hours, req_window_start, req_window_end) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        for (int attempt = 0; attempt < 5; attempt++) {
            String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            if (find(id).isPresent()) {
                continue;
            }
            try (Connection c = sqlite.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, id);
                ps.setString(2, g.guildId());
                ps.setString(3, g.channelId());
                ps.setString(4, g.prize());
                ps.setLong(5, g.coinReward());
                ps.setInt(6, g.winners());
                ps.setLong(7, g.endsAt());
                ps.setString(8, g.reqRoleId());
                ps.setInt(9, g.reqMinDays());
                ps.setInt(10, g.reqMinVoiceHours());
                ps.setInt(11, g.reqWindowStart());
                ps.setInt(12, g.reqWindowEnd());
                ps.executeUpdate();
                return id;
            } catch (SQLException e) {
                if (attempt == 4) {
                    throw new RepositoryException("create giveaway " + g.guildId(), e);
                }
            }
        }
        throw new RepositoryException("create giveaway: não gerou id único", null);
    }

    public Optional<Giveaway> find(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM giveaways WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find giveaway " + id, e);
        }
    }

    public void setMessageId(String id, String messageId) {
        exec("UPDATE giveaways SET message_id=? WHERE id=?", ps -> { ps.setString(1, messageId); ps.setString(2, id); });
    }

    public void setEnded(String id) {
        exec("UPDATE giveaways SET ended=1 WHERE id=?", ps -> ps.setString(1, id));
    }

    /** true se a entrada é nova (INSERT OR IGNORE). */
    public boolean addEntry(String giveawayId, String userId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT OR IGNORE INTO giveaway_entries (giveaway_id, user_id) VALUES (?,?)")) {
            ps.setString(1, giveawayId);
            ps.setString(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RepositoryException("add entry " + giveawayId, e);
        }
    }

    public List<String> entries(String giveawayId) {
        List<String> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT user_id FROM giveaway_entries WHERE giveaway_id=?")) {
            ps.setString(1, giveawayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("entries " + giveawayId, e);
        }
    }

    public int entryCount(String giveawayId) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM giveaway_entries WHERE giveaway_id=?")) {
            ps.setString(1, giveawayId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RepositoryException("entryCount " + giveawayId, e);
        }
    }

    public List<Giveaway> dueActive(long now) {
        List<Giveaway> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM giveaways WHERE ended=0 AND ends_at<=?")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("dueActive", e);
        }
    }

    private static Giveaway map(ResultSet rs) throws SQLException {
        return new Giveaway(rs.getString("id"), rs.getString("guild_id"), rs.getString("channel_id"),
                rs.getString("message_id"), rs.getString("prize"), rs.getLong("coin_reward"),
                rs.getInt("winners"), rs.getLong("ends_at"), rs.getInt("ended") == 1,
                rs.getString("req_role_id"), rs.getInt("req_min_days"), rs.getInt("req_min_voice_hours"),
                rs.getInt("req_window_start"), rs.getInt("req_window_end"));
    }

    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private void exec(String sql, Binder b) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            b.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("exec giveaway", e);
        }
    }
}
