package me.jonycape.dev.flamecore.database;

import lombok.RequiredArgsConstructor;
import me.jonycape.dev.flamecore.Main;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@RequiredArgsConstructor
public final class ParticipantDAO {

    private final Main plugin;

    public void addParticipant(String giveawayId, String playerNick) {
        String sql = "INSERT INTO participants (giveaway_id, player) VALUES (?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, giveawayId);
            ps.setString(2, playerNick);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("Не удалось добавить участника " + playerNick, e);
        }
    }

    public List<String> getParticipants(String giveawayId) {
        List<String> players = new ArrayList<>();
        String sql = "SELECT player FROM participants WHERE giveaway_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, giveawayId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    players.add(rs.getString("player"));
                }
            }
        } catch (SQLException e) {
            error("Не удалось получить участников конкурса " + giveawayId, e);
        }
        return players;
    }

    public boolean hasParticipant(String giveawayId, String playerNick) {
        String sql = "SELECT 1 FROM participants WHERE giveaway_id = ? AND player = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, giveawayId);
            ps.setString(2, playerNick);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            error("Ошибка проверки участия в конкурсе " + giveawayId, e);
            return false;
        }
    }

    public void deleteParticipants(String giveawayId) {
        String sql = "DELETE FROM participants WHERE giveaway_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, giveawayId);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("Не удалось удалить участников конкурса " + giveawayId, e);
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