package com.randomrun.core.profile;

import com.google.gson.annotations.SerializedName;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.network.HttpClient;
import com.google.gson.JsonElement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerProfile {
    private static final String PROFILE_LOG_TAG = "[Profile]";
    private static final int LOAD_TIMEOUT_SECONDS = 15;

    public static final String TRUST_LOG_TAG = "[TrustScore]";
    public static final int PENALTY_LAN = -3;
    public static final int PENALTY_CREATIVE = -5;
    public static final int PENALTY_CHEAT = -8;
    public static final int PENALTY_SUSPICIOUS = -1;
    public static final int BONUS_CLEAN_RUN = 2;
    public static final int BONUS_VICTORY = 3;
    public static final int BONUS_BATTLE_VICTORY = 5;
    public static final int LEADERBOARD_THRESHOLD = 30;
    public static final long MIN_SURVIVAL_TIME_MS = 3000;

    private static PlayerProfile instance;
    private static String currentPlayerName;

    private transient final AtomicBoolean dirty = new AtomicBoolean(false);
    private transient boolean readOnly = false;

    public static class Info {
        public transient String playerName;

        @SerializedName("u")
        public String uuid;

        @SerializedName("ca")
        public long createdAt;

        @SerializedName("ls")
        public long lastSeen;

        @SerializedName("mv")
        public String modVersion;
    }

    public static class Security {
        @SerializedName("hw")
        public String hwidHash;

        @SerializedName("sf")
        public Integer suspiciousFlags;

        @SerializedName("ts")
        public int trustScore;

        @SerializedName("lcd")
        public Long lastCheatDetection;

        @SerializedName("b")
        public Boolean banned;

        @SerializedName("br")
        public String banReason;

        @SerializedName("pwd")
        public String passwordHash;

        @SerializedName("npc")
        public Boolean needsPasswordChange;
    }

    public static class Stats {
        @SerializedName("tr")
        public int totalRuns;

        @SerializedName("fts")
        public boolean firstTrustScoreShown;
    }

    @SerializedName("info")
    private Info info;

    @SerializedName("sec")
    private Security security;

    @SerializedName("stats")
    private Stats stats;

    public PlayerProfile() {}

    public PlayerProfile(String playerName) {
        this.info = new Info();
        this.security = new Security();
        this.stats = new Stats();

        this.info.playerName = playerName;
        this.info.uuid = com.randomrun.infrastructure.auth.PlayerUuidManager.getInstance().getPlayerUuid();
        this.info.createdAt = System.currentTimeMillis();
        this.info.lastSeen = System.currentTimeMillis();
        this.info.modVersion = RandomRunMod.MOD_VERSION;

        this.security.hwidHash = com.randomrun.infrastructure.security.HardwareIdGenerator.generateHWID();
        this.security.trustScore = 100;
        this.security.suspiciousFlags = null;
        this.security.lastCheatDetection = null;
        this.security.banned = false;
        this.security.banReason = null;
        this.security.passwordHash = null;
        this.security.needsPasswordChange = false;

        this.stats.totalRuns = 0;
        this.stats.firstTrustScoreShown = false;
    }

    public void updateLastSeen() {
        this.info.lastSeen = System.currentTimeMillis();
        markDirty();
    }

    private void markDirty() {
        dirty.set(true);
    }

    public boolean needsSave() {
        return dirty.get();
    }

    public String getPlayerName() { return info.playerName; }
    public String getUuid() { return info.uuid; }
    public long getCreatedAt() { return info.createdAt; }
    public String getModVersion() { return info.modVersion; }

    public String getHwidHash() { return security.hwidHash; }
    public void setHwidHash(String hwid) { security.hwidHash = hwid; dirty.set(true); }
    public int getSuspiciousFlags() {
        return security.suspiciousFlags != null ? security.suspiciousFlags : 0;
    }
    public int getTrustScore() { return security.trustScore; }
    public long getLastCheatDetection() {
        return security.lastCheatDetection != null ? security.lastCheatDetection : 0;
    }
    public boolean isBanned() {
        return security.banned != null && security.banned;
    }
    public String getBanReason() { return security.banReason; }
    public String getPasswordHash() { return security.passwordHash; }
    public boolean needsPasswordChange() {
        return security.needsPasswordChange != null && security.needsPasswordChange;
    }

    public int getTotalRuns() { return stats.totalRuns; }
    public boolean isFirstTrustScoreShown() { return stats.firstTrustScoreShown; }
    public void setPlayerName(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            throw new IllegalArgumentException("playerName не может быть пустым");
        }
        this.info.playerName = playerName;
        markDirty();
    }

    public void setUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            throw new IllegalArgumentException("uuid не может быть пустым");
        }
        this.info.uuid = uuid;
        markDirty();
    }

    public void setModVersion(String modVersion) {
        this.info.modVersion = modVersion;
        markDirty();
    }

    public void setSuspiciousFlags(int suspiciousFlags) {
        if (suspiciousFlags < 0) {
            throw new IllegalArgumentException("suspiciousFlags не может быть отрицательным");
        }
        this.security.suspiciousFlags = suspiciousFlags > 0 ? suspiciousFlags : null;
        markDirty();
    }

    public void setTrustScore(int trustScore) {
        this.security.trustScore = Math.max(0, Math.min(200, trustScore));
        markDirty();
    }

    public void setLastCheatDetection(long lastCheatDetection) {
        if (lastCheatDetection < 0) {
            throw new IllegalArgumentException("lastCheatDetection не может быть отрицательным");
        }
        this.security.lastCheatDetection = lastCheatDetection > 0 ? lastCheatDetection : null;
        markDirty();
    }

    public void setBanned(boolean banned) {
        this.security.banned = banned;
        markDirty();
    }

    public void setBanReason(String banReason) {
        this.security.banReason = banReason;
        markDirty();
    }

    public void setPasswordHash(String passwordHash) {
        this.security.passwordHash = passwordHash;
        markDirty();
    }

    public void setNeedsPasswordChange(boolean needsChange) {
        this.security.needsPasswordChange = needsChange;
        markDirty();
    }

    public void setTotalRuns(int totalRuns) {
        if (totalRuns < 0) {
            throw new IllegalArgumentException("totalRuns не может быть отрицательным");
        }
        this.stats.totalRuns = totalRuns;
        markDirty();
    }

    public void setFirstTrustScoreShown(boolean shown) {
        this.stats.firstTrustScoreShown = shown;
        markDirty();
    }

    public void incrementTotalRuns() {
        this.stats.totalRuns++;
        markDirty();
    }
    public void applyTrustPenalty(int penalty, String reason) {
        int oldScore = security.trustScore;
        security.trustScore = Math.max(0, security.trustScore + penalty);
        security.lastCheatDetection = System.currentTimeMillis();

        if (security.suspiciousFlags == null) {
            security.suspiciousFlags = 1;
        } else {
            security.suspiciousFlags++;
        }

        markDirty();

        com.randomrun.infrastructure.RandomRunMod.LOGGER.warn(
            "{} {} | Штраф: {} | {} -> {}",
            TRUST_LOG_TAG, reason, penalty, oldScore, security.trustScore
        );

        if (oldScore >= PlayerProfile.LEADERBOARD_THRESHOLD && security.trustScore < PlayerProfile.LEADERBOARD_THRESHOLD) {
            com.randomrun.infrastructure.RandomRunMod.LOGGER.warn(
                "{} Trust Score упал ниже {} - удаление всех рекордов из лидерборда",
                TRUST_LOG_TAG, PlayerProfile.LEADERBOARD_THRESHOLD
            );

            String playerName = info.playerName;
            if (playerName != null && !playerName.isEmpty()) {
                com.randomrun.features.leaderboard.LeaderboardService.getInstance()
                    .deleteAllPlayerRecords(playerName);
            }
        }
    }

    public void applyTrustBonus(int bonus, String reason) {
        int oldScore = security.trustScore;
        security.trustScore = Math.min(200, security.trustScore + bonus);
        markDirty();
    }

    public boolean canSubmitToLeaderboard() {
        return security.trustScore >= PlayerProfile.LEADERBOARD_THRESHOLD;
    }

    public void addSuspiciousFlag() {
        if (this.security.suspiciousFlags == null) {
            this.security.suspiciousFlags = 1;
        } else {
            this.security.suspiciousFlags++;
        }
        this.security.trustScore = Math.max(0, this.security.trustScore - 1);
        markDirty();
    }

    public static PlayerProfile get() {
        if (instance == null) {
            instance = new PlayerProfile(currentPlayerName != null ? currentPlayerName : "Player");
        }
        return instance;
    }

    public static CompletableFuture<Boolean> load(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        currentPlayerName = playerName;

        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            if (instance == null) {
                instance = new PlayerProfile(playerName);
            }
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                CompletableFuture<JsonElement> loadFuture = HttpClient.getInstance()
                    .get("/profiles/" + playerName + "/profile");
                JsonElement result = loadFuture.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (result == null || !result.isJsonObject()) {
                    loadFuture = HttpClient.getInstance().get("/profiles/" + playerName);
                    result = loadFuture.get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                }

                if (result != null && result.isJsonObject()) {
                    PlayerProfile loadedProfile = new com.google.gson.Gson().fromJson(result, PlayerProfile.class);

                    if (loadedProfile != null && loadedProfile.info != null && loadedProfile.security != null && loadedProfile.stats != null) {
                        int oldTrustScore = (instance != null && instance.security != null) ? instance.security.trustScore : 0;
                        int newTrustScore = loadedProfile.security.trustScore;

                        if (oldTrustScore > newTrustScore && oldTrustScore > 0) {
                            CompactLogger.warn("{} ЗАЩИТА: Trust Score на сервере ({}) меньше локального ({}), сохраняем локальный",
                                PROFILE_LOG_TAG, newTrustScore, oldTrustScore);
                            loadedProfile.security.trustScore = oldTrustScore;
                            loadedProfile.dirty.set(true);
                        }

                        instance = loadedProfile;
                        instance.info.playerName = playerName;
                        instance.readOnly = false;

                        instance.info.lastSeen = System.currentTimeMillis();
                        String oldVersion = instance.info.modVersion;
                        boolean versionChanged = !RandomRunMod.MOD_VERSION.equals(oldVersion);

                        if (versionChanged) {
                            instance.info.modVersion = RandomRunMod.MOD_VERSION;
                        }

                        instance.dirty.set(true);

                        com.randomrun.infrastructure.security.ProfileAntiCheat.checkProfile(instance);

                        if (instance.needsSave()) {
                            save().thenRun(() -> {
                            });
                        }
                    } else {
                        CompactLogger.error("{} Загруженный профиль поврежден (loadedProfile={}, info={}, security={}, stats={})",
                            PROFILE_LOG_TAG,
                            loadedProfile != null,
                            loadedProfile != null && loadedProfile.info != null,
                            loadedProfile != null && loadedProfile.security != null,
                            loadedProfile != null && loadedProfile.stats != null);

                        if (instance == null) {
                            instance = new PlayerProfile(playerName);
                            instance.readOnly = false;
                            save();
                        } else {
                            CompactLogger.warn("{} Используем существующий профиль (TS={}, Runs={})",
                                PROFILE_LOG_TAG, instance.security.trustScore, instance.stats.totalRuns);
                        }
                    }
                } else {
                    if (instance == null) {
                        instance = new PlayerProfile(playerName);
                        instance.readOnly = false;
                        save();
                    } else {
                        CompactLogger.warn("{} Профиль не найден на сервере, сохраняем существующий (TS={}, Runs={})",
                            PROFILE_LOG_TAG, instance.security.trustScore, instance.stats.totalRuns);
                        instance.readOnly = false;
                        save();
                    }
                }

                return true;
            } catch (Exception e) {
                CompactLogger.firebaseError("{} Ошибка загрузки: {}", PROFILE_LOG_TAG, e.getMessage());

                if (instance == null) {
                    instance = new PlayerProfile(playerName);
                    instance.readOnly = true;
                    CompactLogger.warn("{} Создан локальный профиль (read-only)", PROFILE_LOG_TAG);
                } else {
                    instance.readOnly = true;
                    CompactLogger.warn("{} Используем существующий профиль (read-only, TS={}, Runs={})",
                        PROFILE_LOG_TAG, instance.security.trustScore, instance.stats.totalRuns);
                }

                return false;
            }
        });
    }

    public static CompletableFuture<Void> save() {
        if (instance == null || !RandomRunMod.getInstance().getConfig().isOnlineMode() || instance.readOnly) {
            return CompletableFuture.completedFuture(null);
        }

        if (!instance.needsSave()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                instance.info.lastSeen = System.currentTimeMillis();

                boolean success = HttpClient.getInstance()
                    .put("/profiles/" + instance.info.playerName + "/profile", instance)
                    .get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                if (success) {
                    instance.dirty.set(false);
                } else {
                    CompactLogger.firebaseError("{} Ошибка сохранения", PROFILE_LOG_TAG);
                }
            } catch (Exception e) {
                CompactLogger.firebaseError("{} Ошибка: {}", PROFILE_LOG_TAG, e.getMessage());
            }
        });
    }

    @Override
    public String toString() {
        return String.format("PlayerProfile{name=%s, trustScore=%d, totalRuns=%d}",
            info.playerName, security.trustScore, stats.totalRuns);
    }
}
