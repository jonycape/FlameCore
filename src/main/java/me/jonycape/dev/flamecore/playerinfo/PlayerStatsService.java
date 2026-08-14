package me.jonycape.dev.flamecore.playerinfo;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.database.IpRecord;
import me.jonycape.dev.flamecore.database.PlayerStats;
import me.jonycape.dev.flamecore.database.PlayerStatsDAO;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerStatsService implements Listener {

    private final Main plugin;
    private final PlayerStatsDAO dao;
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();

    public PlayerStatsService(Main plugin) {
        this.plugin = plugin;
        this.dao = new PlayerStatsDAO(plugin);
        this.dao.init();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = rawIp(player);
        sessions.put(player.getUniqueId(), System.currentTimeMillis());
        dao.recordJoin(player.getName().toLowerCase(), player.getUniqueId().toString(), ip);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Long start = sessions.remove(player.getUniqueId());
        if (start != null) {
            dao.addTime(player.getName().toLowerCase(), System.currentTimeMillis() - start);
        }
    }

    private String rawIp(Player player) {
        try {
            if (player.getAddress() != null && player.getAddress().getAddress() != null) {
                return player.getAddress().getAddress().getHostAddress();
            }
        } catch (Throwable ignored) {
        }
        return "0.0.0.0";
    }

    public PlayerStats get(String playerLower) {
        return dao.get(playerLower);
    }

    public List<IpRecord> getIps(String playerLower) {
        return dao.getIps(playerLower);
    }

    public long totalTime(String playerLower, Player online) {
        PlayerStats stats = dao.get(playerLower);
        long total = stats != null ? stats.getTotalTime() : 0L;
        if (online != null) {
            Long start = sessions.get(online.getUniqueId());
            if (start != null) {
                total += System.currentTimeMillis() - start;
            }
        }
        return total;
    }

    public void clearSessions() {
        sessions.clear();
    }
}