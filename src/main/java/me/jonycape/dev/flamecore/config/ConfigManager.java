package me.jonycape.dev.flamecore.config;

import lombok.Getter;
import me.jonycape.dev.flamecore.Main;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ConfigManager {

    @Getter
    private static ConfigManager instance;

    private final Main plugin;
    private final Map<String, File> files = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> configs = new LinkedHashMap<>();
    private YamlConfiguration merged;
    private final Map<String, Object> cache = new HashMap<>();

    private static final Map<String, String> FILE_BY_PREFIX = new HashMap<>();

    static {
        FILE_BY_PREFIX.put("database", "config.yml");
        FILE_BY_PREFIX.put("bot", "config.yml");
        FILE_BY_PREFIX.put("owner", "config.yml");

        FILE_BY_PREFIX.put("admins", "admin-guard.yml");
        FILE_BY_PREFIX.put("protection", "admin-guard.yml");
        FILE_BY_PREFIX.put("telegram-messages", "admin-guard.yml");

        FILE_BY_PREFIX.put("messages", "messages.yml");

        FILE_BY_PREFIX.put("customize", "customize.yml");

        FILE_BY_PREFIX.put("stream", "stream.yml");

        FILE_BY_PREFIX.put("promo-codes", "promo-codes.yml");

        FILE_BY_PREFIX.put("donatetop", "donatetop.yml");
        FILE_BY_PREFIX.put("donatetop-hologram", "donatetop.yml");

        FILE_BY_PREFIX.put("playerinfo", "playerinfo.yml");
    }

    private static final List<String> CONFIG_FILES = List.of(
            "config.yml", "admin-guard.yml", "messages.yml",
            "customize.yml", "stream.yml", "promo-codes.yml", "donatetop.yml", "playerinfo.yml");

    private ConfigManager(Main plugin) {
        this.plugin = plugin;
        load();
    }

    public static ConfigManager create(Main plugin) {
        instance = new ConfigManager(plugin);
        return instance;
    }

    public void load() {
        try {
            plugin.getDataFolder().mkdirs();
            files.clear();
            configs.clear();
            for (String name : CONFIG_FILES) {
                File file = new File(plugin.getDataFolder(), name);
                if (!file.exists()) {
                    plugin.saveResource(name, false);
                }
                files.put(name, file);
                configs.put(name, YamlConfiguration.loadConfiguration(file));
            }
            rebuildMerged();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось загрузить конфигурацию", e);
        }
    }

    private void rebuildMerged() {
        this.merged = new YamlConfiguration();
        this.cache.clear();
        for (YamlConfiguration config : configs.values()) {
            for (String key : config.getKeys(true)) {
                Object value = config.get(key);
                merged.set(key, value);
                cache.put(key, value);
            }
        }
    }

    public void reload() {
        load();
    }

    public org.bukkit.configuration.file.YamlConfiguration getConfig() {
        return merged;
    }

    private String ownerFile(String path) {
        int dot = path.indexOf('.');
        String prefix = dot < 0 ? path : path.substring(0, dot);
        return FILE_BY_PREFIX.get(prefix);
    }

    public void set(String path, Object value) {
        String fileName = ownerFile(path);
        YamlConfiguration target = fileName == null ? null : configs.get(fileName);
        if (target == null) {
            merged.set(path, value);
            cache.put(path, value);
            return;
        }
        target.set(path, value);
        merged.set(path, value);
        cache.put(path, value);
    }

    public void save() {
        for (Map.Entry<String, YamlConfiguration> entry : configs.entrySet()) {
            File file = files.get(entry.getKey());
            try {
                entry.getValue().save(file);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Не удалось сохранить " + entry.getKey(), e);
            }
        }
    }

    public String getString(String path) {
        Object value = cache.get(path);
        return value == null ? null : String.valueOf(value);
    }

    public String getString(String path, String def) {
        String value = getString(path);
        return value == null ? def : value;
    }

    public boolean getBoolean(String path, boolean def) {
        Object value = cache.get(path);
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public int getInt(String path, int def) {
        Object value = cache.get(path);
        try {
            return value == null ? def : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public double getDouble(String path, double def) {
        Object value = cache.get(path);
        try {
            return value == null ? def : Double.parseDouble(String.valueOf(value));
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

    public org.bukkit.configuration.ConfigurationSection getSection(String path) {
        Object value = cache.get(path);
        return value instanceof org.bukkit.configuration.ConfigurationSection
                ? (org.bukkit.configuration.ConfigurationSection) value : null;
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