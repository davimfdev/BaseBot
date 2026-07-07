// [OUTLINE START]
// Package: dev.davimf.basebot.modules.tickets
// 
// Class: TicketCategoryRepository
// 
// Constructors:
//   - `Constructor` : `public TicketCategoryRepository(PostgresPool pool)`
// 
// Methods:
//   - `Method` : `public List<TicketCategory> listByGuild(String guildId)`
//   - `Method` : `public Optional<TicketCategory> find(String id)`
//   - `Method` : `public int count(String guildId)`
//   - `Method` : `private TicketCategory map(ResultSet rs)`
//   - `Method` : `private static String write(List<String> value)`
//   - `Method` : `private static List<String> read(String json)`
// 
// Fields:
//   - `Field` : `private static final TypeReference<List<String>> STR_LIST`
//   - `Field` : `private final PostgresPool pool`
// [OUTLINE END]



package dev.davimf.basebot.modules.tickets;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.davimf.basebot.database.model.TicketCategory;
import dev.davimf.basebot.database.postgres.PostgresPool;
import dev.davimf.basebot.database.postgres.RepositoryException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Postgres store for ticket categories (config source of truth; BOTSPECS Module 2). */
public final class TicketCategoryRepository {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STR_LIST = new TypeReference<>() {};

    private final PostgresPool pool;

    public TicketCategoryRepository(PostgresPool pool) {
        this.pool = pool;
    }

    public List<TicketCategory> listByGuild(String guildId) {
        String sql = "SELECT * FROM ticket_categories WHERE guild_id = ? ORDER BY position, created_at";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                List<TicketCategory> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new RepositoryException("list ticket categories " + guildId, e);
        }
    }

    public Optional<TicketCategory> find(String id) {
        String sql = "SELECT * FROM ticket_categories WHERE id = ?";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find ticket category " + id, e);
        }
    }

    public int count(String guildId) {
        String sql = "SELECT count(*) FROM ticket_categories WHERE guild_id = ?";
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("count ticket categories " + guildId, e);
        }
    }

    public void upsert(TicketCategory cat) {
        String sql = """
                INSERT INTO ticket_categories
                    (id, guild_id, name, emoji, description, discord_category_id, staff_role_ids)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (id) DO UPDATE SET
                    name                = EXCLUDED.name,
                    emoji               = EXCLUDED.emoji,
                    description         = EXCLUDED.description,
                    discord_category_id = EXCLUDED.discord_category_id,
                    staff_role_ids      = EXCLUDED.staff_role_ids
                """;
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cat.id());
            ps.setString(2, cat.guildId());
            ps.setString(3, cat.name());
            ps.setString(4, cat.emoji());
            ps.setString(5, cat.description());
            ps.setString(6, cat.discordCategoryId());
            ps.setString(7, write(cat.staffRoleIds()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("upsert ticket category " + cat.id(), e);
        }
    }

    public void delete(String id) {
        try (Connection c = pool.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ticket_categories WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete ticket category " + id, e);
        }
    }

    private TicketCategory map(ResultSet rs) throws SQLException {
        return new TicketCategory(
                rs.getString("id"),
                rs.getString("guild_id"),
                rs.getString("name"),
                rs.getString("emoji"),
                rs.getString("description"),
                rs.getString("discord_category_id"),
                read(rs.getString("staff_role_ids"))
        );
    }

    private static String write(List<String> value) {
        try {
            return JSON.writeValueAsString(value == null ? List.of() : value);
        } catch (Exception e) {
            throw new RepositoryException("serialize staff_role_ids", e);
        }
    }

    private static List<String> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, STR_LIST);
        } catch (Exception e) {
            throw new RepositoryException("deserialize staff_role_ids", e);
        }
    }
}
