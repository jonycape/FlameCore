package me.jonycape.dev.flamecore.protection;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Двойное подтверждение опасных команд.
 *
 * Администратор вводит опасную команду → она НЕ выполняется сразу. Уведомление уходит
 * самому админу (кнопки Подтвердить/Отменить) и владельцу (кнопка «Отклонить»).
 * Если за таймаут владелец не отклонил — команда выполняется автоматически.
 * Если отклонил — вечный бан (считаем взлом/попытку краша).
 */
public final class DangerousCommandService {

    private static final String BAN_MESSAGE = "Защита сработала, за обжалованием в админ-чат.";
    private static final String DATA_PREFIX = "dc:";

    private final Main plugin;
    private final AdminProtectionService admins;
    private final List<Pattern> patterns;
    private final long timeoutMs;
    private final ConcurrentMap<String, PendingCommand> pending = new ConcurrentHashMap<>();
    private final Logger logger;

    public DangerousCommandService(Main plugin, AdminProtectionService admins) {
        this.plugin = plugin;
        this.admins = admins;
        this.timeoutMs = Math.max(5, Main.getCfg().getInt(ConfigKeys.DANGEROUS_CONFIRM_TIMEOUT, 150)) * 1000L;
        this.patterns = new ArrayList<>();
        for (String mask : Main.getCfg().getStringList(ConfigKeys.DANGEROUS_COMMANDS)) {
            if (mask == null || mask.isEmpty()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(mask, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Неверная маска опасной команды: " + mask, e);
            }
        }
        this.logger = createLogger();
    }

    private Logger createLogger() {
        Logger log = Logger.getLogger("me.jonycape.dev.flamecore.DangerousCommand");
        File logsDir = new File(plugin.getDataFolder(), "logs");
        logsDir.mkdirs();
        try {
            FileHandler handler =
                    new FileHandler(new File(logsDir, "dangerous_command.log").getAbsolutePath(), true);
            handler.setFormatter(new TimestampFormatter());
            log.addHandler(handler);
            log.setUseParentHandlers(false);
            log.setLevel(Level.INFO);
        } catch (IOException e) {
            log.log(Level.WARNING, "Не удалось инициализировать лог защиты от опасных команд", e);
        }
        return log;
    }

    /** Возвращает true, если команда опасная и была перехвачена. */
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

        String senderTg = admins.getTelegramId(sender.getName());
        if (senderTg != null && !senderTg.isEmpty()) {
            plugin.getTelegramNotifier().sendDangerAskSender(
                    senderTg, sender.getName(), normalized, id, Math.toIntExact(timeoutMs / 1000));
        }
        plugin.getTelegramNotifier().sendDangerAskOwner(sender.getName(), normalized, id);

        log("ПЕРЕХВАТ ОПАСНОЙ КОМАНДЫ — игрок: " + sender.getName()
                + ", команда: " + normalized + ", время: " + now()
                + ", таймаут: " + (timeoutMs / 1000) + " сек");

        Bukkit.getScheduler().runTaskLater(plugin, () -> resolveTimeout(id), Math.max(1, timeoutMs / 50));
        return true;
    }

    /** Вызывается из Telegram-callback с данными вида "dc:<действие> <id>". */
    public void handleCallback(String data, String from) {
        if (data == null || !data.startsWith(DATA_PREFIX)) {
            return;
        }
        // Колбэк приходит из потока Telegram-бота — все действия Bukkit выполняем в главном потоке.
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

    /** Админ сам отменил — команда не выполнится, без бана. */
    private void cancel(String id) {
        PendingCommand action = pending.remove(id);
        if (action == null) {
            return;
        }
        log("ОТМЕНА ОПЕРАТОРОМ — игрок: " + action.player
                + ", команда: " + action.command + ", время: " + now());
        Player sender = Bukkit.getPlayerExact(action.player);
        if (sender != null) {
            sender.sendMessage("Твоя команда «" + action.command + "» была отменена.");
        }
    }

    /** Владелец отклонил — вечный бан и команда не выполняется. */
    private void reject(String id) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PendingCommand action = pending.remove(id);
            if (action == null) {
                return;
            }
            log("ОТКЛОНЕНО ВЛАДЕЛЬЦЕМ — игрок: " + action.player
                    + ", команда: " + action.command + ", БАН, время: " + now());
            Bukkit.getBanList(BanList.Type.NAME).addBan(action.player, BAN_MESSAGE, null, "FlameCore");
            Player sender = Bukkit.getPlayerExact(action.player);
            if (sender != null) {
                sender.kick(net.kyori.adventure.text.Component.text(BAN_MESSAGE));
            }
        });
    }

    /** Таймаут истёк и никто не отменил/не отклонил — выполняем команду. */
    private void resolveTimeout(String id) {
        PendingCommand action = pending.remove(id);
        if (action == null) {
            return;
        }
        Player sender = Bukkit.getPlayerExact(action.player);
        if (sender == null || !sender.isOnline()) {
            log("АВТО-ВЫПОЛНЕНИЕ ПРОПУЩЕНО (офлайн) — " + action.player
                    + ", команда: " + action.command + ", время: " + now());
            return;
        }
        log("АВТО-ВЫПОЛНЕНИЕ (таймаут) — игрок: " + action.player
                + ", команда: " + action.command + ", время: " + now());
        plugin.getTelegramNotifier().sendDangerExecuted(action.player, action.command);
        Bukkit.dispatchCommand(sender, action.command);
    }

    private void log(String message) {
        logger.log(Level.INFO, message);
    }

    private String now() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
    }

    @lombok.Value
    private static final class PendingCommand {
        String id;
        String player;
        String command;
        long expireAt;
    }

    private static final class TimestampFormatter extends Formatter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            return "[" + dateFormat.format(new Date(record.getMillis())) + "] "
                    + record.getMessage() + System.lineSeparator();
        }
    }
}