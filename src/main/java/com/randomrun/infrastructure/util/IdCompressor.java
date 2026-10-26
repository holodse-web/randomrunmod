package com.randomrun.infrastructure.util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

public class IdCompressor {
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final int PREFIX_LENGTH = 10;
    private static final int MAX_CACHE_SIZE = 1000;
    
    private static final Set<String> COMMON_NAMESPACES = Set.of("minecraft", "forge", "fabric");
    
    private static final Map<String, String> compressCache = new ConcurrentHashMap<>();
    private static final Map<String, String> decompressCache = new ConcurrentHashMap<>();
    private static final AtomicLong bytesSaved = new AtomicLong(0);

    public static String compress(String fullId) {
        if (fullId == null) return null;
        
        String cached = compressCache.get(fullId);
        if (cached != null) return cached;
        
        String result;
        if (fullId.startsWith(MINECRAFT_PREFIX)) {
            result = fullId.substring(PREFIX_LENGTH);
            bytesSaved.addAndGet(PREFIX_LENGTH);
        } else {
            result = fullId;
        }
        
        addToCache(compressCache, fullId, result);
        return result;
    }

    public static String decompress(String compressedId) {
        if (compressedId == null) return null;
        
        String cached = decompressCache.get(compressedId);
        if (cached != null) return cached;
        
        String result;
        if (!compressedId.contains(":")) {
            result = new StringBuilder(PREFIX_LENGTH + compressedId.length())
                .append(MINECRAFT_PREFIX)
                .append(compressedId)
                .toString();
        } else {
            result = compressedId;
        }
        
        addToCache(decompressCache, compressedId, result);
        return result;
    }
    
    public static List<String> compressBatch(List<String> fullIds) {
        if (fullIds == null) return null;
        return fullIds.stream()
            .map(IdCompressor::compress)
            .collect(Collectors.toList());
    }
    
    public static List<String> decompressBatch(List<String> compressedIds) {
        if (compressedIds == null) return null;
        return compressedIds.stream()
            .map(IdCompressor::decompress)
            .collect(Collectors.toList());
    }
    
    public static String compressAny(String fullId) {
        if (fullId == null) return null;
        
        for (String ns : COMMON_NAMESPACES) {
            String prefix = ns + ":";
            if (fullId.startsWith(prefix)) {
                bytesSaved.addAndGet(prefix.length());
                return fullId.substring(prefix.length());
            }
        }
        return fullId;
    }
    
    public static boolean isValidId(String id) {
        if (id == null || id.isEmpty()) return false;
        return id.matches("^[a-z0-9_]+:[a-z0-9_/]+$") || id.matches("^[a-z0-9_/]+$");
    }
    
    public static boolean isCompressed(String id) {
        return id != null && !id.contains(":");
    }
    
    public static boolean needsCompression(String id) {
        return id != null && id.startsWith(MINECRAFT_PREFIX);
    }
    
    public static String getNamespace(String id) {
        if (id == null) return null;
        int colonIndex = id.indexOf(':');
        return colonIndex > 0 ? id.substring(0, colonIndex) : "minecraft";
    }
    
    public static String getPath(String id) {
        if (id == null) return null;
        int colonIndex = id.indexOf(':');
        return colonIndex > 0 ? id.substring(colonIndex + 1) : id;
    }
    
    public static long getBytesSaved() {
        return bytesSaved.get();
    }
    
    public static void resetStats() {
        bytesSaved.set(0);
    }
    
    public static void clearCache() {
        compressCache.clear();
        decompressCache.clear();
    }
    
    public static int getCacheSize() {
        return compressCache.size() + decompressCache.size();
    }
    
    private static void addToCache(Map<String, String> cache, String key, String value) {
        if (cache.size() >= MAX_CACHE_SIZE) {
            cache.clear();
        }
        cache.put(key, value);
    }
}
