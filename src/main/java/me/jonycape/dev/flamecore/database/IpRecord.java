package me.jonycape.dev.flamecore.database;

public final class IpRecord {

    private final String ip;
    private final long firstSeen;

    public IpRecord(String ip, long firstSeen) {
        this.ip = ip;
        this.firstSeen = firstSeen;
    }

    public String getIp() {
        return ip;
    }

    public long getFirstSeen() {
        return firstSeen;
    }
}