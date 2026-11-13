package com.randomrun.features.leaderboard;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class LeaderboardEntry implements Comparable<LeaderboardEntry> {
    private static final long MAX_TIME_MS = 100 * 60 * 60 * 1000L;
    
    @SerializedName("t")
    private final long completionTime;
    
    @SerializedName("ts")
    private long timestamp;
    
    @SerializedName("s")
    private final String worldSeed;
    
    @SerializedName("mv")
    private final String modVersion;
    
    @SerializedName("a")
    private final int attempts;
    
    public transient String targetId;
    public transient String playerName;
    
    private transient String cachedFormatted;
    
    public LeaderboardEntry() {
        this("", 0, "", "", 0);
    }
    
    public LeaderboardEntry(String targetId, long completionTime,
                           String worldSeed, String modVersion, int attempts) {
        this.targetId = targetId;
        this.completionTime = completionTime;
        this.timestamp = System.currentTimeMillis();
        this.worldSeed = worldSeed;
        this.modVersion = modVersion;
        this.attempts = attempts;
    }
    
    public boolean verifyIntegrity() {
        return completionTime > 0 && completionTime < MAX_TIME_MS &&
               timestamp > 0 &&
               worldSeed != null &&
               modVersion != null &&
               attempts >= 0;
    }
    
    public String getFormattedTime() {
        if (cachedFormatted == null) {
            long totalSeconds = completionTime / 1000;
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            long millis = completionTime % 1000;
            cachedFormatted = String.format("%02d:%02d.%03d", minutes, seconds, millis);
        }
        return cachedFormatted;
    }
    
    public String getPlayerName() { return playerName; }
    public long getCompletionTime() { return completionTime; }
    public long getTimestamp() { return timestamp; }
    public String getWorldSeed() { return worldSeed; }
    public String getModVersion() { return modVersion; }
    public int getAttempts() { return attempts; }
    public String getTargetId() { return targetId; }
    
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    @Override
    public int compareTo(LeaderboardEntry other) {
        return Long.compare(this.completionTime, other.completionTime);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LeaderboardEntry that = (LeaderboardEntry) o;
        return completionTime == that.completionTime &&
               timestamp == that.timestamp &&
               attempts == that.attempts &&
               Objects.equals(worldSeed, that.worldSeed) &&
               Objects.equals(modVersion, that.modVersion);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(completionTime, timestamp, worldSeed, 
                           modVersion, attempts);
    }
    
    public static class Builder {
        private String targetId;
        private long completionTime;
        private String worldSeed;
        private String modVersion;
        private int attempts;
        
        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }
        
        public Builder completionTime(long completionTime) {
            this.completionTime = completionTime;
            return this;
        }
        
        public Builder worldSeed(String worldSeed) {
            this.worldSeed = worldSeed;
            return this;
        }
        
        public Builder modVersion(String modVersion) {
            this.modVersion = modVersion;
            return this;
        }
        
        public Builder attempts(int attempts) {
            this.attempts = attempts;
            return this;
        }
        
        public LeaderboardEntry build() {
            return new LeaderboardEntry(targetId, completionTime,
                                       worldSeed, modVersion, attempts);
        }
    }

    public static class LatestRunEntry {
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
}
