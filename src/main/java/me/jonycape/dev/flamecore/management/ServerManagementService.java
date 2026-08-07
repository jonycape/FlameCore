package me.jonycape.dev.flamecore.management;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;

public final class ServerManagementService {

    private static final long START_TIME = System.currentTimeMillis();

    private final Main plugin;

    public ServerManagementService(Main plugin) {
        this.plugin = plugin;
    }

    public String tps() {
        double[] tps = Bukkit.getTPS();
        String color = tps[0] >= 19 ? "&a" : tps[0] >= 15 ? "&e" : "&c";
        return raw(ConfigKeys.MESSAGE_MANAGEMENT_TPS)
                .replace("%tps1%", color + fmt(tps[0]))
                .replace("%tps5%", color + fmt(tps[1]))
                .replace("%tps15%", color + fmt(tps[2]));
    }

    public String mspt() {
        double mspt = Bukkit.getAverageTickTime();
        String color = mspt < 40 ? "&a" : mspt < 50 ? "&e" : "&c";
        return raw(ConfigKeys.MESSAGE_MANAGEMENT_MSPT).replace("%mspt%", color + String.format("%.1f", mspt));
    }

    public String system() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory();
        long used = total - rt.freeMemory();
        double load = os.getSystemLoadAverage();
        return raw(ConfigKeys.MESSAGE_MANAGEMENT_SYSTEM)
                .replace("%cpu%", (load < 0 ? "н/д" : String.format("%.1f", load)))
                .replace("%os%", os.getName() + " " + os.getVersion())
                .replace("%cores%", String.valueOf(rt.availableProcessors()))
                .replace("%mem%", mb(used) + " / " + mb(total) + " MB");
    }

    public String online() {
        return raw(ConfigKeys.MESSAGE_MANAGEMENT_ONLINE)
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    }

    public String worlds() {
        StringBuilder sb = new StringBuilder(raw(ConfigKeys.MESSAGE_MANAGEMENT_WORLDS));
        for (World world : Bukkit.getWorlds()) {
            String name = world.getName();
            int count = world.getPlayers().size();
            String label;
            if (name.equalsIgnoreCase("world_the_end")) {
                label = "Энд";
            } else if (name.equalsIgnoreCase("world_nether")) {
                label = "Недер";
            } else {
                label = "Обычный мир";
            }
            sb.append("\n&7  ").append(label).append(" &7(&f").append(name).append("&7): ").append(count);
        }
        return color(sb.toString());
    }

    public void announce(String text) {
        String prefix = Main.getCfg().getString(ConfigKeys.MESSAGE_PREFIX, "");
        Bukkit.broadcastMessage(MessageUtils.color(prefix + text));
    }

    public String player(Player target) {
        String ip = target.getAddress() != null
                ? target.getAddress().getAddress().getHostAddress() : "неизвестен";
        String prefix = placeholderPrefix(target);
        String world = target.getWorld() != null ? target.getWorld().getName() : "?";
        return raw(ConfigKeys.MESSAGE_MANAGEMENT_PLAYER)
                .replace("%player%", target.getName())
                .replace("%prefix%", prefix)
                .replace("%ip%", ip)
                .replace("%world%", world)
                .replace("%ping%", String.valueOf(target.getPing()))
                .replace("%gamemode%", target.getGameMode().name());
    }

    public String check() {
        long uptime = System.currentTimeMillis() - START_TIME;
        String db = plugin.getDatabaseManager().getConnection() != null ? "&a✔" : "&c✘";
        String telegram = plugin.getTelegramNotifier().isAvailable() ? "&a✔" : "&c✘";
        return raw(ConfigKeys.MESSAGE_MANAGEMENT_CHECK)
                .replace("%db%", db)
                .replace("%telegram%", telegram)
                .replace("%uptime%", formatUptime(uptime))
                .replace("%version%", plugin.getDescription().getVersion())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    }

    private String placeholderPrefix(Player player) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return "";
        }
        try {
            Object papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            var method = papi.getClass().getMethod("setPlaceholders", Player.class, String.class);
            Object result = method.invoke(papi, player, "%luckperms_prefix%");
            return result == null ? "" : result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String raw(String key) {
        return color(Main.getCfg().getString(key, ""));
    }

    private String color(String s) {
        return MessageUtils.color(s);
    }

    private String fmt(double v) {
        return String.format("%.2f", v);
    }

    private String mb(long bytes) {
        return String.format("%.0f", bytes / 1024.0 / 1024.0);
    }

    private String formatUptime(long ms) {
        long s = ms / 1000;
        return (s / 3600) + "ч " + ((s % 3600) / 60) + "м " + (s % 60) + "с";
    }
}