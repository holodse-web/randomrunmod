package com.randomrun.core.history;

import com.google.gson.annotations.SerializedName;
import java.util.Objects;

public class Run {
    @SerializedName("target_id")
    private String targetId;
    
    @SerializedName("timestamp")
    private long timestamp;
    
    @SerializedName("duration")
    private long duration;
    
    @SerializedName("result")
    private RunResult result;
    
    @SerializedName("world_seed")
    private String worldSeed;
    
    @SerializedName("custom_seed")
    private String customSeed;
    
    @SerializedName("is_battle")
    private boolean isBattle;
    
    @SerializedName("battle_room_id")
    private String battleRoomId;
    
    @SerializedName("is_hardcore")
    private boolean isHardcore;
    
    @SerializedName("is_random_drop")
    private boolean isRandomDrop;

    @SerializedName("is_upgrader")
    private boolean isUpgrader;
    
    @SerializedName("mod_version")
    private String modVersion;
    
    @SerializedName("attempt_number")
    private int attemptNumber;
    
    @SerializedName("target_type")
    private com.randomrun.core.session.TargetType targetType;
    
    @SerializedName("mob_variant")
    private String mobVariant;
    
    public Run() {}
    
    private Run(String targetId, long timestamp, long duration,
                    RunResult result, String worldSeed, String customSeed,
                    boolean isBattle, String battleRoomId, boolean isHardcore, boolean isRandomDrop, boolean isUpgrader,
                    String modVersion, int attemptNumber,
                    com.randomrun.core.session.TargetType targetType, String mobVariant) {
        this.targetId = targetId;
        this.timestamp = timestamp;
        this.duration = duration;
        this.result = result;
        this.worldSeed = worldSeed;
        this.customSeed = customSeed;
        this.isBattle = isBattle;
        this.battleRoomId = battleRoomId;
        this.isHardcore = isHardcore;
        this.isRandomDrop = isRandomDrop;
        this.isUpgrader = isUpgrader;
        this.modVersion = modVersion;
        this.attemptNumber = attemptNumber;
        this.targetType = targetType;
        this.mobVariant = mobVariant;
    }
    
    public static class Builder {
        private String targetId;
        private long timestamp = System.currentTimeMillis();
        private long duration;
        private RunResult result;
        private String worldSeed;
        private String customSeed;
        private boolean isBattle = false;
        private String battleRoomId;
        private boolean isHardcore = false;
        private boolean isRandomDrop = false;
        private boolean isUpgrader = false;
        private String modVersion;
        private int attemptNumber = 1;
        private com.randomrun.core.session.TargetType targetType = com.randomrun.core.session.TargetType.ITEM;
        private String mobVariant;
        
        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            if (timestamp < 0) {
                throw new IllegalArgumentException("timestamp не может быть отрицательным");
            }
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder duration(long duration) {
            if (duration < 0) {
                throw new IllegalArgumentException("duration не может быть отрицательным");
            }
            this.duration = duration;
            return this;
        }
        
        public Builder result(RunResult result) {
            this.result = result;
            return this;
        }
        
        public Builder worldSeed(String worldSeed) {
            this.worldSeed = worldSeed;
            return this;
        }
        
        public Builder customSeed(String customSeed) {
            this.customSeed = customSeed;
            return this;
        }
        
        public Builder isBattle(boolean isBattle) {
            this.isBattle = isBattle;
            return this;
        }
        
        public Builder battleRoomId(String battleRoomId) {
            this.battleRoomId = battleRoomId;
            return this;
        }
        
        public Builder isHardcore(boolean isHardcore) {
            this.isHardcore = isHardcore;
            return this;
        }
        
        public Builder isRandomDrop(boolean isRandomDrop) {
            this.isRandomDrop = isRandomDrop;
            return this;
        }

        public Builder isUpgrader(boolean isUpgrader) {
            this.isUpgrader = isUpgrader;
            return this;
        }
        
        public Builder modVersion(String modVersion) {
            this.modVersion = modVersion;
            return this;
        }
        
        public Builder attemptNumber(int attemptNumber) {
            if (attemptNumber < 1) {
                throw new IllegalArgumentException("attemptNumber должен быть >= 1");
            }
            this.attemptNumber = attemptNumber;
            return this;
        }
        
        public Builder targetType(com.randomrun.core.session.TargetType targetType) {
            this.targetType = targetType;
            return this;
        }
        
        public Builder mobVariant(String mobVariant) {
            this.mobVariant = mobVariant;
            return this;
        }
        
        public Run build() {
            if (targetId == null || targetId.isEmpty()) {
                throw new IllegalStateException("targetId обязателен");
            }
            if (result == null) {
                throw new IllegalStateException("result обязателен");
            }
            
            return new Run(targetId, timestamp, duration,
                result, worldSeed, customSeed, isBattle, battleRoomId,
                isHardcore, isRandomDrop, isUpgrader, modVersion, attemptNumber, targetType, mobVariant);
        }
    }
    
    public boolean isVictory() {
        return result == RunResult.VICTORY;
    }
    
    public boolean isEligibleForLeaderboard() {
        return result == RunResult.VICTORY 
            && customSeed == null
            && isHardcore
            && !isRandomDrop
            && !isUpgrader;
    }
    
    public String getTargetId() { return targetId; }
    public long getTimestamp() { return timestamp; }
    public long getDuration() { return duration; }
    public RunResult getResult() { return result; }
    public String getWorldSeed() { return worldSeed; }
    public String getCustomSeed() { return customSeed; }
    public boolean isBattle() { return isBattle; }
    public String getBattleRoomId() { return battleRoomId; }
    public boolean isHardcore() { return isHardcore; }
    public boolean isRandomDrop() { return isRandomDrop; }
    public boolean isUpgrader() { return isUpgrader; }
    public String getModVersion() { return modVersion; }
    public int getAttemptNumber() { return attemptNumber; }
    public com.randomrun.core.session.TargetType getTargetType() { return targetType; }
    public String getMobVariant() { return mobVariant; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Run that = (Run) o;
        return timestamp == that.timestamp &&
               duration == that.duration &&
               Objects.equals(targetId, that.targetId) &&
               result == that.result;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(targetId, timestamp, duration, result);
    }
    
    @Override
    public String toString() {
        return String.format("Run{target=%s, result=%s, duration=%dms, timestamp=%d}", 
            targetId, result, duration, timestamp);
    }
}
