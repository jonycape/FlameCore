package me.jonycape.dev.flamecore.protection;

import me.jonycape.dev.flamecore.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.BiConsumer;

public final class AdminProtectionListener implements Listener {

    private final Main plugin;
    private final AdminProtectionService service;

    public AdminProtectionListener(Main plugin, AdminProtectionService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        service.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        service.onPlayerQuit(event.getPlayer());
    }

    public BiConsumer<String, String> createCallbackHandler() {
        return this::handleCallback;
    }

    private void handleCallback(String data, String fromName) {
        if (data == null) {
            return;
        }
        try {
            String[] parts = data.split("\\s+", 2);
            if (parts.length < 2) {
                return;
            }
            String action = parts[0];
            String playerName = parts[1];
            org.bukkit.entity.Player player = Bukkit.getPlayerExact(playerName);
            switch (action.toLowerCase()) {
                case "allow":
                    service.approveAdmin(player);
                    break;
                case "kick":
                    service.kickAdmin(player);
                    break;
                case "panel":
                    service.grantPanelAccess(player);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка обработки callback Telegram: " + e.getMessage());
        }
    }
}