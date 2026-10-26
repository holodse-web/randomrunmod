package com.randomrun.mixin.ui;

import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.ui.widget.styled.ButtonMenu;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void addRandomRunButton(CallbackInfo ci) {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int x = 10;
        int y = 10;
        
        this.addRenderableWidget(new ButtonMenu(
            x, y, buttonWidth, buttonHeight,
            Component.literal("RandomRun"),
            button -> {
                if (this.minecraft != null) {
                    this.minecraft.gui.setScreen(new MainModScreen(this));
                }
            },
            0, 0.1f
        ));
    }
}
