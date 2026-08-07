package me.jonycape.dev.flamecore.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TimeUtils {

    private static final long MILLIS_PER_MINUTE = 60_000L;
    private static final long MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE;
    private static final long MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR;

    public static long parse(String input) {
        if (input == null || input.length() < 2) {
            return -1;
        }
        String valuePart = input.substring(0, input.length() - 1);
        char suffix = Character.toLowerCase(input.charAt(input.length() - 1));
        long value;
        try {
            value = Long.parseLong(valuePart);
        } catch (NumberFormatException e) {
            return -1;
        }
        if (value <= 0) {
            return -1;
        }
        switch (suffix) {
            case 'm':
                return value * MILLIS_PER_MINUTE;
            case 'h':
                return value * MILLIS_PER_HOUR;
            case 'd':
                return value * MILLIS_PER_DAY;
            default:
                return -1;
        }
    }

    public static String format(long millis) {
        long days = millis / MILLIS_PER_DAY;
        long hours = (millis % MILLIS_PER_DAY) / MILLIS_PER_HOUR;
        long minutes = (millis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("д ");
        }
        if (hours > 0) {
            sb.append(hours).append("ч ");
        }
        sb.append(minutes).append("м");
        return sb.toString();
    }
}