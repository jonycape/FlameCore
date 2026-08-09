package me.jonycape.dev.flamecore.protection;

import me.jonycape.dev.flamecore.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class DangerousCommandListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Main main = Main.getInstance();
        if (main == null) {
            return;
        }
        Player sender = event.getPlayer();
        AdminProtectionService admins = main.getAdminProtectionService();
        if (!admins.isAdmin(sender.getName()) && !admins.isOwner(sender.getName())) {
            return;
        }
        if (main.getDangerousCommandService().intercept(sender, event.getMessage())) {
            event.setCancelled(true);
        }
    }
}