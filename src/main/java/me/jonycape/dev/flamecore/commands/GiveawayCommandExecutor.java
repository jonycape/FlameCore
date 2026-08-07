package me.jonycape.dev.flamecore.commands;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class GiveawayCommandExecutor extends BaseCommand {

    private final GiveawayCommand createCommand;
    private final GiveawayJoinCommand joinCommand;

    public GiveawayCommandExecutor(Main plugin) {
        super(plugin);
        this.createCommand = new GiveawayCommand(plugin);
        this.joinCommand = new GiveawayJoinCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendMessage(sender, ConfigKeys.MESSAGE_GIVEAWAY_CREATE_USAGE);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create":
                createCommand.execute(sender, args);
                return true;
            case "join":
                joinCommand.execute(sender, args);
                return true;
            default:
                sendMessage(sender, ConfigKeys.MESSAGE_UNKNOWN_COMMAND);
                return true;
        }
    }
}