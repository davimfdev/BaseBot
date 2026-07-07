package dev.davimf.basebot.database.postgres;

import dev.davimf.basebot.database.sqlite.SqliteManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * One-shot, idempotent copy of the 5 config tables from local SQLite to Postgres/Neon.
 * Preserves ids (operational SQLite tables reference them), seeds shop_stock with the current
 * sold counters, and advances the shop_items identity sequence. Run with the bot stopped.
 * Re-running is safe: rows already present are skipped (ON CONFLICT DO NOTHING).
 */
public final class ConfigMigrationTool {

    public record Result(int panels, int options, int levelRewards, int quiz, int shopItems, int actionTypes) {}

    private ConfigMigrationTool() {}

    /**
     * Manual entry point: run once with the bot stopped and after the Postgres schema (004)
     * has been applied. Loads config from the environment/.env like the bot does, copies the
     * config tables, and prints the per-table counts.
     */
    public static void main(String[] args) {
        dev.davimf.basebot.config.BotConfig config = dev.davimf.basebot.config.ConfigLoader.load();
        SqliteManager sqlite = new SqliteManager(config.sqlite());
        PostgresPool pg = new PostgresPool(config.postgres());
        try {
            Result r = migrate(sqlite, pg);
            System.out.println("Config migration complete: " + r);
        } finally {
            sqlite.close();
            pg.close();
        }
    }

    public static Result migrate(SqliteManager sqlite, PostgresPool pg) {
        try (Connection s = sqlite.getConnection(); Connection p = pg.getConnection()) {
            boolean prevAuto = p.getAutoCommit();
            p.setAutoCommit(false);
            try {
                int panels = copyPanels(s, p);
                int options = copyOptions(s, p);
                int rewards = copyLevelRewards(s, p);
                int quiz = copyQuiz(s, p);
                int shop = copyShopItems(s, p);        // also seeds shop_stock in SQLite
                seedShopStock(s);
                advanceShopSequence(p);
                int types = copyActionTypes(s, p);
                p.commit();
                return new Result(panels, options, rewards, quiz, shop, types);
            } catch (SQLException e) {
                p.rollback();
                throw e;
            } finally {
                p.setAutoCommit(prevAuto);
            }
        } catch (SQLException e) {
            throw new RepositoryException("config migration", e);
        }
    }

    private static int copyPanels(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO self_role_panels "
                + "(id, guild_id, title, description, style, unique_choice, channel_id, message_id) "
                + "VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM self_role_panels");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("id"));
                w.setString(2, rs.getString("guild_id"));
                w.setString(3, rs.getString("title"));
                w.setString(4, rs.getString("description"));
                w.setString(5, rs.getString("style"));
                w.setBoolean(6, rs.getInt("unique_choice") == 1);
                w.setString(7, rs.getString("channel_id"));
                w.setString(8, rs.getString("message_id"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static int copyOptions(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO self_role_options (panel_id, role_id, label, emoji, position) "
                + "VALUES (?,?,?,?,?) ON CONFLICT (panel_id, role_id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM self_role_options");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("panel_id"));
                w.setString(2, rs.getString("role_id"));
                w.setString(3, rs.getString("label"));
                w.setString(4, rs.getString("emoji"));
                w.setInt(5, rs.getInt("position"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static int copyLevelRewards(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO level_rewards (guild_id, level, role_id) VALUES (?,?,?) "
                + "ON CONFLICT (guild_id, level) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM level_rewards");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("guild_id"));
                w.setInt(2, rs.getInt("level"));
                w.setString(3, rs.getString("role_id"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static int copyQuiz(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO quiz_questions (id, guild_id, question, correct, wrong1, wrong2, wrong3) "
                + "VALUES (?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM quiz_questions");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("id"));
                w.setString(2, rs.getString("guild_id"));
                w.setString(3, rs.getString("question"));
                w.setString(4, rs.getString("correct"));
                w.setString(5, rs.getString("wrong1"));
                w.setString(6, rs.getString("wrong2"));
                w.setString(7, rs.getString("wrong3"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static int copyShopItems(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO shop_items "
                + "(id, guild_id, type, role_id, name, description, price, duration_s, stock, per_user, created_at) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM shop_items");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setLong(1, rs.getLong("id"));
                w.setString(2, rs.getString("guild_id"));
                w.setString(3, rs.getString("type"));
                w.setString(4, rs.getString("role_id"));
                w.setString(5, rs.getString("name"));
                w.setString(6, rs.getString("description"));
                w.setLong(7, rs.getLong("price"));
                setNullableLong(w, 8, rs, "duration_s");
                setNullableInt(w, 9, rs, "stock");
                setNullableInt(w, 10, rs, "per_user");
                w.setLong(11, rs.getLong("created_at"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    /** Seed shop_stock (SQLite) com o sold atual da própria shop_items do SQLite. Idempotente. */
    private static void seedShopStock(Connection s) throws SQLException {
        try (PreparedStatement st = s.prepareStatement(
                "INSERT OR IGNORE INTO shop_stock (item_id, sold) SELECT id, sold FROM shop_items")) {
            st.executeUpdate();
        }
    }

    /** Avança a identity da shop_items no Postgres pro max(id)+1 pra novos inserts não colidirem. */
    private static void advanceShopSequence(Connection p) throws SQLException {
        try (PreparedStatement st = p.prepareStatement(
                "SELECT setval(pg_get_serial_sequence('shop_items','id'), "
                + "COALESCE((SELECT MAX(id) FROM shop_items), 0) + 1, false)")) {
            st.executeQuery();
        }
    }

    private static int copyActionTypes(Connection s, Connection p) throws SQLException {
        String ins = "INSERT INTO fac_action_types "
                + "(id, guild_id, name, max_contingent, min_contingent, dirty_money) "
                + "VALUES (?,?,?,?,?,?) ON CONFLICT (id) DO NOTHING";
        int n = 0;
        try (PreparedStatement rd = s.prepareStatement("SELECT * FROM fac_action_types");
             ResultSet rs = rd.executeQuery();
             PreparedStatement w = p.prepareStatement(ins)) {
            while (rs.next()) {
                w.setString(1, rs.getString("id"));
                w.setString(2, rs.getString("guild_id"));
                w.setString(3, rs.getString("name"));
                w.setInt(4, rs.getInt("max_contingent"));
                w.setInt(5, rs.getInt("min_contingent"));
                w.setInt(6, rs.getInt("dirty_money"));
                n += w.executeUpdate();
            }
        }
        return n;
    }

    private static void setNullableLong(PreparedStatement w, int i, ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        if (rs.wasNull()) { w.setNull(i, java.sql.Types.BIGINT); } else { w.setLong(i, v); }
    }

    private static void setNullableInt(PreparedStatement w, int i, ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        if (rs.wasNull()) { w.setNull(i, java.sql.Types.INTEGER); } else { w.setInt(i, v); }
    }
}
