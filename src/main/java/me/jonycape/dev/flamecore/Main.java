package me.jonycape.dev.flamecore;

import lombok.Getter;
import me.jonycape.dev.flamecore.commands.FlameCoreCommand;
import me.jonycape.dev.flamecore.commands.GiveawayCommandExecutor;
import me.jonycape.dev.flamecore.config.ConfigManager;
import me.jonycape.dev.flamecore.database.DatabaseManager;
import me.jonycape.dev.flamecore.giveaway.GiveawayManager;
import me.jonycape.dev.flamecore.giveaway.GiveawayScheduler;
import me.jonycape.dev.flamecore.protection.AdminProtectionListener;
import me.jonycape.dev.flamecore.protection.AdminProtectionService;
import me.jonycape.dev.flamecore.protection.AdminRestrictionListener;
import me.jonycape.dev.flamecore.protection.TelegramNotifier;
import org.bukkit.plugin.java.JavaPlugin;

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

    @Override
    public void onEnable() {
        instance = this;

        ConfigManager.create(this);
        initDatabase();
        initGiveaways();
        initCommands();
        initProtection();

        getLogger().info("FlameCore был успешно запущен.");
    }

    @Override
    public void onDisable() {
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
        new GiveawayScheduler(this, giveawayManager).start();
    }

    private void initCommands() {
        getCommand("giveaway").setExecutor(new GiveawayCommandExecutor(this));
        FlameCoreCommand flameCore = new FlameCoreCommand(this);
        getCommand("flamecore").setExecutor(flameCore);
        getCommand("flamecore").setTabCompleter(flameCore);
    }

    private void initProtection() {
        this.telegramNotifier = new TelegramNotifier(this);
        this.adminProtectionService = new AdminProtectionService(this);
        AdminProtectionListener listener = new AdminProtectionListener(this, adminProtectionService);
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(new AdminRestrictionListener(adminProtectionService), this);
        telegramNotifier.startPolling(listener.createCallbackHandler());
    }

    public void reloadPlugin() {
        try {
            ConfigManager.getInstance().reload();
            this.adminProtectionService = new AdminProtectionService(this);
            new GiveawayManager(this).init();
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