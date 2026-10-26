package com.randomrun.features.battle.manager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class BattleCleanupTask {
    private BattleCleanupTask() {}

    public static CompletableFuture<Boolean> finish(CompletableFuture<Boolean> notification, long timeoutMillis,
            Executor clientExecutor, BooleanSupplier sameRoom, Runnable cleanup) {
        return notification.copy().completeOnTimeout(false, timeoutMillis, TimeUnit.MILLISECONDS)
            .handle((success, error) -> error == null && Boolean.TRUE.equals(success))
            .thenApplyAsync(success -> {
                if (sameRoom.getAsBoolean()) cleanup.run();
                return success;
            }, clientExecutor);
    }
}
