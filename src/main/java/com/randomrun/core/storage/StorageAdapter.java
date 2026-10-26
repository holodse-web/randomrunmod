package com.randomrun.core.storage;

import com.randomrun.core.history.Run;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface StorageAdapter {
    CompletableFuture<List<Run>> loadHistory(String playerName);
    
    CompletableFuture<Boolean> saveRecord(String playerName, Run record);
    
    CompletableFuture<Boolean> saveHistory(String playerName, List<Run> records);

    CompletableFuture<Boolean> isAvailable();
    
    String getType();
    
    default CompletableFuture<Map<String, Integer>> getMetrics() {
        return CompletableFuture.completedFuture(Map.of());
    }
}
