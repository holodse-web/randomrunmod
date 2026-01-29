package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.ui.screen.DefeatScreen;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.world.GameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class GameModeProtectionMixin {
    
    @Inject(method = "setGameMode", at = @At("HEAD"), cancellable = true)
    private void onSetGameMode(GameMode gameMode, CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        // Block gamemode changes during active run
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING ||
            runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            
            // Only allow survival mode
            if (gameMode != GameMode.SURVIVAL) {
                runManager.failRun();
                
                net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
                if (client != null && runManager.getTargetItem() != null) {
                    client.execute(() -> {
                        client.setScreen(new DefeatScreen(
                            runManager.getTargetItem(), 
                            runManager.getCurrentTime(),
                            net.minecraft.text.Text.translatable("randomrun.defeat.gamemode_change").getString()
                        ));
                    });
                }
            }
        }
    }
}
