package dev.davimf.basebot.modules.base.economy;

import dev.davimf.basebot.database.postgres.RepositoryException;
import dev.davimf.basebot.database.sqlite.SqliteManager;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Equip;
import dev.davimf.basebot.modules.base.economy.EquipmentCatalog.Slot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Inventário do usuário (migração 035). Resultado explícito em useOnce/destroy; slot vem do catálogo. */
public final class InventoryRepository {

    public record Row(long id, String itemKey, Slot slot, int usosLeft, boolean equipped) {}

    public enum UseResultType { USED, USED_AND_BROKE, PERMANENT, NOT_FOUND, NOT_OWNER }
    public record UseResult(UseResultType type, int usosLeft) {}

    public enum DestroyResultType { DESTROYED, NOT_FOUND, NOT_OWNER }
    public record DestroyResult(DestroyResultType type) {}

    private final SqliteManager sqlite;

    public InventoryRepository(SqliteManager sqlite) { this.sqlite = sqlite; }

    /** Compra: resolve slot + usos do catálogo, insere instância nova. Devolve rowId (ou -1 se key inválida). */
    public long buy(String g, String u, String itemKey) {
        Equip e = EquipmentCatalog.byKey(itemKey);
        if (e == null) {
            return -1;
        }
        String sql = "INSERT INTO user_inventory (guild_id, user_id, item_key, slot, usos_left, equipped, created_at) "
                + "VALUES (?,?,?,?,?,0,?)";
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, e.key());
            ps.setString(4, e.slot().name());
            ps.setInt(5, e.maxUsos());
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        } catch (SQLException ex) {
            throw new RepositoryException("inventory buy " + g + "/" + u, ex);
        }
    }

    /** Itens do usuário; pula linhas cujo item_key sumiu do catálogo ou cujo slot não bate (dados inconsistentes). */
    public List<Row> list(String g, String u) {
        List<Row> out = new ArrayList<>();
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM user_inventory WHERE guild_id=? AND user_id=? ORDER BY slot, created_at")) {
            ps.setString(1, g);
            ps.setString(2, u);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Row row = mapValid(rs);
                    if (row != null) {
                        out.add(row);
                    }
                }
            }
            return out;
        } catch (SQLException e) {
            throw new RepositoryException("inventory list " + g + "/" + u, e);
        }
    }

    public Row equipped(String g, String u, Slot slot) {
        try (Connection c = sqlite.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM user_inventory WHERE guild_id=? AND user_id=? AND slot=? AND equipped=1")) {
            ps.setString(1, g);
            ps.setString(2, u);
            ps.setString(3, slot.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapValid(rs) : null;
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory equipped " + g + "/" + u, e);
        }
    }

    /** Equipa numa transação: valida dono, desequipa o slot, equipa esta linha. false se não existe/não é do dono/key inválida. */
    public boolean equip(String g, String u, long rowId) {
        try (Connection c = sqlite.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String slot;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT item_key, slot FROM user_inventory WHERE id=? AND guild_id=? AND user_id=?")) {
                    sel.setLong(1, rowId);
                    sel.setString(2, g);
                    sel.setString(3, u);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return false;
                        }
                        Equip e = EquipmentCatalog.byKey(rs.getString("item_key"));
                        slot = rs.getString("slot");
                        if (e == null || !e.slot().name().equals(slot)) { // slot inconsistente com o catálogo
                            c.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement un = c.prepareStatement(
                        "UPDATE user_inventory SET equipped=0 WHERE guild_id=? AND user_id=? AND slot=? AND equipped=1")) {
                    un.setString(1, g);
                    un.setString(2, u);
                    un.setString(3, slot);
                    un.executeUpdate();
                }
                try (PreparedStatement eq = c.prepareStatement("UPDATE user_inventory SET equipped=1 WHERE id=?")) {
                    eq.setLong(1, rowId);
                    eq.executeUpdate();
                }
                c.commit();
                return true;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory equip " + g + "/" + u + "/" + rowId, e);
        }
    }

    /** Consome 1 uso numa transação. Resultado explícito. */
    public UseResult useOnce(String g, String u, long rowId) {
        try (Connection c = sqlite.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int usos;
                String owner;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT user_id, usos_left FROM user_inventory WHERE id=? AND guild_id=?")) {
                    sel.setLong(1, rowId);
                    sel.setString(2, g);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return new UseResult(UseResultType.NOT_FOUND, 0);
                        }
                        owner = rs.getString("user_id");
                        usos = rs.getInt("usos_left");
                    }
                }
                if (!owner.equals(u)) {
                    c.rollback();
                    return new UseResult(UseResultType.NOT_OWNER, 0);
                }
                if (usos < 0) { // permanente (reservado; v1 não usa)
                    c.rollback();
                    return new UseResult(UseResultType.PERMANENT, usos);
                }
                int left = usos - 1;
                if (left <= 0) {
                    try (PreparedStatement del = c.prepareStatement("DELETE FROM user_inventory WHERE id=?")) {
                        del.setLong(1, rowId);
                        del.executeUpdate();
                    }
                    c.commit();
                    return new UseResult(UseResultType.USED_AND_BROKE, 0);
                }
                try (PreparedStatement up = c.prepareStatement("UPDATE user_inventory SET usos_left=? WHERE id=?")) {
                    up.setInt(1, left);
                    up.setLong(2, rowId);
                    up.executeUpdate();
                }
                c.commit();
                return new UseResult(UseResultType.USED, left);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory useOnce " + g + "/" + u + "/" + rowId, e);
        }
    }

    /** Consome {@code n} usos numa transação (n>=1). Se sobrar <= 0, quebra (deleta). Resultado explícito. */
    public UseResult useMany(String g, String u, long rowId, int n) {
        int use = Math.max(1, n);
        try (Connection c = sqlite.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                int usos;
                String owner;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT user_id, usos_left FROM user_inventory WHERE id=? AND guild_id=?")) {
                    sel.setLong(1, rowId);
                    sel.setString(2, g);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return new UseResult(UseResultType.NOT_FOUND, 0);
                        }
                        owner = rs.getString("user_id");
                        usos = rs.getInt("usos_left");
                    }
                }
                if (!owner.equals(u)) {
                    c.rollback();
                    return new UseResult(UseResultType.NOT_OWNER, 0);
                }
                if (usos < 0) { // permanente (reservado; v1 não usa)
                    c.rollback();
                    return new UseResult(UseResultType.PERMANENT, usos);
                }
                int left = usos - use;
                if (left <= 0) {
                    try (PreparedStatement del = c.prepareStatement("DELETE FROM user_inventory WHERE id=?")) {
                        del.setLong(1, rowId);
                        del.executeUpdate();
                    }
                    c.commit();
                    return new UseResult(UseResultType.USED_AND_BROKE, 0);
                }
                try (PreparedStatement up = c.prepareStatement("UPDATE user_inventory SET usos_left=? WHERE id=?")) {
                    up.setInt(1, left);
                    up.setLong(2, rowId);
                    up.executeUpdate();
                }
                c.commit();
                return new UseResult(UseResultType.USED, left);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory useMany " + g + "/" + u + "/" + rowId, e);
        }
    }

    /** Destrói (remove) a linha. Resultado explícito. */
    public DestroyResult destroy(String g, String u, long rowId) {
        try (Connection c = sqlite.getConnection()) {
            boolean prev = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                String owner;
                try (PreparedStatement sel = c.prepareStatement(
                        "SELECT user_id FROM user_inventory WHERE id=? AND guild_id=?")) {
                    sel.setLong(1, rowId);
                    sel.setString(2, g);
                    try (ResultSet rs = sel.executeQuery()) {
                        if (!rs.next()) {
                            c.rollback();
                            return new DestroyResult(DestroyResultType.NOT_FOUND);
                        }
                        owner = rs.getString("user_id");
                    }
                }
                if (!owner.equals(u)) {
                    c.rollback();
                    return new DestroyResult(DestroyResultType.NOT_OWNER);
                }
                try (PreparedStatement del = c.prepareStatement("DELETE FROM user_inventory WHERE id=?")) {
                    del.setLong(1, rowId);
                    del.executeUpdate();
                }
                c.commit();
                return new DestroyResult(DestroyResultType.DESTROYED);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prev);
            }
        } catch (SQLException e) {
            throw new RepositoryException("inventory destroy " + g + "/" + u + "/" + rowId, e);
        }
    }

    /** Mapeia a linha; devolve null se o item_key sumiu do catálogo ou o slot não bate. */
    private static Row mapValid(ResultSet rs) throws SQLException {
        String key = rs.getString("item_key");
        String slotStr = rs.getString("slot");
        Equip e = EquipmentCatalog.byKey(key);
        if (e == null || !e.slot().name().equals(slotStr)) {
            return null;
        }
        return new Row(rs.getLong("id"), key, e.slot(), rs.getInt("usos_left"), rs.getInt("equipped") == 1);
    }
}
