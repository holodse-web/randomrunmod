package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class DebugHudMixin {

    private static int logCounter = 0;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (logCounter++ % 100 == 0) { // Log every ~5 seconds (assuming 20fps logic or 60fps render)
             InGameHud hud = (InGameHud)(Object)this;
             // We can't easily access private fields like hudHidden without Accessor, 
             // but we can check if we are in spectator via client
             net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
             boolean hudHidden = client.options.hudHidden;
             boolean isSpectator = client.interactionManager != null && client.interactionManager.getCurrentGameMode() == net.minecraft.world.GameMode.SPECTATOR;
             
             RandomRunMod.LOGGER.info("🔍 InGameHud.render() - hudHidden: " + hudHidden + ", isSpectator: " + isSpectator);
        }
    }

    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbarHead(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        
        // Скрыть хотбар, если открыт экран победы/поражения/ничьей
        if (client.currentScreen instanceof com.randomrun.ui.screen.endgame.VictoryScreen ||
            client.currentScreen instanceof com.randomrun.ui.screen.endgame.DefeatScreen ||
            client.currentScreen instanceof com.randomrun.ui.screen.endgame.DrawScreen) {
            ci.cancel();
            return;
        }

        if (logCounter % 100 == 0) {
            RandomRunMod.LOGGER.info("🔍 InGameHud.renderHotbar() CALLED");
        }
    }
}
