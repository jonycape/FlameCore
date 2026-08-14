package me.jonycape.dev.flamecore;

import lombok.Getter;
import me.jonycape.dev.flamecore.commands.FlameCoreCommand;
import me.jonycape.dev.flamecore.commands.GiveawayCommandExecutor;
import me.jonycape.dev.flamecore.commands.StreamCommandExecutor;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.config.ConfigManager;
import me.jonycape.dev.flamecore.database.DatabaseManager;
import me.jonycape.dev.flamecore.giveaway.GiveawayManager;
import me.jonycape.dev.flamecore.giveaway.GiveawayScheduler;
import me.jonycape.dev.flamecore.protection.AdminProtectionListener;
import me.jonycape.dev.flamecore.protection.AdminProtectionService;
import me.jonycape.dev.flamecore.protection.AdminRestrictionListener;
import me.jonycape.dev.flamecore.protection.DangerousCommandListener;
import me.jonycape.dev.flamecore.protection.DangerousCommandService;
import me.jonycape.dev.flamecore.protection.TelegramNotifier;
import me.clip.placeholderapi.PlaceholderAPI;
import me.jonycape.dev.flamecore.promocode.PromoCodeListener;
import me.jonycape.dev.flamecore.promocode.PromoCodeService;
import me.jonycape.dev.flamecore.customize.CustomizeService;
import me.jonycape.dev.flamecore.placeholder.FlameCoreExpansion;
import me.jonycape.dev.flamecore.stream.StreamService;
import me.jonycape.dev.flamecore.donatetop.DonateTopService;
import me.jonycape.dev.flamecore.playerinfo.PlayerInfoService;
import me.jonycape.dev.flamecore.playerinfo.PlayerStatsService;
import me.jonycape.dev.flamecore.donate.DonateService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public final class Main extends JavaPlugin {

    private static Main instance;

    @Getter
    private DatabaseManager databaseManager;

    @Getter
    private GiveawayManager giveawayManager;

    @Getter
    private TelegramNotifier telegramNotifier;

    @Getter
    private AdminProtectionService adminProtectionService;

    @Getter
    private DangerousCommandService dangerousCommandService;

    @Getter
    private PromoCodeService promoCodes;

    @Getter
    private CustomizeService customizeService;

    @Getter
    private StreamService streamService;

    @Getter
    private DonateTopService donateTopService;

    @Getter
    private PlayerStatsService playerStatsService;

    @Getter
    private PlayerInfoService playerInfoService;

    @Getter
    private DonateService donateService;

    @Override
    public void onEnable() {
        instance = this;

        ConfigManager.create(this);
        initDatabase();
        initPlayerStats();
        initGiveaways();
        initPromoCodes();
        initCustomize();
        initPlaceholders();
        initStream();
        initDonateTop();
        initDonate();
        initCommands();
        initProtection();

        logDisabledModules();

        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onDamage(EntityDamageEvent event) {
                if (donateTopService != null && donateTopService.isNpc(event.getEntity())) {
                    event.setCancelled(true);
                }
            }
        }, this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (adminProtectionService != null) {
                adminProtectionService.pruneExpired();
            }
        }, 20L, 20L);

        getLogger().info("FlameCore был успешно запущен.");
    }

    @Override
    public void onDisable() {
        if (donateTopService != null) {
            donateTopService.stop();
        }
        if (customizeService != null) {
            customizeService.stop();
        }
        if (telegramNotifier != null) {
            telegramNotifier.stopPolling();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        instance = null;
        getLogger().info("FlameCore завершил работу.");
    }

    private void initDatabase() {
        this.databaseManager = new DatabaseManager(this);
        databaseManager.init();
        databaseManager.getConnection();
    }

    private void initPlayerStats() {
        this.playerStatsService = new PlayerStatsService(this);
        getServer().getPluginManager().registerEvents(playerStatsService, this);
    }

    private void initDonate() {
        if (!isModuleEnabled(ConfigKeys.MODULE_DONATE)) {
            return;
        }
        this.donateService = new DonateService(this);
    }

    private void initGiveaways() {
        if (!isModuleEnabled(ConfigKeys.MODULE_GIVEAWAY)) {
            return;
        }
        this.giveawayManager = new GiveawayManager(this);
        giveawayManager.init();
        new GiveawayScheduler(this).start();
    }

    private void initPromoCodes() {
        if (!isModuleEnabled(ConfigKeys.MODULE_PROMOCODE)) {
            return;
        }
        this.promoCodes = new PromoCodeService(this);
        getServer().getPluginManager().registerEvents(new PromoCodeListener(), this);
    }

    private void initCustomize() {
        if (!isModuleEnabled(ConfigKeys.MODULE_CUSTOMIZE)) {
            return;
        }
        this.customizeService = new CustomizeService(this);
        customizeService.start();
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onQuit(PlayerQuitEvent event) {
                customizeService.onQuit(event.getPlayer());
            }
        }, this);
    }

    private void initPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            if (PlaceholderAPI.registerExpansion(new FlameCoreExpansion())) {
                getLogger().info("Зарегистрировано расширение PlaceholderAPI 'flamecore'.");
            }
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Не удалось зарегистрировать расширение PlaceholderAPI", t);
        }
    }

    private void initStream() {
        if (!isModuleEnabled(ConfigKeys.MODULE_STREAM)) {
            return;
        }
        this.streamService = new StreamService(this);
    }

    private void initDonateTop() {
        if (!isModuleEnabled(ConfigKeys.MODULE_DONATETOP)) {
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("DecentHolograms") == null) {
            getLogger().warning("DecentHolograms не найден — топ платежей отключён.");
            return;
        }
        try {
            this.donateTopService = new DonateTopService(this);
            donateTopService.init();
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Не удалось запустить топ платежей", t);
        }
    }

    private void initCommands() {
        getCommand("giveaway").setExecutor(new GiveawayCommandExecutor(this));
        FlameCoreCommand flameCore = new FlameCoreCommand(this);
        getCommand("flamecore").setExecutor(flameCore);
        getCommand("flamecore").setTabCompleter(flameCore);
        getCommand("stream").setExecutor(new StreamCommandExecutor(this));
    }

    private void initProtection() {
        if (!isModuleEnabled(ConfigKeys.MODULE_PROTECTION)) {
            return;
        }
        this.telegramNotifier = new TelegramNotifier(this);
        if (isModuleEnabled(ConfigKeys.MODULE_PLAYERINFO)) {
            this.playerInfoService = new PlayerInfoService(this, playerStatsService, telegramNotifier);
        }
        this.adminProtectionService = new AdminProtectionService(this);
        AdminProtectionListener listener = new AdminProtectionListener();
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(new AdminRestrictionListener(), this);

        this.dangerousCommandService = new DangerousCommandService(this, adminProtectionService);
        getServer().getPluginManager().registerEvents(new DangerousCommandListener(), this);

        telegramNotifier.startPolling((data, from) -> {
            if (data != null && data.startsWith("dc:")) {
                dangerousCommandService.handleCallback(data, from);
            } else if (data != null && data.startsWith("info:")) {
                if (playerInfoService != null) {
                    playerInfoService.handleCallback(data, from);
                }
            } else {
                listener.handleCallback(data, from);
            }
        }, (text, userId) -> {
            if (playerInfoService != null) {
                playerInfoService.handleCommand(text, userId);
            }
        });
    }

    public void reloadPlugin() {
        try {
            Map<String, Long> pendingSnapshot = adminProtectionService == null
                    ? null : adminProtectionService.pendingSnapshot();

            ConfigManager.getInstance().reload();

            if (isModuleEnabled(ConfigKeys.MODULE_PROTECTION)) {
                this.adminProtectionService = new AdminProtectionService(this);
                if (pendingSnapshot != null) {
                    adminProtectionService.restorePending(pendingSnapshot);
                }
                if (dangerousCommandService != null) {
                    dangerousCommandService.reload(adminProtectionService);
                }
            }

            if (isModuleEnabled(ConfigKeys.MODULE_GIVEAWAY)) {
                this.giveawayManager = new GiveawayManager(this);
                giveawayManager.init();
            }
            if (isModuleEnabled(ConfigKeys.MODULE_PROMOCODE) && promoCodes != null) {
                promoCodes.reload();
            }
            if (donateTopService != null) {
                donateTopService.stop();
            }
            initDonateTop();
            if (customizeService != null) {
                customizeService.stop();
                customizeService.start();
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка при перезагрузке плагина", e);
        }
    }

    private boolean isModuleEnabled(String key) {
        return ConfigManager.getInstance().getBoolean(key, true);
    }

    private void logDisabledModules() {
        Map<String, String> moduleNames = new LinkedHashMap<>();
        moduleNames.put(ConfigKeys.MODULE_PROTECTION, "protection (защита админов/владельца)");
        moduleNames.put(ConfigKeys.MODULE_GIVEAWAY, "giveaway (конкурсы)");
        moduleNames.put(ConfigKeys.MODULE_PROMOCODE, "promocode (промокоды)");
        moduleNames.put(ConfigKeys.MODULE_CUSTOMIZE, "customize (кастомизация)");
        moduleNames.put(ConfigKeys.MODULE_STREAM, "stream (стримеры)");
        moduleNames.put(ConfigKeys.MODULE_DONATETOP, "donatetop (топ платежей)");
        moduleNames.put(ConfigKeys.MODULE_PLAYERINFO, "playerinfo (/info в Telegram)");
        moduleNames.put(ConfigKeys.MODULE_DONATE, "donate (донат-алерт)");
        for (Map.Entry<String, String> entry : moduleNames.entrySet()) {
            if (!isModuleEnabled(entry.getKey())) {
                getLogger().info("Модуль '" + entry.getValue() + "' отключён (config.yml → modules).");
            }
        }
    }

    public static ConfigManager getCfg() {
        return ConfigManager.getInstance();
    }

    public static Main getInstance() {
        return instance;
    }
}