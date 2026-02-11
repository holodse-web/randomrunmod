package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.item.ModelTransformationMode;

public class MatchReadyScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private boolean isReady = false;
    private long startTime;
    private float itemRotation = 0f;
    
    public MatchReadyScreen(Screen parent, String matchId) {
        super(Text.translatable("randomrun.battle.match_ready"));
        this.parent = parent;
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    protected void init() {
        super.init();
        
        
        this.clearChildren();
        
        int centerX = width / 2;
        
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 55,
            200, 20,
            Text.translatable("randomrun.battle.ready"),
            button -> {
                if (!isReady) {
                    isReady = true;
                    BattleManager.getInstance().sendLobbyReady();
                    init();
                }
            },
            0, 0.1f
        ));
        
        addDrawableChild(new ButtonDefault(
            centerX - 100, height - 30,
            200, 20,
            Text.translatable("randomrun.button.cancel"),
            button -> {
                BattleManager.getInstance().deleteRoom();
                BattleManager.getInstance().stopBattle();
                MinecraftClient.getInstance().setScreen(parent);
            },
            1, 0.15f
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        long elapsed = System.currentTimeMillis() - startTime;
        
        // Анимированный заголовок
        float titlePulse = (float) Math.sin(elapsed / 300.0) * 0.2f + 0.8f;
        int titleColor = interpolateColor(0xFFD700, 0xFFFFFF, titlePulse);
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal("§l" + Text.translatable("randomrun.battle.match_ready").getString().toUpperCase()), 
            centerX, 30, titleColor);
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        
        if (room != null) {
            Item targetItem = Registries.ITEM.get(Identifier.of(room.getTargetItem()));
            
            // 3D вращающийся предмет
            itemRotation += delta * 2f;
            render3DItem(context, centerX, centerY - 60, targetItem, elapsed);
            
            String itemName = targetItem.getName().getString();
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§e§lЦель: §f" + itemName),
                centerX, centerY - 10, 0xFFFFFF);
            
            // Информация о сиде
            String seedText = String.valueOf(room.getSeed());
            if (seedText.length() > 15) seedText = seedText.substring(0, 12) + "...";
            context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§b§lСид: §7" + seedText),
                centerX, centerY + 10, 0xFFFFFF);
            
            // Панель готовности игроков
            renderReadinessPanel(context, room, centerX, centerY + 40, elapsed);
        }
    }
    
    private void render3DItem(DrawContext context, int x, int y, Item item, long elapsed) {
        ItemStack stack = new ItemStack(item);
        
        // Пульсирующий масштаб
        float pulse = (float) Math.sin(elapsed / 500.0) * 0.1f + 1.0f;
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 100);
        context.getMatrices().scale(60f * pulse, -60f * pulse, 60f * pulse);
        
        // Вращение
        context.getMatrices().multiply(new org.joml.Quaternionf().rotateY((float) Math.toRadians(itemRotation)));
        
        MinecraftClient client = MinecraftClient.getInstance();
        
        DiffuseLighting.disableGuiDepthLighting();
        
        client.getItemRenderer().renderItem(
            stack,
            ModelTransformationMode.GUI,
            15728880,
            OverlayTexture.DEFAULT_UV,
            context.getMatrices(),
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
            client.world,
            0
        );
        
        context.draw();
        net.minecraft.client.render.DiffuseLighting.enableGuiDepthLighting();
        
        context.getMatrices().pop();
    }
    
    private void renderReadinessPanel(DrawContext context, BattleRoom room, int centerX, int y, long elapsed) {
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        boolean isHost = room.isHost(playerName);
        boolean opponentReady = isHost ? room.isGuestReady() : room.isHostReady();
        
        int panelWidth = 300;
        int panelHeight = 80;
        int panelX = centerX - panelWidth / 2;
        
        // Фон панели
        context.fill(panelX, y, panelX + panelWidth, y + panelHeight, 0xCC000000);
        context.drawBorder(panelX, y, panelWidth, panelHeight, 0xFF6930c3);
        
        // Ваш статус
        int leftX = panelX + 30;
        int rightX = panelX + panelWidth - 30;
        int iconY = y + 20;
        
        // Левый игрок (Вы)
        renderPlayerStatus(context, leftX, iconY, "§eВы", isReady, elapsed, true);
        
        // Правый игрок (Противник)
        String opponentName = isHost ? room.getGuest() : room.getHost();
        if (opponentName == null) opponentName = "Противник";
        
        // Исправление: Использовать статус готовности комнаты для СЕБЯ, чтобы гарантировать синхронизацию
        // isReady локально, но room.isHostReady()/isGuestReady() это истина сервера
        // Мы должны доверять истине сервера для визуализации, чтобы избежать путаницы
        
        boolean myServerReady = isHost ? room.isHostReady() : room.isGuestReady();
        
        // Если сервер говорит, что мы готовы, обновляем локальное состояние
        if (myServerReady && !isReady) isReady = true;
        
        renderPlayerStatus(context, leftX, iconY, "§eВы", isReady, elapsed, true);
        renderPlayerStatus(context, rightX, iconY, "§e" + opponentName, opponentReady, elapsed, false);
        
        // Переопределить истиной сервера, если доступна, чтобы предотвратить 3/2 или рассинхрон
        // На самом деле, просто доверяем флагам сервера для отображения количества
        int serverReadyCount = 0;
        if (room.isHostReady()) serverReadyCount++;
        if (room.isGuestReady()) serverReadyCount++;
        
        // Если мы локально готовы, но сервер еще не обновился, показываем локально для обратной связи
        // Но если на сервере 2/2, показываем 2/2
        
        int displayCount = serverReadyCount;
        if (isReady && !myServerReady && displayCount < 2) {
             // Мы нажали готов, ждем подтверждения сервера
             // Не считать дважды, если мы уже посчитаны
        }
        
        int barWidth = 200;
        int barHeight = 10;
        int barX = centerX - barWidth / 2;
        int barY = y + panelHeight - 20;
        
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF2a2a3c);
        
        float progress = displayCount / 2.0f;
        int fillWidth = (int) (barWidth * progress);
        
        // Анимированная заливка
        float fillPulse = displayCount == 2 ? (float) Math.sin(elapsed / 200.0) * 0.3f + 0.7f : 1.0f;
        int fillColor = displayCount == 2 ? 
            interpolateColor(0x00FF00, 0xFFD700, fillPulse) : 0xFF6930c3;
        context.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
        
        // Текст количества готовых
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("§7Готовность: §f" + displayCount + "/2"),
            centerX, barY - 12, 0xFFFFFF);
    }
    
    private void renderPlayerStatus(DrawContext context, int x, int y, String name, boolean ready, long elapsed, boolean isYou) {
        // Имя
        int nameWidth = textRenderer.getWidth(name);
        context.drawTextWithShadow(textRenderer, Text.literal(name), x - nameWidth / 2, y - 15, 0xFFFFFF);
        
        // Иконка статуса
        if (ready) {
            // Пульсирующая галочка
            float pulse = (float) Math.sin(elapsed / 300.0) * 0.3f + 0.7f;
            int color = interpolateColor(0x00FF00, 0xFFFFFF, pulse);
            context.drawCenteredTextWithShadow(textRenderer, "§l✓", x, y, color);
            context.drawCenteredTextWithShadow(textRenderer, "§aГотов", x, y + 15, 0x00FF00);
        } else {
            // Анимированные песочные часы
            int frame = (int) ((elapsed / 500) % 3);
            String icon = frame == 0 ? "⏳" : frame == 1 ? "⌛" : "⏳";
            context.drawCenteredTextWithShadow(textRenderer, "§e" + icon, x, y, 0xFFAA00);
            context.drawCenteredTextWithShadow(textRenderer, "§7Ожидание", x, y + 15, 0x808080);
        }
    }
    
    private int interpolateColor(int color1, int color2, float ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
