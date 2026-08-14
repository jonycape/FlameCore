package me.jonycape.dev.flamecore.donate;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.utils.MessageProcessor;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.concurrent.ThreadLocalRandom;

public final class DonateService {

    private static final Color[] COLORS = {hex("ff8800"), hex("ffd166"), hex("ff0000")};
    private static final Color[] FADE = {hex("7a0000"), hex("ffdd55")};

    private final Main plugin;

    public DonateService(Main plugin) {
        this.plugin = plugin;
    }

    public void announce(String playerName, String itemRaw) {
        String item = MessageUtils.color(itemRaw);
        MessageProcessor.broadcast(Main.getCfg().getStringList(ConfigKeys.DONATE_BROADCAST),
                "player", playerName, "item", item);
        Player player = Bukkit.getPlayerExact(playerName);
        if (player != null && player.isOnline()) {
            spawnFireworks(player);
        }
    }

    private void spawnFireworks(Player player) {
        Location base = player.getLocation().add(0, 1, 0);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < 3; i++) {
            Location loc = base.clone().add(
                    rnd.nextDouble(-1.5, 1.5),
                    rnd.nextDouble(0.5, 2.0),
                    rnd.nextDouble(-1.5, 1.5));
            Firework firework = player.getWorld().spawn(loc, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BURST)
                    .withColor(COLORS)
                    .withFade(FADE)
                    .build());
            meta.setPower(0);
            firework.setFireworkMeta(meta);
            firework.detonate();
        }
    }

    private static Color hex(String hex) {
        int rgb = (int) Long.parseLong(hex, 16);
        return Color.fromRGB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}