package me.jonycape.dev.flamecore.protection;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * Перехватывает опасные команды, введённые администратором/владельцем в чат.
 * Команда не выполняется, пока не завершится двойное подтверждение.
 */
public final class DangerousCommandListener implements Listener {

    private final AdminProtectionService admins;
    private final DangerousCommandService service;

    public DangerousCommandListener(AdminProtectionService admins, DangerousCommandService service) {
        this.admins = admins;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player sender = event.getPlayer();
        if (!admins.isAdmin(sender.getName()) && !admins.isOwner(sender.getName())) {
            return;
        }
        if (service.intercept(sender, event.getMessage())) {
            event.setCancelled(true);
        }
    }
}