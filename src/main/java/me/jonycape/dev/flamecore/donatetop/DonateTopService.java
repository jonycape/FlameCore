package me.jonycape.dev.flamecore.donatetop;

import eu.decentsoftware.holograms.api.DecentHologramsAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.api.holograms.HologramLine;
import eu.decentsoftware.holograms.api.holograms.HologramManager;
import eu.decentsoftware.holograms.api.holograms.HologramPage;
import lombok.Getter;
import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.database.DonateTopDAO;
import me.jonycape.dev.flamecore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public final class DonateTopService {

    private static final String HOLOGRAM_ID = "flamecore_donatetop";
    private static final int TOP_SIZE = 5;
    private static final double HOLOGRAM_HEIGHT = 1.8;
    private static final double LOOK_RANGE = 8.0;

    @Getter
    private final Main plugin;
    private final DonateTopDAO dao;

    private Villager npc;
    private Hologram hologram;
    private final List<String> templateLines = new ArrayList<>();
    private int taskId = -1;
    private int lookTaskId = -1;

    public DonateTopService(Main plugin) {
        this.plugin = plugin;
        this.dao = new DonateTopDAO(plugin);
    }

    public void init() {
        dao.init();
        templateLines.clear();
        templateLines.addAll(Main.getCfg().getStringList(ConfigKeys.DONATE_TOP_HOLOGRAM));

        if (isHologramsAvailable()) {
            Location npcLoc = loadNpcLocation();
            if (npcLoc != null) {
                spawnNpc(npcLoc);
                startLookTask();
            }
            Location holLoc = loadHologramLocation();
            if (holLoc != null) {
                hologram = createHologram(holLoc);
            }
            int minutes = Main.getCfg().getInt(ConfigKeys.DONATE_TOP_UPDATE_MINUTES, 5);
            taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh,
                    0L, minutes * 60L * 20L).getTaskId();
        }
        refresh();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        if (lookTaskId != -1) {
            Bukkit.getScheduler().cancelTask(lookTaskId);
            lookTaskId = -1;
        }
        remove();
    }

    public boolean isNpc(org.bukkit.entity.Entity entity) {
        return npc != null && npc.equals(entity);
    }

    public boolean isHologramsAvailable() {
        return DecentHologramsAPI.isRunning();
    }

    public void addPayment(String player) {
        dao.addPayment(player);
        refresh();
    }

    public void clearTop() {
        dao.clear();
        refresh();
    }

    public List<TopEntry> getTop() {
        return dao.getTop(TOP_SIZE);
    }

    public void spawnAt(Location loc) {
        remove();
        Location npcLoc = loc.clone();
        Location holLoc = loc.clone();
        holLoc.setY(holLoc.getY() + HOLOGRAM_HEIGHT);
        spawnNpc(npcLoc);
        startLookTask();
        if (isHologramsAvailable()) {
            hologram = createHologram(holLoc);
        }
        saveNpcLocation(npcLoc);
        saveHologramLocation(holLoc);
        refresh();
    }

    public void remove() {
        if (isHologramsAvailable()) {
            HologramManager manager = DecentHologramsAPI.get().getHologramManager();
            Hologram existing = manager.getHologram(HOLOGRAM_ID);
            if (existing != null) {
                existing.destroy();
            }
            manager.removeHologram(HOLOGRAM_ID);
        }
        hologram = null;
        if (npc != null) {
            npc.remove();
            npc = null;
        }
    }

    public void refresh() {
        if (hologram == null || !isHologramsAvailable()) {
            return;
        }
        List<TopEntry> top = getTop();
        HologramPage page = hologram.getPage(0);
        List<HologramLine> lines = page.getLines();
        for (int i = 0; i < templateLines.size(); i++) {
            if (i < lines.size()) {
                lines.get(i).setText(colorizeLine(templateLines.get(i), top));
            }
        }
        hologram.updateAll();
    }

    private Hologram createHologram(Location loc) {
        HologramManager manager = DecentHologramsAPI.get().getHologramManager();
        Hologram h = new Hologram(HOLOGRAM_ID, loc);
        h.setSaveToFile(false);
        HologramPage page = h.getPage(0);
        List<TopEntry> top = getTop();
        for (String raw : templateLines) {
            page.addLine(new HologramLine(page, page.getNextLineLocation(), colorizeLine(raw, top)));
        }
        manager.registerHologram(h);
        h.showAll();
        return h;
    }

    private String colorizeLine(String raw, List<TopEntry> top) {
        String line = raw;
        for (int i = 1; i <= TOP_SIZE; i++) {
            String value = "N/A";
            if (i <= top.size()) {
                TopEntry entry = top.get(i - 1);
                value = entry.player() + " — " + entry.payments();
            }
            line = line.replace("%flamecore_donatetop_" + i + "%", value);
        }
        return MessageUtils.color(line);
    }

    private void spawnNpc(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        npc = world.spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setSilent(true);
            v.setInvulnerable(true);
            v.setCollidable(false);
            v.setCanPickupItems(false);
            v.setRemoveWhenFarAway(false);
            v.setPersistent(true);
        });
    }

    private void startLookTask() {
        if (lookTaskId != -1) {
            return;
        }
        lookTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (npc == null || !npc.isValid()) {
                return;
            }
            Player nearest = findNearestPlayer(npc.getLocation(), LOOK_RANGE);
            if (nearest == null) {
                return;
            }
            Location loc = npc.getLocation().clone();
            Vector dir = nearest.getEyeLocation().toVector()
                    .subtract(npc.getEyeLocation().toVector()).normalize();
            loc.setDirection(dir);
            npc.teleport(loc);
        }, 0L, 1L).getTaskId();
    }

    private Player findNearestPlayer(Location loc, double range) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        double rangeSq = range * range;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(loc.getWorld())) {
                continue;
            }
            double distSq = p.getLocation().distanceSquared(loc);
            if (distSq < rangeSq && distSq < best) {
                best = distSq;
                nearest = p;
            }
        }
        return nearest;
    }

    private void saveNpcLocation(Location loc) {
        Main.getCfg().set(ConfigKeys.DONATE_TOP_NPC_LOCATION, loc);
        Main.getCfg().save();
    }

    private void saveHologramLocation(Location loc) {
        Main.getCfg().set(ConfigKeys.DONATE_TOP_HOLOGRAM_LOCATION, loc);
        Main.getCfg().save();
    }

    private Location loadNpcLocation() {
        return Main.getCfg().getConfig().getLocation(ConfigKeys.DONATE_TOP_NPC_LOCATION);
    }

    private Location loadHologramLocation() {
        return Main.getCfg().getConfig().getLocation(ConfigKeys.DONATE_TOP_HOLOGRAM_LOCATION);
    }
}