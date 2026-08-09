package me.jonycape.dev.flamecore.protection;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.management.SessionManager;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AdminProtectionService {

    private final Main plugin;
    private final Map<String, String> admins;
    private final Map<String, Long> pendingLogins;
    private final Map<String, BossBar> bars;
    private final String ownerName;
    private final long loginTimeoutMs;

    public AdminProtectionService(Main plugin) {
        this.plugin = plugin;
        this.admins = new HashMap<>(Main.getCfg().getStringMap(ConfigKeys.ADMINS));
        this.pendingLogins = new ConcurrentHashMap<>();
        this.bars = new ConcurrentHashMap<>();
        this.ownerName = Main.getCfg().getString(ConfigKeys.OWNER_NAME, "").toLowerCase();
        this.loginTimeoutMs = Math.max(10, Main.getCfg().getInt(ConfigKeys.LOGIN_TIMEOUT, 90)) * 1000L;
    }

    public boolean isAdmin(String name) {
        return admins.containsKey(name.toLowerCase());
    }

    public boolean isOwner(String name) {
        return name != null && name.equalsIgnoreCase(ownerName);
    }

    public String getTelegramId(String name) {
        return admins.get(name.toLowerCase());
    }

    public void onPlayerJoin(Player player) {
        if (!isAdmin(player.getName()) && !isOwner(player.getName())) {
            return;
        }
        String ip = player.getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : "неизвестен";
        long expiresAt = System.currentTimeMillis() + loginTimeoutMs;
        pendingLogins.put(player.getName().toLowerCase(), expiresAt);
        freezePlayer(player);
        log("ЗАПРОС ВХОДА — " + player.getName() + ", IP: " + ip);

        if (isOwner(player.getName())) {
            plugin.getTelegramNotifier().sendOwnerLogin(
                    player.getName(), ip, expiresAt, true);
        } else {
            String adminTelegramId = admins.get(player.getName().toLowerCase());
            if (adminTelegramId != null && !adminTelegramId.isEmpty()) {
                plugin.getTelegramNotifier().sendAdminLogin(
                        adminTelegramId, player.getName(), ip, expiresAt);
            }
        }
        notifyWaiting(player);
        showBar(player);
    }

    public void onPlayerQuit(Player player) {
        String name = player.getName().toLowerCase();
        SessionManager.revoke(name);
        pendingLogins.remove(name);
        hideBar(player);
        unfreezePlayer(player);
    }

    public void approveAdmin(Player player) {
        if (player == null || !isPendingLogin(player.getName())) {
            return;
        }
        pendingLogins.remove(player.getName().toLowerCase());
        hideBar(player);
        unfreezePlayer(player);
        player.sendMessage(MessageUtils.color(MessageUtils.replace(
                Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_APPROVED), "player", player.getName())));
        log("Вход ПОДТВЕРЖДЁН: " + player.getName());
    }

    public void kickAdmin(Player player) {
        if (player == null) {
            return;
        }
        boolean wasPending = isPendingLogin(player.getName());
        pendingLogins.remove(player.getName().toLowerCase());
        hideBar(player);
        unfreezePlayer(player);
        if (player.isOnline()) {
            player.kick(LegacyComponentSerializer.legacySection().deserialize(
                    MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_DENIED))));
        }
        if (wasPending) {
            log("Вход ОТКЛОНЁН (кик): " + player.getName());
        }
    }

    public void grantPanelAccess(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        SessionManager.grant(player.getName());
        pendingLogins.remove(player.getName().toLowerCase());
        hideBar(player);
        unfreezePlayer(player);
        player.sendMessage(MessageUtils.color(MessageUtils.replace(
                Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_OWNER_PANEL_GRANTED), "player", player.getName())));
        log("Панель выдана: " + player.getName());
    }

    public boolean isPendingLogin(String name) {
        Long expiresAt = pendingLogins.get(name.toLowerCase());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt <= System.currentTimeMillis()) {
            pendingLogins.remove(name.toLowerCase());
            return false;
        }
        return true;
    }

    public void pruneExpired() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : pendingLogins.entrySet()) {
            if (entry.getValue() <= now) {
                pendingLogins.remove(entry.getKey());
                Player player = Bukkit.getPlayerExact(entry.getKey());
                if (player != null) {
                    hideBar(player);
                    unfreezePlayer(player);
                    log("Вход просрочен, игрок разморожен: " + player.getName());
                }
            }
        }
    }

    public Map<String, Long> pendingSnapshot() {
        return new HashMap<>(pendingLogins);
    }

    public void restorePending(Map<String, Long> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            if (entry.getValue() > System.currentTimeMillis()) {
                pendingLogins.put(entry.getKey(), entry.getValue());
            }
        }
    }

    private void freezePlayer(Player player) {
        if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        player.setWalkSpeed(0.0F);
    }

    private void unfreezePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.setWalkSpeed(0.2F);
        player.setInvulnerable(false);
    }

    private void notifyWaiting(Player player) {
        player.sendMessage(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_WAITING)));
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        player.showTitle(Title.title(
                legacy.deserialize(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_WAITING_TITLE))),
                legacy.deserialize(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_WAITING_SUBTITLE))),
                Title.Times.times(Ticks.duration(10), Ticks.duration(100), Ticks.duration(10))));
        player.setInvulnerable(true);
    }

    private void showBar(Player player) {
        BossBar bar = Bukkit.createBossBar(
                MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_ADMIN_WAITING_TITLE)),
                BarColor.YELLOW, BarStyle.SOLID);
        bar.addPlayer(player);
        bars.put(player.getName().toLowerCase(), bar);
    }

    private void hideBar(Player player) {
        BossBar bar = bars.remove(player.getName().toLowerCase());
        if (bar != null) {
            bar.removePlayer(player);
            bar.setVisible(false);
        }
    }

    private void log(String message) {
        plugin.getLogger().info(message);
    }
}