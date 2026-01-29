package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.version.VersionChecker;
import com.randomrun.ui.screen.MainModScreen;
import com.randomrun.ui.screen.UpdateRequiredScreen;
import com.randomrun.ui.widget.StyledButton;
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
        
        RandomRunMod.LOGGER.info("=== TitleScreen Button Click Debug ===");
        RandomRunMod.LOGGER.info("Check completed: " + checker.isCheckCompleted());
        RandomRunMod.LOGGER.info("Update required: " + checker.isUpdateRequired());
        RandomRunMod.LOGGER.info("Current version: " + checker.getCurrentVersion());
        RandomRunMod.LOGGER.info("Latest version: " + checker.getLatestVersion());
        
        this.addDrawableChild(new StyledButton(
            x, y, buttonWidth, buttonHeight,
            Text.literal("RandomRun"),
            button -> {
                if (this.client != null) {
                    RandomRunMod.LOGGER.info("=== RandomRun Button Clicked ===");
                    RandomRunMod.LOGGER.info("Update required: " + checker.isUpdateRequired());
                    
                    // Проверяем версию
                    if (checker.isUpdateRequired()) {
                        // Версия устарела - показываем экран обновления
                        RandomRunMod.LOGGER.info("Opening UpdateRequiredScreen");
                        this.client.setScreen(new UpdateRequiredScreen());
                    } else {
                        // Версия актуальна - открываем мод
                        RandomRunMod.LOGGER.info("Opening MainModScreen");
                        this.client.setScreen(new MainModScreen(this));
                    }
                }
            },
            0, 0.1f
        ));
    }
}
