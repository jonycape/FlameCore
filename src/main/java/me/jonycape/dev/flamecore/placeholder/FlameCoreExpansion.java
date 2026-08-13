package me.jonycape.dev.flamecore.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.jonycape.dev.flamecore.Main;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class FlameCoreExpansion extends PlaceholderExpansion {

    @Override
    public String getIdentifier() {
        return "flamecore";
    }

    @Override
    public String getAuthor() {
        return "jonycape";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (player == null) {
            return "";
        }
        if (!params.equalsIgnoreCase("glowcolor")) {
            return "";
        }
        Main main = Main.getInstance();
        if (main == null || main.getCustomizeService() == null) {
            return "";
        }
        ChatColor color = main.getCustomizeService().currentColor(player);
        return color == null ? "" : color.toString();
    }
}
