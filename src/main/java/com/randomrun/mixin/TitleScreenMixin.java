package com.randomrun.mixin;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.version.VersionChecker;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.ui.screen.main.UpdateRequiredScreen;
import com.randomrun.ui.widget.styled.ButtonMenu;
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
        // Очистка данных спидрана при входе в главное меню
        com.randomrun.challenges.classic.world.WorldCreator.clearPendingData();
        
        int buttonWidth = 100;
        int buttonHeight = 20;
        int x = 10;
        int y = 10;
        
        // Получаем VersionChecker
        VersionChecker checker = VersionChecker.getInstance();
        
        RandomRunMod.LOGGER.info("=== Отладка нажатия кнопки TitleScreen ===");
        RandomRunMod.LOGGER.info("Проверка завершена: " + checker.isCheckCompleted());
        RandomRunMod.LOGGER.info("Требуется обновление: " + checker.isUpdateRequired());
        RandomRunMod.LOGGER.info("Текущая версия: " + checker.getCurrentVersion());
        RandomRunMod.LOGGER.info("Последняя версия: " + checker.getLatestVersion());
        
        this.addDrawableChild(new ButtonMenu(
            x, y, buttonWidth, buttonHeight,
            Text.literal("RandomRun"),
            button -> {
                if (this.client != null) {
                    RandomRunMod.LOGGER.info("=== Нажата кнопка RandomRun ===");
                    RandomRunMod.LOGGER.info("Требуется обновление: " + checker.isUpdateRequired());
                    
                    // Проверяем версию
                    if (checker.isUpdateRequired()) {
                        // Версия устарела - показываем экран обновления
                        RandomRunMod.LOGGER.info("Открытие экрана UpdateRequiredScreen");
                        this.client.setScreen(new UpdateRequiredScreen());
                    } else {
                        // Версия актуальна - открываем мод
                        RandomRunMod.LOGGER.info("Открытие экрана MainModScreen");
                        this.client.setScreen(new MainModScreen(this));
                    }
                }
            },
            0, 0.1f
        ));
    }
}
