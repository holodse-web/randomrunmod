package com.randomrun.mixin;

import com.randomrun.gui.screen.MainModScreen;
import com.randomrun.gui.screen.UpdateRequiredScreen;
import com.randomrun.gui.widget.StyledButton;
import com.randomrun.version.VersionChecker;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    
    protected TitleScreenMixin(Text title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void addRandomRunButton(CallbackInfo ci) {
        int buttonWidth = 100;
        int buttonHeight = 20;
        int x = 10;
        int y = 10;
        
        // Получаем VersionChecker
        VersionChecker checker = VersionChecker.getInstance();
        
        com.randomrun.RandomRunMod.LOGGER.info("=== TitleScreen Button Click Debug ===");
        com.randomrun.RandomRunMod.LOGGER.info("Check completed: " + checker.isCheckCompleted());
        com.randomrun.RandomRunMod.LOGGER.info("Update required: " + checker.isUpdateRequired());
        com.randomrun.RandomRunMod.LOGGER.info("Current version: " + checker.getCurrentVersion());
        com.randomrun.RandomRunMod.LOGGER.info("Latest version: " + checker.getLatestVersion());
        
        this.addDrawableChild(new StyledButton(
            x, y, buttonWidth, buttonHeight,
            Text.literal("RandomRun"),
            button -> {
                if (this.client != null) {
                    com.randomrun.RandomRunMod.LOGGER.info("=== RandomRun Button Clicked ===");
                    com.randomrun.RandomRunMod.LOGGER.info("Update required: " + checker.isUpdateRequired());
                    
                    // Проверяем версию
                    if (checker.isUpdateRequired()) {
                        // Версия устарела - показываем экран обновления
                        com.randomrun.RandomRunMod.LOGGER.info("Opening UpdateRequiredScreen");
                        this.client.setScreen(new UpdateRequiredScreen());
                    } else {
                        // Версия актуальна - открываем мод
                        com.randomrun.RandomRunMod.LOGGER.info("Opening MainModScreen");
                        this.client.setScreen(new MainModScreen(this));
                    }
                }
            },
            0, 0.1f
        ));
    }
}
