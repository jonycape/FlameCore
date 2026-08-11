package me.jonycape.dev.flamecore.customize;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.config.ConfigManager;
import me.jonycape.dev.flamecore.utils.MessageUtils;
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
    private final Map<UUID, Parrot> parrots = new HashMap<>();
    private final Map<String, ChatColor> colorNames = new HashMap<>();
    private final Map<String, String> rawNames = new HashMap<>();

    private int rainbowIndex;
    private int glowCounter;
    private int nimbCounter;
    private int taskId = -1;

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

        rawNames.put("red", "&cКрасный");
        rawNames.put("orange", "&6Оранжевый");
        rawNames.put("yellow", "&eЖёлтый");
        rawNames.put("green", "&aЗелёный");
        rawNames.put("cyan", "&bГолубой");
        rawNames.put("blue", "&9Синий");
        rawNames.put("purple", "&5Фиолетовый");
        rawNames.put("pink", "&dРозовый");
        rawNames.put("white", "&fБелый");
    }

    public void start() {
        loadColors();
        int tick = ConfigManager.getInstance().getInt("customize.task-interval", 2);
        tick = Math.max(1, Math.min(tick, 20));
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, tick, tick).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (Parrot parrot : parrots.values()) {
            if (parrot != null && parrot.isValid()) {
                parrot.remove();
            }
        }
        parrots.clear();
        for (Customization customization : active.values()) {
            Player player = Bukkit.getPlayer(customization.getPlayerId());
            if (player != null) {
                removeFromAllTeams(player);
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        active.clear();
    }

    public void onQuit(Player player) {
        UUID id = player.getUniqueId();
        removeFromAllTeams(player);
        player.removePotionEffect(PotionEffectType.GLOWING);
        Parrot parrot = parrots.remove(id);
        if (parrot != null && parrot.isValid()) {
            parrot.remove();
        }
        active.remove(id);
    }

    public void menu(Player player) {
        player.sendMessage(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_MENU)));
    }

    public void setColor(Player player, String name) {
        String key = name.toLowerCase();
        Customization state = active.computeIfAbsent(player.getUniqueId(), Customization::new);

        if (key.equals("off")) {
            state.setGlowColor(null);
            state.setRainbow(false);
            removeFromColorTeams(player);
            player.removePotionEffect(PotionEffectType.GLOWING);
            player.sendMessage(MessageUtils.color(
                    Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_COLOR_SET)
                            .replace("%color%", "&7выключено")));
            return;
        }
        if (key.equals("rainbow")) {
            state.setRainbow(true);
            state.setGlowColor(null);
            applyRainbow(player, nextRainbow());
            player.sendMessage(MessageUtils.color(
                    Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_COLOR_SET)
                            .replace("%color%", "&d🌈 Радужный")));
            return;
        }
        ChatColor color = colorNames.get(key);
        if (color == null) {
            player.sendMessage(MessageUtils.color(
                    Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_INVALID_COLOR)
                            .replace("%color%", name)));
            return;
        }
        state.setRainbow(false);
        state.setGlowColor(color);
        applyGlow(player);
        player.sendMessage(MessageUtils.color(
                Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_COLOR_SET)
                        .replace("%color%", rawNames.getOrDefault(key, "&f" + key))));
    }

    public void toggleParrot(Player player) {
        UUID id = player.getUniqueId();
        Customization state = active.computeIfAbsent(id, Customization::new);
        boolean enable = !state.isParrot();
        state.setParrot(enable);
        if (enable) {
            Parrot current = parrots.get(id);
            if (current != null && current.isValid()) {
                player.setShoulderEntityLeft(current);
            } else {
                parrots.put(id, spawnParrot(player));
            }
            player.sendMessage(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_PARROT_ON)));
        } else {
            Parrot parrot = parrots.remove(id);
            clearShoulder(player);
            if (parrot != null && parrot.isValid()) {
                parrot.remove();
            }
            player.sendMessage(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_PARROT_OFF)));
        }
    }

    public void toggleNimb(Player player) {
        UUID id = player.getUniqueId();
        Customization state = active.computeIfAbsent(id, Customization::new);
        boolean enable = !state.isNimb();
        state.setNimb(enable);
        if (enable) {
            player.sendMessage(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_NIMB_ON)));
        } else {
            player.sendMessage(MessageUtils.color(Main.getCfg().getMultiLine(ConfigKeys.MESSAGE_CUSTOMIZE_NIMB_OFF)));
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
        if (active.isEmpty() && parrots.isEmpty()) {
            return;
        }
        int taskTick = Math.max(1, ConfigManager.getInstance().getInt("customize.task-interval", 2));
        taskTick = Math.min(taskTick, 20);
        boolean rainbowTick = every(ConfigKeys.CUSTOMIZE_GLOW_INTERVAL, 10, taskTick, ++glowCounter);
        boolean nimbTick = every(ConfigKeys.CUSTOMIZE_NIMB_INTERVAL, 5, taskTick, ++nimbCounter);
        int nimbCount = Math.max(3, Main.getCfg().getInt(ConfigKeys.CUSTOMIZE_NIMB_PARTICLES, 8));
        double nimbRadius = Math.max(0.2, Main.getCfg().getDouble(ConfigKeys.CUSTOMIZE_NIMB_RADIUS, 0.5));

        for (Customization state : active.values()) {
            Player player = Bukkit.getPlayer(state.getPlayerId());
            if (player == null || !player.isOnline() || player.getWorld() == null) {
                continue;
            }
            if (state.isParrot()) {
                parrotTick(player);
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

    private boolean every(String key, int defaultDiv, int taskTick, int counter) {
        int interval = Math.max(1, Main.getCfg().getInt(key, defaultDiv));
        int every = Math.max(1, (int) Math.round(interval / (double) taskTick));
        return counter % every == 0;
    }

    private void parrotTick(Player player) {
        UUID id = player.getUniqueId();
        org.bukkit.entity.Entity shoulder = player.getShoulderEntityLeft();
        if (shoulder instanceof Parrot) {
            shoulder.setInvulnerable(true);
            return;
        }
        Parrot old = parrots.remove(id);
        if (old != null && old.isValid()) {
            old.remove();
        }
        if (shoulder != null) {
            player.setShoulderEntityLeft(null);
        }
        parrots.put(id, spawnParrot(player));
    }

    private Parrot spawnParrot(Player player) {
        Parrot parrot = (Parrot) player.getWorld().spawnEntity(player.getLocation(), org.bukkit.entity.EntityType.PARROT);
        parrot.setVariant(PARROT_VARIANT);
        parrot.setInvulnerable(true);
        parrot.setSilent(true);
        parrot.setAI(false);
        parrot.setGravity(false);
        player.setShoulderEntityLeft(parrot);
        if (parrot.isValid()) {
            parrot.remove();
        }
        return parrot;
    }

    private void clearShoulder(Player player) {
        org.bukkit.entity.Entity left = player.getShoulderEntityLeft();
        if (left != null) {
            player.setShoulderEntityLeft(null);
        }
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
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        player.setScoreboard(board);
        Team team = getTeam(color);
        if (team != null) {
            removeFromColorTeams(player);
            team.addEntry(player.getName());
        }
    }

    private void applyRainbow(Player player, ChatColor color) {
        if (!player.hasPotionEffect(PotionEffectType.GLOWING)) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.GLOWING, GLOW_DURATION, 0, false, false, false));
        }
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        player.setScoreboard(board);
        Team team = getTeam(color);
        if (team != null) {
            removeFromColorTeams(player);
            team.addEntry(player.getName());
        }
    }

    private Team getTeam(ChatColor color) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
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
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getEntryTeam(player.getName());
        return team != null && team.getName().startsWith("flamecore_");
    }

    private void removeFromColorTeams(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getEntryTeam(player.getName());
        if (team != null && team.getName().startsWith("flamecore_")) {
            team.removeEntry(player.getName());
        }
    }

    private void removeFromAllTeams(Player player) {
        removeFromColorTeams(player);
    }

    private ChatColor nextRainbow() {
        return RAINBOW[rainbowIndex++ % RAINBOW.length];
    }
}