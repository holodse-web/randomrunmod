package com.randomrun.leaderboard;

import com.randomrun.battle.FirebaseClient;
import com.randomrun.main.RandomRunMod;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LeaderboardManager {
    
    private static LeaderboardManager instance;
    private final FirebaseClient firebaseClient;
    
    private LeaderboardManager() {
        this.firebaseClient = FirebaseClient.getInstance();
    }
    
    public static LeaderboardManager getInstance() {
        if (instance == null) {
            instance = new LeaderboardManager();
        }
        return instance;
    }
    
    public CompletableFuture<Boolean> submitRecord(LeaderboardEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!entry.verifyIntegrity()) {
                    RandomRunMod.LOGGER.error("Failed to submit record: integrity check failed");
                    return false;
                }
                
                RandomRunMod.LOGGER.info("═══════════════════════════════════");
                RandomRunMod.LOGGER.info("📊 Submitting record to Firebase:");
                RandomRunMod.LOGGER.info("  - Player: " + entry.playerName);
                RandomRunMod.LOGGER.info("  - Target: " + entry.targetId);
                RandomRunMod.LOGGER.info("  - Type: " + entry.targetType);
                RandomRunMod.LOGGER.info("  - Time: " + entry.completionTime + "ms");
                
                // Check for existing records from this player for this target
                String playerBasePath = "/leaderboard/players/" + sanitizeId(entry.playerName);
                com.google.gson.JsonObject existingRecords = firebaseClient.get(playerBasePath).join();
                
                String recordToDelete = null;
                long bestExistingTime = Long.MAX_VALUE;
                
                if (existingRecords != null) {
                    com.google.gson.Gson gson = new com.google.gson.Gson();
                    for (String key : existingRecords.keySet()) {
                        try {
                            LeaderboardEntry existing = gson.fromJson(existingRecords.get(key), LeaderboardEntry.class);
                            if (existing != null && existing.targetId.equals(entry.targetId)) {
                                if (existing.completionTime < bestExistingTime) {
                                    bestExistingTime = existing.completionTime;
                                    recordToDelete = key;
                                }
                            }
                        } catch (Exception e) {
                            RandomRunMod.LOGGER.warn("Failed to parse existing record: " + key, e);
                        }
                    }
                }
                
                // If new time is worse than existing, don't save
                if (bestExistingTime < entry.completionTime) {
                    RandomRunMod.LOGGER.info("⚠️ New time (" + entry.completionTime + "ms) is worse than existing (" + bestExistingTime + "ms). Not saving.");
                    return false;
                }
                
                String uniqueId = UUID.randomUUID().toString();
                String recordPath = "/leaderboard/records/" + entry.targetType.toLowerCase() + "/" + 
                                   sanitizeId(entry.targetId) + "/" + uniqueId;
                String playerPath = "/leaderboard/players/" + sanitizeId(entry.playerName) + "/" + uniqueId;
                
                RandomRunMod.LOGGER.info("  - Record Path: " + recordPath);
                RandomRunMod.LOGGER.info("  - Player Path: " + playerPath);
                RandomRunMod.LOGGER.info("═══════════════════════════════════");
                
                // Delete old record if exists
                if (recordToDelete != null) {
                    String oldPlayerPath = playerBasePath + "/" + recordToDelete;
                    String oldRecordPath = "/leaderboard/records/" + entry.targetType.toLowerCase() + "/" + 
                                          sanitizeId(entry.targetId) + "/" + recordToDelete;
                    
                    RandomRunMod.LOGGER.info("🗑️ Deleting old record: " + recordToDelete);
                    firebaseClient.delete(oldPlayerPath).join();
                    firebaseClient.delete(oldRecordPath).join();
                }
                
                // Save to records path (organized by target)
                boolean recordSuccess = firebaseClient.put(recordPath, entry).join();
                
                // Save to player path (organized by player)
                boolean playerSuccess = firebaseClient.put(playerPath, entry).join();
                
                boolean success = recordSuccess && playerSuccess;
                
                if (success) {
                    RandomRunMod.LOGGER.info("✅ Record successfully submitted to Firebase (both paths)!");
                } else {
                    RandomRunMod.LOGGER.error("❌ Failed to submit record to Firebase (record: " + recordSuccess + ", player: " + playerSuccess + ")");
                }
                
                return success;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Error submitting record to leaderboard", e);
                return false;
            }
        });
    }
    
    public LeaderboardEntry createEntry(String playerName, String targetId, String targetType,
                                       long completionTime, String worldSeed, long timeLimit,
                                       String difficulty, boolean isTimeChallenge) {
        String modVersion = RandomRunMod.MOD_VERSION;
        String minecraftVersion = SharedConstants.getGameVersion().getName();
        
        return new LeaderboardEntry(
            playerName,
            targetId,
            targetType,
            completionTime,
            worldSeed,
            timeLimit,
            difficulty,
            isTimeChallenge,
            modVersion,
            minecraftVersion
        );
    }
    
    public String getPlayerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            return client.player.getName().getString();
        } else if (client.getSession() != null) {
            return client.getSession().getUsername();
        }
        return "Unknown Player";
    }
    
    public String getCurrentWorldSeed() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        String lastCreatedSeed = com.randomrun.challenges.classic.world.WorldCreator.getLastCreatedSeed();
        if (lastCreatedSeed != null && !lastCreatedSeed.isEmpty()) {
            return lastCreatedSeed;
        }
        
        if (client.world != null && client.getServer() != null) {
            try {
                long seed = client.getServer().getOverworld().getSeed();
                return String.valueOf(seed);
            } catch (Exception e) {
                RandomRunMod.LOGGER.warn("Could not get world seed from server", e);
            }
        }
        
        return "unknown";
    }
    
    private String sanitizeId(String id) {
        return id.replace(":", "_").replace("/", "_").replace(".", "_");
    }
    
    public CompletableFuture<Boolean> submitCurrentRun(String targetId, String targetType, 
                                                       long completionTime, long timeLimit, 
                                                       String difficulty, boolean isTimeChallenge) {
        String playerName = getPlayerName();
        String worldSeed = getCurrentWorldSeed();
        
        LeaderboardEntry entry = createEntry(
            playerName,
            targetId,
            targetType,
            completionTime,
            worldSeed,
            timeLimit,
            difficulty,
            isTimeChallenge
        );
        
        return submitRecord(entry);
    }
    
    public CompletableFuture<java.util.List<LeaderboardEntry>> getPlayerRecords(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String path = "/leaderboard/players/" + sanitizeId(playerName);
                
                RandomRunMod.LOGGER.info("Fetching records for player: " + playerName);
                
                com.google.gson.JsonObject data = firebaseClient.get(path).join();
                
                if (data == null) {
                    RandomRunMod.LOGGER.info("No records found for player: " + playerName);
                    return new java.util.ArrayList<>();
                }
                
                java.util.List<LeaderboardEntry> records = new java.util.ArrayList<>();
                com.google.gson.Gson gson = new com.google.gson.Gson();
                
                for (String key : data.keySet()) {
                    try {
                        LeaderboardEntry entry = gson.fromJson(data.get(key), LeaderboardEntry.class);
                        if (entry != null && entry.verifyIntegrity()) {
                            records.add(entry);
                        }
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.warn("Failed to parse record: " + key, e);
                    }
                }
                
                RandomRunMod.LOGGER.info("Loaded " + records.size() + " records for player: " + playerName);
                return records;
                
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Error fetching player records", e);
                return new java.util.ArrayList<>();
            }
        });
    }
    
    public CompletableFuture<java.util.List<LeaderboardEntry>> getTopRecordsForTarget(String targetId, String targetType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String path = "/leaderboard/records/" + targetType.toLowerCase() + "/" + sanitizeId(targetId);
                
                RandomRunMod.LOGGER.info("Fetching top " + limit + " records for: " + targetId);
                
                com.google.gson.JsonObject data = firebaseClient.get(path).join();
                
                if (data == null) {
                    RandomRunMod.LOGGER.info("No records found for: " + targetId);
                    return new java.util.ArrayList<>();
                }
                
                java.util.List<LeaderboardEntry> records = new java.util.ArrayList<>();
                com.google.gson.Gson gson = new com.google.gson.Gson();
                
                for (String key : data.keySet()) {
                    try {
                        LeaderboardEntry entry = gson.fromJson(data.get(key), LeaderboardEntry.class);
                        if (entry != null && entry.verifyIntegrity()) {
                            records.add(entry);
                        }
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.warn("Failed to parse record: " + key, e);
                    }
                }
                
                records.sort(java.util.Comparator.comparingLong(e -> e.completionTime));
                
                if (records.size() > limit) {
                    records = records.subList(0, limit);
                }
                
                RandomRunMod.LOGGER.info("Loaded " + records.size() + " top records for: " + targetId);
                return records;
                
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Error fetching top records", e);
                return new java.util.ArrayList<>();
            }
        });
    }
}
