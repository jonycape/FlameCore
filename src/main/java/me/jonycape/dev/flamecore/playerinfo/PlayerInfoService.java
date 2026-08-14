package me.jonycape.dev.flamecore.playerinfo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.database.IpRecord;
import me.jonycape.dev.flamecore.database.PlayerStats;
import me.jonycape.dev.flamecore.protection.TelegramNotifier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerInfoService {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static final String GEO_API = "http://ip-api.com/json/%s?fields=status,country,city&lang=ru";

    private final Main plugin;
    private final PlayerStatsService statsService;
    private final TelegramNotifier notifier;
    private final Map<String, String> geoCache = new ConcurrentHashMap<>();

    public PlayerInfoService(Main plugin, PlayerStatsService statsService, TelegramNotifier notifier) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.notifier = notifier;
    }

    public void handleCommand(String text, String chatId) {
        if (!isAdminOrOwner(chatId)) {
            return;
        }
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 0 || !parts[0].toLowerCase().startsWith("/info")) {
            return;
        }
        if (parts.length < 2) {
            notifier.sendToChat(chatId, "Использование: <b>/info</b> &lt;ник&gt;", null);
            return;
        }
        String target = parts[1];
        Bukkit.getScheduler().runTask(plugin, () -> sendMenu(target, chatId));
    }

    public void handleCallback(String data, String chatId) {
        String[] parts = data.split("\\s+", 2);
        if (parts.length < 2) {
            return;
        }
        String action = parts[0];
        String nick = parts[1];
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (action) {
                case "info:basic" -> sendBasic(nick, chatId);
                case "info:game" -> sendGame(nick, chatId);
                case "info:mod" -> sendMod(nick, chatId);
                default -> {
                }
            }
        });
    }

    private boolean isAdminOrOwner(String chatId) {
        if (chatId == null || chatId.isEmpty()) {
            return false;
        }
        String ownerId = Main.getCfg().getString(ConfigKeys.OWNER_TELEGRAM_ID, "");
        if (ownerId.equals(chatId)) {
            return true;
        }
        return Main.getCfg().getStringMap(ConfigKeys.ADMINS).containsValue(chatId);
    }

    private void sendMenu(String nick, String chatId) {
        String text = tgText(ConfigKeys.PLAYERINFO_MENU, "player", TelegramNotifier.esc(nick));
        notifier.sendToChat(chatId, text, new InlineKeyboardMarkup(
                new InlineKeyboardButton("ℹ Основная информация").callbackData("info:basic " + nick),
                new InlineKeyboardButton("🎮 Игровая информация").callbackData("info:game " + nick),
                new InlineKeyboardButton("🛡 Модерация").callbackData("info:mod " + nick)));
    }

    private void sendBasic(String nick, String chatId) {
        Player online = Bukkit.getPlayerExact(nick);
        PlayerStats stats = statsService.get(nick.toLowerCase());

        String uuid = online != null ? online.getUniqueId().toString()
                : (stats != null && stats.getUuid() != null ? stats.getUuid() : "—");
        String ip = online != null && online.getAddress() != null && online.getAddress().getAddress() != null
                ? online.getAddress().getAddress().getHostAddress()
                : (stats != null && stats.getLastIp() != null ? stats.getLastIp() : "—");
        String status = online != null ? "🟢 Онлайн" : "⚫ Оффлайн";
        String first = stats != null && stats.getFirstJoin() > 0 ? TIME_FORMAT.format(Instant.ofEpochMilli(stats.getFirstJoin())) : "—";
        String last = stats != null && stats.getLastJoin() > 0 ? TIME_FORMAT.format(Instant.ofEpochMilli(stats.getLastJoin())) : "—";
        String total = fmtDuration(statsService.totalTime(nick.toLowerCase(), online));
        String geo = geoCache.containsKey(ip) ? geoCache.get(ip) : "определяется…";

        notifier.sendToChat(chatId, tgText(ConfigKeys.PLAYERINFO_BASIC,
                "player", TelegramNotifier.esc(nick),
                "uuid", TelegramNotifier.esc(uuid),
                "ip", TelegramNotifier.esc(ip),
                "geo", TelegramNotifier.esc(geo),
                "first", TelegramNotifier.esc(first),
                "last", TelegramNotifier.esc(last),
                "total", TelegramNotifier.esc(total),
                "status", status), null);

        if (!ip.equals("—") && !geoCache.containsKey(ip)) {
            fetchGeoAsync(ip, chatId);
        }
    }

    private void sendGame(String nick, String chatId) {
        Player online = Bukkit.getPlayerExact(nick);
        if (online == null) {
            notifier.sendToChat(chatId, "Игрок <b>" + TelegramNotifier.esc(nick) + "</b> сейчас <b>не в сети</b>.", null);
            return;
        }
        Location loc = online.getLocation();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "—";
        String mode = online.getGameMode().toString();
        String level = String.valueOf(online.getLevel());
        String health = String.valueOf((int) Math.ceil(online.getHealth()));
        String hunger = String.valueOf(online.getFoodLevel());

        notifier.sendToChat(chatId, tgText(ConfigKeys.PLAYERINFO_GAME,
                "player", TelegramNotifier.esc(nick),
                "world", TelegramNotifier.esc(world),
                "x", String.valueOf((int) loc.getX()),
                "y", String.valueOf((int) loc.getY()),
                "z", String.valueOf((int) loc.getZ()),
                "gamemode", TelegramNotifier.esc(mode),
                "level", level,
                "health", health,
                "hunger", hunger), null);
    }

    private void sendMod(String nick, String chatId) {
        Player online = Bukkit.getPlayerExact(nick);
        PlayerStats stats = statsService.get(nick.toLowerCase());

        UUID uuid = null;
        String uuidStr = online != null ? online.getUniqueId().toString()
                : (stats != null && stats.getUuid() != null ? stats.getUuid() : null);
        if (uuidStr != null) {
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String muted = mutedText(uuid);
        String ipHistory = ipHistoryText(nick.toLowerCase());

        notifier.sendToChat(chatId, tgText(ConfigKeys.PLAYERINFO_MOD,
                "player", TelegramNotifier.esc(nick),
                "muted", muted,
                "ips", TelegramNotifier.esc(ipHistory)), null);
    }

    private String ipHistoryText(String playerLower) {
        List<IpRecord> records = statsService.getIps(playerLower);
        if (records.isEmpty()) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (IpRecord record : records) {
            sb.append(TelegramNotifier.esc(record.getIp()));
            if (record.getFirstSeen() > 0) {
                sb.append(" <i>(").append(TIME_FORMAT.format(Instant.ofEpochMilli(record.getFirstSeen()))).append(")</i>");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private String mutedText(UUID uuid) {
        if (uuid == null) {
            return "—";
        }
        try {
            Class<?> dbClass = Class.forName("litesolutions.litebans.api.Database");
            Object database = dbClass.getMethod("get").invoke(null);
            Object result = dbClass.getMethod("isPlayerMuted", UUID.class, String.class).invoke(database, uuid, "");
            return Boolean.TRUE.equals(result) ? "🔴 Да" : "🟢 Нет";
        } catch (Throwable ignored) {
            return "— (LiteBans не найден)";
        }
    }

    private void fetchGeoAsync(String ip, String chatId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String geo = queryGeo(ip);
            if (geo != null) {
                geoCache.put(ip, geo);
                notifier.sendToChat(chatId, "📍 IP <b>" + TelegramNotifier.esc(ip) + "</b>: " + TelegramNotifier.esc(geo), null);
            } else {
                geoCache.put(ip, "не определено");
            }
        });
    }

    private String queryGeo(String ip) {
        try {
            URL url = new URL(String.format(GEO_API, ip));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "FlameCore/1.0");
            try (InputStream in = connection.getInputStream()) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                if (!"success".equals(obj.get("status").getAsString())) {
                    return null;
                }
                String country = obj.has("country") ? obj.get("country").getAsString() : "";
                String city = obj.has("city") ? obj.get("city").getAsString() : "";
                return (country.isEmpty() && city.isEmpty()) ? null
                        : (city.isEmpty() ? country : city + ", " + country);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String fmtDuration(long millis) {
        long totalSeconds = millis / 1000L;
        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (days > 0) {
            return days + " дн " + hours + " ч";
        }
        if (hours > 0) {
            return hours + " ч " + minutes + " мин";
        }
        return minutes + " мин";
    }

    private static String tgText(String key, String... pairs) {
        String body = Main.getCfg().getMultiLine(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            body = body.replace("%" + pairs[i] + "%", pairs[i + 1]);
        }
        return body;
    }
}