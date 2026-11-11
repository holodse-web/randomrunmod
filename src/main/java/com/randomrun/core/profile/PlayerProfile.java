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
        this.info.uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        this.info.createdAt = System.currentTimeMillis();
        this.info.lastSeen = System.currentTimeMillis();
        this.info.modVersion = RandomRunMod.MOD_VERSION;

        this.security.trustScore = 100;
        this.security.suspiciousFlags = null;
        this.security.lastCheatDetection = null;
        this.security.banned = false;
        this.security.banReason = null;

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

        if (com.randomrun.network.BackendSession.owns(info.playerName)) {
            String report = penalty == PENALTY_CREATIVE ? "CREATIVE" : penalty == PENALTY_CHEAT ? "CHEAT_MOD" : "UNTRUSTED_RUN";
            HttpClient.getInstance().post("/security/report", java.util.Map.of("reason", report));
        }
    }

    public void applyTrustBonus(int bonus, String reason) {

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

    public static void clear() { instance = null; currentPlayerName = null; }

    public static PlayerProfile get() {
        if (instance == null) {
            instance = new PlayerProfile(currentPlayerName != null ? currentPlayerName : "Player");
        }
        return instance;
    }

    public static CompletableFuture<Boolean> load(String playerName) {
        if (playerName == null || playerName.isBlank()) return CompletableFuture.completedFuture(false);
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            currentPlayerName = playerName;
            instance = new PlayerProfile(playerName);
            instance.readOnly = true;
            return CompletableFuture.completedFuture(true);
        }
        if (!com.randomrun.network.BackendSession.owns(playerName)) return CompletableFuture.completedFuture(false);
        var session = com.randomrun.network.BackendSession.current();
        return HttpClient.getInstance().getNoCache("/profiles/" + session.name() + "/profile").thenApply(result -> {
            PlayerProfile loaded = new com.google.gson.Gson().fromJson(result, PlayerProfile.class);
            loaded.info.playerName = session.name();
            loaded.readOnly = false;
            synchronized (com.randomrun.network.BackendSession.class) {
                if (session != com.randomrun.network.BackendSession.current()) return false;
                currentPlayerName = session.name();
                instance = loaded;
            }
            return true;
        }).exceptionally(error -> false);
    }

    public static CompletableFuture<Void> save() {
        PlayerProfile profile = instance;
        if (profile == null || profile.readOnly || !RandomRunMod.getInstance().getConfig().isOnlineMode()
                || !com.randomrun.network.BackendSession.owns(profile.info.playerName)) return CompletableFuture.completedFuture(null);
        return HttpClient.getInstance().put("/profiles/" + profile.info.playerName + "/profile",
            java.util.Map.of("stats", java.util.Map.of("fts", profile.stats.firstTrustScoreShown)))
            .thenAccept(saved -> { if (instance == profile && saved) profile.dirty.set(false); });
    }

    @Override
    public String toString() {
        return String.format("PlayerProfile{name=%s, trustScore=%d, totalRuns=%d}",
            info.playerName, security.trustScore, stats.totalRuns);
    }
}
