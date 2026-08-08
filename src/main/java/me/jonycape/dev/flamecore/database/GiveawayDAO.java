package me.jonycape.dev.flamecore.database;

import lombok.RequiredArgsConstructor;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.giveaway.Giveaway;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@RequiredArgsConstructor
public final class GiveawayDAO {

    private final Main plugin;

    public void init() {
        execute("CREATE TABLE IF NOT EXISTS giveaways ("
                + "id VARCHAR(16) PRIMARY KEY, "
                + "prize VARCHAR(255) NOT NULL, "
                + "end_time BIGINT NOT NULL)");
        execute("CREATE TABLE IF NOT EXISTS participants ("
                + "giveaway_id VARCHAR(16) NOT NULL, "
                + "player VARCHAR(32) NOT NULL, "
                + "PRIMARY KEY (giveaway_id, player))");
    }

    public void insertGiveaway(Giveaway giveaway) {
        String sql = "INSERT INTO giveaways (id, prize, end_time) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, giveaway.getId());
            ps.setString(2, giveaway.getPrize());
            ps.setLong(3, giveaway.getEndTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            error("Не удалось сохранить конкурс " + giveaway.getId(), e);
        }
    }

    public Giveaway findByGameId(String id) {
        String sql = "SELECT id, prize, end_time FROM giveaways WHERE id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return Giveaway.builder()
                        .id(id)
                        .prize(rs.getString("prize"))
                        .endTime(rs.getLong("end_time"))
                        .participants(loadParticipants(id))
                        .build();
            }
        } catch (SQLException e) {
            error("Не удалось загрузить конкурс " + id, e);
            return null;
        }
    }

    public List<Giveaway> loadAll() {
        List<Giveaway> result = new ArrayList<>();
        String sql = "SELECT id, prize, end_time FROM giveaways";
        try (Statement st = conn().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String id = rs.getString("id");
                result.add(Giveaway.builder()
                        .id(id)
                        .prize(rs.getString("prize"))
                        .endTime(rs.getLong("end_time"))
                        .participants(loadParticipants(id))
                        .build());
            }
        } catch (SQLException e) {
            error("Не удалось загрузить список конкурсов", e);
        }
        return result;
    }

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

    public void deleteGiveaway(String giveawayId) {
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM participants WHERE giveaway_id = ?")) {
            ps.setString(1, giveawayId);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("Не удалось удалить участников конкурса " + giveawayId, e);
        }
        try (PreparedStatement ps = conn().prepareStatement(
                "DELETE FROM giveaways WHERE id = ?")) {
            ps.setString(1, giveawayId);
            ps.executeUpdate();
        } catch (SQLException e) {
            error("Не удалось удалить конкурс " + giveawayId, e);
        }
    }

    private List<String> loadParticipants(String id) {
        List<String> players = new ArrayList<>();
        String sql = "SELECT player FROM participants WHERE giveaway_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    players.add(rs.getString("player"));
                }
            }
        } catch (SQLException e) {
            error("Не удалось загрузить участников конкурса " + id, e);
        }
        return players;
    }

    private void execute(String sql) {
        try (Statement st = conn().createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            error("Ошибка выполнения SQL: " + sql, e);
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