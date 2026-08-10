package me.jonycape.dev.flamecore.promocode;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class PromoCodeListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Main main = Main.getInstance();
        if (main == null) {
            return;
        }
        PromoCodeService service = main.getPromoCodes();
        if (service == null) {
            return;
        }
        String message = event.getMessage();
        if (message.isEmpty() || message.charAt(0) != '/') {
            return;
        }
        String command = message.substring(1).trim();
        if (command.isEmpty()) {
            return;
        }
        int space = command.indexOf(' ');
        String name = space < 0 ? command : command.substring(0, space);
        if (service.getCode(name) == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        PromoCodeService.Result result = service.consume(player, name);
        if (result == PromoCodeService.Result.NOT_FOUND) {
            player.sendMessage(MessageUtils.color(MessageUtils.replace(
                    Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_PROMO_NOT_FOUND),
                    "command", "/" + name)));
        }
    }
}