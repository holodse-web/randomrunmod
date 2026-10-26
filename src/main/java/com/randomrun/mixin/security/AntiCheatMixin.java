package com.randomrun.mixin.security;

import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.infrastructure.data.RunSecurityValidator;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.ui.screen.endgame.DefeatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class AntiCheatMixin {
    @Mixin(ClientPacketListener.class)
    public static class ClientCommand {
        @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
        private void onSendCommand(String command, CallbackInfo ci) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            com.randomrun.core.session.RunSessionManager sessionManager = 
                com.randomrun.core.session.RunSessionManager.getInstance();
            
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
                com.randomrun.infrastructure.util.ClientMessages.send(client.player, com.randomrun.infrastructure.util.LanguageManager.text("randomrun.error.command_blocked"), true);
            }
            
            RandomRunMod.LOGGER.warn("[AntiCheat] Блокировка команды - /" + command.split(" ")[0]);
        }
    }

    @Mixin(MultiPlayerGameMode.class)
    public static class GameModeProtection {
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
    public static class GameModeSwitch {
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
    public static class LanRestriction {
        @Inject(method = "publishServer(Lnet/minecraft/server/MinecraftServer$MultiplayerScope;I)Z", at = @At("HEAD"), cancellable = true)
        private void onOpenToLan(net.minecraft.server.MinecraftServer.MultiplayerScope scope, int port, CallbackInfoReturnable<Boolean> cir) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            com.randomrun.core.session.RunSessionManager sessionManager = 
                com.randomrun.core.session.RunSessionManager.getInstance();
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
        
        private boolean isStillRunning(RunDataManager runManager, com.randomrun.core.session.RunSessionManager sessionManager) {
            return runManager.getStatus() == RunDataManager.RunStatus.RUNNING || 
                   runManager.getStatus() == RunDataManager.RunStatus.FROZEN ||
                   sessionManager.hasActiveRun();
        }
        
        private void showDefeatScreen(RunDataManager runManager, com.randomrun.core.session.RunSessionManager sessionManager) {
            long finalTime = runManager.getCurrentTime();
            runManager.failRun();
            
            String reason = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.defeat.lan_cheat").getString();
            if (reason.equals("randomrun.defeat.lan_cheat")) {
                reason = "Обнаружено читерство: Открытие LAN запрещено";
            }
            
            Item targetItem = runManager.getTargetItem();
            
            if (targetItem == null && sessionManager.hasActiveRun()) {
                com.randomrun.core.session.RunSession session = sessionManager.getCurrentSession();
                if (session != null) {
                    targetItem = session.getTargetItem();
                    
                    if (targetItem == null && session.getTargetType() == com.randomrun.core.session.TargetType.MOB) {
                        net.minecraft.world.entity.EntityType<?> mobType = session.getTargetMob();
                        if (mobType != null) {
                            net.minecraft.world.item.Item spawnEgg = net.minecraft.world.item.SpawnEggItem.byId(mobType).map(net.minecraft.core.Holder::value).orElse(null);
                            if (spawnEgg != null) {
                                targetItem = spawnEgg;
                            }
                        }
                    }
                }
            }
            
            if (targetItem != null) {
                Minecraft.getInstance().gui.setScreen(new DefeatScreen(targetItem, finalTime, reason));
            }
        }
        
        private void notifyPlayer() {
            if (Minecraft.getInstance().player != null) {
                com.randomrun.infrastructure.util.ClientMessages.send(Minecraft.getInstance().player, Component.literal("§c§l[RandomRun] §cОткрытие LAN запрещено!"), false);
            }
        }
    }
}
