package me.jonycape.dev.flamecore.commands;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.giveaway.GiveawayManager;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class GiveawayJoinCommand extends BaseCommand {

    public GiveawayJoinCommand(Main plugin) {
        super(plugin);
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendMessage(sender, ConfigKeys.MESSAGE_GIVEAWAY_JOIN_USAGE);
            return;
        }
        if (sender.getName().equals("CONSOLE")) {
            sender.sendMessage(MessageUtils.color(
                    Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_GIVEAWAY_CONSOLE_ONLY)));
            return;
        }
        String id = args[1];
        GiveawayManager.JoinResult result = plugin.getGiveawayManager().join(id, sender.getName());
        switch (result) {
            case NOT_FOUND:
                sendMessage(sender, ConfigKeys.MESSAGE_GIVEAWAY_NOT_FOUND, "id", id);
                break;
            case ALREADY_JOINED:
                sendMessage(sender, ConfigKeys.MESSAGE_GIVEAWAY_ALREADY_JOINED, "id", id);
                break;
            case OK:
                sendMessage(sender, ConfigKeys.MESSAGE_GIVEAWAY_JOIN_SUCCESS, "id", id);
                break;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        execute(sender, args);
        return true;
    }
}