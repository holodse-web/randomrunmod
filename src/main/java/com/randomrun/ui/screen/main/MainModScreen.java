package com.randomrun.ui.screen.main;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.LanguageDropdownWidget;
import com.randomrun.ui.widget.ToggleSwitchWidget;
import com.randomrun.ui.widget.styled.ButtonMenu;
import com.randomrun.ui.screen.settings.SettingsScreen;
import com.randomrun.challenges.advancement.screen.AchievementSelectionScreen;
import com.randomrun.challenges.classic.screen.SpeedrunScreen;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import com.randomrun.ui.widget.SupportWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.gui.widget.ClickableWidget;
import java.util.ArrayList;
import java.util.List;

public class MainModScreen extends AbstractRandomRunScreen {
    private static final Identifier LOGO_TEXTURE = Identifier.of(RandomRunMod.MOD_ID, "textures/gui/logo.png");
    
    private final List<ClickableWidget> mainWidgets = new ArrayList<>();
    
    private final Screen parent;
    private float logoScale = 1.0f;
    private float targetLogoScale = 1.0f;
    private boolean logoHovered = false;
    private float fadeProgress = 0f;
    private long openTime;
    private static final float FADE_SPEED = 0.08f;
    
    private static final int LOGO_WIDTH = 100;
    private static final int LOGO_HEIGHT = 50;
    
    // Support Button (Top Left)
    private static final Identifier HEART_ICON = Identifier.of("minecraft", "textures/item/poppy.png"); // Placeholder or use custom texture
    private boolean supportHovered = false;
    private SupportWidget supportWidget;
    
    private static boolean profileLoaded = false;
    
    public MainModScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.main.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        // Проверка первого запуска
        if (RandomRunMod.getInstance().getConfig().isFirstRun()) {
            MinecraftClient.getInstance().setScreen(new FirstLaunchScreen(this));
            return;
        }

        // Убедиться, что язык корректен при инициализации экрана
        com.randomrun.main.util.LanguageManager.ensureLanguage();

        // FIX: Clear pending run data when in main menu to prevent interference with regular worlds
        if (this.client != null && this.client.world == null) {
             com.randomrun.challenges.classic.world.WorldCreator.clearPendingData();
             if (RandomRunMod.getInstance().getRunDataManager() != null) {
                 RandomRunMod.getInstance().getRunDataManager().cancelRun();
             }
        }
        
        super.init();
        mainWidgets.clear();
        openTime = System.currentTimeMillis();
        fadeProgress = 0f;
        
        // Initialize Support Widget
        supportWidget = new SupportWidget(MinecraftClient.getInstance(), width, height, () -> {
            setMainWidgetsVisible(true);
        });
        
        // Auto-load profile if online and not loaded yet
        if (!profileLoaded && RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            com.randomrun.main.data.PlayerProfile.load(playerName).thenRun(() -> {
                 if (RandomRunMod.getInstance().getRunDataManager() != null) {
                     RandomRunMod.getInstance().getRunDataManager().reloadResults();
                 }
            });
            profileLoaded = true;
        }
        
        int centerX = width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = height / 2 - 10;
        int spacing = 25;
        
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        
       
        mainWidgets.add(addDrawableChild(new ButtonMenu(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.button.speedrun"),
            button -> {
                if (RandomRunMod.getInstance().getConfig().isAchievementChallengeEnabled()) {
                    MinecraftClient.getInstance().setScreen(new AchievementSelectionScreen(this));
                } else {
                    MinecraftClient.getInstance().setScreen(new SpeedrunScreen(this));
                }
            },
            0, 0.1f
        )));
        
      
        mainWidgets.add(addDrawableChild(new ButtonMenu(
            centerX - buttonWidth / 2, startY + spacing,
            buttonWidth, buttonHeight,
            Text.literal(playerName),
            button -> MinecraftClient.getInstance().setScreen(new ResultsScreen(this)),
            1, 0.15f
        )));
        
     
        mainWidgets.add(addDrawableChild(new ButtonMenu(
            centerX - buttonWidth / 2, startY + spacing * 2,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.button.settings"),
            button -> MinecraftClient.getInstance().setScreen(new SettingsScreen(this)),
            2, 0.2f
        )));
        
      
        mainWidgets.add(addDrawableChild(new ButtonMenu(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            3, 0.25f
        )));
        
        mainWidgets.add(addDrawableChild(new LanguageDropdownWidget(
            10, 10,
            this,
            0.35f
        )));

        // Рычаг онлайн режима
        ModConfig config = RandomRunMod.getInstance().getConfig();
        // Верхний правый угол экрана
        int leverX = width - 50; 
        int leverY = 10;
        
        mainWidgets.add(addDrawableChild(new ToggleSwitchWidget(
            leverX, leverY,
            config.isOnlineMode(),
            () -> {
                config.setOnlineMode(!config.isOnlineMode());
                RandomRunMod.getInstance().saveConfig();
            }
        )));
        
        // Support Button Widget (Top Center)
        // Positioned between Language and Online Toggle
        int supportWidth = 100;
        int supportX = (width - supportWidth) / 2;
        int supportY = 10;
        
        mainWidgets.add(addDrawableChild(new ButtonMenu(
            supportX, supportY,
            supportWidth, 20,
            Text.translatable("randomrun.button.support"),
            button -> {
                supportWidget.show();
                setMainWidgetsVisible(false);
            },
            4, 0.3f
        )));
        
        // Add Support Widget last to render on top
        addDrawableChild(supportWidget);
    }

    private void setMainWidgetsVisible(boolean visible) {
        for (ClickableWidget widget : mainWidgets) {
            widget.visible = visible;
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long elapsed = System.currentTimeMillis() - openTime;
        fadeProgress = Math.min(1.0f, elapsed / 1000.0f * (1.0f / FADE_SPEED));
        
        // Рендеринг фона и виджетов через super.render
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Рендеринг базового фона (градиент, частицы)
        super.renderBackground(context, mouseX, mouseY, delta);
        
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        
        int logoX = width / 2 - LOGO_WIDTH / 2;
        int logoY = height / 2 - 95;
        int contentTop = logoY - 15;
        int contentBottom = height / 2 + 85;
        int contentLeft = width / 2 - 120;
        int contentRight = width / 2 + 120;
        
        // Тень
        for (int i = 0; i < 10; i++) {
            int shadowAlpha = (int) ((10 - i) * 2.5f * fadeProgress);
            context.fill(contentLeft - i, contentTop - i, contentRight + i, contentBottom + i, (shadowAlpha << 24));
        }
        
        // Фоновая панель
        int bgAlpha = (int) (0x45 * fadeProgress); 
        int purpleTint = 0x1a0a2e; 
        int r = (purpleTint >> 16) & 0xFF;
        int g = (purpleTint >> 8) & 0xFF;
        int b = purpleTint & 0xFF;
        context.fill(contentLeft, contentTop, contentRight, contentBottom, (bgAlpha << 24) | (r << 16) | (g << 8) | b);
        
        // Рамка
        renderAnimatedBorder(context, contentLeft, contentTop, contentRight, contentBottom, 2);
        
        // Логотип
        logoHovered = mouseX >= logoX && mouseX <= logoX + LOGO_WIDTH &&
                      mouseY >= logoY && mouseY <= logoY + LOGO_HEIGHT;
        targetLogoScale = logoHovered ? 1.08f : 1.0f;
        logoScale = MathHelper.lerp(delta * 0.2f, logoScale, targetLogoScale);
        renderLogo(context, logoX, logoY);
        
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
    
    
    private void renderLogo(DrawContext context, int x, int y) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        
        context.getMatrices().push();
        
    
        float centerX = x + LOGO_WIDTH / 2f;
        float centerY = y + LOGO_HEIGHT / 2f;
        
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(logoScale, logoScale, 1.0f);
        context.getMatrices().translate(-centerX, -centerY, 0);
        
      
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, fadeProgress);
        
      
        try {
            context.drawTexture(RenderLayer::getGuiTextured, LOGO_TEXTURE, x, y, 0.0f, 0.0f, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
        } catch (Exception e) {
         
            String title = "RandomRun";
            context.drawCenteredTextWithShadow(textRenderer, title, 
                (int)centerX, (int)centerY, 0x6930c3);
        }
        
        context.getMatrices().pop();
    }
    
    public static void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom, int borderWidth) {
        float time = (System.currentTimeMillis() % 2000) / 2000f;
        
        // Рисуем 4 сплошных прямоугольника для рамки
        int color = getStaticBorderColor(time);
        
        // Верх
        context.fill(left, top, right, top + borderWidth, color);
        // Низ
        context.fill(left, bottom - borderWidth, right, bottom, color);
        // Лево
        context.fill(left, top, left + borderWidth, bottom, color);
        // Право
        context.fill(right - borderWidth, top, right, bottom, color);
    }
    
    private static int getStaticBorderColor(float t) {
        int alpha = 255 << 24;
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        int r = (int) MathHelper.lerp(factor, 105, 255);
        int g = (int) MathHelper.lerp(factor, 48, 255);
        int b = (int) MathHelper.lerp(factor, 195, 255);
        return alpha | (r << 16) | (g << 8) | b;
    }
    
    // Удален неиспользуемый метод renderAnimatedBorder(..., boolean)
    
    // Удален неиспользуемый метод lerpBorderColor(float)
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // If support widget is visible, only allow interaction with it
        if (supportWidget != null && supportWidget.isVisible()) {
            return supportWidget.mouseClicked(mouseX, mouseY, button);
        }
     
        if (modInfoWidget != null && modInfoWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
