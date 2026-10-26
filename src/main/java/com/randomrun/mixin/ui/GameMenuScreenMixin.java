package com.randomrun.mixin.ui;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.infrastructure.RandomRunMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final int SEED_CHANGE_COOLDOWN_MS = 5000;
    private Button changeSeedButton = null;
    
    protected GameMenuScreenMixin(Component title) {
        super(title);
    }
    
    @Inject(method = "init", at = @At("TAIL"))
    private void onMenuOpen(CallbackInfo ci) {
    }
    
    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void onInitWidgets(CallbackInfo ci) {
        BattleManager battleManager = BattleManager.getInstance();
        if (battleManager.isInBattle()) {
            return;
        }
        
        com.randomrun.core.session.RunSessionManager sessionManager = 
            com.randomrun.core.session.RunSessionManager.getInstance();
        
        if (!sessionManager.hasActiveRun()) {
            return;
        }
        
        java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> elementsCopy = 
            new java.util.ArrayList<>(this.children());
        
        boolean changeSeedButtonReplaced = false;
        
        for (var element : elementsCopy) {
            if (element instanceof Button button) {
                Component message = button.getMessage();
                String messageStr = message != null ? message.getString() : "";
                
                if (!changeSeedButtonReplaced && ((message != null &&
                    message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents &&
                    contents.getKey().equals("menu.worldOptions")) || messageStr.contains("Открыть для сети") || 
                    messageStr.contains("Open to LAN") ||
                    messageStr.contains("для сети") ||
                    (messageStr.toLowerCase().contains("open") && messageStr.toLowerCase().contains("lan")))) {
                    com.randomrun.core.session.RunSession session = sessionManager.getCurrentSession();
                    long timeSinceLoad = session != null ? session.getTimeSinceWorldLoad() : 0;
                    long remainingMs = SEED_CHANGE_COOLDOWN_MS - timeSinceLoad;
                    boolean isLocked = remainingMs > 0;
                    
                    Component buttonText;
                    if (isLocked) {
                        int remainingSec = (int) Math.ceil(remainingMs / 1000.0);
                        buttonText = Component.literal("Сменить сид (" + remainingSec + "с)");
                    } else {
                        buttonText = Component.literal("Сменить сид");
                    }
                    
                    changeSeedButton = Button.builder(
                        buttonText,
                        btn -> {
                            if (!isButtonLocked()) {
                                onChangeSeedClick();
                            }
                        }
                    )
                    .bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight())
                    .build();
                    
                    changeSeedButton.active = !isLocked;
                    
                    this.removeWidget(button);
                    this.addRenderableWidget(changeSeedButton);
                    changeSeedButtonReplaced = true;
                }
            }
        }
        
        if (!changeSeedButtonReplaced) {
            RandomRunMod.LOGGER.warn("[GameMenu] Кнопка LAN не найдена!");
        }
    }
    
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (changeSeedButton != null) {
            com.randomrun.core.session.RunSessionManager sessionManager =
                com.randomrun.core.session.RunSessionManager.getInstance();
            
            if (sessionManager.hasActiveRun()) {
                com.randomrun.core.session.RunSession session = sessionManager.getCurrentSession();
                long timeSinceLoad = session != null ? session.getTimeSinceWorldLoad() : 0;
                long remainingMs = SEED_CHANGE_COOLDOWN_MS - timeSinceLoad;
                boolean isLocked = remainingMs > 0;
                
                if (isLocked) {
                    int remainingSec = (int) Math.ceil(remainingMs / 1000.0);
                    changeSeedButton.setMessage(Component.literal("Сменить сид (" + remainingSec + "с)"));
                    changeSeedButton.active = false;
                } else {
                    changeSeedButton.setMessage(Component.literal("Сменить сид"));
                    changeSeedButton.active = true;
                }
            }
            
            if (changeSeedButton.isMouseOver(mouseX, mouseY)) {
                context.setTooltipForNextFrame(
                    this.font,
                    Component.literal("§7Небольшой фриз после нажатия - норма"),
                    mouseX,
                    mouseY
                );
            }
        }
    }
    
    private boolean isButtonLocked() {
        com.randomrun.core.session.RunSessionManager sessionManager = 
            com.randomrun.core.session.RunSessionManager.getInstance();
        
        if (!sessionManager.hasActiveRun()) {
            return false;
        }
        
        com.randomrun.core.session.RunSession session = sessionManager.getCurrentSession();
        if (session == null) {
            return false;
        }
        
        long timeSinceLoad = session.getTimeSinceWorldLoad();
        return timeSinceLoad < SEED_CHANGE_COOLDOWN_MS;
    }
    
    private void onChangeSeedClick() {
        Minecraft client = Minecraft.getInstance();
        
        com.randomrun.core.session.RunSessionManager sessionManager = 
            com.randomrun.core.session.RunSessionManager.getInstance();
        com.randomrun.core.session.RunSession currentSession = sessionManager.getCurrentSession();
        
        if (currentSession == null) {
            RandomRunMod.LOGGER.warn("[ChangeSeed] Нет активной сессии");
            return;
        }
        
        com.randomrun.core.session.TargetType targetType = currentSession.getTargetType();
        net.minecraft.world.item.Item targetItem = currentSession.getTargetItem();
        net.minecraft.world.entity.EntityType<?> targetMob = currentSession.getTargetMob();
        String mobVariant = currentSession.getMobVariant();
        boolean upgrader = currentSession.isUpgrader();
        boolean randomDrop = currentSession.isRandomDrop();
        
        sessionManager.cancelRun();
        
        com.randomrun.features.challenges.classic.world.WorldCreator.clearPendingData();
        
        if (client.level != null) {
            client.level.disconnect(net.minecraft.network.chat.Component.translatable("menu.returnToMenu"));
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
                
                client.execute(() -> {
                    try {
                        if (targetType == com.randomrun.core.session.TargetType.MOB && targetMob != null) {
                            sessionManager.startMobRun(targetMob, mobVariant, false, null, null, "RandomRun Speedrun", upgrader);
                            
                            com.randomrun.features.challenges.classic.world.WorldCreationRequest request =
                                new com.randomrun.features.challenges.classic.world.WorldCreationRequest.Builder()
                                    .mode(com.randomrun.features.challenges.classic.world.WorldMode.STANDARD)
                                    .randomDrop(randomDrop)
                                    .upgrader(upgrader)
                                    .build();
                            
                            com.randomrun.features.challenges.classic.world.WorldCreator.getInstance()
                                .createWorld(request);
                        } else if (targetItem != null) {
                            com.randomrun.features.challenges.classic.world.WorldCreationRequest request =
                                new com.randomrun.features.challenges.classic.world.WorldCreationRequest.Builder()
                                    .targetItem(targetItem)
                                    .randomDrop(randomDrop)
                                    .upgrader(upgrader)
                                    .mode(com.randomrun.features.challenges.classic.world.WorldMode.STANDARD)
                                    .build();
                            
                            com.randomrun.features.challenges.classic.world.WorldCreator.getInstance()
                                .createWorld(request);
                        } else {
                            RandomRunMod.LOGGER.error("[ChangeSeed] Нет цели для нового забега");
                        }
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.error("[ChangeSeed] Ошибка: {}", e.getMessage(), e);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                RandomRunMod.LOGGER.error("[ChangeSeed] Прервано: {}", e.getMessage());
            }
        });
    }
    
    @Mixin(Minecraft.class)
    public static class Disconnect {
    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private void onDisconnect(Component reason, CallbackInfo ci) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PauseScreen)) return;
        BattleManager battleManager = BattleManager.getInstance();
        BattleRoom room = battleManager.getCurrentRoom();
        
        if (!battleManager.isInBattle()) {
            com.randomrun.core.session.RunSessionManager sessionManager = 
                com.randomrun.core.session.RunSessionManager.getInstance();
            
            if (sessionManager.hasActiveRun() && sessionManager.isRunning()) {
                com.randomrun.core.service.RunCompletionService.getInstance()
                    .completeWithGaveUp();
            } else {
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                sessionManager.cancelRun();
            }
        }
        
        if (room != null && room.isSharedWorld() && battleManager.isHost()) {
            if (room.getStatus() == BattleRoom.RoomStatus.STARTED && room.getWinner() == null) {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    com.randomrun.infrastructure.util.ClientMessages.send(client.player, Component.literal("§c§l⚠ Вы хост! Выход завершит игру для всех игроков!"), false);
                    com.randomrun.infrastructure.util.ClientMessages.send(client.player, Component.literal("§7Дождитесь окончания игры или нажмите ESC → Выход ещё раз для подтверждения."), false);
                }
                
                if (!battleManager.isDisconnectConfirmed()) {
                    battleManager.setDisconnectConfirmed(true);
                    ci.cancel();
                    Minecraft.getInstance().gui.setScreen(null);
                }
            }
        }
    }
    }
}
