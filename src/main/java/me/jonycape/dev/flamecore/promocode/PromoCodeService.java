package me.jonycape.dev.flamecore.promocode;

import lombok.Getter;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.database.PromoCodeDAO;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.command.CommandMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class PromoCodeService {

    private final Main plugin;
    private final PromoCodeDAO dao;
    private final Map<String, PromoCode> codes;
    private CommandMap commandMap;

    @Getter
    private final Map<String, PromoCode> loadedCodes;

    public PromoCodeService(Main plugin) {
        this.plugin = plugin;
        this.dao = new PromoCodeDAO(plugin);
        this.codes = new HashMap<>();
        this.loadedCodes = codes;
        dao.init();
        load();
    }

    public void reload() {
        codes.clear();
        load();
    }

    public void load() {
        ConfigurationSection section = Main.getCfg().getSection(ConfigKeys.PROMO_CODES);
        if (section == null) {
            return;
        }
        for (String codeName : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(codeName);
            if (entry == null) {
                continue;
            }
            int uses = Math.max(1, entry.getInt("uses", 1));
            List<String> commands = entry.getStringList("commands");
            List<Color> colors = parseColors(entry, "colors", new String[]{"ff8800", "ff0000"});
            List<Color> fade = parseColors(entry, "fade", new String[]{"ffdd55", "7a0000"});
            codes.put(codeName.toLowerCase(),
                    new PromoCode(codeName, uses, commands, colors, fade));
            registerCommand(codeName.toLowerCase());
        }
    }

    private void registerCommand(String name) {
        if (commandMap == null) {
            commandMap = plugin.getServer().getCommandMap();
        }
        commandMap.register("flamecore", new PromoCodeCommand(name));
    }

    public PromoCode getCode(String name) {
        return codes.get(name.toLowerCase());
    }

    public Result consume(Player player, String codeName) {
        PromoCode code = codes.get(codeName.toLowerCase());
        if (code == null) {
            return Result.NOT_FOUND;
        }
        String playerKey = player.getName().toLowerCase();
        int current = dao.getUses(code.getCode(), playerKey);
        if (current >= code.getUses()) {
            player.sendMessage(MessageUtils.color(MessageUtils.replace(
                    Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_PROMO_ALREADY_USED),
                    "code", code.getCode())));
            return Result.ALREADY_USED;
        }
        dao.setUses(code.getCode(), playerKey, current + 1);

        for (String cmd : code.getCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("%player%", player.getName()));
        }

        player.sendMessage(MessageUtils.color(MessageUtils.replace(
                Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_PROMO_ACTIVATED),
                "code", code.getCode())));
        showTitle(player, code.getCode());
        spawnFireworks(player, code);
        return Result.OK;
    }

    private void showTitle(Player player, String code) {
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        player.showTitle(Title.title(
                legacy.deserialize(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_PROMO_TITLE))),
                legacy.deserialize(MessageUtils.color(MessageUtils.replace(
                        Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_PROMO_SUBTITLE), "code", code))),
                Title.Times.times(Ticks.duration(10), Ticks.duration(70), Ticks.duration(10))));
    }

    private void spawnFireworks(Player player, PromoCode code) {
        Location base = player.getLocation().add(0, 1, 0);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            Location loc = base.clone().add(
                    rnd.nextDouble(-1.5, 1.5),
                    rnd.nextDouble(0.5, 2.0),
                    rnd.nextDouble(-1.5, 1.5));
            Firework firework = player.getWorld().spawn(loc, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            FireworkEffect effect = FireworkEffect.builder()
                    .with(FireworkEffect.Type.BURST)
                    .withColor(code.getColors())
                    .withFade(code.getFade())
                    .build();
            meta.addEffect(effect);
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.detonate();
        }
    }

    private List<Color> parseColors(ConfigurationSection section, String path, String[] defaults) {
        List<String> raw = section.getStringList(path);
        List<Color> colors = new ArrayList<>();
        if (raw.isEmpty()) {
            for (String hex : defaults) {
                colors.add(hexToColor(hex));
            }
            return colors;
        }
        for (String hex : raw) {
            Color color = hexToColor(hex.trim().replace("#", ""));
            if (color != null) {
                colors.add(color);
            }
        }
        if (colors.isEmpty()) {
            for (String hex : defaults) {
                colors.add(hexToColor(hex));
            }
        }
        return colors;
    }

    private Color hexToColor(String hex) {
        if (hex == null || hex.length() != 6) {
            return null;
        }
        try {
            int rgb = (int) Long.parseLong(hex, 16);
            return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public enum Result {
        OK,
        NOT_FOUND,
        ALREADY_USED
    }
}