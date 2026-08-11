package me.jonycape.dev.flamecore;

import lombok.Getter;
import me.jonycape.dev.flamecore.commands.FlameCoreCommand;
import me.jonycape.dev.flamecore.commands.GiveawayCommandExecutor;
import me.jonycape.dev.flamecore.commands.StreamCommandExecutor;
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
import me.jonycape.dev.flamecore.promocode.PromoCodeListener;
import me.jonycape.dev.flamecore.promocode.PromoCodeService;
import me.jonycape.dev.flamecore.customize.CustomizeService;
import me.jonycape.dev.flamecore.stream.StreamService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

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

    @Override
    public void onEnable() {
        instance = this;

        ConfigManager.create(this);
        initDatabase();
        initGiveaways();
        initPromoCodes();
        initCustomize();
        initStream();
        initCommands();
        initProtection();

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (adminProtectionService != null) {
                adminProtectionService.pruneExpired();
            }
        }, 20L, 20L);

        getLogger().info("FlameCore был успешно запущен.");
    }

    @Override
    public void onDisable() {
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

    private void initGiveaways() {
        this.giveawayManager = new GiveawayManager(this);
        giveawayManager.init();
        new GiveawayScheduler(this).start();
    }

    private void initPromoCodes() {
        this.promoCodes = new PromoCodeService(this);
        getServer().getPluginManager().registerEvents(new PromoCodeListener(), this);
    }

    private void initCustomize() {
        this.customizeService = new CustomizeService(this);
        customizeService.start();
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onQuit(PlayerQuitEvent event) {
                customizeService.onQuit(event.getPlayer());
            }
        }, this);
    }

    private void initStream() {
        this.streamService = new StreamService(this);
    }

    private void initCommands() {
        getCommand("giveaway").setExecutor(new GiveawayCommandExecutor(this));
        FlameCoreCommand flameCore = new FlameCoreCommand(this);
        getCommand("flamecore").setExecutor(flameCore);
        getCommand("flamecore").setTabCompleter(flameCore);
        getCommand("stream").setExecutor(new StreamCommandExecutor(this));
    }

    private void initProtection() {
        this.telegramNotifier = new TelegramNotifier(this);
        this.adminProtectionService = new AdminProtectionService(this);
        AdminProtectionListener listener = new AdminProtectionListener();
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(new AdminRestrictionListener(), this);

        this.dangerousCommandService = new DangerousCommandService(this, adminProtectionService);
        getServer().getPluginManager().registerEvents(new DangerousCommandListener(), this);

        telegramNotifier.startPolling((data, from) -> {
            if (data != null && data.startsWith("dc:")) {
                dangerousCommandService.handleCallback(data, from);
            } else {
                listener.handleCallback(data, from);
            }
        });
    }

    public void reloadPlugin() {
        try {
            Map<String, Long> pendingSnapshot = adminProtectionService == null
                    ? null : adminProtectionService.pendingSnapshot();

            ConfigManager.getInstance().reload();
            this.adminProtectionService = new AdminProtectionService(this);
            if (pendingSnapshot != null) {
                adminProtectionService.restorePending(pendingSnapshot);
            }
            if (dangerousCommandService != null) {
                dangerousCommandService.reload(adminProtectionService);
            }
            this.giveawayManager = new GiveawayManager(this);
            giveawayManager.init();
            promoCodes.reload();
            if (customizeService != null) {
                customizeService.stop();
                customizeService.start();
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Ошибка при перезагрузке плагина", e);
        }
    }

    public static ConfigManager getCfg() {
        return ConfigManager.getInstance();
    }

    public static Main getInstance() {
        return instance;
    }
}