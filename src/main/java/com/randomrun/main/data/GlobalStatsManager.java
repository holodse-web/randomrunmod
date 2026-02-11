package com.randomrun.main.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.battle.FirebaseClient;
import com.randomrun.main.RandomRunMod;

import java.util.concurrent.CompletableFuture;

public class GlobalStatsManager {

    private static final String STATS_PATH = "/stats/global";

    public static void incrementRun() {
        incrementStat("total_runs_global");
    }

    public static void incrementRoomCreated() {
        incrementStat("total_rooms_created");
    }
    
    public static void addPlaytime(long seconds) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonElement statsElement = FirebaseClient.getInstance().get(STATS_PATH).join();
                JsonObject stats;
                if (statsElement != null && statsElement.isJsonObject()) {
                    stats = statsElement.getAsJsonObject();
                } else {
                    stats = new JsonObject();
                }
                
                long currentSeconds = stats.has("total_playtime_seconds") ? stats.get("total_playtime_seconds").getAsLong() : 0;
                stats.addProperty("total_playtime_seconds", currentSeconds + seconds);
                
                // Для удобства обновляем и часы
                stats.addProperty("total_playtime_hours", (currentSeconds + seconds) / 3600);
                
                FirebaseClient.getInstance().patch(STATS_PATH, stats).join();
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Ошибка обновления глобальной статистики", e);
            }
        });
    }

    private static void incrementStat(String statName) {
        CompletableFuture.runAsync(() -> {
            try {
                // В идеале это должно быть RPC, но используем Read-Modify-Write для простоты
                // При высокой нагрузке возможны коллизии, но для статистики это не критично
                JsonElement statsElement = FirebaseClient.getInstance().get(STATS_PATH).join();
                JsonObject stats;
                if (statsElement != null && statsElement.isJsonObject()) {
                    stats = statsElement.getAsJsonObject();
                } else {
                    stats = new JsonObject();
                }
                
                long current = stats.has(statName) ? stats.get(statName).getAsLong() : 0;
                stats.addProperty(statName, current + 1);
                
                FirebaseClient.getInstance().patch(STATS_PATH, stats).join();
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("Ошибка обновления глобальной статистики: " + statName, e);
            }
        });
    }
}
