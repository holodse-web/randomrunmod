package com.randomrun.main.data;

import com.randomrun.main.RandomRunMod;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

public class SecurityManager {
    private static final String SALT = "randomrun_integrity_check_2024";
    private static final Set<String> BLOCKED_COMMANDS = new HashSet<>();
    
    static {
        
        BLOCKED_COMMANDS.add("give");
        BLOCKED_COMMANDS.add("tp");
        BLOCKED_COMMANDS.add("teleport");
        BLOCKED_COMMANDS.add("gamemode");
        BLOCKED_COMMANDS.add("gm");
        BLOCKED_COMMANDS.add("time");
        BLOCKED_COMMANDS.add("weather");
        BLOCKED_COMMANDS.add("difficulty");
        BLOCKED_COMMANDS.add("effect");
        BLOCKED_COMMANDS.add("enchant");
        BLOCKED_COMMANDS.add("xp");
        BLOCKED_COMMANDS.add("experience");
        BLOCKED_COMMANDS.add("kill");
        BLOCKED_COMMANDS.add("summon");
        BLOCKED_COMMANDS.add("setblock");
        BLOCKED_COMMANDS.add("fill");
        BLOCKED_COMMANDS.add("clone");
        BLOCKED_COMMANDS.add("clear");
        BLOCKED_COMMANDS.add("replaceitem");
        BLOCKED_COMMANDS.add("item");
        BLOCKED_COMMANDS.add("loot");
        BLOCKED_COMMANDS.add("locate");
        BLOCKED_COMMANDS.add("spreadplayers");
        BLOCKED_COMMANDS.add("spawnpoint");
        BLOCKED_COMMANDS.add("setworldspawn");
        BLOCKED_COMMANDS.add("seed");
    }
    
    public static String generateHash(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String toHash = data + SALT;
            byte[] hash = md.digest(toHash.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            RandomRunMod.LOGGER.error("Не удалось сгенерировать хэш", e);
            return "error";
        }
    }
    
    public static boolean verifyHash(String data, String hash) {
        return generateHash(data).equals(hash);
    }
    
    public static boolean isCommandBlocked(String command) {
        if (command == null || command.isEmpty()) return false;
        
       
        String cmd = command.startsWith("/") ? command.substring(1) : command;
        
       
        String baseCommand = cmd.split(" ")[0].toLowerCase();
        
        return BLOCKED_COMMANDS.contains(baseCommand);
    }
    
    public static String hashResult(String itemId, long time, long timestamp) {
        String data = itemId + ":" + time + ":" + timestamp;
        return generateHash(data).substring(0, 16);
    }
    
    public static boolean verifyResult(String itemId, long time, long timestamp, String hash) {
        return hashResult(itemId, time, timestamp).equals(hash);
    }
}
