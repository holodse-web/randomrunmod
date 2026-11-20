package com.randomrun.mixin;

import com.randomrun.core.run.RunDataManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.speedrun.WorldCreator;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.overlay.LoadingCurtain;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


public final class WorldMixins {
    private WorldMixins() {}

    @Mixin(ClientPacketListener.class)
    public static class JoinWorldMixin {
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
                    if (room != null && room.getGameMode() == com.randomrun.features.battle.manager.BattleRoom.GameMode.RANDOM_DROP) {
                        isRandomDrop = true;
                    }
                }

                if (!isRandomDrop) {
                    com.randomrun.core.run.RunSessionManager sessionManager = com.randomrun.core.run.RunSessionManager.getInstance();
                    if (sessionManager.hasActiveRun()) {
                        com.randomrun.core.run.RunSession session = sessionManager.getCurrentSession();
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


            if (mc.getSingleplayerServer() != null && WorldCreator.isCreationTriggered()) {
                java.nio.file.Path root = mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath().normalize();
                if (root.getFileName() != null && root.getFileName().toString().equals(".")) root = root.getParent();
                WorldCreator.markRunWorld(root);
            }

            var sessions = com.randomrun.core.run.RunSessionManager.getInstance();
            var previous = sessions.getCurrentSession();
            if (!battleManager.isInBattle() && previous != null && previous.isActive() && !worldName.equals(previous.getWorldName())) {
                RandomRunMod.LOGGER.warn("[World] Run cancelled: joined another world");
                sessions.cancelRun();
                runManager.cancelRun();
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
                RandomRunMod.LOGGER.warn("[Battle] Missing target item");
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
            com.randomrun.core.run.RunSessionManager.getInstance().startRun(
                targetItem, true, battleRoomId, battleSeed, worldName, room != null && room.isUpgrader());
            com.randomrun.core.run.RunSession battleSession = com.randomrun.core.run.RunSessionManager.getInstance().getCurrentSession();
            if (battleSession != null && room != null) battleSession.setRandomDrop(room.isRandomDrop());

            battleManager.freezePlayer();
            battleManager.setPlayerLoaded();
            battleManager.onWorldLoaded();

            configureSpeedrunWorld(mc);
            WorldCreator.clearPendingData();
        }

        private void handleSoloJoin(Minecraft mc, RunDataManager runManager, String worldName) {
            if (!worldName.startsWith("RandomRun")) {
                RandomRunMod.LOGGER.warn("[World] Clearing run state in a regular world");
                WorldCreator.clearPendingData();
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                return;
            }

            Item targetItem = WorldCreator.getPendingTargetItem();
            boolean randomDrop = WorldCreator.isPendingRandomDrop();

            if (targetItem == null) {
                RandomRunMod.LOGGER.warn("[Solo] Missing target item");
                WorldCreator.clearPendingData();
                return;
            }

            runManager.startNewRun(targetItem, worldName);

            String worldSeed = WorldCreator.getLastCreatedSeed();
            String customSeed = WorldCreator.isManualSeed() ? worldSeed : null;

            com.randomrun.core.run.RunSessionManager.getInstance().startRun(
                targetItem, false, null, customSeed, worldName, WorldCreator.isPendingUpgrader());
            com.randomrun.network.RunVerifier.activate(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetItem).getPath(),
                mc.getSingleplayerServer() != null ? String.valueOf(mc.getSingleplayerServer().overworld().getSeed()) : "");
            com.randomrun.network.RealtimeClient.getInstance().setPresence("solo",
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(targetItem).getPath());

            com.randomrun.core.run.RunSession session = com.randomrun.core.run.RunSessionManager.getInstance().getCurrentSession();
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
                com.randomrun.core.run.RunSessionManager.getInstance().setWorldSeed(worldSeed);
            }
        }
    }

    @Mixin(CreateWorldScreen.class)
    public static abstract class AutoCreateWorldMixin {
        @Inject(method = "init", at = @At("TAIL"))
        private void onInit(CallbackInfo ci) {
            if (!WorldCreator.hasPendingRun()) return;

            if (WorldCreator.isCreationTriggered()) return;
            long attempt = WorldCreator.getCreationAttempt();

            CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
            CreateWorldScreenAccessor screenAccessor = (CreateWorldScreenAccessor) screen;

            try {
                net.minecraft.client.gui.screens.worldselection.WorldCreationUiState worldCreator = screenAccessor.getWorldCreator();
                if (worldCreator == null) {
                    WorldCreator.getInstance().cancelCreation();
                    return;
                }

                String worldName = generateWorldName();
                worldCreator.setName(worldName);

                net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.SelectedGameMode targetMode = determineGameMode();
                worldCreator.setGameMode(targetMode);

                configureSeed(worldCreator);
            } catch (Exception e) {
                RandomRunMod.LOGGER.error("[World] Setup failed", e);
                WorldCreator.getInstance().cancelCreation();
                return;
            }

            Minecraft.getInstance().execute(() -> {
                try {
                    if (!WorldCreator.isCurrentAttempt(attempt) || WorldCreator.isCreationTriggered()) return;
                    WorldCreator.setCreationTriggered(true);

                    screenAccessor.invokeCreateLevel();
                } catch (Exception e) {
                    RandomRunMod.LOGGER.error("[World] Creation failed", e);
                    WorldCreator.getInstance().cancelCreation();
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
                WorldCreator.setLastCreatedSeed(pendingSeed, !isBattle && !WorldCreator.isServerSeed());
            }
        }
    }

    @Mixin(CreateWorldScreen.class)
    public interface CreateWorldScreenAccessor {
        @Invoker("onCreate")
        void invokeCreateLevel();

        @Accessor("uiState")
        net.minecraft.client.gui.screens.worldselection.WorldCreationUiState getWorldCreator();
    }

    @Mixin(SelectWorldScreen.class)
    public static class WorldSelectionAutoClickMixin {
        @Inject(method = "init", at = @At("TAIL"))
        private void autoClickCreate(CallbackInfo ci) {
            if (!WorldCreator.hasPendingRun()) return;

            for (net.minecraft.client.gui.components.events.GuiEventListener element : ((net.minecraft.client.gui.screens.Screen)(Object)this).children()) {
                if (!(element instanceof Button)) continue;

                Button button = (Button) element;

                if (isCreateButton(button)) {
                    button.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
                    return;
                }
            }

            RandomRunMod.LOGGER.warn("[AutoClick] Create World button missing");
        }

        private boolean isCreateButton(Button button) {
            Component message = button.getMessage();

            if (message.getContents() instanceof TranslatableContents) {
                String key = ((TranslatableContents) message.getContents()).getKey();
                if ("selectWorld.create".equals(key)) {
                    return true;
                }
            }

            String text = message.getString();
            return text.equals("Create New World") || text.equals("Создать новый мир");
        }
    }

    @Mixin(LevelLoadTracker.class)
    public static class LevelLoadTrackerMixin {
        @Redirect(method = "startClientLoad", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/LevelLoadTracker;CLIENT_WAIT_TIMEOUT_MS:J"))
        private long randomrun$shortClientWait() {
            return LoadingCurtain.isCovering() ? 4000L : 30000L;
        }
    }
}
