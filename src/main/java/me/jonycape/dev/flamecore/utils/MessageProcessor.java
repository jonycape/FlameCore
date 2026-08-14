package me.jonycape.dev.flamecore.utils;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public final class MessageProcessor {

    private static final String CONSOLE = "[console]";
    private static final String SOUND = "[sound]";
    private static final String MESSAGE = "[message]";

    private static final String[] SOUND_PREFIXES = {"ENTITY_", "BLOCK_", "UI_", "ITEM_", "AMBIENT_"};

    private static final Map<String, String> SOUND_ALIASES = Map.ofEntries(
            Map.entry("LEVEL_UP", "ENTITY_PLAYER_LEVELUP"),
            Map.entry("NOTE_PLING", "BLOCK_NOTE_BLOCK_PLING"),
            Map.entry("ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP"),
            Map.entry("EXPERIENCE_ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP"),
            Map.entry("PARROT_AMBIENT", "ENTITY_PARROT_AMBIENT"),
            Map.entry("PARROT_HURT", "ENTITY_PARROT_HURT"),
            Map.entry("PARROT_STEP", "ENTITY_PARROT_STEP"),
            Map.entry("PARROT_FLY", "ENTITY_PARROT_FLY"),
            Map.entry("ANVIL", "BLOCK_ANVIL_USE"),
            Map.entry("CLICK", "UI_BUTTON_CLICK"),
            Map.entry("FIZZ", "BLOCK_FIRE_EXTINGUISH"),
            Map.entry("BURP", "ENTITY_PLAYER_BURP"),
            Map.entry("EAT", "ENTITY_GENERIC_EAT"),
            Map.entry("BOW_HIT", "ENTITY_ARROW_HIT"));

    private MessageProcessor() {
    }

    public static void send(CommandSender target, List<String> lines, String... pairs) {
        if (target == null || lines == null) {
            return;
        }
        for (String line : lines) {
            handleLine(target, line, pairs);
        }
    }

    public static void broadcast(List<String> lines, String... pairs) {
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (startsWithIgnoreCase(trimmed, CONSOLE)) {
                dispatchConsole(trimmed.substring(CONSOLE.length()).trim(), null, pairs);
            } else {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    handleLine(player, trimmed, pairs);
                }
            }
        }
    }

    private static void handleLine(CommandSender target, String line, String... pairs) {
        if (target == null || line == null) {
            return;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (startsWithIgnoreCase(trimmed, CONSOLE)) {
            dispatchConsole(trimmed.substring(CONSOLE.length()).trim(), target, pairs);
        } else if (startsWithIgnoreCase(trimmed, SOUND)) {
            playSound(target, trimmed.substring(SOUND.length()).trim());
        } else if (startsWithIgnoreCase(trimmed, MESSAGE)) {
            sendText(target, trimmed.substring(MESSAGE.length()).trim(), pairs);
        } else {
            sendText(target, trimmed, pairs);
        }
    }

    private static void dispatchConsole(String cmd, CommandSender context, String... pairs) {
        if (cmd.isEmpty()) {
            return;
        }
        String resolved = apply(cmd, context, pairs);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
    }

    private static void sendText(CommandSender target, String text, String... pairs) {
        if (text.isEmpty()) {
            return;
        }
        target.sendMessage(MessageUtils.color(apply(text, target, pairs)));
    }

    private static void playSound(CommandSender target, String rest) {
        if (!(target instanceof Player player) || rest.isEmpty()) {
            return;
        }
        String[] parts = rest.split("\\s+");
        Sound sound = resolveSound(parts[0]);
        if (sound == null) {
            return;
        }
        float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
        float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    private static Sound resolveSound(String raw) {
        String name = raw.toUpperCase().replace(' ', '_');
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException ignored) {
        }
        String alias = SOUND_ALIASES.get(name);
        if (alias != null) {
            try {
                return Sound.valueOf(alias);
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String prefix : SOUND_PREFIXES) {
            try {
                return Sound.valueOf(prefix + name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    private static String apply(String text, CommandSender target, String... pairs) {
        String result = text;
        if (target instanceof Player player) {
            result = result.replace("%player%", player.getName());
        }
        return MessageUtils.replace(result, pairs);
    }

    private static boolean startsWithIgnoreCase(String line, String prefix) {
        return line.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}