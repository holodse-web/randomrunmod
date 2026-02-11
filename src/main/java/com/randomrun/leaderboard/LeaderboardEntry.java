package com.randomrun.leaderboard;

import com.google.gson.annotations.SerializedName;

public class LeaderboardEntry {
    @SerializedName("n")
    public String playerName;
    
    @SerializedName("t")
    public long completionTime;
    
    @SerializedName("ts")
    public long timestamp;
    
    @SerializedName("s")
    public String worldSeed;
    
    @SerializedName("l")
    public long timeLimit;
    
    // Удалены поля сложности и версии Minecraft для экономии трафика
    // @SerializedName("d")
    // public String difficulty;
    
    // @SerializedName("tc")
    // public boolean isTimeChallenge;
    
    @SerializedName("mv")
    public String modVersion;
    
    @SerializedName("h")
    public boolean isHardcore;
    
    // @SerializedName("mc")
    // public String minecraftVersion;
    
    @SerializedName("a")
    public int attempts;
    
    // Поля, не сохраняемые в JSON (transient), используются только в рантайме
    public transient String targetId;
    public transient String targetType;

    public LeaderboardEntry() {}

    public LeaderboardEntry(String playerName, String targetId, String targetType, long completionTime,
                            String worldSeed, long timeLimit, String difficulty, boolean isTimeChallenge,
                            String modVersion, String minecraftVersion, boolean isHardcore) {
        this.playerName = playerName;
        this.targetId = targetId;
        this.targetType = targetType;
        this.completionTime = completionTime;
        this.timestamp = System.currentTimeMillis();
        this.worldSeed = worldSeed;
        this.timeLimit = timeLimit;
        // this.difficulty = difficulty; // Удалено
        // this.isTimeChallenge = isTimeChallenge; // Удалено по запросу
        this.modVersion = modVersion;
        // this.minecraftVersion = minecraftVersion; // Удалено
        this.isHardcore = isHardcore;
    }

    public boolean verifyIntegrity() {
        return playerName != null && completionTime > 0;
    }
}
