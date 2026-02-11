package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.main.data.SecurityManager;
import com.randomrun.ui.screen.endgame.DefeatScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameModeSelectionScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.item.Item;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Объединяет все механики Античита.
 * Содержит миксины для Команд, Защиты режима игры и Ограничений LAN.
 */
public class AntiCheatMixin {

    @Mixin(ClientPlayNetworkHandler.class)
    public static class ClientCommand {
        @Inject(method = "sendChatCommand", at = @At("HEAD"), cancellable = true)
        private void onSendCommand(String command, CallbackInfo ci) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            
            // Проверка, активен ли забег
            if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING ||
                runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                
                // Разрешить команду /go
                if (command.equals("go") || command.startsWith("go ")) {
                    return;
                }
                
                // Блокировать чит-команды
                if (SecurityManager.isCommandBlocked(command)) {
                    ci.cancel();
                    
                    // Если в битве, сообщить о поражении (обнаружен чит)
                    BattleManager battleManager = BattleManager.getInstance();
                    if (battleManager.isInBattle()) {
                         battleManager.reportDefeat("Чит: Команда /" + command.split(" ")[0]);
                    }
                    
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.player.sendMessage(
                            Text.translatable("randomrun.error.command_blocked"),
                            true
                        );
                    }
                }
            }
        }
    }

    @Mixin(ClientPlayerInteractionManager.class)
    public static class GameModeProtection {
        @Inject(method = "setGameMode", at = @At("HEAD"))
        private void onSetGameMode(GameMode gameMode, CallbackInfo ci) {
            // Если в битве, смена режима игры на КРЕАТИВ или НАБЛЮДАТЕЛЬ (если не смерть) подозрительна
            BattleManager battleManager = BattleManager.getInstance();
            if (battleManager.isInBattle()) {
                if (gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR) {
                    // Однако, наблюдатель используется при смерти. Нам нужно различать ручное переключение и смерть.
                    // PlayerDeathMixin обрабатывает смерть. 
                    // Если игрок мертв/умирает, это нормально.
                    
                    MinecraftClient client = MinecraftClient.getInstance();
                    // Добавлена проверка возраста игрока, чтобы избежать ложных срабатываний при загрузке мира
                    if (client.player != null && !client.player.isDead() && client.player.age > 40) {
                         RandomRunMod.LOGGER.warn("Обнаружена незаконная смена режима игры во время битвы!");
                         battleManager.reportDefeat("Чит: Смена режима игры");
                    }
                }
            }
        }
    }

    @Mixin(GameModeSelectionScreen.class)
    public static class GameModeSwitch {
        @Inject(method = "<init>", at = @At("RETURN"))
        private void preventGameModeSwitch(CallbackInfo ci) {
            BattleManager battleManager = BattleManager.getInstance();
            if (battleManager.isInBattle()) {
                // Закрыть меню F3+F4 немедленно, если открыто во время битвы
                MinecraftClient client = MinecraftClient.getInstance();
                client.execute(() -> {
                    if (client.currentScreen instanceof GameModeSelectionScreen) {
                        client.setScreen(null);
                    }
                });
            }
        }
    }

    @Mixin(IntegratedServer.class)
    public static class LanRestriction {
        @Inject(method = "openToLan", at = @At("HEAD"), cancellable = true)
        private void onOpenToLan(GameMode gameMode, boolean cheatsAllowed, int port, CallbackInfoReturnable<Boolean> cir) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            BattleManager battleManager = BattleManager.getInstance();

            // Проверка, активен ли забег (Запущен или Заморожен/Ожидание)
            boolean isRunActive = runManager.getStatus() == RunDataManager.RunStatus.RUNNING || 
                                  runManager.getStatus() == RunDataManager.RunStatus.FROZEN;
            
            // Если забег активен И мы НЕ в битве в общем мире (Соло или Раздельные миры)
            if (isRunActive && !battleManager.isSharedWorld()) {
                RandomRunMod.LOGGER.warn("Обнаружено незаконное открытие LAN! Триггер поражения.");
                
                // Отменить открытие LAN
                cir.setReturnValue(false);
                
                // Выполнить логику поражения в потоке клиента
                MinecraftClient.getInstance().execute(() -> {
                    // Двойная проверка статуса в потоке клиента для безопасности
                    if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING || 
                        runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                        
                        long finalTime = runManager.getCurrentTime();
                        runManager.failRun();
                        
                        String reason = Text.translatable("randomrun.defeat.lan_cheat").getString();
                        // Запасной вариант, если перевод отсутствует
                        if (reason.equals("randomrun.defeat.lan_cheat")) {
                            reason = "Обнаружено читерство: Открытие LAN запрещено";
                        }
                        
                        Item targetItem = runManager.getTargetItem();
                        Identifier targetAdvancement = runManager.getTargetAdvancementId();
                        
                        if (targetItem != null) {
                            MinecraftClient.getInstance().setScreen(new DefeatScreen(
                                targetItem,
                                finalTime,
                                reason
                            ));
                        } else if (targetAdvancement != null) {
                             MinecraftClient.getInstance().setScreen(new DefeatScreen(
                                targetAdvancement,
                                finalTime,
                                reason
                            ));
                        }
                        
                        if (MinecraftClient.getInstance().player != null) {
                            MinecraftClient.getInstance().player.sendMessage(
                                Text.literal("§c§l[RandomRun] §cОткрытие LAN запрещено!"), 
                                false
                            );
                        }
                    }
                });
            }
        }
    }
}
