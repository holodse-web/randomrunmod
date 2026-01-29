package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.ui.screen.DefeatScreen;
import com.randomrun.challenges.classic.screen.SpeedrunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
public class DeathScreenMixin {
    
    // Injected into DeathScreen constructor
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onDeathScreenInit(Text message, boolean isHardcore, CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        // If there's an active run, close this death screen and show defeat screen instead
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING ||
            runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && (runManager.getTargetItem() != null || runManager.getTargetAdvancementId() != null)) {
                // Close death screen and show defeat screen
                client.execute(() -> {
                    long finalTime = runManager.getCurrentTime();
                    runManager.failRun();
                    
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
