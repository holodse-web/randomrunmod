package com.randomrun.mixin.network;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.storage.LevelResource;

@Mixin(Minecraft.class)
public class DisconnectCleanupMixin {
    private static Path pendingWorldPath = null;
    private static String pendingWorldName = null;

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void onDisconnectHead(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        pendingWorldPath = null;
        pendingWorldName = null;
        
        Minecraft client = Minecraft.getInstance();
        
        if (client.getSingleplayerServer() == null) {
            return;
        }
        
        if (!shouldDeleteWorld()) {
            return;
        }

        try {
            Path worldRoot = getWorldPath(client);
            String worldName = worldRoot.getFileName().toString();
            
            if (!isRandomRunWorld(worldName)) {
                return;
            }

            pendingWorldPath = worldRoot;
            pendingWorldName = worldName;
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[Cleanup] Ошибка получения пути мира", e);
        }
    }
    
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("TAIL"))
    private void onDisconnectTail(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo ci) {
        if (pendingWorldPath != null && pendingWorldName != null) {
            Path worldRoot = pendingWorldPath;
            String worldName = pendingWorldName;
            pendingWorldPath = null;
            pendingWorldName = null;
            
            deleteWorldAsync(worldRoot, worldName);
        }
    }
    
    private boolean shouldDeleteWorld() {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        BattleManager battle = BattleManager.getInstance();
        
        return config.isDeleteWorldsAfterSpeedrun() && !battle.isSharedWorld();
    }
    
    private Path getWorldPath(Minecraft client) {
        Path worldRoot = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT);
        
        if (worldRoot.getFileName().toString().equals(".")) {
            worldRoot = worldRoot.getParent();
        }

        worldRoot = worldRoot.toAbsolutePath().normalize();
        
        return worldRoot;
    }
    
    private boolean isRandomRunWorld(String worldName) {
        return worldName.startsWith("RandomRun") || worldName.startsWith("speedrun_");
    }
    
    private void deleteWorldAsync(Path worldRoot, String worldName) {
        CompletableFuture<Void> cleanupFinished = new CompletableFuture<>();
        Thread cleanupThread = new Thread(() -> {
            try {
                System.gc();
                Thread.sleep(3000);
                System.gc();
                Thread.sleep(2000);
                
                for (int attempt = 1; attempt <= 20; attempt++) {
                    if (!Files.exists(worldRoot)) {
                        return;
                    }
                    
                    if (tryDeleteWorld(worldRoot)) {
                        return;
                    }
                    
                    System.gc();
                    Thread.sleep(1500);
                }
                
                RandomRunMod.LOGGER.error("[Cleanup] Не удалось удалить: {}", worldRoot);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                RandomRunMod.LOGGER.warn("[Cleanup] Удаление прервано: {}", worldRoot);
            } finally {
                cleanupFinished.complete(null);
            }
        }, "RandomRun-WorldCleanup");
        cleanupThread.setDaemon(true);
        Thread shutdownHook = new Thread(() -> {
            try {
                cleanupThread.join(10_000);
                if (cleanupThread.isAlive()) {
                    RandomRunMod.LOGGER.warn("[Cleanup] Таймаут удаления при выходе: {}", worldRoot);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "RandomRun-WorldCleanup-Shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        cleanupFinished.thenRun(() -> {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException e) {
                return;
            }
        });
        cleanupThread.start();
    }
    
    private boolean tryDeleteWorld(Path worldRoot) {
        try (Stream<Path> paths = Files.walk(worldRoot)) {
            paths
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        path.toFile().setWritable(true);
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        RandomRunMod.LOGGER.debug("[Cleanup] Не удалось удалить: " + path.getFileName());
                    }
                });
            
            return !Files.exists(worldRoot);
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[Cleanup] Ошибка удаления", e);
            return false;
        }
    }
}
