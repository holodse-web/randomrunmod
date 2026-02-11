package com.randomrun.main.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.randomrun.battle.FirebaseClient;
import com.randomrun.main.RandomRunMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import com.randomrun.main.util.IdCompressor;

public class PlayerProfile {
    
    @SerializedName("total_runs")
    private int totalRuns = 0;
    
    @SerializedName("total_wins")
    private int totalWins = 0;
    
    // Playtime удален, чтобы не создавать лишний трафик
    // @SerializedName("playtime")
    // private long playtime = 0; 
    
    @SerializedName("last_seen")
    private long lastSeen = 0;
    
    @SerializedName("saved_ip")
    private String savedIp = "";
    
    @SerializedName("bests")
    private Map<String, Long> bests = new HashMap<>(); // item_id -> time_ms
    
    @SerializedName("history")
    private java.util.List<RunRecord> history = new java.util.ArrayList<>();

    private transient boolean isOnlineProfile = false;
    private transient boolean isDirty = false;

    private static PlayerProfile currentProfile;
    private static final File CONFIG_DIR = new File(FabricLoader.getInstance().getConfigDir().toFile(), "randomrun");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Executor for async I/O operations to prevent main thread blocking
    private static final java.util.concurrent.ExecutorService IO_EXECUTOR = java.util.concurrent.Executors.newSingleThreadExecutor();

    public static class RunRecord {
        public long timestamp;
        public String itemId;
        public long duration;
        public boolean win;
        public String seed; // New field
        public boolean isOnline; // New field for game mode tracking
        public boolean isHardcore; // New field
        
        public RunRecord(long timestamp, String itemId, long duration, boolean win, String seed, boolean isOnline, boolean isHardcore) {
            this.timestamp = timestamp;
            this.itemId = itemId;
            this.duration = duration;
            this.win = win;
            this.seed = seed;
            this.isOnline = isOnline;
            this.isHardcore = isHardcore;
        }
    }

    public static PlayerProfile get() {
        if (currentProfile == null) {
            currentProfile = new PlayerProfile();
            if (RandomRunMod.getInstance().getConfig() != null) {
                currentProfile.isOnlineProfile = RandomRunMod.getInstance().getConfig().isOnlineMode();
            }
        }
        return currentProfile;
    }

    public static java.util.concurrent.CompletableFuture<Void> load(String playerName) {
        boolean isOnline = RandomRunMod.getInstance().getConfig().isOnlineMode();
        
        // Reset/Init current profile based on mode to prevent data leaks
        currentProfile = new PlayerProfile();
        currentProfile.isOnlineProfile = isOnline;
        currentProfile.isDirty = false;

        if (!isOnline) {
            return java.util.concurrent.CompletableFuture.runAsync(() -> {
                File localFile = new File(CONFIG_DIR, "profile_local_" + playerName + ".json");
                File legacyFile = new File(CONFIG_DIR, "profile_" + playerName + ".json");
                
                // Migration: if local doesn't exist but legacy does, copy legacy to local
                if (!localFile.exists() && legacyFile.exists()) {
                    try {
                        java.nio.file.Files.copy(legacyFile.toPath(), localFile.toPath());
                        RandomRunMod.LOGGER.info("Migrated legacy profile to local profile.");
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.error("Migration failed", e);
                    }
                }

                if (localFile.exists()) {
                    try (Reader reader = new FileReader(localFile)) {
                        PlayerProfile loaded = GSON.fromJson(reader, PlayerProfile.class);
                        if (loaded != null) {
                            currentProfile = loaded;
                        }
                        currentProfile.isOnlineProfile = false;
                        RandomRunMod.LOGGER.info("Загружен локальный профиль: " + playerName);
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.error("Ошибка загрузки локального профиля", e);
                    }
                }
            }, IO_EXECUTOR);
        }

        // Online Mode
        return FirebaseClient.getInstance().get("/profiles/" + playerName).thenAccept(jsonElement -> {
            if (jsonElement != null && jsonElement.isJsonObject()) {
                try {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    PlayerProfile loaded = gson.fromJson(jsonElement, PlayerProfile.class);
                    if (loaded != null) {
                        currentProfile = loaded;
                    }
                    currentProfile.isOnlineProfile = true;
                    currentProfile.isDirty = false;
                    RandomRunMod.LOGGER.info("Профиль игрока загружен: " + playerName);
                    
                    // Cache asynchronously
                    save(true);
                } catch (Exception e) {
                    RandomRunMod.LOGGER.error("Ошибка парсинга профиля", e);
                }
            } else {
                currentProfile = new PlayerProfile();
                currentProfile.isOnlineProfile = true;
                RandomRunMod.LOGGER.info("Создан новый профиль для: " + playerName);
                currentProfile.markDirty();
                save(); // Initial save
            }
        });
    }

    public void markDirty() {
        this.isDirty = true;
        this.lastSeen = System.currentTimeMillis();
    }
    
    public String getSavedIp() {
        return savedIp != null ? savedIp : "";
    }

    public void setSavedIp(String savedIp) {
        this.savedIp = savedIp;
        markDirty();
        save();
    }

    public static void save() {
        save(false);
    }

    public static void save(boolean force) {
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        if (currentProfile == null) return;
        if (!currentProfile.isDirty && !force) return;
        
        // Create a snapshot to save to avoid concurrent modification exceptions if the profile changes while saving
        final PlayerProfile profileToSave = currentProfile; // Reference copy
        // In a real deep copy scenario we'd need more, but here we just want to capture the reference for the async task
        // Note: Fields like Maps/Lists are still mutable references. For perfect thread safety we'd need to clone,
        // but for this mod's scale, just ensuring we don't block main thread is the priority.
        
        IO_EXECUTOR.submit(() -> {
            if (!CONFIG_DIR.exists()) CONFIG_DIR.mkdirs();
            
            String fileName = profileToSave.isOnlineProfile ? 
                "profile_online_cache_" + playerName + ".json" : 
                "profile_local_" + playerName + ".json";
                
            File localFile = new File(CONFIG_DIR, fileName);
            
            try (Writer writer = new FileWriter(localFile)) {
                GSON.toJson(profileToSave, writer);
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Ошибка сохранения профиля (" + fileName + ")", e);
            }

            // Only upload if it is an ONLINE profile AND we are in ONLINE mode
            if (profileToSave.isOnlineProfile && RandomRunMod.getInstance().getConfig().isOnlineMode()) {
                FirebaseClient.getInstance().put("/profiles/" + playerName, profileToSave).thenRun(() -> {
                    // Reset dirty flag only if we are still referring to the same profile object
                    if (currentProfile == profileToSave) {
                        currentProfile.isDirty = false;
                    }
                });
            } else {
                 if (currentProfile == profileToSave) {
                    currentProfile.isDirty = false;
                }
            }
        });
    }

    public void addRun(long durationMs, boolean win, String itemId, long bestTimeMs, String seed, boolean isOnline, boolean isHardcore) {
        // COMPRESS ID before storing
        String compressedId = IdCompressor.compress(itemId);
        
        this.totalRuns++;
        if (win) this.totalWins++;
        // this.playtime += (durationMs / 1000); // Playtime отключен
        
        if (compressedId != null && bestTimeMs > 0) {
            if (!bests.containsKey(compressedId) || bestTimeMs < bests.get(compressedId)) {
                bests.put(compressedId, bestTimeMs);
            }
        }
        
        // Add to history
        if (history == null) history = new java.util.ArrayList<>();
        history.add(new RunRecord(System.currentTimeMillis(), compressedId, durationMs, win, seed, isOnline, isHardcore));
        
        // Limit history size to 50 to prevent overflow
        if (history.size() > 50) {
            history.remove(0);
        }
        
        markDirty();
        save();
    }
    
    public int getTotalRuns() { return totalRuns; }
    public int getTotalWins() { return totalWins; }
    // public long getPlaytime() { return playtime; } // Playtime отключен
    
    public Map<String, Long> getBests() {
        if (bests == null) bests = new HashMap<>();
        return bests;
    }
    
    public java.util.List<RunRecord> getHistory() {
        if (history == null) history = new java.util.ArrayList<>();
        return history;
    }
}
