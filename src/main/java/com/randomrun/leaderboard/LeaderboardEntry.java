package com.randomrun.leaderboard;

import com.google.gson.annotations.SerializedName;

public class LeaderboardEntry {
    
    @SerializedName("playerName")
    public String playerName;
    
    @SerializedName("targetId")
    public String targetId;
    
    @SerializedName("targetType")
    public String targetType; // "ITEM" or "ADVANCEMENT"
    
    @SerializedName("completionTime")
    public long completionTime;
    
    @SerializedName("worldSeed")
    public String worldSeed;
    
    @SerializedName("timestamp")
    public long timestamp;
    
    @SerializedName("modVersion")
    public String modVersion;
    
    @SerializedName("minecraftVersion")
    public String minecraftVersion;
    
    @SerializedName("timeLimit")
    public long timeLimit;
    
    @SerializedName("difficulty")
    public String difficulty;
    
    @SerializedName("isTimeChallenge")
    public boolean isTimeChallenge;
    
    @SerializedName("hash")
    public String hash;
    
    public LeaderboardEntry() {
    }
    
    public LeaderboardEntry(String playerName, String targetId, String targetType, 
                           long completionTime, String worldSeed, long timeLimit, 
                           String difficulty, boolean isTimeChallenge, String modVersion, String minecraftVersion) {
        this.playerName = playerName;
        this.targetId = targetId;
        this.targetType = targetType;
        this.completionTime = completionTime;
        this.worldSeed = worldSeed != null ? worldSeed : "unknown";
        this.timestamp = System.currentTimeMillis();
        this.modVersion = modVersion;
        this.minecraftVersion = minecraftVersion;
        this.timeLimit = timeLimit;
        this.difficulty = difficulty;
        this.isTimeChallenge = isTimeChallenge;
        this.hash = generateHash();
    }
    
    private String generateHash() {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            String data = playerName + targetId + completionTime + worldSeed + timestamp + "rrm_secret_2026";
            byte[] hash = md.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 32);
        } catch (Exception e) {
            return "invalid";
        }
    }
    
    public boolean verifyIntegrity() {
        return hash != null && hash.equals(generateHash());
    }
}
