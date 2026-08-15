package me.jonycape.dev.flamecore.customize;

import org.bukkit.ChatColor;

import java.util.UUID;

public final class Customization {

    private final UUID playerId;
    private ChatColor glowColor;
    private ChatColor currentColor;
    private boolean rainbow;

    public Customization(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ChatColor getGlowColor() {
        return glowColor;
    }

    public void setGlowColor(ChatColor glowColor) {
        this.glowColor = glowColor;
    }

    public ChatColor getCurrentColor() {
        return currentColor;
    }

    public void setCurrentColor(ChatColor currentColor) {
        this.currentColor = currentColor;
    }

    public boolean isRainbow() {
        return rainbow;
    }

    public void setRainbow(boolean rainbow) {
        this.rainbow = rainbow;
    }

    public boolean hasGlow() {
        return rainbow || glowColor != null;
    }
}