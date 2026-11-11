package com.randomrun.core.init;

import com.randomrun.core.service.DataSyncService;
import com.randomrun.network.BackendSession;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;

public final class CoreInitializer {
    private static boolean initialized;
    private static long generation = -1;
    private static CompletableFuture<Void> initializationFuture;
    private CoreInitializer() {}
    public static synchronized CompletableFuture<Void> initialize(String name) {
        if (name == null || name.isBlank()) return CompletableFuture.failedFuture(new IllegalArgumentException("Empty player name"));
        long expected = BackendSession.generation();
        if (generation == expected && initialized) return CompletableFuture.completedFuture(null);
        if (generation == expected && initializationFuture != null && !initializationFuture.isDone()) return initializationFuture;
        generation = expected;
        initializationFuture = DataSyncService.getInstance().loadPlayerData(name).thenAccept(loaded -> {
            synchronized (CoreInitializer.class) {
                if (generation != expected || BackendSession.generation() != expected) throw new CancellationException("Session changed");
                if (!loaded) throw new IllegalStateException("Player data unavailable");
                initialized = true;
            }
        });
        return initializationFuture;
    }
    public static synchronized boolean isInitialized() { return initialized && generation == BackendSession.generation(); }
    public static synchronized void reset() { initialized = false; generation = -1; initializationFuture = null; }
}
