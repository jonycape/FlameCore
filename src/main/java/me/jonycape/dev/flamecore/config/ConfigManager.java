package me.jonycape.dev.flamecore.config;

import lombok.Getter;
import me.jonycape.dev.flamecore.Main;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ConfigManager {

    @Getter
    private static ConfigManager instance;

    private final Main plugin;
    private File file;
    private YamlConfiguration config;
    private final Map<String, Object> cache = new HashMap<>();

    private ConfigManager(Main plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "config.yml");
        load();
    }

    public static ConfigManager create(Main plugin) {
        instance = new ConfigManager(plugin);
        return instance;
    }

    public void load() {
        try {
            if (!file.exists()) {
                plugin.getDataFolder().mkdirs();
                plugin.saveResource("config.yml", false);
            }
            this.config = YamlConfiguration.loadConfiguration(file);
            this.cache.clear();
            for (String key : config.getKeys(true)) {
                cache.put(key, config.get(key));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить конфигурацию", e);
        }
    }

    public void reload() {
        load();
    }

    public String getString(String path) {
        Object value = cache.get(path);
        return value == null ? null : String.valueOf(value);
    }

    public String getString(String path, String def) {
        String value = getString(path);
        return value == null ? def : value;
    }

    public int getInt(String path, int def) {
        Object value = cache.get(path);
        try {
            return value == null ? def : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object value = cache.get(path);
        return value instanceof List ? (List<String>) value : java.util.Collections.emptyList();
    }

    public String getMultiLine(String path) {
        Object value = cache.get(path);
        if (value instanceof List) {
            return String.join("\n", ((List<String>) value));
        }
        return value == null ? "" : String.valueOf(value);
    }

    public Map<String, String> getStringMap(String path) {
        Object value = cache.get(path);
        if (value instanceof org.bukkit.configuration.ConfigurationSection section) {
            Map<String, String> map = new HashMap<>();
            for (String key : section.getKeys(false)) {
                String item = section.getString(key);
                if (item != null) {
                    map.put(key.toLowerCase(), item);
                }
            }
            return map;
        }
        return java.util.Collections.emptyMap();
    }
}