package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class BlockBreakMixin {
    
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            cir.setReturnValue(false);
        }
    }
    
    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void onUpdateBlockBreaking(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            cir.setReturnValue(false);
        }
    }
}
