package com.randomrun.mixin;

import com.randomrun.features.voice.control.VoiceController;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** During a voice run the keyboard and mouse stop controlling the player; the voice does. */
public class VoiceInputMixin {
    @Mixin(net.minecraft.client.KeyMapping.class)
    public static class Keys {
        @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
        private void randomrun$voiceIsDown(CallbackInfoReturnable<Boolean> cir) {
            Boolean value = VoiceController.get().keyDown((net.minecraft.client.KeyMapping) (Object) this);
            if (value != null) cir.setReturnValue(value);
        }

        @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
        private void randomrun$voiceConsumeClick(CallbackInfoReturnable<Boolean> cir) {
            Boolean value = VoiceController.get().consumeClick((net.minecraft.client.KeyMapping) (Object) this);
            if (value != null) cir.setReturnValue(value);
        }
    }

    @Mixin(net.minecraft.client.MouseHandler.class)
    public static class Mouse {
        @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
        private void randomrun$voiceNoMouseLook(double movementTime, CallbackInfo ci) {
            if (VoiceController.get().blocksMouse()) ci.cancel();
        }

        @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
        private void randomrun$voiceNoScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
            if (!VoiceController.get().blocksMouse()) return;
            var screen = Minecraft.getInstance().gui.screen();
            if (screen == null || screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) ci.cancel();
        }
    }

    @Mixin(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class)
    public static class Containers {
        @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
        private void randomrun$voiceNoClicks(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
            if (VoiceController.get().blocksMouse()) cir.setReturnValue(true);
        }

        @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
        private void randomrun$voiceNoRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
            if (VoiceController.get().blocksMouse()) cir.setReturnValue(true);
        }

        @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
        private void randomrun$voiceNoDrag(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
            if (VoiceController.get().blocksMouse()) cir.setReturnValue(true);
        }

        @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
        private void randomrun$voiceKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
            if (!VoiceController.get().blocksMouse()) return;
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) return;
            if (Minecraft.getInstance().options.keyInventory.matches(event)) return;
            cir.setReturnValue(true);
        }
    }
}
