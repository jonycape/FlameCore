package me.jonycape.dev.flamecore.protection;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.management.SessionManager;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class AdminProtectionService {

    private final Main plugin;
    private final Set<String> admins;
    private final Set<String> pendingLogins;
    private final String ownerName;
    private final Logger logger;

    public AdminProtectionService(Main plugin) {
        this.plugin = plugin;
        this.admins = new HashSet<>();
        for (String name : Main.getCfg().getStringList(ConfigKeys.ADMINS)) {
            admins.add(name.toLowerCase());
        }
        this.pendingLogins = new HashSet<>();
        this.ownerName = Main.getCfg().getString(ConfigKeys.OWNER_NAME, "").toLowerCase();
        this.logger = createLogger();
    }

    private Logger createLogger() {
        Logger log = Logger.getLogger("me.jonycape.dev.flamecore.AdminProtection");
        File logsDir = new File(plugin.getDataFolder(), "logs");
        logsDir.mkdirs();
        try {
            FileHandler handler =
                    new FileHandler(new File(logsDir, "admin_protection.log").getAbsolutePath(), true);
            handler.setFormatter(new TimestampFormatter());
            log.addHandler(handler);
            log.setUseParentHandlers(false);
            log.setLevel(Level.INFO);
        } catch (IOException e) {
            log.log(Level.WARNING, "Не удалось инициализировать лог-файл защиты администратора", e);
        }
        return log;
    }

    public boolean isAdmin(String name) {
        return admins.contains(name.toLowerCase());
    }

    public boolean isOwner(String name) {
        return name != null && name.equalsIgnoreCase(ownerName);
    }

    public void onPlayerJoin(Player player) {
        if (!isAdmin(player.getName()) && !isOwner(player.getName())) {
            return;
        }
        String ip = player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "неизвестен";
        log("ЗАПРОС ВХОДА — админ: " + player.getName() + ", IP: " + ip + ", время: " + now());

        pendingLogins.add(player.getName().toLowerCase());
        freezePlayer(player);

        if (isOwner(player.getName())) {
            plugin.getTelegramNotifier().sendOwnerLogin(
                    player.getName(), ip, System.currentTimeMillis(), true);
        } else {
            plugin.getTelegramNotifier().sendAdminLogin(
                    player.getName(), ip, System.currentTimeMillis());
        }
        notifyWaiting(player);
    }

    public void onPlayerQuit(Player player) {
        String name = player.getName().toLowerCase();
        SessionManager.revoke(name);
        pendingLogins.remove(name);
        unfreezePlayer(player);
        log("ВЫХОД — " + player.getName() + ", доступ сброшен, время: " + now());
    }

    public void approveAdmin(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        pendingLogins.remove(player.getName().toLowerCase());
        unfreezePlayer(player);
        player.sendMessage(MessageUtils.color(
                prefix()
                        + MessageUtils.replace(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_APPROVED),
                        "player", player.getName())));
        log("Вход администратора ПОДТВЕРЖДЁН: " + player.getName() + ", время: " + now());
    }

    public void kickAdmin(Player player) {
        if (player == null) {
            return;
        }
        if (player.isOnline()) {
            pendingLogins.remove(player.getName().toLowerCase());
            unfreezePlayer(player);
            player.kick(net.kyori.adventure.text.Component.text(
                    MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_DENIED))));
        } else {
            pendingLogins.remove(player.getName().toLowerCase());
        }
        log("Вход администратора ОТКЛОНЁН (кик): " + player.getName() + ", время: " + now());
    }

    public void grantPanelAccess(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        SessionManager.grant(player.getName());
        player.sendMessage(MessageUtils.color(
                prefix()
                        + MessageUtils.replace(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_OWNER_PANEL_GRANTED),
                        "player", player.getName())));
        log("Владельцу выдана панель управления: " + player.getName() + ", время: " + now());
    }

    public boolean isPendingLogin(String name) {
        return pendingLogins.contains(name.toLowerCase());
    }

    private void freezePlayer(Player player) {
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)) {
            player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        }
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        player.setWalkSpeed(0.0F);
    }

    private void unfreezePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
        player.setWalkSpeed(0.2F);
        player.setInvulnerable(false);
    }

    private void notifyWaiting(Player player) {
        player.sendMessage(MessageUtils.color(
                prefix() + Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_WAITING)));
        player.setInvulnerable(true);
    }

    private String prefix() {
        return Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_PREFIX);
    }

    private void log(String message) {
        logger.log(Level.INFO, message);
    }

    private String now() {
        return new SimpleDateFormat("dd.MM.yyyy HH:mm:ss").format(new Date());
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