package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class PlayerSprintMixin {
    
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void onSetSprinting(boolean sprinting, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof net.minecraft.client.network.ClientPlayerEntity)) return;
        if (sprinting) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            
            if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                ci.cancel();
            }
        }
    }
}
