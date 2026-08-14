package me.jonycape.dev.flamecore.customize;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.config.ConfigManager;
import me.jonycape.dev.flamecore.utils.MessageProcessor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CustomizeService {

    private static final ChatColor[] RAINBOW = {
            ChatColor.RED, ChatColor.GOLD, ChatColor.YELLOW,
            ChatColor.GREEN, ChatColor.AQUA, ChatColor.BLUE,
            ChatColor.LIGHT_PURPLE
    };

    private static final int GLOW_DURATION = 20 * 60 * 60;

    private static final Parrot.Variant PARROT_VARIANT = Parrot.Variant.RED;

    private final Main plugin;
    private final Map<UUID, Customization> active = new HashMap<>();
    private final Map<String, ChatColor> colorNames = new HashMap<>();
    private final Map<String, String> rawNames = new HashMap<>();

    private int rainbowIndex;
    private int glowCounter;
    private int nimbCounter;
    private int taskId = -1;

    private int taskTick = 2;
    private int glowEvery = 1;
    private int nimbEvery = 1;
    private int nimbCount = 8;
    private double nimbRadius = 0.5;

    public CustomizeService(Main plugin) {
        this.plugin = plugin;
    }

    private void loadColors() {
        colorNames.put("red", ChatColor.RED);
        colorNames.put("orange", ChatColor.GOLD);
        colorNames.put("yellow", ChatColor.YELLOW);
        colorNames.put("green", ChatColor.GREEN);
        colorNames.put("cyan", ChatColor.AQUA);
        colorNames.put("blue", ChatColor.BLUE);
        colorNames.put("purple", ChatColor.DARK_PURPLE);
        colorNames.put("pink", ChatColor.LIGHT_PURPLE);
        colorNames.put("white", ChatColor.WHITE);

        rawNames.put("red", "&#FF5555Красный");
        rawNames.put("orange", "&#FFAA00Оранжевый");
        rawNames.put("yellow", "&#FFFF55Жёлтый");
        rawNames.put("green", "&#55FF55Зелёный");
        rawNames.put("cyan", "&#55FFFFГолубой");
        rawNames.put("blue", "&#5555FFСиний");
        rawNames.put("purple", "&#AA00AAФиолетовый");
        rawNames.put("pink", "&#FF55FFРозовый");
        rawNames.put("white", "&#FFFFFFБелый");
    }

    public void start() {
        loadColors();
        taskTick = ConfigManager.getInstance().getInt("customize.task-interval", 2);
        taskTick = Math.max(1, Math.min(taskTick, 20));
        glowEvery = everyOf(ConfigKeys.CUSTOMIZE_GLOW_INTERVAL, 10);
        nimbEvery = everyOf(ConfigKeys.CUSTOMIZE_NIMB_INTERVAL, 5);
        nimbCount = Math.max(3, Main.getCfg().getInt(ConfigKeys.CUSTOMIZE_NIMB_PARTICLES, 8));
        nimbRadius = Math.max(0.2, Main.getCfg().getDouble(ConfigKeys.CUSTOMIZE_NIMB_RADIUS, 0.5));
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, taskTick, taskTick).getTaskId();
    }

    private int everyOf(String key, int defaultDiv) {
        int interval = Math.max(1, Main.getCfg().getInt(key, defaultDiv));
        return Math.max(1, (int) Math.round(interval / (double) taskTick));
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (Customization customization : active.values()) {
            Player player = Bukkit.getPlayer(customization.getPlayerId());
            if (player == null) {
                continue;
            }
            if (customization.isParrot()) {
                clearShoulder(player);
            }
            removeFromAllTeams(player);
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
        active.clear();
    }

    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        Customization state = active.get(id);
        if (state != null && state.isParrot()) {
            clearShoulder(player);
        }
        removeFromAllTeams(player);
        player.removePotionEffect(PotionEffectType.GLOWING);
        active.remove(id);
    }

    public void menu(Player player) {
        MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_MENU));
    }

    public void setColor(Player player, String name) {
        String key = name.toLowerCase();
        Customization state = active.computeIfAbsent(player.getUniqueId(), Customization::new);

        if (key.equals("off")) {
            state.setGlowColor(null);
            state.setCurrentColor(null);
            state.setRainbow(false);
            removeFromColorTeams(player);
            player.removePotionEffect(PotionEffectType.GLOWING);
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_COLOR_SET),
                    "color", "&7выключено");
            return;
        }
        if (key.equals("rainbow")) {
            state.setRainbow(true);
            state.setGlowColor(null);
            state.setCurrentColor(null);
            applyRainbow(player, nextRainbow());
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_COLOR_SET),
                    "color", "&d🌈 Радужный");
            return;
        }
        ChatColor color = colorNames.get(key);
        if (color == null) {
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_INVALID_COLOR),
                    "color", name);
            return;
        }
        state.setRainbow(false);
        state.setGlowColor(color);
        state.setCurrentColor(color);
        applyGlow(player);
        MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_COLOR_SET),
                "color", rawNames.getOrDefault(key, "&f" + key));
    }

    public void toggleParrot(Player player) {
        UUID id = player.getUniqueId();
        Customization state = active.computeIfAbsent(id, Customization::new);
        boolean enable = !state.isParrot();
        state.setParrot(enable);
        if (enable) {
            mountShoulderParrot(player);
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_PARROT_ON));
        } else {
            clearShoulder(player);
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_PARROT_OFF));
        }
    }

    public void toggleNimb(Player player) {
        UUID id = player.getUniqueId();
        Customization state = active.computeIfAbsent(id, Customization::new);
        boolean enable = !state.isNimb();
        state.setNimb(enable);
        if (enable) {
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_NIMB_ON));
        } else {
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_CUSTOMIZE_NIMB_OFF));
        }
    }

    public List<String> getColorNames() {
        List<String> names = new ArrayList<>(colorNames.keySet());
        names.add("rainbow");
        names.add("off");
        Collections.sort(names);
        return names;
    }

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        boolean rainbowTick = every(glowEvery, ++glowCounter);
        boolean nimbTick = every(nimbEvery, ++nimbCounter);

        for (Customization state : active.values()) {
            Player player = Bukkit.getPlayer(state.getPlayerId());
            if (player == null || !player.isOnline() || player.getWorld() == null) {
                continue;
            }
            if (state.isNimb() && nimbTick) {
                spawnNimb(player, nimbCount, nimbRadius);
            }
            if (state.isRainbow()) {
                if (rainbowTick) {
                    applyRainbow(player, nextRainbow());
                }
            } else if (state.getGlowColor() != null) {
                if (rainbowTick || !player.hasPotionEffect(PotionEffectType.GLOWING)
                        || !isOnColorTeam(player)) {
                    ensureGlow(player);
                }
            }
        }
    }

    private boolean every(int every, int counter) {
        return counter % every == 0;
    }

    private void parrotTick(Player player) {
        if (player.getShoulderEntityLeft() == null) {
            mountShoulderParrot(player);
        }
    }

    private void mountShoulderParrot(Player player) {
        Parrot parrot = (Parrot) player.getWorld().spawnEntity(
                player.getLocation(), org.bukkit.entity.EntityType.PARROT);
        parrot.setVariant(PARROT_VARIANT);
        parrot.setInvulnerable(true);
        parrot.setSilent(true);
        player.setShoulderEntityLeft(parrot);
        parrot.remove();
    }

    private void clearShoulder(Player player) {
        player.setShoulderEntityLeft(null);
    }

    private void spawnNimb(Player player, int count, double radius) {
        Location head = player.getEyeLocation().add(0, 0.35, 0);
        double start = Math.toRadians(player.getTicksLived() * 1.5);
        for (int i = 0; i < count; i++) {
            double angle = start + (2 * Math.PI * i / count);
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            player.getWorld().spawnParticle(Particle.END_ROD,
                    head.getX() + dx, head.getY(), head.getZ() + dz, 0, 0, 0, 0, 0);
        }
    }

    private void applyGlow(Player player) {
        ensureGlow(player);
    }

    private void ensureGlow(Player player) {
        if (!player.hasPotionEffect(PotionEffectType.GLOWING)) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.GLOWING, GLOW_DURATION, 0, false, false, false));
        }
        Customization state = active.get(player.getUniqueId());
        if (state == null) {
            return;
        }
        ChatColor color = state.getGlowColor();
        if (color == null) {
            return;
        }
        applyColorTeam(player, color);
    }

    private void applyRainbow(Player player, ChatColor color) {
        Customization state = active.get(player.getUniqueId());
        if (state != null) {
            state.setCurrentColor(color);
        }
        if (!player.hasPotionEffect(PotionEffectType.GLOWING)) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.GLOWING, GLOW_DURATION, 0, false, false, false));
        }
        applyColorTeam(player, color);
    }

    public ChatColor currentColor(Player player) {
        Customization state = active.get(player.getUniqueId());
        return state == null ? null : state.getCurrentColor();
    }

    private void applyColorTeam(Player player, ChatColor color) {
        Scoreboard board = activeBoard(player);
        Team team = getTeam(board, color);
        if (team != null) {
            removeFromColorTeams(player);
            team.addEntry(player.getName());
        }
    }

    private Team getTeam(Scoreboard board, ChatColor color) {
        String name = "flamecore_" + color.getChar();
        Team team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        team.setColor(color);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setAllowFriendlyFire(true);
        return team;
    }

    private boolean isOnColorTeam(Player player) {
        Scoreboard board = activeBoard(player);
        Team team = board.getEntryTeam(player.getName());
        return team != null && team.getName().startsWith("flamecore_");
    }

    private void removeFromColorTeams(Player player) {
        Scoreboard board = activeBoard(player);
        Team team = board.getEntryTeam(player.getName());
        if (team != null && team.getName().startsWith("flamecore_")) {
            team.removeEntry(player.getName());
        }
    }

    private Scoreboard activeBoard(Player player) {
        Scoreboard board = player.getScoreboard();
        return board == null ? Bukkit.getScoreboardManager().getMainScoreboard() : board;
    }

    private void removeFromAllTeams(Player player) {
        removeFromColorTeams(player);
    }

    private ChatColor nextRainbow() {
        return RAINBOW[rainbowIndex++ % RAINBOW.length];
    }
}