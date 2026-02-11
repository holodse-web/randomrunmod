package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    
    protected GameMenuScreenMixin(Text title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onMenuOpen(CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING) {
            runManager.pauseRun();
        }
    }
    
    @Inject(method = "disconnect", at = @At("HEAD"), cancellable = true)
    private void onDisconnect(CallbackInfo ci) {
        BattleManager battleManager = BattleManager.getInstance();
        BattleRoom room = battleManager.getCurrentRoom();
        
        // Проверка, находимся ли мы в битве в общем мире как ХОСТ и игра все еще идет
        if (room != null && room.isSharedWorld() && battleManager.isHost()) {
            // Проверка, идет ли игра (не закончена)
            if (room.getStatus() == BattleRoom.RoomStatus.STARTED && room.getWinner() == null) {
                // Показать предупреждающее сообщение
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("§c§l⚠ Вы хост! Выход завершит игру для всех игроков!"),
                        false
                    );
                    client.player.sendMessage(
                        Text.literal("§7Дождитесь окончания игры или нажмите ESC → Выход ещё раз для подтверждения."),
                        false
                    );
                }
                
                // Отмена первой попытки выхода - требуется двойной клик
                if (!battleManager.isDisconnectConfirmed()) {
                    battleManager.setDisconnectConfirmed(true);
                    ci.cancel();
                    // Закрыть меню
                    MinecraftClient.getInstance().setScreen(null);
                }
            }
        }
    }
}
