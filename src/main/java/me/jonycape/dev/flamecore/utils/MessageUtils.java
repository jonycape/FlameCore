package me.jonycape.dev.flamecore.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.md_5.bungee.api.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessageUtils {

    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9a-fA-F]{6})");

    public static String color(String message) {
        if (message == null) {
            return "";
        }
        Matcher matcher = HEX_COLOR.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String replace(String message, String... pairs) {
        String result = message;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            result = result.replace("%" + pairs[i] + "%", pairs[i + 1]);
        }
        return result;
    }
}