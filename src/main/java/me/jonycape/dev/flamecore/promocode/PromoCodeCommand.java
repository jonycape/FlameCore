package me.jonycape.dev.flamecore.promocode;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class PromoCodeCommand extends Command {

    public PromoCodeCommand(String name) {
        super(name, "Активировать промокод", "/" + name, List.of(name));
        setPermission(null);
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        return true;
    }
}