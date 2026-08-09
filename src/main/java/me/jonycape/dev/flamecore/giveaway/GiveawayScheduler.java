package me.jonycape.dev.flamecore.giveaway;

import me.jonycape.dev.flamecore.Main;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public final class GiveawayScheduler {

    private final Main plugin;

    public GiveawayScheduler(Main plugin) {
        this.plugin = plugin;
    }

    public void start() {
        new BukkitRunnable() {
            @Override
            public void run() {
                GiveawayManager manager = Main.getInstance() == null ? null : Main.getInstance().getGiveawayManager();
                if (manager == null) {
                    return;
                }
                long now = System.currentTimeMillis();
                List<Giveaway> expired = new ArrayList<>();
                for (Giveaway giveaway : manager.getActiveGiveaways()) {
                    if (giveaway.getEndTime() <= now) {
                        expired.add(giveaway);
                    }
                }
                for (Giveaway giveaway : expired) {
                    manager.finish(giveaway);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}