package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.fabricmc.loader.api.FabricLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import net.minecraft.util.WorldSavePath;

@Mixin(MinecraftClient.class)
public class DisconnectCleanupMixin {

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screen/Screen;)V", at = @At("HEAD"))
    private void onDisconnect(Screen screen, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        ModConfig config = RandomRunMod.getInstance().getConfig();
        RunDataManager run = RandomRunMod.getInstance().getRunDataManager();
        BattleManager battle = BattleManager.getInstance();

        if (client.getServer() == null) return;

        // Получаем точный путь к папке мира от сервера
        Path rawWorldRoot = client.getServer().getSavePath(WorldSavePath.ROOT);
        // Normalize path if it ends with "."
        if (rawWorldRoot.getFileName().toString().equals(".")) {
             rawWorldRoot = rawWorldRoot.getParent();
        }
        
        final Path worldRoot = rawWorldRoot;
        String currentFolderName = worldRoot.getFileName().toString();
        
        // RELAXED CHECK: If config is enabled and folder starts with "RandomRun", we assume it's safe to delete.
        // We do not strictly check run.getStatus() because it might be reset or in a weird state.
        // We rely on the folder name convention "RandomRun..."
        
        boolean isRandomRunWorld = currentFolderName.startsWith("RandomRun");
        boolean isCompletedOrFailed = (run.getStatus() == RunDataManager.RunStatus.COMPLETED || run.getStatus() == RunDataManager.RunStatus.FAILED);
        
        // If we are in a RandomRun world, and config is enabled, AND (status is completed/failed OR user requested always delete)
        // User request: "Auto delete worlds... (those that are speedruns)"
        // If the user quits a speedrun, they might want it deleted too? 
        // But usually only after completion.
        // Let's stick to COMPLETED/FAILED for now, but ensure we catch it correctly.
        
        if (!config.isDeleteWorldsAfterSpeedrun()) return;
        if (battle.isSharedWorld()) return; // Never delete shared worlds automatically here (handled by BattleManager)
        
        if (!isRandomRunWorld) {
             // RandomRunMod.LOGGER.info("Skipping deletion: Not a RandomRun folder (" + currentFolderName + ")");
             return;
        }
        
        // RELAXED CHECK: If it is a RandomRun world and config is enabled, DELETE IT.
        // We do not check for COMPLETED/FAILED status anymore, because:
        // 1. User might quit mid-run (Rage quit / Reset) and expects deletion.
        // 2. Status might be reset before this mixin runs in some edge cases.
        // 3. The folder name "RandomRun" + Config Enabled is a strong enough signal of intent.
        
        /*
        if (!isCompletedOrFailed) {
             return;
        }
        */

        RandomRunMod.LOGGER.info("🧹 Планирование удаления мира: " + worldRoot.toString());

        new Thread(() -> {
            try {
                // Ждем остановки сервера и освобождения файлов
                // INCREASED DELAY to 3 seconds to ensure lock release
                Thread.sleep(3000);
                
                int attempts = 10; // INCREASED ATTEMPTS
                boolean success = false;
                
                while (attempts > 0 && !success) {
                    if (Files.exists(worldRoot)) {
                        try {
                            Files.walk(worldRoot)
                                    .sorted(Comparator.reverseOrder())
                                    .forEach(path -> {
                                        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                                    });
                            
                            // Проверяем, удалилась ли корневая папка
                            if (!Files.exists(worldRoot)) {
                                RandomRunMod.LOGGER.info("✓ Мир успешно удален: " + worldRoot.toString());
                                success = true;
                            } else {
                                RandomRunMod.LOGGER.warn("⚠ Не удалось полностью удалить папку (попытка " + (6 - attempts) + ")");
                            }
                        } catch (Exception e) {
                            RandomRunMod.LOGGER.error("Ошибка удаления мира: " + worldRoot, e);
                        }
                    } else {
                        RandomRunMod.LOGGER.info("Папка мира уже не существует.");
                        success = true;
                    }
                    
                    if (!success) {
                        attempts--;
                        Thread.sleep(1000); // Ждем секунду перед повторной попыткой
                    }
                }
                
                if (!success) {
                    RandomRunMod.LOGGER.error("❌ Не удалось удалить мир после 5 попыток: " + worldRoot);
                }
                
            } catch (InterruptedException e) {
                RandomRunMod.LOGGER.error("Ошибка планирования автоудаления мира", e);
            }
        }, "RandomRun-WorldCleanup").start();
    }
}
