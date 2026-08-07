package me.jonycape.dev.flamecore.management;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SessionManager {

    private static final Set<String> PANEL_OWNERS = ConcurrentHashMap.newKeySet();

    public static void grant(String name) {
        PANEL_OWNERS.add(name.toLowerCase());
    }

    public static void revoke(String name) {
        PANEL_OWNERS.remove(name.toLowerCase());
    }

    public static boolean hasAccess(String name) {
        return PANEL_OWNERS.contains(name.toLowerCase());
    }

    public static void clear() {
        PANEL_OWNERS.clear();
    }
}