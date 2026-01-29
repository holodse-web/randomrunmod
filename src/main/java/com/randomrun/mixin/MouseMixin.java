package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    
    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        
        // Block mouse clicks when frozen (except for GUI)
        if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            
            // Allow clicks if a screen is open
            if (client.currentScreen != null) {
                return;
            }
            
            // Block attack and use clicks
            if (action == 1) { // GLFW_PRESS
                ci.cancel();
            }
        }
    }
}
