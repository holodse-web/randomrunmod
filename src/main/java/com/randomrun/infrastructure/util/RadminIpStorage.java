package com.randomrun.infrastructure.util;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class RadminIpStorage {
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("randomrun");
    private static final Path IP_FILE = CONFIG_DIR.resolve("radmin_ip.txt");
    private static final String IP_PATTERN = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    
    private static final Object lock = new Object();
    private static String cachedIp = null;
    
    public static void saveIp(String ip) {
        if (!isValidIp(ip)) {
            CompactLogger.warn("Invalid Radmin IP: {}", ip);
            return;
        }
        
        synchronized (lock) {
            try {
                Files.createDirectories(CONFIG_DIR);
                Files.writeString(IP_FILE, ip);
                cachedIp = ip;
            } catch (IOException e) {
                CompactLogger.error("Failed to save Radmin IP", e);
            }
        }
    }
    
    public static String loadIp() {
        synchronized (lock) {
            if (cachedIp != null) {
                return cachedIp;
            }
            
            try {
                if (Files.exists(IP_FILE)) {
                    String ip = Files.readString(IP_FILE).trim();
                    
                    if (isValidIp(ip)) {
                        cachedIp = ip;
                        return ip;
                    }
                }
            } catch (IOException e) {
                CompactLogger.error("Failed to load Radmin IP", e);
            }
            
            return "";
        }
    }
    
    public static void clearIp() {
        synchronized (lock) {
            try {
                if (Files.exists(IP_FILE)) {
                    Files.delete(IP_FILE);
                    cachedIp = null;
                }
            } catch (IOException e) {
                CompactLogger.error("Failed to clear Radmin IP", e);
            }
        }
    }
    
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        return ip.matches(IP_PATTERN);
    }
    
    public static boolean hasIp() {
        return Files.exists(IP_FILE);
    }
    
    public static void clearCache() {
        synchronized (lock) {
            cachedIp = null;
        }
    }
}
