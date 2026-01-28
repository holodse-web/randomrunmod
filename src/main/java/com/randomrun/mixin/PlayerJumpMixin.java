package com.randomrun.mixin;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class PlayerJumpMixin {
    
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        
        if (entity instanceof net.minecraft.client.network.ClientPlayerEntity) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            
            if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                ci.cancel();
            }
        }
    }
}
