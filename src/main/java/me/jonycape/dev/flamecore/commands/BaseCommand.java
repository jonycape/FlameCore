package me.jonycape.dev.flamecore.commands;

import lombok.RequiredArgsConstructor;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

@RequiredArgsConstructor
public abstract class BaseCommand implements CommandExecutor {

    protected final Main plugin;

    @Override
    public abstract boolean onCommand(CommandSender sender, Command command, String label, String[] args);

    protected void sendMessage(CommandSender sender, String key, String... pairs) {
        String body = MessageUtils.replace(Main.getCfg().getMultiLine(key), pairs);
        sender.sendMessage(MessageUtils.color(body));
    }
}