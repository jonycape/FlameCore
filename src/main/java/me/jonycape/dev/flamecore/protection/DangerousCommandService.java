package me.jonycape.dev.flamecore.protection;

import lombok.Value;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

public final class DangerousCommandService {

    private static final String BAN_MESSAGE = "Защита сработала, за обжалованием в админ-чат.";
    private static final String DATA_PREFIX = "dc:";

    private final Main plugin;
    private volatile AdminProtectionService admins;
    private final List<Pattern> patterns;
    private final long timeoutMs;
    private final ConcurrentMap<String, PendingCommand> pending = new ConcurrentHashMap<>();

    public DangerousCommandService(Main plugin, AdminProtectionService admins) {
        this.plugin = plugin;
        this.admins = admins;
        this.timeoutMs = Math.max(5, Main.getCfg().getInt(ConfigKeys.DANGEROUS_CONFIRM_TIMEOUT, 150)) * 1000L;
        this.patterns = loadPatterns();
    }

    private List<Pattern> loadPatterns() {
        List<Pattern> list = new ArrayList<>();
        for (String mask : Main.getCfg().getStringList(ConfigKeys.DANGEROUS_COMMANDS)) {
            if (mask == null || mask.isEmpty()) {
                continue;
            }
            try {
                list.add(Pattern.compile(mask, Pattern.CASE_INSENSITIVE));
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    public void reload(AdminProtectionService admins) {
        this.admins = admins;
        this.patterns.clear();
        this.patterns.addAll(loadPatterns());
    }

    public boolean intercept(Player sender, String message) {
        String normalized = message.startsWith("/") ? message.substring(1) : message;
        boolean dangerous = patterns.stream()
                .anyMatch(p -> p.matcher(normalized).find());
        if (!dangerous) {
            return false;
        }

        String id = UUID.randomUUID().toString();
        long expireAt = System.currentTimeMillis() + timeoutMs;
        pending.put(id, new PendingCommand(id, sender.getName(), normalized, expireAt));
        long timestamp = System.currentTimeMillis();

        String senderTg = admins.getTelegramId(sender.getName());
        if (senderTg != null && !senderTg.isEmpty()) {
            plugin.getTelegramNotifier().sendDangerAskSender(
                    senderTg, sender.getName(), normalized, id, Math.toIntExact(timeoutMs / 1000), timestamp);
        }
        plugin.getTelegramNotifier().sendDangerAskOwner(sender.getName(), normalized, id, timestamp);

        Bukkit.getScheduler().runTaskLater(plugin, () -> resolveTimeout(id), Math.max(1, timeoutMs / 50));
        return true;
    }

    public void handleCallback(String data, String from) {
        if (data == null || !data.startsWith(DATA_PREFIX)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            String[] parts = data.split("\\s+", 2);
            if (parts.length < 2) {
                return;
            }
            String action = parts[0].substring(DATA_PREFIX.length());
            String id = parts[1];
            switch (action) {
                case "cancel":
                    cancel(id);
                    break;
                case "reject":
                    reject(id);
                    break;
                default:
                    break;
            }
        });
    }

    private void cancel(String id) {
        PendingCommand action = pending.remove(id);
        if (action == null) {
            return;
        }
        Player sender = Bukkit.getPlayerExact(action.getPlayer());
        if (sender != null) {
            sender.sendMessage("Твоя команда «" + action.getCommand() + "» была отменена.");
        }
    }

    private void reject(String id) {
        PendingCommand action = pending.remove(id);
        if (action == null) {
            return;
        }
        Bukkit.getBanList(BanList.Type.NAME).addBan(action.getPlayer(), BAN_MESSAGE, null, "FlameCore");
        Player sender = Bukkit.getPlayerExact(action.getPlayer());
        if (sender != null) {
            sender.kick(net.kyori.adventure.text.Component.text(BAN_MESSAGE));
        }
    }

    private void resolveTimeout(String id) {
        PendingCommand action = pending.remove(id);
        if (action == null) {
            return;
        }
        Player sender = Bukkit.getPlayerExact(action.getPlayer());
        if (sender == null || !sender.isOnline()) {
            return;
        }
        plugin.getTelegramNotifier().sendDangerExecuted(action.getPlayer(), action.getCommand());
        Bukkit.dispatchCommand(sender, action.getCommand());
    }

    @Value
    private static final class PendingCommand {
        String id;
        String player;
        String command;
        long expireAt;
    }
}