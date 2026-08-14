package me.jonycape.dev.flamecore.database;

public final class PlayerStats {

    private final String player;
    private final String uuid;
    private final long firstJoin;
    private final long lastJoin;
    private final long totalTime;
    private final String lastIp;

    public PlayerStats(String player, String uuid, long firstJoin, long lastJoin, long totalTime, String lastIp) {
        this.player = player;
        this.uuid = uuid;
        this.firstJoin = firstJoin;
        this.lastJoin = lastJoin;
        this.totalTime = totalTime;
        this.lastIp = lastIp;
    }

    public String getPlayer() {
        return player;
    }

    public String getUuid() {
        return uuid;
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public long getLastJoin() {
        return lastJoin;
    }

    public long getTotalTime() {
        return totalTime;
    }

    public String getLastIp() {
        return lastIp;
    }
}