package com.randomrun.mixin;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class PlayerDeathMixin {
    
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void onDeath(net.minecraft.entity.damage.DamageSource source, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!(entity instanceof ClientPlayerEntity)) return;
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING ||
            runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            
            // Stop timer and fail run
            long finalTime = runManager.getCurrentTime();
            runManager.failRun();
            
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client != null && runManager.getTargetItem() != null) {
                // Cancel respawn screen and show defeat screen immediately
                client.execute(() -> {
                    client.setScreen(new com.randomrun.gui.screen.DefeatScreen(
                        runManager.getTargetItem(), 
                        finalTime,
                        net.minecraft.text.Text.translatable("randomrun.defeat.death").getString()
                    ));
                });
            }
        }
    }
}
