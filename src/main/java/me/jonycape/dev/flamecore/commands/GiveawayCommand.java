package me.jonycape.dev.flamecore.commands;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.utils.TimeUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.Arrays;

public final class GiveawayCommand extends BaseCommand {

    public GiveawayCommand(Main plugin) {
        super(plugin);
    }

    public void execute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendMessage(sender, ConfigKeys.MESSAGE_GIVEAWAY_CREATE_USAGE);
            return;
        }
        String prize = String.join(" ", Arrays.copyOfRange(args, 1, args.length - 1));
        String time = args[args.length - 1];
        if (TimeUtils.parse(time) < 0) {
            sendMessage(sender, ConfigKeys.MESSAGE_GIVEAWAY_INVALID_TIME);
            return;
        }
        plugin.getGiveawayManager().create(prize, time);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        execute(sender, args);
        return true;
    }
}