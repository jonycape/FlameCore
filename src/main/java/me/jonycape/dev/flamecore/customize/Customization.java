package me.jonycape.dev.flamecore.customize;

import org.bukkit.ChatColor;

import java.util.UUID;

public final class Customization {

    private final UUID playerId;
    private ChatColor glowColor;
    private ChatColor currentColor;
    private boolean rainbow;
    private boolean parrot;
    private boolean nimb;

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

    public boolean isParrot() {
        return parrot;
    }

    public void setParrot(boolean parrot) {
        this.parrot = parrot;
    }

    public boolean isNimb() {
        return nimb;
    }

    public void setNimb(boolean nimb) {
        this.nimb = nimb;
    }

    public boolean hasGlow() {
        return rainbow || glowColor != null;
    }
}