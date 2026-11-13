package com.randomrun.mixin;

import com.randomrun.core.data.PlayerProfile;
import com.randomrun.core.run.RunDataManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.security.RunSecurityValidator;
import com.randomrun.ui.screen.endgame.EndgameScreen.DefeatScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Anti-cheat: game mode, commands and LAN restrictions during runs. */
public final class SecurityMixins {
    private SecurityMixins() {}

    @Mixin(ClientPacketListener.class)
    public static class ClientCommandMixin {
        @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
        private void onSendCommand(String command, CallbackInfo ci) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            com.randomrun.core.run.RunSessionManager sessionManager =
                com.randomrun.core.run.RunSessionManager.getInstance();

            if (!isRunActive(runManager) && !sessionManager.hasActiveRun()) return;

            if (command.equals("go") || command.startsWith("go ")) return;

            if (RunSecurityValidator.isCommandBlocked(command)) {
                ci.cancel();
                handleCheatCommand(command);
            }
        }

        private boolean isRunActive(RunDataManager runManager) {
            RunDataManager.RunStatus status = runManager.getStatus();
            return status == RunDataManager.RunStatus.RUNNING || status == RunDataManager.RunStatus.FROZEN;
        }

        private void handleCheatCommand(String command) {
            BattleManager battleManager = BattleManager.getInstance();
            if (battleManager.isInBattle()) {
                battleManager.reportDefeat("[Cheat] Команда /" + command.split(" ")[0]);
            }

            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                com.randomrun.infrastructure.util.CompactLogger.ClientMessages.send(client.player, com.randomrun.infrastructure.util.LanguageManager.text("randomrun.error.command_blocked"), true);
            }

            RandomRunMod.LOGGER.warn("[AntiCheat] Блокировка команды - /" + command.split(" ")[0]);
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    public static class GameModeProtectionMixin {
        @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V", at = @At("HEAD"))
        private void onSetGameMode(GameType gameMode, CallbackInfo ci) {
            BattleManager battleManager = BattleManager.getInstance();
            if (!battleManager.isInBattle()) return;

            if (gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR) {
                Minecraft client = Minecraft.getInstance();

                if (client.player != null && !client.player.isDeadOrDying() && client.player.tickCount > 40) {
                    RandomRunMod.LOGGER.warn("[AntiCheat] Смена режима игры");
                    battleManager.reportDefeat("[Cheat] Смена режима");
                }
            }
        }
    }

    @Mixin(GameModeSwitcherScreen.class)
    public static class GameModeSwitchMixin {
        @Inject(method = "<init>", at = @At("RETURN"))
        private void preventGameModeSwitch(CallbackInfo ci) {
            BattleManager battleManager = BattleManager.getInstance();
            if (!battleManager.isInBattle()) return;

            Minecraft client = Minecraft.getInstance();
            client.execute(() -> {
                if (client.gui.screen() instanceof GameModeSwitcherScreen) {
                    client.gui.setScreen(null);
                }
            });
        }
    }

    @Mixin(IntegratedServer.class)
    public static class LanRestrictionMixin {
        @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("HEAD"), cancellable = true)
        private void onOpenToLan(net.minecraft.server.MinecraftServer.MultiplayerScope scope, int port, CallbackInfoReturnable<Boolean> cir) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            com.randomrun.core.run.RunSessionManager sessionManager =
                com.randomrun.core.run.RunSessionManager.getInstance();
            BattleManager battleManager = BattleManager.getInstance();

            boolean isRunActive = runManager.getStatus() == RunDataManager.RunStatus.RUNNING || 
                                  runManager.getStatus() == RunDataManager.RunStatus.FROZEN ||
                                  sessionManager.hasActiveRun();

            if (isRunActive && !battleManager.isSharedWorld()) {
                RandomRunMod.LOGGER.warn("[AntiCheat] Попытка открыть LAN");

                PlayerProfile profile = PlayerProfile.get();
                if (profile != null) {
                    profile.applyTrustPenalty(PlayerProfile.PENALTY_LAN, "Попытка открыть LAN");
                    PlayerProfile.save();
                }

                cir.setReturnValue(false);

                Minecraft.getInstance().execute(() -> {
                    if (isStillRunning(runManager, sessionManager)) {
                        showDefeatScreen(runManager, sessionManager);
                        notifyPlayer();
                    }
                });
            }
        }

        private boolean isStillRunning(RunDataManager runManager, com.randomrun.core.run.RunSessionManager sessionManager) {
            return runManager.getStatus() == RunDataManager.RunStatus.RUNNING || 
                   runManager.getStatus() == RunDataManager.RunStatus.FROZEN ||
                   sessionManager.hasActiveRun();
        }

        private void showDefeatScreen(RunDataManager runManager, com.randomrun.core.run.RunSessionManager sessionManager) {
            long finalTime = runManager.getCurrentTime();
            runManager.failRun();

            String reason = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.defeat.lan_cheat").getString();
            if (reason.equals("randomrun.defeat.lan_cheat")) {
                reason = "Обнаружено читерство: Открытие LAN запрещено";
            }

            Item targetItem = runManager.getTargetItem();

            if (targetItem == null && sessionManager.hasActiveRun()) {
                com.randomrun.core.run.RunSession session = sessionManager.getCurrentSession();
                if (session != null) {
                    targetItem = session.getTargetItem();


                }
            }

            if (targetItem != null) {
                Minecraft.getInstance().gui.setScreen(new DefeatScreen(targetItem, finalTime, reason));
            }
        }

        private void notifyPlayer() {
            if (Minecraft.getInstance().player != null) {
                com.randomrun.infrastructure.util.CompactLogger.ClientMessages.send(Minecraft.getInstance().player, com.randomrun.infrastructure.util.LanguageManager.text("randomrun.anticheat.lan"), false);
            }
        }
    }

    @Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
    public static class GameModeTrackerMixin {
        private static GameType lastGameMode = null;
        private static long lastModeChangeTime = 0;

        @Inject(method = "setLocalMode(Lnet/minecraft/world/level/GameType;)V", at = @At("HEAD"))
        private void onGameModeChange(GameType gameMode, CallbackInfo ci) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();

            if (!isRunActive(runManager)) {
                lastGameMode = gameMode;
                return;
            }

            if (lastGameMode == null) {
                lastGameMode = gameMode;
                return;
            }

            if (lastGameMode != gameMode) {
                long now = System.currentTimeMillis();

                if (gameMode == GameType.CREATIVE || gameMode == GameType.SPECTATOR) {
                    handleCheatModeChange(gameMode, runManager);
                }
                else if (now - lastModeChangeTime < 5000) {
                    handleSuspiciousModeChange(gameMode);
                }

                lastGameMode = gameMode;
                lastModeChangeTime = now;
            }
        }

        private boolean isRunActive(RunDataManager runManager) {
            RunDataManager.RunStatus status = runManager.getStatus();
            return status == RunDataManager.RunStatus.RUNNING || status == RunDataManager.RunStatus.FROZEN;
        }

        private void handleCheatModeChange(GameType gameMode, RunDataManager runManager) {
            RandomRunMod.LOGGER.error("[AntiCheat] Смена на читерский режим: {}", gameMode);

            PlayerProfile profile = PlayerProfile.get();
            if (profile != null) {
                profile.applyTrustPenalty(PlayerProfile.PENALTY_CREATIVE,
                    "Смена на режим " + gameMode);
                PlayerProfile.save();
            }

            runManager.failRun();

            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                com.randomrun.infrastructure.util.CompactLogger.ClientMessages.send(client.player, com.randomrun.infrastructure.util.LanguageManager.text("randomrun.anticheat.gamemode"), false);
            }
        }

        private void handleSuspiciousModeChange(GameType gameMode) {
            RandomRunMod.LOGGER.warn("[AntiCheat] Подозрительная смена режима: {}", gameMode);

            PlayerProfile profile = PlayerProfile.get();
            if (profile != null) {
                profile.addSuspiciousFlag();
                PlayerProfile.save();
            }
        }

    }
}
