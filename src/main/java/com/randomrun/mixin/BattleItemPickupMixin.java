package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public class BattleItemPickupMixin {
    
    private int tickCounter = 0;
    
    @Inject(method = "tick", at = @At("TAIL"))
    private void onPlayerTick(CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;
        
        // Запуск только на стороне клиента и ТОЛЬКО для основного игрока
        // Это предотвращает сообщение хоста о победе других игроков в общем мире
        if (!player.getWorld().isClient) return;
        
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client.player == null || !player.getUuid().equals(client.player.getUuid())) {
            return;
        }
        
        // DEBUG LOGGING
        // RandomRunMod.LOGGER.info("BattleItemPickupMixin: Проверка подбора предмета для " + player.getName().getString());
        
        // Оптимизация: проверка только раз в секунду (20 тиков) вместо каждого тика
        if (++tickCounter < 20) return;
        tickCounter = 0;
        
        BattleManager battleManager = BattleManager.getInstance();
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (battleManager.isInBattle()) {
            if (runManager.getStatus() != RunDataManager.RunStatus.RUNNING) {
                // Еще не запущено, пропуск проверки
                return;
            }
            
            for (ItemStack stack : player.getInventory().main) {
                if (stack != null && !stack.isEmpty() && runManager.checkItemPickup(stack.getItem())) {
                    long elapsedTime = runManager.getCurrentTime();
                    
                    RandomRunMod.LOGGER.info("═══════════════════════════════════");
                    RandomRunMod.LOGGER.info("🏆 ЦЕЛЕВОЙ ПРЕДМЕТ НАЙДЕН В ИНВЕНТАРЕ!");
                    RandomRunMod.LOGGER.info("  - Предмет: " + stack.getItem().getName().getString());
                    RandomRunMod.LOGGER.info("  - Время: " + elapsedTime + "мс");
                    RandomRunMod.LOGGER.info("═══════════════════════════════════");
                    
                    runManager.completeRun();
                    battleManager.reportVictory(elapsedTime);
                    
                    break;
                }
            }
        }
    }
}
