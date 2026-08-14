package me.jonycape.dev.flamecore.stream;

import me.jonycape.dev.flamecore.Main;
import me.jonycape.dev.flamecore.config.ConfigKeys;
import me.jonycape.dev.flamecore.utils.MessageProcessor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StreamService {

    private final Main plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public StreamService(Main plugin) {
        this.plugin = plugin;
    }

    public void announce(Player player, String url) {
        if (!validUrl(url)) {
            MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_STREAM_USAGE));
            return;
        }
        long cooldownMs = Math.max(0, Main.getCfg().getInt(ConfigKeys.STREAM_COOLDOWN, 120)) * 1000L;
        if (cooldownMs > 0) {
            Long last = cooldowns.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (last != null && now - last < cooldownMs) {
                long remaining = (cooldownMs - (now - last)) / 1000 + 1;
                MessageProcessor.send(player, Main.getCfg().getStringList(ConfigKeys.MESSAGE_STREAM_COOLDOWN),
                        "time", String.valueOf(remaining));
                return;
            }
            cooldowns.put(player.getUniqueId(), now);
            pruneExpired(now, cooldownMs);
        }
        MessageProcessor.broadcast(Main.getCfg().getStringList(ConfigKeys.MESSAGE_STREAM_BROADCAST),
                "player", player.getName(),
                "url", url);
    }

    public boolean validUrl(String url) {
        return url != null
                && !url.isBlank()
                && url.length() <= 200
                && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private void pruneExpired(long now, long cooldownMs) {
        if (cooldowns.size() < 64) {
            return;
        }
        cooldowns.entrySet().removeIf(e -> now - e.getValue() >= cooldownMs);
    }
}