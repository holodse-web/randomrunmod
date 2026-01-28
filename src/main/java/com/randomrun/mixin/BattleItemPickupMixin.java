package com.randomrun.mixin;

import com.randomrun.RandomRunMod;
import com.randomrun.battle.BattleManager;
import com.randomrun.data.RunDataManager;
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
        
        if (!player.getWorld().isClient) return;
        
        // Optimize: check only once per second (20 ticks) instead of every tick
        if (++tickCounter < 20) return;
        tickCounter = 0;
        
        BattleManager battleManager = BattleManager.getInstance();
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (battleManager.isInBattle()) {
            if (runManager.getStatus() != RunDataManager.RunStatus.RUNNING) {
                // Not running yet, skip check
                return;
            }
            
            for (ItemStack stack : player.getInventory().main) {
                if (stack != null && !stack.isEmpty() && runManager.checkItemPickup(stack.getItem())) {
                    long elapsedTime = runManager.getCurrentTime();
                    
                    RandomRunMod.LOGGER.info("═══════════════════════════════════");
                    RandomRunMod.LOGGER.info("🏆 TARGET ITEM FOUND IN INVENTORY!");
                    RandomRunMod.LOGGER.info("  - Item: " + stack.getItem().getName().getString());
                    RandomRunMod.LOGGER.info("  - Time: " + elapsedTime + "ms");
                    RandomRunMod.LOGGER.info("═══════════════════════════════════");
                    
                    runManager.completeRun();
                    battleManager.reportVictory(elapsedTime);
                    
                    break;
                }
            }
        }
    }
}
