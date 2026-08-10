package me.jonycape.dev.flamecore.database;

import lombok.RequiredArgsConstructor;
import me.jonycape.dev.flamecore.Main;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

@RequiredArgsConstructor
public final class PromoCodeDAO {

    private final Main plugin;

    public void init() {
        String sql = "CREATE TABLE IF NOT EXISTS promo_uses ("
                + "code VARCHAR(64) NOT NULL, "
                + "player VARCHAR(32) NOT NULL, "
                + "uses INT NOT NULL, "
                + "PRIMARY KEY (code, player))";
        try (java.sql.Statement st = conn().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            error("Ошибка создания таблицы promo_uses", e);
        }
    }

    public int getUses(String code, String player) {
        String sql = "SELECT uses FROM promo_uses WHERE code = ? AND player = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, player);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("uses") : 0;
            }
        } catch (SQLException e) {
            error("Ошибка чтения использований промокода " + code, e);
            return 0;
        }
    }

    public void setUses(String code, String player, int uses) {
        String sql = "MERGE INTO promo_uses (code, player, uses) KEY (code, player) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setString(2, player);
            ps.setInt(3, uses);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("Ошибка сохранения использований промокода " + code, e);
        }
    }

    private java.sql.Connection conn() throws SQLException {
        java.sql.Connection c = plugin.getDatabaseManager().getConnection();
        if (c == null) {
            throw new SQLException("Соединение с базой данных отсутствует");
        }
        return c;
    }

    private void error(String message, SQLException e) {
        plugin.getLogger().log(Level.SEVERE, message, e);
    }
}