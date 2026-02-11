package com.randomrun.challenges.advancement.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.challenges.advancement.data.AdvancementLoader;
import com.randomrun.challenges.time.screen.TimeSelectionScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.HebayterWidget;
import com.randomrun.ui.widget.RevealAnimationWidget;
import com.randomrun.challenges.classic.world.WorldCreator;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class AchievementRevealScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final AdvancementLoader.AdvancementInfo targetAdvancement;
    
    private RevealAnimationWidget revealWidget;
    private HebayterWidget creditWidget;
    
    public AchievementRevealScreen(Screen parent, AdvancementLoader.AdvancementInfo advancement) {
        super(Text.translatable("randomrun.screen.achievement_reveal.title"));
        this.parent = parent;
        this.targetAdvancement = advancement;
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int buttonY = height - 55;
        
        revealWidget = new RevealAnimationWidget(centerX, height / 2 - 40, targetAdvancement.icon);
        addSelectableChild(revealWidget);
        
        boolean timeChallengeEnabled = RandomRunMod.getInstance().getConfig().isTimeChallengeEnabled();
        
        if (timeChallengeEnabled) {
            // Time Challenge button
            // Кнопка испытания временем
            addDrawableChild(new ButtonDefault(
                centerX - 100, buttonY,
                200, 20,
                Text.translatable("randomrun.button.select_time"),
                button -> {
                    MinecraftClient.getInstance().setScreen(new TimeSelectionScreen(this, targetAdvancement));
                }
            ));
        } else {
            // Use Start Speedrun button
            // Кнопка начала спидрана
            addDrawableChild(new ButtonDefault(
                centerX - 100, buttonY,
                200, 20,
                Text.translatable("randomrun.button.start_speedrun"),
                button -> {
                    startSpeedrun(0);
                }
            ));
        }
        
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30, 
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> close()
        ));
        
        creditWidget = new HebayterWidget(10, height - 20, Text.translatable("randomrun.credit.idea").getString(), "https://www.tiktok.com/@hebayter?_r=1&_t=ZS-93TaWwDu10z");
    }
    
    public void startSpeedrun(long timeLimitMs) {
        startSpeedrunWithSeed(timeLimitMs, null);
    }
    
    public void startSpeedrunWithSeed(long timeLimitMs, String seed) {
        var runManager = RandomRunMod.getInstance().getRunDataManager();
        runManager.startNewRun(targetAdvancement.id, timeLimitMs, "pending");
        
        // Save advancement display info for HUD (before advancement is obtained)
        // Сохранить информацию о достижении для HUD (до получения достижения)
        String displayName = targetAdvancement.title.getString();
        net.minecraft.item.Item iconItem = targetAdvancement.icon.getItem();
        runManager.setAdvancementDisplayInfo(displayName, iconItem);
        
        WorldCreator.createSpeedrunWorld(targetAdvancement.id, timeLimitMs, seed);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (revealWidget != null && revealWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (creditWidget != null && creditWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (revealWidget != null && revealWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (revealWidget != null && revealWidget.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderGradientBackground(context);
        
        if (revealWidget != null) {
            revealWidget.render(context, mouseX, mouseY, delta);
        }
        
        context.drawCenteredTextWithShadow(textRenderer, targetAdvancement.title, width / 2, height / 2 + 40, 0xFFFFFF);
        // Draw description
        // Отрисовка описания
        int maxWidth = Math.min(width - 40, 300);
        java.util.List<net.minecraft.text.OrderedText> lines = textRenderer.wrapLines(targetAdvancement.description, maxWidth);
        int yOffset = height / 2 + 55;
        
        for (net.minecraft.text.OrderedText line : lines) {
            context.drawCenteredTextWithShadow(textRenderer, line, width / 2, yOffset, 0xAAAAAA);
            yOffset += textRenderer.fontHeight + 2;
        }
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.translatable("randomrun.drag_to_rotate"), 
            width / 2, yOffset + 15, 0x666666); 
        
        super.render(context, mouseX, mouseY, delta);
        
        if (creditWidget != null) {
            creditWidget.render(context, mouseX, mouseY, delta);
        }
    }
    
    @Override
    public void close() {
        if (parent instanceof AchievementSelectionScreen) {
            AchievementSelectionScreen screen = (AchievementSelectionScreen) parent;
            screen.resetSlotMachine();
            MinecraftClient.getInstance().setScreen(screen);
        } else {
            MinecraftClient.getInstance().setScreen(parent);
        }
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
