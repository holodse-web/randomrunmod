package com.randomrun.mixin.ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class EndgameHudHiderMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void randomrun$hideHudForUpgrader(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen()
                instanceof com.randomrun.features.upgrader.client.UpgraderScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "extractDebugOverlay", at = @At("HEAD"), cancellable = true)
    private void randomrun$hideDebugForUpgrader(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (net.minecraft.client.Minecraft.getInstance().gui.screen()
                instanceof com.randomrun.features.upgrader.client.UpgraderScreen) {
            ci.cancel();
        }
    }
    @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbarHead(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        
        if (client.gui.screen() instanceof com.randomrun.ui.screen.endgame.VictoryScreen ||
            client.gui.screen() instanceof com.randomrun.ui.screen.endgame.DefeatScreen ||
            client.gui.screen() instanceof com.randomrun.ui.screen.endgame.DrawScreen) {
            ci.cancel();
            return;
        }
    }
}
