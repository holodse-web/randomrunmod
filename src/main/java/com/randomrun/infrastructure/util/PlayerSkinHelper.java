package com.randomrun.infrastructure.util;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;

public class PlayerSkinHelper {
    private static final int MAX_CACHED_PLAYERS = 512;
    private static final Map<UUID, Identifier> skinCache = boundedCache();
    private static final Map<String, UUID> offlineIds = boundedCache();

    private static <K, V> Map<K, V> boundedCache() {
        return new LinkedHashMap<>(32, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX_CACHED_PLAYERS;
            }
        };
    }
    
    public static Identifier getPlayerSkin(String playerName, UUID playerUuid) {
        synchronized (skinCache) {
            return skinCache.computeIfAbsent(playerUuid, id -> DefaultPlayerSkin.get(id).body().texturePath());
        }
    }
    
    public static void clearCache() {
        synchronized (skinCache) { skinCache.clear(); }
        synchronized (offlineIds) { offlineIds.clear(); }
    }
    
    public static UUID getOfflineUUID(String playerName) {
        synchronized (offlineIds) {
            return offlineIds.computeIfAbsent(playerName,
                name -> UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
        }
    }
}
