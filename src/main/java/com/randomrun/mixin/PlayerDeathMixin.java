package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.ui.screen.endgame.DefeatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.randomrun.battle.BattleManager;

@Mixin(LivingEntity.class)
public class PlayerDeathMixin {
    
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(net.minecraft.entity.damage.DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof ClientPlayerEntity)) return;
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING) {
            
            // Проверка, находимся ли мы в битве (ЛЮБОЙ: Общий мир или Разные миры)
            BattleManager battleManager = BattleManager.getInstance();
            boolean isBattle = battleManager.isInBattle();
            
            // Если в битве, обработка логики продолжения (авто-возрождение)
            if (isBattle) {
                // Убедиться, что забег продолжается
                if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                    runManager.unfreezeRun(); 
                }
                
                // АВТОМАТИЧЕСКОЕ ВОЗРОЖДЕНИЕ (Auto-Respawn)
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client.player != null) {
                    RandomRunMod.LOGGER.info("Игрок погиб в онлайн битве - Автоматическое возрождение...");
                    
                    // Отправить пакет возрождения
                    client.player.requestRespawn();
                    
                    // Отправить уведомление о смерти в чат/события (но НЕ исключать из игры)
                    battleManager.reportDeathEvent(client.player.getName().getString());
                    
                    // Закрыть экран смерти (если он успел открыться)
                    client.setScreen(null);
                    
                    return; 
                }
            }
            
            // Остановить таймер и провалить забег
            long finalTime = runManager.getCurrentTime();
            runManager.failRun();
            
            // Сообщить о поражении, если в битве (не общей)
            if (battleManager.isInBattle()) {
                battleManager.reportDefeat("Death");
            }
            
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && (runManager.getTargetItem() != null || runManager.getTargetAdvancementId() != null)) {
                // Отменить экран возрождения и сразу показать экран поражения
                client.execute(() -> {
                    if (runManager.getTargetItem() != null) {
                        client.setScreen(new DefeatScreen(
                            runManager.getTargetItem(),
                            finalTime,
                            net.minecraft.text.Text.translatable("randomrun.defeat.death").getString()
                        ));
                    } else {
                        client.setScreen(new DefeatScreen(
                            runManager.getTargetAdvancementId(),
                            finalTime,
                            net.minecraft.text.Text.translatable("randomrun.defeat.death").getString()
                        ));
                    }
                });
            }
        }
    }
}
