package com.randomrun.gui.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class PublicQueueScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private boolean inQueue = false;
    
    public PublicQueueScreen(Screen parent) {
        super(Text.literal("Публичная очередь"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Clear all widgets before adding new ones to prevent overlapping
        this.clearChildren();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        addDrawableChild(new StyledButton2(
            centerX - 100, centerY - 10,
            200, 20,
            Text.literal(inQueue ? "§c✗ Покинуть очередь" : "§a⚡ Найти противника"),
            button -> toggleQueue(),
            0, 0.1f
        ));
        
        addDrawableChild(new StyledButton2(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.back"),
            button -> {
                if (inQueue) {
                    leaveQueue();
                }
                MinecraftClient.getInstance().setScreen(parent);
            },
            1, 0.15f
        ));
    }
    
    private void toggleQueue() {
        if (inQueue) {
            leaveQueue();
        } else {
            joinQueue();
        }
    }
    
    private void joinQueue() {
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        
        BattleManager.getInstance().joinPublicQueue(playerName).thenAccept(success -> {
            if (success) {
                MinecraftClient.getInstance().execute(() -> {
                    inQueue = true;
                    init();
                    if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(
                            Text.literal("§aПоиск противника..."), false
                        );
                    }
                });
            } else {
                MinecraftClient.getInstance().execute(() -> {
                    if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.sendMessage(
                            Text.literal("§cОшибка входа в очередь"), false
                        );
                    }
                });
            }
        });
    }
    
    private void leaveQueue() {
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        BattleManager.getInstance().leavePublicQueue(playerName);
        BattleManager.getInstance().stopBattle();
        inQueue = false;
        init();
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Check if match was found - if so, screen was already changed, don't render
        BattleManager manager = BattleManager.getInstance();
        if (inQueue && manager.getCurrentRoom() != null) {
            // Match found, animation screen already opened, close this screen
            return;
        }
        
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("§l§bПУБЛИЧНАЯ ОЧЕРЕДЬ"), 
            centerX, 30, 0xFFFFFF);
        
        if (inQueue) {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§e⏳ Поиск противника..."),
                centerX, centerY - 40, 0xFFFF55);
            
            long dots = (System.currentTimeMillis() / 500) % 4;
            String dotString = ".".repeat((int) dots);
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Ожидание" + dotString),
                centerX, centerY - 25, 0xAAAAAA);
        } else {
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Нажмите кнопку для поиска противника"),
                centerX, centerY - 40, 0xAAAAAA);
        }
    }
    
    
    @Override
    public void close() {
        if (inQueue) {
            leaveQueue();
        }
        super.close();
    }
}
