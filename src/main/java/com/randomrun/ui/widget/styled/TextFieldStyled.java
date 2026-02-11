package com.randomrun.ui.widget.styled;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class TextFieldStyled extends TextFieldWidget {
    private float hoverProgress = 0f;
    private float borderAnimation = 0f;
    private float borderAnimationAlpha = 0f;
    
    // Поля анимации
    private final float delay;
    private float animationProgress = 0f;
    private final long creationTime;
    
    private static final float HOVER_SPEED = 0.15f;
    private static final float BORDER_FADE_SPEED = 0.1f;
    private static final float ANIMATION_DURATION = 400f;
    
    private int baseColor = 0x302b63;
    private int hoverColor = 0x6930c3;
    private Text centeredPlaceholder;
    
    public TextFieldStyled(net.minecraft.client.font.TextRenderer textRenderer, int x, int y, int width, int height, Text message) {
        this(textRenderer, x, y, width, height, message, 0f);
    }

    public TextFieldStyled(net.minecraft.client.font.TextRenderer textRenderer, int x, int y, int width, int height, Text message, float delay) {
        super(textRenderer, x, y, width, height, message);
        this.delay = delay;
        this.creationTime = System.currentTimeMillis();
        // Отключаем стандартную рамку, так как мы рисуем свою
        this.setDrawsBackground(false);
    }
    
    public TextFieldStyled setColors(int baseColor, int hoverColor) {
        this.baseColor = baseColor;
        this.hoverColor = hoverColor;
        return this;
    }
    
    public void setCenteredPlaceholder(Text text) {
        this.centeredPlaceholder = text;
    }
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Обновление анимации появления (slide-up)
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) {
            animationProgress = 0f;
        } else {
            float animElapsed = elapsed - delayMs;
            animationProgress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        }
        
        // Плавное появление (cubic ease out)
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        
        // Расчет смещения (появление снизу)
        int slideOffset = (int) ((1f - easedProgress) * 30);
        float alpha = easedProgress;
        
        if (alpha <= 0) return;

        // Логика наведения (hover) и фокуса
        boolean hovered = isHovered() || isFocused();
        float targetHover = hovered ? 1f : 0f;
        hoverProgress = MathHelper.lerp(HOVER_SPEED, hoverProgress, targetHover);
        
        // Логика анимации обводки (появление при наведении)
        float targetBorderAlpha = hovered ? 1f : 0f;
        borderAnimationAlpha = MathHelper.lerp(BORDER_FADE_SPEED, borderAnimationAlpha, targetBorderAlpha);
        
        // Обновление позиции анимации обводки
        if (hovered) {
            borderAnimation += delta * 0.05f;
            if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        }
        
        // Расчет анимированной позиции
        int animatedY = getY() + slideOffset;

        // Рендеринг фона с эффектом наведения
        int bgColor = lerpColor(baseColor, hoverColor, hoverProgress);
        int bgAlpha = (int) (alpha * 224); // E0 approx 224
        int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF); 
        
        // Отрисовка фона
        context.fill(getX(), animatedY, getX() + width, animatedY + height, finalBgColor);
        
        // Рендеринг анимированной обводки
        renderAnimatedBorder(context, getX(), animatedY, getX() + width, animatedY + height, alpha);
        
        // Сохраняем оригинальный Y
        int originalY = getY();
        // Временно меняем Y для отрисовки текста в правильном месте (с учетом анимации)
        setY(animatedY);
        
        // --- ЦЕНТРИРОВАНИЕ И МАСШТАБИРОВАНИЕ ---
        float scale = 1.2f; // Немного увеличиваем текст
        int yAdjustment = 8; // Смещаем текст ввода НИЖЕ (было 3)
        
        net.minecraft.client.font.TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int textWidth = renderer.getWidth(getText());
        
        // Базовые координаты отрисовки текста внутри виджета (с учетом стандартного отступа 4px)
        float baseTextX = 4.0f; 
        float baseTextY = (height - 8) / 2.0f;
        
        // Целевые координаты (центрированные)
        float targetX = (width - textWidth * scale) / 2.0f;
        float targetY = (height - 8 * scale) / 2.0f + yAdjustment;
        
        // Смещение для матрицы
        float tx = targetX - scale * baseTextX;
        float ty = targetY - scale * baseTextY;
        
        float matrixTx = getX() * (1 - scale) + tx;
        float matrixTy = getY() * (1 - scale) + ty;
        
        context.getMatrices().push();
        context.getMatrices().translate(matrixTx, matrixTy, 0);
        context.getMatrices().scale(scale, scale, 1f);
        
        // Корректируем координаты мыши для корректной работы логики виджета (курсор, выделение)
        int adjustedMouseX = (int)((mouseX - matrixTx) / scale);
        int adjustedMouseY = (int)((mouseY - matrixTy) / scale);
        
        // Рисуем текст через супер-класс
        super.renderWidget(context, adjustedMouseX, adjustedMouseY, delta);
        
        context.getMatrices().pop();
        
        // Рисуем центрированный плейсхолдер (если нет фокуса и текст пустой)
        if (centeredPlaceholder != null && getText().isEmpty() && !isFocused()) {
            // Плейсхолдер
            float placeholderScale = 1f; 
            int placeholderYAdjustment = 0;
            
            int placeholderWidth = renderer.getWidth(centeredPlaceholder);
            
            // Центрируем с учетом масштаба
            float phTargetX = (width - placeholderWidth * placeholderScale) / 2.0f;
            float phTargetY = (height - 8 * placeholderScale) / 2.0f + placeholderYAdjustment;
            
            // Глобальные координаты
            float phMatrixTx = getX() + phTargetX;
            float phMatrixTy = getY() + phTargetY;
            
            context.getMatrices().push();
            context.getMatrices().translate(phMatrixTx, phMatrixTy, 0);
            context.getMatrices().scale(placeholderScale, placeholderScale, 1f);
            
            context.drawTextWithShadow(renderer, centeredPlaceholder, 0, 0, 0xAAAAAA);
            
            context.getMatrices().pop();
        }
        
        // Возвращаем оригинальный Y
        setY(originalY);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Повторяем логику расчета матрицы для корректного хитбокса
        float scale = 1.2f;
        int yAdjustment = 3;
        
        net.minecraft.client.font.TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int textWidth = renderer.getWidth(getText());
        
        float baseTextX = 4.0f; 
        float baseTextY = (height - 8) / 2.0f;
        
        float targetX = (width - textWidth * scale) / 2.0f;
        float targetY = (height - 8 * scale) / 2.0f + yAdjustment;
        
        float tx = targetX - scale * baseTextX;
        float ty = targetY - scale * baseTextY;
        
        // Важно: getX() и getY() здесь могут быть другими, если анимация еще идет? 
        // Нет, mouseClicked вызывается когда виджет уже на месте (обычно).
        // Но в renderWidget мы меняли Y на animatedY. В mouseClicked Y - это текущий Y (конечный).
        // Если анимация еще идет, клики могут быть смещены. Но обычно кликают когда уже появилось.
        // Будем считать Y статичным (конечным).
        
        float matrixTx = getX() * (1 - scale) + tx;
        float matrixTy = getY() * (1 - scale) + ty;
        
        double adjustedMouseX = (mouseX - matrixTx) / scale;
        double adjustedMouseY = (mouseY - matrixTy) / scale;
        
        return super.mouseClicked(adjustedMouseX, adjustedMouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        float scale = 1.2f;
        int yAdjustment = 3;
        net.minecraft.client.font.TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int textWidth = renderer.getWidth(getText());
        float baseTextX = 4.0f; 
        float baseTextY = (height - 8) / 2.0f;
        float targetX = (width - textWidth * scale) / 2.0f;
        float targetY = (height - 8 * scale) / 2.0f + yAdjustment;
        float tx = targetX - scale * baseTextX;
        float ty = targetY - scale * baseTextY;
        float matrixTx = getX() * (1 - scale) + tx;
        float matrixTy = getY() * (1 - scale) + ty;
        
        double adjustedMouseX = (mouseX - matrixTx) / scale;
        double adjustedMouseY = (mouseY - matrixTy) / scale;
        
        // deltaX/Y тоже нужно масштабировать?
        // Логика перемещения курсора зависит от координат. Delta используется для скролла?
        // TextFieldWidget.mouseDragged использует mouseX для выделения.
        return super.mouseDragged(adjustedMouseX, adjustedMouseY, button, deltaX / scale, deltaY / scale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        float scale = 1.2f;
        int yAdjustment = 8;
        net.minecraft.client.font.TextRenderer renderer = MinecraftClient.getInstance().textRenderer;
        int textWidth = renderer.getWidth(getText());
        float baseTextX = 4.0f; 
        float baseTextY = (height - 8) / 2.0f;
        float targetX = (width - textWidth * scale) / 2.0f;
        float targetY = (height - 8 * scale) / 2.0f + yAdjustment;
        float tx = targetX - scale * baseTextX;
        float ty = targetY - scale * baseTextY;
        float matrixTx = getX() * (1 - scale) + tx;
        float matrixTy = getY() * (1 - scale) + ty;
        
        double adjustedMouseX = (mouseX - matrixTx) / scale;
        double adjustedMouseY = (mouseY - matrixTy) / scale;
        
        return super.mouseReleased(adjustedMouseX, adjustedMouseY, button);
    }

    private void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        int borderWidth = 2; // 1.5px (округлено до 2)
        int perimeter = 2 * (right - left) + 2 * (bottom - top);
        
        // Отрисовка сегментов обводки с анимированными цветами
        for (int i = 0; i < perimeter; i++) {
            float progress = (borderAnimation + (float) i / perimeter) % 1.0f;
            int color = lerpBorderColor(progress, alpha);
            
            int x, y;
            if (i < (right - left)) {
                // Верхняя грань
                x = left + i;
                y = top;
            } else if (i < (right - left) + (bottom - top)) {
                // Правая грань
                x = right;
                y = top + (i - (right - left));
            } else if (i < 2 * (right - left) + (bottom - top)) {
                // Нижняя грань
                x = right - (i - (right - left) - (bottom - top));
                y = bottom;
            } else {
                // Левая грань
                x = left;
                y = bottom - (i - 2 * (right - left) - (bottom - top));
            }
            
            context.fill(x, y, x + borderWidth, y + borderWidth, color);
        }
    }
    
    // Использование установленных цветов также для анимации обводки
    private int lerpBorderColor(float t, float alpha) {
        // Базовый фиолетовый цвет
        int purple = hoverColor;
        int white = 0xFFFFFF;
        
        // Использование синусоиды для плавного перехода
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        
        // Интерполяция между фиолетовым и белым на основе позиции анимации и альфа-канала наведения
        int r1 = (purple >> 16) & 0xFF;
        int g1 = (purple >> 8) & 0xFF;
        int b1 = purple & 0xFF;
        
        int r2 = (white >> 16) & 0xFF;
        int g2 = (white >> 8) & 0xFF;
        int b2 = white & 0xFF;
        
        // Применение альфа-канала анимации обводки к белому компоненту
        float animatedFactor = factor * borderAnimationAlpha;
        
        int r = (int) MathHelper.lerp(animatedFactor, r1, r2);
        int g = (int) MathHelper.lerp(animatedFactor, g1, g2);
        int b = (int) MathHelper.lerp(animatedFactor, b1, b2);
        
        int colorAlpha = (int) (alpha * 255);
        return (colorAlpha << 24) | (r << 16) | (g << 8) | b;
    }
    
    private int lerpColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) MathHelper.lerp(t, r1, r2);
        int g = (int) MathHelper.lerp(t, g1, g2);
        int b = (int) MathHelper.lerp(t, b1, b2);
        
        return (r << 16) | (g << 8) | b;
    }
}
