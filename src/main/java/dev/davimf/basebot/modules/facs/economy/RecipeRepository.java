// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.economy
// 
// Class: RecipeRepository
// 
// Constructors:
//   - `Constructor` : `public RecipeRepository(SqliteManager sqlite)`
// 
// Methods:
//   - `Method` : `public Optional<Recipe> findByProduct(String guildId, String product)`
//   - `Method` : `public List<Recipe> list(String guildId)`
//   - `Method` : `private static String recipeId(Connection c, String guildId, String product)`
//   - `Method` : `private static List<Input> loadInputs(Connection c, String recipeId)`
//   - `Method` : `private static String newId()`
// 
// Fields:
//   - `Field` : `private final SqliteManager sqlite`
// 
// Record: Input
// 
// Record Components:
//   - Record Component : public final String item
//   - Record Component : public final long qty
// 
// Record: Recipe
// 
// Record Components:
//   - Record Component : public final String id
//   - Record Component : public final String guildId
//   - Record Component : public final String product
//   - Record Component : public final long outputQty
//   - Record Component : public final List<Input> inputs
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.economy;

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

/** SQLite store for production recipes (BOTSPECS Module 4 — /produzir). */
public final class RecipeRepository {

    /** A recipe input: {@code qty} units of {@code item} per craft. */
    public record Input(String item, long qty) {}

    /** A full recipe: {@code outputQty} units of {@code product} from {@code inputs}. */
    public record Recipe(String id, String guildId, String product, long outputQty, List<Input> inputs) {}

    private final SqliteManager sqlite;

    public RecipeRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    /** Replaces any existing recipe for {@code product} with the given definition. */
    public void upsert(String guildId, String product, long outputQty, List<Input> inputs) {
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String existingId = recipeId(c, guildId, product);
                if (existingId != null) {
                    deleteInputs(c, existingId);
                    try (PreparedStatement d = c.prepareStatement("DELETE FROM fac_recipes WHERE id = ?")) {
                        d.setString(1, existingId);
                        d.executeUpdate();
                    }
                }
                String id = newId();
                try (PreparedStatement r = c.prepareStatement(
                        "INSERT INTO fac_recipes (id, guild_id, product, output_qty) VALUES (?, ?, ?, ?)")) {
                    r.setString(1, id);
                    r.setString(2, guildId);
                    r.setString(3, product);
                    r.setLong(4, outputQty);
                    r.executeUpdate();
                }
                try (PreparedStatement in = c.prepareStatement(
                        "INSERT INTO fac_recipe_inputs (id, recipe_id, item, qty) VALUES (?, ?, ?, ?)")) {
                    for (Input input : inputs) {
                        in.setString(1, newId());
                        in.setString(2, id);
                        in.setString(3, input.item());
                        in.setLong(4, input.qty());
                        in.addBatch();
                    }
                    in.executeBatch();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("upsert recipe " + product, e);
        }
    }

    public Optional<Recipe> findByProduct(String guildId, String product) {
        String sql = "SELECT * FROM fac_recipes WHERE guild_id = ? AND product = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            ps.setString(2, product);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String id = rs.getString("id");
                return Optional.of(new Recipe(id, guildId, rs.getString("product"),
                        rs.getLong("output_qty"), loadInputs(c, id)));
            }
        } catch (SQLException e) {
            throw new RepositoryException("find recipe " + product, e);
        }
    }

    public List<Recipe> list(String guildId) {
        String sql = "SELECT * FROM fac_recipes WHERE guild_id = ? ORDER BY product";
        List<Recipe> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    out.add(new Recipe(id, guildId, rs.getString("product"),
                            rs.getLong("output_qty"), loadInputs(c, id)));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list recipes for " + guildId, e);
        }
    }

    // --- helpers ---------------------------------------------------------------

    private static String recipeId(Connection c, String guildId, String product) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM fac_recipes WHERE guild_id = ? AND product = ?")) {
            ps.setString(1, guildId);
            ps.setString(2, product);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void deleteInputs(Connection c, String recipeId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM fac_recipe_inputs WHERE recipe_id = ?")) {
            ps.setString(1, recipeId);
            ps.executeUpdate();
        }
    }

    private static List<Input> loadInputs(Connection c, String recipeId) throws SQLException {
        List<Input> inputs = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT item, qty FROM fac_recipe_inputs WHERE recipe_id = ?")) {
            ps.setString(1, recipeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    inputs.add(new Input(rs.getString("item"), rs.getLong("qty")));
                }
            }
        }
        return inputs;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
