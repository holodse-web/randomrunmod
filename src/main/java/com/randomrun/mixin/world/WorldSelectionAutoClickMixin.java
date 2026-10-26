package com.randomrun.mixin.world;

import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.infrastructure.RandomRunMod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SelectWorldScreen.class)
public class WorldSelectionAutoClickMixin {
    @Inject(method = "init", at = @At("TAIL"))
    private void autoClickCreate(CallbackInfo ci) {
        if (!WorldCreator.hasPendingRun()) return;
        
        for (net.minecraft.client.gui.components.events.GuiEventListener element : ((net.minecraft.client.gui.screens.Screen)(Object)this).children()) {
            if (!(element instanceof Button)) continue;
            
            Button button = (Button) element;
            
            if (isCreateButton(button)) {
                button.onPress(new net.minecraft.client.input.KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
                return;
            }
        }
        
        RandomRunMod.LOGGER.warn("[AutoClick] Кнопка не найдена");
    }
    
    private boolean isCreateButton(Button button) {
        Component message = button.getMessage();
        
        if (message.getContents() instanceof TranslatableContents) {
            String key = ((TranslatableContents) message.getContents()).getKey();
            if ("selectWorld.create".equals(key)) {
                return true;
            }
        }
        
        String text = message.getString();
        return text.equals("Create New World") || text.equals("Создать новый мир");
    }
}
