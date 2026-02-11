package com.randomrun.leaderboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.battle.FirebaseClient;
import com.randomrun.main.RandomRunMod;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;

// import java.util.UUID; // UNUSED
import java.util.concurrent.CompletableFuture;

public class LeaderboardManager {
    
    // Изменение этой константы эффективно "сбрасывает" таблицу лидеров для старых клиентов
    // или переносит новые записи в новую таблицу.
    // private static final String PROTOCOL_VERSION = "v1"; // UNUSED
    
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
                    RandomRunMod.LOGGER.error("Не удалось отправить рекорд: проверка целостности не пройдена");
                    return false;
                }
                
                // Путь: /leaderboards/{targetId}/{playerName}
                // Используем ID игрока как ключ, чтобы перезаписывать старый рекорд (Upsert)
                String path = "/leaderboards/" + sanitizeId(entry.targetId) + "/" + sanitizeId(entry.playerName);
                
                // Сначала проверяем, есть ли уже рекорд лучше
                JsonElement existingElement = firebaseClient.get(path).join();
                if (existingElement != null && existingElement.isJsonObject()) {
                    JsonObject existing = existingElement.getAsJsonObject();
                    LeaderboardEntry oldEntry = new com.google.gson.Gson().fromJson(existing, LeaderboardEntry.class);
                    if (oldEntry.completionTime <= entry.completionTime) {
                         RandomRunMod.LOGGER.info("Новый результат хуже или равен старому. Пропуск.");
                         return false;
                    }
                }

                boolean success = firebaseClient.put(path, entry).join();
                
                if (success) {
                    RandomRunMod.LOGGER.info("✅ Рекорд успешно обновлен: " + entry.targetId);
                    
                    // Removed side effects (PlayerProfile and GlobalStatsManager updates)
                    // These are now handled in VictoryHandler and BattleManager to avoid double counting
                }
                
                return success;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Ошибка при отправке рекорда", e);
                return false;
            }
        });
    }
    
    public LeaderboardEntry createEntry(String playerName, String targetId, String targetType,
                                       long completionTime, String worldSeed, long timeLimit,
                                       String difficulty, boolean isTimeChallenge, int attempts, boolean isHardcore) {
        String modVersion = RandomRunMod.MOD_VERSION;
        String minecraftVersion = SharedConstants.getGameVersion().getName();
        
        LeaderboardEntry entry = new LeaderboardEntry(
            playerName,
            targetId,
            targetType,
            completionTime,
            worldSeed,
            timeLimit,
            difficulty,
            isTimeChallenge,
            modVersion,
            minecraftVersion,
            isHardcore
        );
        entry.attempts = attempts;
        return entry;
    }
    
    public String getPlayerName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            return client.player.getName().getString();
        } else if (client.getSession() != null) {
            return client.getSession().getUsername();
        }
        return "Неизвестный игрок";
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
                RandomRunMod.LOGGER.warn("Не удалось получить сид мира с сервера", e);
            }
        }
        
        return "unknown";
    }
    
    private String sanitizeId(String id) {
        return id.replace(":", "_").replace("/", "_").replace(".", "_");
    }
    
    public CompletableFuture<Boolean> submitCurrentRun(String targetId, String targetType, 
                                                       long completionTime, long timeLimit, 
                                                       String difficulty, boolean isTimeChallenge, int attempts, boolean isHardcore) {
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
            isTimeChallenge,
            attempts,
            isHardcore
        );
        
        return submitRecord(entry);
    }
    
    public CompletableFuture<java.util.List<LeaderboardEntry>> getPlayerRecords(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // В новой схеме мы не храним список рекордов игрока отдельно.
                // Чтобы получить их, нужно запросить профиль игрока (PlayerProfile.bests)
                // Или сделать сложный запрос ко всем лидербордам (неэффективно).
                // Для совместимости возвращаем пустой список, так как эта функция 
                // использовалась для синхронизации локальной истории, 
                // которая теперь берется из профиля.
                return new java.util.ArrayList<>();
            } catch (Exception e) {
                return new java.util.ArrayList<>();
            }
        });
    }
    
    public CompletableFuture<java.util.List<LeaderboardEntry>> getTopRecordsForTarget(String targetId, String targetType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String path = "/leaderboards/" + sanitizeId(targetId);
                
                JsonElement dataElement = firebaseClient.get(path).join();
                if (dataElement == null || !dataElement.isJsonObject()) return new java.util.ArrayList<>();
                
                JsonObject data = dataElement.getAsJsonObject();
                java.util.List<LeaderboardEntry> records = new java.util.ArrayList<>();
                com.google.gson.Gson gson = new com.google.gson.Gson();
                
                for (String key : data.keySet()) {
                    LeaderboardEntry entry = gson.fromJson(data.get(key), LeaderboardEntry.class);
                    if (entry != null) {
                        records.add(entry);
                    }
                }
                
                records.sort(java.util.Comparator.comparingLong(e -> e.completionTime));
                if (records.size() > limit) records = records.subList(0, limit);
                
                return records;
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Ошибка при получении лучших рекордов", e);
                return new java.util.ArrayList<>();
            }
        });
    }
}
