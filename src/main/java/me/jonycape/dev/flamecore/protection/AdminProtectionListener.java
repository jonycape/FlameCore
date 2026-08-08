package me.jonycape.dev.flamecore.protection;

import me.jonycape.dev.flamecore.Main;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.BiConsumer;

@RequiredArgsConstructor
public final class AdminProtectionListener implements Listener {

    private final Main plugin;
    private final AdminProtectionService service;

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        service.onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        service.onPlayerQuit(event.getPlayer());
    }

    public void handleCallback(String data, String fromName) {
        if (data == null) {
            return;
        }
        // Колбэк приходит из потока Telegram-бота — Bukkit-API можно трогать только в главном потоке.
        Bukkit.getScheduler().runTask(plugin, () -> {
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
        });
    }
}