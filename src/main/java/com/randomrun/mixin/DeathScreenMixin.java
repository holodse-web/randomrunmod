package com.randomrun.mixin;

import com.randomrun.RandomRunMod;
import com.randomrun.data.RunDataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public class DeathScreenMixin {
    
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onDeathScreenInit(Text message, boolean isHardcore, CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        // If there's an active run, close this death screen and show defeat screen instead
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING ||
            runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && runManager.getTargetItem() != null) {
                // Close death screen and show defeat screen
                client.execute(() -> {
                    long finalTime = runManager.getCurrentTime();
                    runManager.failRun();
                    
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
