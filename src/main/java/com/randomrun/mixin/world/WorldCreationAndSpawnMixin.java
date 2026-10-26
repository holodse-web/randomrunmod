package com.randomrun.mixin.world;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.infrastructure.RandomRunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class WorldCreationAndSpawnMixin {
    @Mixin(ClientPacketListener.class)
    public static class JoinWorld {
        private void configureSpeedrunWorld(Minecraft client) {
            if (client.getSingleplayerServer() == null) return;
            
            MinecraftServer server = client.getSingleplayerServer();
            ServerLevel world = server.overworld();
            
            if (world != null) {
                long worldSeed = world.getSeed();
                String seedString = String.valueOf(worldSeed);
                WorldCreator.setLastCreatedSeed(seedString, false);
                
                world.getWorldBorder().setCenter(0.0, 0.0);
                
                boolean isRandomDrop = false;
                
                BattleManager battleManager = BattleManager.getInstance();
                if (battleManager.isInBattle()) {
                    com.randomrun.features.battle.manager.BattleRoom room = battleManager.getCurrentRoom();
                    if (room != null && room.getGameMode() == com.randomrun.features.battle.manager.GameMode.RANDOM_DROP) {
                        isRandomDrop = true;
                    }
                }
                
                if (!isRandomDrop) {
                    com.randomrun.core.session.RunSessionManager sessionManager = com.randomrun.core.session.RunSessionManager.getInstance();
                    if (sessionManager.hasActiveRun()) {
                        com.randomrun.core.session.RunSession session = sessionManager.getCurrentSession();
                        if (session != null && session.isRandomDrop()) {
                            isRandomDrop = true;
                        }
                    }
                }
                
                if (isRandomDrop) {
                    world.getWorldBorder().setSize(200.0);
                } else {
                    world.getWorldBorder().setSize(1000000.0);
                }
                
                net.minecraft.core.BlockPos spawnPos = world.getRespawnData().pos();
            }
        }

        @Inject(method = "handleLogin", at = @At("TAIL"))
        private void onJoinWorld(ClientboundLoginPacket packet, CallbackInfo ci) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            BattleManager battleManager = BattleManager.getInstance();
            Minecraft mc = Minecraft.getInstance();
            
            String worldName = "unknown";
            if (mc.getSingleplayerServer() != null) {
                worldName = mc.getSingleplayerServer().getWorldData().getLevelName();
            } else if (mc.getCurrentServer() != null) {
                worldName = mc.getCurrentServer().ip;
            }
            
            if (battleManager.isInBattle()) {
                handleBattleJoin(mc, runManager, battleManager, worldName);
            } else if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                if ("pending".equals(runManager.getRunWorldName())) {
                    runManager.setRunWorldName(worldName);
                }
            } else if (WorldCreator.hasPendingRun()) {
                handleSoloJoin(mc, runManager, worldName);
            }
        }
        
        private void handleBattleJoin(Minecraft mc, RunDataManager runManager, BattleManager battleManager, String worldName) {
            Item targetItem = runManager.getTargetItem();
            com.randomrun.features.battle.manager.BattleRoom room = battleManager.getCurrentRoom();
            
            if (targetItem == null && room != null && room.getTargetItem() != null) {
                targetItem = BuiltInRegistries.ITEM.getValue(Identifier.parse(
                    com.randomrun.infrastructure.util.IdCompressor.decompress(room.getTargetItem())));
                runManager.setTargetItem(targetItem);
            }
            
            if (targetItem == null) {
                RandomRunMod.LOGGER.warn("[Battle] Нет целевого предмета");
                return;
            }
            
            RunDataManager.RunStatus currentStatus = runManager.getStatus();
            if (currentStatus != RunDataManager.RunStatus.FROZEN) {
                if (currentStatus != RunDataManager.RunStatus.INACTIVE) {
                    runManager.cancelRun();
                }
                runManager.startNewRun(targetItem, worldName);
            }
            
            String battleSeed = room != null ? room.getSeed() : null;
            if (mc.getSingleplayerServer() != null && room != null && (battleSeed == null || battleSeed.isEmpty())) {
                long worldSeed = mc.getSingleplayerServer().overworld().getSeed();
                battleSeed = String.valueOf(worldSeed);
                room.setSeed(battleSeed);
                battleManager.saveSeedToDatabase(battleSeed);
            }
            
            String battleRoomId = battleManager.getCurrentRoomId();
            com.randomrun.core.session.RunSessionManager.getInstance().startRun(
                targetItem, true, battleRoomId, battleSeed, worldName, room != null && room.isUpgrader());
            com.randomrun.core.session.RunSession battleSession = com.randomrun.core.session.RunSessionManager.getInstance().getCurrentSession();
            if (battleSession != null && room != null) battleSession.setRandomDrop(room.isRandomDrop());
            
            battleManager.freezePlayer();
            battleManager.setPlayerLoaded();
            battleManager.onWorldLoaded();
            
            configureSpeedrunWorld(mc);
            WorldCreator.clearPendingData();
        }
        
        private void handleSoloJoin(Minecraft mc, RunDataManager runManager, String worldName) {
            if (!worldName.startsWith("RandomRun")) {
                RandomRunMod.LOGGER.warn("[World] Не RandomRun мир, очистка");
                WorldCreator.clearPendingData();
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                return;
            }

            Item targetItem = WorldCreator.getPendingTargetItem();
            boolean randomDrop = WorldCreator.isPendingRandomDrop();
            
            if (targetItem == null) {
                RandomRunMod.LOGGER.warn("[Solo] Нет целевого предмета");
                WorldCreator.clearPendingData();
                return;
            }
            
            runManager.startNewRun(targetItem, worldName);
            
            String worldSeed = WorldCreator.getLastCreatedSeed();
            String customSeed = WorldCreator.isManualSeed() ? worldSeed : null;
            
            com.randomrun.core.session.RunSessionManager.getInstance().startRun(
                targetItem, false, null, customSeed, worldName, WorldCreator.isPendingUpgrader());
            
            com.randomrun.core.session.RunSession session = com.randomrun.core.session.RunSessionManager.getInstance().getCurrentSession();
            if (session != null && randomDrop) {
                session.setRandomDrop(true);
            }
            
            configureSpeedrunWorld(mc);
            setSeedIfAvailable();
            
            WorldCreator.clearPendingData();
        }
        
        private void setSeedIfAvailable() {
            String worldSeed = WorldCreator.getLastCreatedSeed();
            if (worldSeed != null && !worldSeed.isEmpty()) {
                com.randomrun.core.session.RunSessionManager.getInstance().setWorldSeed(worldSeed);
            }
        }
    }

    @Mixin(CreateWorldScreen.class)
    public static abstract class AutoCreate {
        @Inject(method = "init", at = @At("TAIL"))
        private void onInit(CallbackInfo ci) {
            if (!WorldCreator.hasPendingRun()) return;
            
            if (WorldCreator.isCreationTriggered()) {
                RandomRunMod.LOGGER.warn("[World] Повторное создание, пропуск");
                return;
            }
            
            CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
            ScreenAccessor screenAccessor = (ScreenAccessor) screen;
            
            try {
                net.minecraft.client.gui.screens.worldselection.WorldCreationUiState worldCreator = screenAccessor.getWorldCreator();
                if (worldCreator == null) return;
                
                String worldName = generateWorldName();
                worldCreator.setName(worldName);
                
                net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode targetMode = determineGameMode();
                worldCreator.setGameMode(targetMode);
                
                configureSeed(worldCreator);
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("[World] Ошибка настройки", e);
            }
            
            Minecraft.getInstance().execute(() -> {
                try {
                    if (WorldCreator.isCreationTriggered()) return;
                    WorldCreator.setCreationTriggered(true);
                    
                    screenAccessor.invokeCreateLevel();
                } catch (Exception e) {
                    RandomRunMod.LOGGER.error("[World] Ошибка создания", e);
                    WorldCreator.setCreationTriggered(false);
                }
            });
        }
        
        private String generateWorldName() {
            if (WorldCreator.getPendingTargetItem() != null) {
                return WorldCreator.generateWorldName(WorldCreator.getPendingTargetItem());
            }
            return "RandomRun Speedrun";
        }
        
        private net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode determineGameMode() {
            if (BattleManager.getInstance().isInBattle()) {
                return net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.SURVIVAL;
            }
            
            boolean isHardcore = RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
            return isHardcore 
                ? net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.HARDCORE 
                : net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode.SURVIVAL;
        }
        
        private void configureSeed(net.minecraft.client.gui.screens.worldselection.WorldCreationUiState worldCreator) {
            String pendingSeed = WorldCreator.getPendingSeed();
            boolean isBattle = BattleManager.getInstance().isInBattle();
            
            if (isBattle && (pendingSeed == null || pendingSeed.isEmpty() || pendingSeed.equals("null"))) {
                com.randomrun.features.battle.manager.BattleRoom room = BattleManager.getInstance().getCurrentRoom();
                if (room != null && room.getSeed() != null && !room.getSeed().isEmpty()) {
                    pendingSeed = room.getSeed();
                }
            }
            
            if (pendingSeed != null && !pendingSeed.isEmpty() && !pendingSeed.equals("null")) {
                pendingSeed = pendingSeed.trim();
                worldCreator.setSeed(pendingSeed);
                WorldCreator.setLastCreatedSeed(pendingSeed, !isBattle);
            }
        }
    }

    @Mixin(CreateWorldScreen.class)
    public interface ScreenAccessor {
        @Invoker("onCreate")
        void invokeCreateLevel();
        
        @Accessor("uiState")
        net.minecraft.client.gui.screens.worldselection.WorldCreationUiState getWorldCreator();
    }
}
