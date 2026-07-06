// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.economy
// 
// Class: FarmRepository
// 
// Constructors:
//   - `Constructor` : `public FarmRepository(SqliteManager sqlite)`
// 
// Methods:
//   - `Method` : `public String create(String guildId, String farmerId, String item, long quantity)`
//   - `Method` : `public Optional<Pending> find(String id)`
// 
// Fields:
//   - `Field` : `private final SqliteManager sqlite`
// 
// Record: Pending
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String farmerId
//   - Record Component : public final String item
//   - Record Component : public final long quantity
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

/** Pending farm submissions awaiting approval (BOTSPECS Module 4 — /farm). */
public final class FarmRepository {

    /** A queued farm submission. */
    public record Pending(String id, String guildId, String farmerId, String item, long quantity) {}

    private final SqliteManager sqlite;

    public FarmRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    public String create(String guildId, String farmerId, String item, long quantity) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO fac_farm_pending (id, guild_id, farmer_id, item, quantity)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, farmerId);
            ps.setString(4, item);
            ps.setLong(5, quantity);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create farm pending for " + guildId, e);
        }
    }

    public Optional<Pending> find(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM fac_farm_pending WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new Pending(rs.getString("id"), rs.getString("guild_id"),
                        rs.getString("farmer_id"), rs.getString("item"), rs.getLong("quantity")));
            }
        } catch (SQLException e) {
            throw new RepositoryException("find farm pending " + id, e);
        }
    }

    public void delete(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM fac_farm_pending WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete farm pending " + id, e);
        }
    }
}
