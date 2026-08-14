package me.jonycape.dev.flamecore.commands;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.utils.MessageProcessor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StreamCommandExecutor implements CommandExecutor {

    private final Main plugin;

    public StreamCommandExecutor(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (plugin.getStreamService() == null) {
            MessageProcessor.send(sender, Main.getCfg().getStringList(ConfigKeys.MESSAGE_MODULE_DISABLED),
                    "module", "стримеры");
            return true;
        }
        if (!(sender instanceof Player player)) {
            MessageProcessor.send(sender, Main.getCfg().getStringList(ConfigKeys.MESSAGE_STREAM_CONSOLE_ONLY));
            return true;
        }
        if (!player.hasPermission(ConfigKeys.PERM_STREAM)) {
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_NO_PERMISSION));
            return true;
        }
        if (args.length < 1) {
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_STREAM_USAGE));
            return true;
        }
        plugin.getStreamService().announce(player, args[0]);
        return true;
    }
}