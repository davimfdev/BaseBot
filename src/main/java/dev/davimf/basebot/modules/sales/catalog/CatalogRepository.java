// [OUTLINE START]
// Package: dev.davimf.basebot.modules.sales.catalog
// 
// Class: CatalogRepository
// 
// Constructors:
//   - `Constructor` : `public CatalogRepository(SqliteManager sqlite)`
// 
// Methods:
//   - `Method` : `public String createCategory(String guildId, String name)`
//   - `Method` : `public List<CatalogCategory> listCategories(String guildId)`
//   - `Method` : `public Optional<CatalogCategory> findCategory(String id)`
//   - `Method` : `public String createProduct(String guildId, String categoryId, String name, String description, long priceCents)`
//   - `Method` : `public List<CatalogProduct> listProducts(String categoryId)`
//   - `Method` : `public Optional<CatalogProduct> findProduct(String id)`
//   - `Method` : `private static CatalogCategory mapCategory(ResultSet rs)`
//   - `Method` : `private static CatalogProduct mapProduct(ResultSet rs)`
//   - `Method` : `private static String newId()`
// 
// Fields:
//   - `Field` : `private final SqliteManager sqlite`
// [OUTLINE END]



package dev.davimf.basebot.modules.sales.catalog;

import dev.davimf.basebot.database.model.CatalogCategory;
import dev.davimf.basebot.database.model.CatalogProduct;
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

/** SQLite store for the product catalog: categories and their products (BOTSPECS Module 3). */
public final class CatalogRepository {

    private final SqliteManager sqlite;

    public CatalogRepository(SqliteManager sqlite) {
        this.sqlite = sqlite;
    }

    // --- categories ------------------------------------------------------------

    /** Creates a category at the end of the guild's list and returns its new id. */
    public String createCategory(String guildId, String name) {
        String id = newId();
        String sql = """
                INSERT INTO catalog_categories (id, guild_id, name, position)
                VALUES (?, ?, ?, (SELECT COALESCE(MAX(position), -1) + 1
                                  FROM catalog_categories WHERE guild_id = ?))
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, name);
            ps.setString(4, guildId);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create catalog category for " + guildId, e);
        }
    }

    public List<CatalogCategory> listCategories(String guildId) {
        String sql = "SELECT * FROM catalog_categories WHERE guild_id = ? ORDER BY position, name";
        List<CatalogCategory> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, guildId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapCategory(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list catalog categories for " + guildId, e);
        }
    }

    public Optional<CatalogCategory> findCategory(String id) {
        String sql = "SELECT * FROM catalog_categories WHERE id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapCategory(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find catalog category " + id, e);
        }
    }

    /** Deletes a category and all of its products. */
    public void deleteCategory(String id) {
        try (Connection c = sqlite.getConnection()) {
            boolean prevAuto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement p = c.prepareStatement(
                         "DELETE FROM catalog_products WHERE category_id = ?");
                 PreparedStatement cat = c.prepareStatement(
                         "DELETE FROM catalog_categories WHERE id = ?")) {
                p.setString(1, id);
                p.executeUpdate();
                cat.setString(1, id);
                cat.executeUpdate();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("delete catalog category " + id, e);
        }
    }

    // --- products --------------------------------------------------------------

    public String createProduct(String guildId, String categoryId, String name,
                                String description, long priceCents) {
        String id = newId();
        String sql = """
                INSERT INTO catalog_products
                    (id, guild_id, category_id, name, description, price_cents, position)
                VALUES (?, ?, ?, ?, ?, ?, (SELECT COALESCE(MAX(position), -1) + 1
                                           FROM catalog_products WHERE category_id = ?))
                """;
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, guildId);
            ps.setString(3, categoryId);
            ps.setString(4, name);
            ps.setString(5, description);
            ps.setLong(6, priceCents);
            ps.setString(7, categoryId);
            ps.executeUpdate();
            return id;
        } catch (SQLException e) {
            throw new RepositoryException("create catalog product in " + categoryId, e);
        }
    }

    public List<CatalogProduct> listProducts(String categoryId) {
        String sql = "SELECT * FROM catalog_products WHERE category_id = ? ORDER BY position, name";
        List<CatalogProduct> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapProduct(rs));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("list catalog products in " + categoryId, e);
        }
    }

    public Optional<CatalogProduct> findProduct(String id) {
        String sql = "SELECT * FROM catalog_products WHERE id = ?";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapProduct(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("find catalog product " + id, e);
        }
    }

    public void deleteProduct(String id) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM catalog_products WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("delete catalog product " + id, e);
        }
    }

    // --- mapping ---------------------------------------------------------------

    private static CatalogCategory mapCategory(ResultSet rs) throws SQLException {
        return new CatalogCategory(
                rs.getString("id"),
                rs.getString("guild_id"),
                rs.getString("name"),
                rs.getInt("position"));
    }

    private static CatalogProduct mapProduct(ResultSet rs) throws SQLException {
        return new CatalogProduct(
                rs.getString("id"),
                rs.getString("guild_id"),
                rs.getString("category_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getLong("price_cents"),
                rs.getInt("position"));
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
