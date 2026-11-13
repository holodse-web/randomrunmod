package com.randomrun.ui.screen.main;

import com.randomrun.ui.util.BackgroundRenderer;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.OverlayWidgets.ModInfoWidget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AbstractRandomRunScreen extends Screen implements com.randomrun.ui.util.UiScale.Scaled {
    protected ModInfoWidget modInfoWidget;
    private static final float TRANSITION_MS = 340f;
    private long shownAt;
    private boolean contentTransformed;

    private int savedGuiScale = -1;
    private boolean shouldRestoreScale = false;
    private static boolean isApplyingGuiScale = false;

    private static int calculateOptimalGuiScale(Minecraft client) {
        if (client == null || client.getWindow() == null) return 3;

        int width = client.getWindow().getScreenWidth();
        int height = client.getWindow().getScreenHeight();

        if (width >= 3840 || height >= 2160) {
            return 2;
        }
        else if (width >= 2560 || height >= 1440) {
            return 2;
        }
        else {
            return 3;
        }
    }

    protected AbstractRandomRunScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        // Size comes from UiScale: the same layout on every resolution and GUI scale.
        initWidgets();
    }

    private void setupGuiScale() {
        if (isApplyingGuiScale) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;

        int optimalScale = calculateOptimalGuiScale(client);
        int currentScale = client.options.guiScale().get();

        if (currentScale != optimalScale) {
            savedGuiScale = currentScale;
            shouldRestoreScale = true;
            applyGuiScale(optimalScale);
        }
    }

    private void initWidgets() {
        GlobalParticleSystem.getInstance().updateScreenSize(width, height);
        modInfoWidget = new ModInfoWidget(width, height, font);
    }



    private void applyGuiScale(int scale) {
        if (isApplyingGuiScale) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.options == null) return;

        isApplyingGuiScale = true;
        try {
            client.options.guiScale().set(scale);
            client.getWindow().calculateScale(scale, client.isEnforceUnicode());
            client.resizeGui();
        } finally {
            isApplyingGuiScale = false;
        }
    }

    @Override
    public void added() {
        super.added();
        shownAt = System.currentTimeMillis();
    }

    public long shownAt() {
        return shownAt;
    }

    public float transition() {
        if (!com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) return 1f;
        return Math.min(1f, (System.currentTimeMillis() - shownAt) / TRANSITION_MS);
    }

    private void beginContent(GuiGraphicsExtractor context) {
        if (contentTransformed) return;
        float t = com.randomrun.ui.util.Ui.easeOut(transition());
        if (t >= 1f) return;
        contentTransformed = true;
        float scale = 0.96f + 0.04f * t;
        context.pose().pushMatrix();
        context.pose().translate(width / 2f, height / 2f + (1f - t) * 10f);
        context.pose().scale(scale, scale);
        context.pose().translate(-width / 2f, -height / 2f);
    }

    public void finishFrame(GuiGraphicsExtractor context) {
        if (contentTransformed) {
            contentTransformed = false;
            context.pose().popMatrix();
        }
        float t = transition();
        if (t < 1f) {
            context.nextStratum();
            BackgroundRenderer.getInstance().renderVeil(context, 1f - com.randomrun.ui.util.Ui.easeOut(t));
        }
    }

    @Override
    public void removed() {
        super.removed();
    }

    private void restoreGuiScale() {
        if (!shouldRestoreScale || savedGuiScale == -1) return;

        applyGuiScale(savedGuiScale);
        shouldRestoreScale = false;
        savedGuiScale = -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        BackgroundRenderer.getInstance().extractBackground(context, mouseX, mouseY, delta);

        GlobalParticleSystem particles = GlobalParticleSystem.getInstance();
        particles.update(delta);
        particles.extractRenderState(context);
        beginContent(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        if (modInfoWidget != null) {
            modInfoWidget.extractRenderState(context, mouseX, mouseY, delta);
        }

    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        GlobalParticleSystem.getInstance().updateMousePosition((int) mouseX, (int) mouseY);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (modInfoWidget != null && modInfoWidget.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            return true;
        }
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }
}
