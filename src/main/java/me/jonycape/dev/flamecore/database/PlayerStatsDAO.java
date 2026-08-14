package me.jonycape.dev.flamecore.database;

import lombok.RequiredArgsConstructor;
import me.jonycape.dev.flamecore.Main;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@RequiredArgsConstructor
public final class PlayerStatsDAO {

    private final Main plugin;

    public void init() {
        execute("CREATE TABLE IF NOT EXISTS player_stats ("
                + "player VARCHAR(32) PRIMARY KEY, "
                + "uuid VARCHAR(36), "
                + "first_join BIGINT DEFAULT 0, "
                + "last_join BIGINT DEFAULT 0, "
                + "total_time BIGINT DEFAULT 0, "
                + "last_ip VARCHAR(45))");
        execute("CREATE TABLE IF NOT EXISTS player_ips ("
                + "player VARCHAR(32) NOT NULL, "
                + "ip VARCHAR(45) NOT NULL, "
                + "first_seen BIGINT DEFAULT 0, "
                + "PRIMARY KEY (player, ip))");
    }

    public void recordJoin(String player, String uuid, String ip) {
        String upsert = "INSERT INTO player_stats (player, uuid, first_join, last_join, total_time, last_ip) "
                + "VALUES (?, ?, ?, ?, 0, ?) "
                + "ON CONFLICT (player) DO UPDATE SET "
                + "uuid = EXCLUDED.uuid, last_join = EXCLUDED.last_join, last_ip = EXCLUDED.last_ip";
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = conn().prepareStatement(upsert)) {
            ps.setString(1, player);
            ps.setString(2, uuid);
            ps.setLong(3, now);
            ps.setLong(4, now);
            ps.setString(5, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось записать вход для " + player, e);
        }
        insertIp(player, ip, now);
    }

    private void insertIp(String player, String ip, long time) {
        String sql = "INSERT INTO player_ips (player, ip, first_seen) VALUES (?, ?, ?) "
                + "ON CONFLICT (player, ip) DO NOTHING";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, player);
            ps.setString(2, ip);
            ps.setLong(3, time);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось записать IP для " + player, e);
        }
    }

    public void addTime(String player, long millis) {
        String sql = "UPDATE player_stats SET total_time = total_time + ? WHERE player = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setLong(1, millis);
            ps.setString(2, player);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось обновить время для " + player, e);
        }
    }

    public PlayerStats get(String player) {
        String sql = "SELECT player, uuid, first_join, last_join, total_time, last_ip "
                + "FROM player_stats WHERE player = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new PlayerStats(
                            rs.getString("player"),
                            rs.getString("uuid"),
                            rs.getLong("first_join"),
                            rs.getLong("last_join"),
                            rs.getLong("total_time"),
                            rs.getString("last_ip"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить статистику для " + player, e);
        }
        return null;
    }

    public List<IpRecord> getIps(String player) {
        List<IpRecord> result = new ArrayList<>();
        String sql = "SELECT ip, first_seen FROM player_ips WHERE player = ? ORDER BY first_seen ASC";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, player);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new IpRecord(rs.getString("ip"), rs.getLong("first_seen")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить историю IP для " + player, e);
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