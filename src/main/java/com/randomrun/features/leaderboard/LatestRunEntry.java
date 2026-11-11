package com.randomrun.features.leaderboard;

import com.google.gson.annotations.SerializedName;

public class LatestRunEntry {
    @SerializedName("ti")
    private String targetId;

    @SerializedName("d")
    private long duration;

    @SerializedName("ts")
    private long timestamp;
    
    @SerializedName("tt")
    private String targetType;
    
    
    private transient String playerName;

    public LatestRunEntry() {
    }

    public LatestRunEntry(String targetId, long duration, long timestamp) {
        this.targetId = targetId;
        this.duration = duration;
        this.timestamp = timestamp;
    }
    


    public String getTargetId() { return targetId; }
    public long getDuration() { return duration; }
    public long getTimestamp() { return timestamp; }
    public String getPlayerName() { return playerName; }
    public String getTargetType() { return targetType; }

    
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    
    public String getFormattedDuration() {
        long totalSeconds = duration / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = duration % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }
}
