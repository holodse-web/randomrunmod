package com.randomrun.mixin;

import com.randomrun.features.speedrun.WorldCreator;
import com.randomrun.ui.overlay.LoadingCurtain;
import com.randomrun.ui.screen.endgame.EndgameScreen.DefeatScreen;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.ui.util.UiScale;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla screens: title, death, world loading, screen transitions and the mod's UI scale. */
public final class ScreenMixins {
    private ScreenMixins() {}

    @Mixin(TitleScreen.class)
    public static abstract class TitleScreenMixin extends Screen {
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

    @Mixin(DeathScreen.class)
    public static class DeathScreenMixin {
        @Inject(method = "<init>", at = @At("RETURN"))
        private void onDeathScreenInit(CallbackInfo ci) {
            com.randomrun.core.run.RunSessionManager sessionManager = com.randomrun.core.run.RunSessionManager.getInstance();

            if (sessionManager.hasActiveRun()) {
                if (com.randomrun.features.battle.manager.BattleManager.getInstance().isInBattle()) {
                    return;
                }

                Minecraft client = Minecraft.getInstance();
                if (client != null) {
                    com.randomrun.core.run.RunCompletionService.getInstance()
                        .completeWithDeath()
                        .thenAccept(record -> {
                            if (record != null && client.player != null) {
                                client.execute(() -> {
                                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                                        net.minecraft.resources.Identifier.parse(record.getTargetId())
                                    );
                                    client.gui.setScreen(new DefeatScreen(
                                        item,
                                        record.getDuration(),
                                        com.randomrun.infrastructure.util.LanguageManager.text("randomrun.defeat.death").getString()
                                    ));
                                });
                            }
                        });
                }
            }
        }
    }

    @Mixin(LevelLoadingScreen.class)
    public static abstract class LevelLoadingScreenMixin extends Screen {
        @Shadow
        private LevelLoadTracker loadTracker;

        protected LevelLoadingScreenMixin(Component title) {
            super(title);
        }

        @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
        private void randomrun$coverLoading(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
            if (!LoadingCurtain.isCovering()) return;
            if (loadTracker != null) {
                if (loadTracker.isLevelReady()) LoadingCurtain.reportWorldProgress(1f);
                else if (loadTracker.hasProgress()) LoadingCurtain.reportWorldProgress(loadTracker.serverProgress());
            }
            context.fill(0, 0, width, height, 0xFF000000);
            ci.cancel();
        }

        @Override
        public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && WorldCreator.isSpeedrunLoading()) {
                WorldCreator.getInstance().cancelCreation();
                if (minecraft != null) minecraft.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
                return true;
            }
            return super.keyPressed(event);
        }
    }

    @Mixin(Screen.class)
    public static class ScreenTransitionMixin {
        @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
        private void randomrun$finishTransition(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
            if ((Object) this instanceof AbstractRandomRunScreen screen) screen.finishFrame(graphics);
        }
    }

    @Mixin(net.minecraft.client.gui.screens.Screen.class)
    public static class UiScaleScreenMixin {
        @ModifyVariable(method = "init(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private int randomrun$virtualWidth(int width) {
            return UiScale.applies((net.minecraft.client.gui.screens.Screen) (Object) this) ? UiScale.width() : width;
        }

        @ModifyVariable(method = "init(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
        private int randomrun$virtualHeight(int height) {
            return UiScale.applies((net.minecraft.client.gui.screens.Screen) (Object) this) ? UiScale.height() : height;
        }

        @ModifyVariable(method = "resize(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
        private int randomrun$virtualResizeWidth(int width) {
            return UiScale.applies((net.minecraft.client.gui.screens.Screen) (Object) this) ? UiScale.width() : width;
        }

        @ModifyVariable(method = "resize(II)V", at = @At("HEAD"), argsOnly = true, ordinal = 1)
        private int randomrun$virtualResizeHeight(int height) {
            return UiScale.applies((net.minecraft.client.gui.screens.Screen) (Object) this) ? UiScale.height() : height;
        }

        @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
        private void randomrun$pushCanvas(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
            if (!UiScale.applies((net.minecraft.client.gui.screens.Screen) (Object) this)) return;
            graphics.pose().pushMatrix();
            float factor = UiScale.factor();
            graphics.pose().scale(factor, factor);
        }

        @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("RETURN"))
        private void randomrun$popCanvas(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
            if (UiScale.applies((net.minecraft.client.gui.screens.Screen) (Object) this)) graphics.pose().popMatrix();
        }
    }

    @Mixin(net.minecraft.client.MouseHandler.class)
    public static class UiScaleMouseMixin {
        @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("HEAD"), cancellable = true)
        private static void randomrun$virtualMouseX(Window window, double x, CallbackInfoReturnable<Double> cir) {
            if (UiScale.active()) cir.setReturnValue(UiScale.mouseX(window, x));
        }

        @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("HEAD"), cancellable = true)
        private static void randomrun$virtualMouseY(Window window, double y, CallbackInfoReturnable<Double> cir) {
            if (UiScale.active()) cir.setReturnValue(UiScale.mouseY(window, y));
        }
    }
}
