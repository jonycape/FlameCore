package me.jonycape.dev.flamecore.database;

import lombok.RequiredArgsConstructor;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.donatetop.TopEntry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@RequiredArgsConstructor
public final class DonateTopDAO {

    private final Main plugin;

    public void init() {
        execute("CREATE TABLE IF NOT EXISTS donate_top ("
                + "player VARCHAR(32) PRIMARY KEY, "
                + "payments INT NOT NULL DEFAULT 0)");
    }

    public void addPayment(String player) {
        String sql = "MERGE INTO donate_top t USING (SELECT CAST(? AS VARCHAR) AS player) s "
                + "ON t.player = s.player "
                + "WHEN MATCHED THEN UPDATE SET t.payments = t.payments + 1 "
                + "WHEN NOT MATCHED THEN INSERT (player, payments) VALUES (s.player, 1)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, player);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось добавить платёж для " + player, e);
        }
    }

    public void clear() {
        try (Statement st = conn().createStatement()) {
            st.execute("DELETE FROM donate_top");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось очистить топ платежей", e);
        }
    }

    public List<TopEntry> getTop(int limit) {
        List<TopEntry> result = new ArrayList<>();
        String sql = "SELECT player, payments FROM donate_top "
                + "ORDER BY payments DESC, player ASC LIMIT ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TopEntry(rs.getString("player"), rs.getInt("payments")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить топ платежей", e);
        }
        return result;
    }

    private void execute(String sql) {
        try (Statement st = conn().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Ошибка выполнения SQL: " + sql, e);
        }
    }

    private java.sql.Connection conn() throws SQLException {
        java.sql.Connection c = plugin.getDatabaseManager().getConnection();
        if (c == null) {
            throw new SQLException("Соединение с базой данных отсутствует");
        }
        return c;
    }
}