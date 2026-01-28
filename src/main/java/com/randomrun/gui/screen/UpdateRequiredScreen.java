package com.randomrun.gui.screen;

import com.randomrun.gui.widget.ModInfoWidget;
import com.randomrun.version.VersionChecker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class UpdateRequiredScreen extends AbstractRandomRunScreen {
    private ModInfoWidget modInfoWidget;
    
    public UpdateRequiredScreen() {
        super(Text.literal("Обновление требуется"));
    }
    
    @Override
    protected void init() {
        super.init();
        modInfoWidget = new ModInfoWidget(width, height, textRenderer);
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Вызываем родительский render для фона с партиклами
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        // Заголовок
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("§c§lОБНОВИТЕ МОД"),
            centerX,
            centerY - 60,
            0xFF5555
        );
        
        // Текущая версия
        String currentVersion = "Ваша версия: §e" + VersionChecker.getInstance().getCurrentVersion();
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal(currentVersion),
            centerX,
            centerY - 20,
            0xFFFFFF
        );
        
        // Требуемая версия
        String requiredVersion = "Требуется версия: §a" + VersionChecker.getInstance().getLatestVersion();
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal(requiredVersion),
            centerX,
            centerY + 10,
            0xFFFFFF
        );
        
        // Инструкция
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal("§7Пожалуйста, обновите мод до последней версии"),
            centerX,
            centerY + 50,
            0xAAAAAA
        );
        
        // Render mod info widget (version and Telegram link)
        if (modInfoWidget != null) {
            modInfoWidget.render(context, mouseX, mouseY, delta);
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check mod info widget click
        if (modInfoWidget != null && modInfoWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true; // Можно закрыть через ESC
    }
    
    @Override
    public void close() {
        // При закрытии возвращаемся на титульный экран
        if (this.client != null) {
            this.client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
        }
    }
}
