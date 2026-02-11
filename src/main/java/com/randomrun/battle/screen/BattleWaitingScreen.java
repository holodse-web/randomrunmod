package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.item.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.util.Random;
import java.util.Map;
import java.util.HashMap;

public class BattleWaitingScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String roomCode;
    
    private final boolean isHost;
    private int lastPlayerCount = 0;
    private long startTime;
    
    private final Map<String, PlayerSkinWidget> playerWidgets = new HashMap<>();
    
    private static final long CODE_ANIMATION_DURATION = 2000;
    private static final long DIGIT_REVEAL_INTERVAL = 400;
    private Random random = new Random();
    
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private float targetLevitationOffset = 0f;
    private long lastDragTime = 0;
    private float rotationSpeed = 0f;
    private float levitationSpeed = 0f;
    private static final long RESUME_DELAY = 500;
    private static final float RESUME_ACCELERATION = 0.02f;
    
    private boolean dragging = false;
    private float dragRotationX = 0f;
    private float dragRotationY = 0f;
    private double lastMouseX, lastMouseY;
    private float frozenLevitationOffset = 0f;
    
    private boolean isRadminMode = false;

    public BattleWaitingScreen(Screen parent, String roomCode, boolean isHost) {
        this(parent, roomCode, isHost, false);
    }

    public BattleWaitingScreen(Screen parent, String roomCode, boolean isHost, boolean isRadminMode) {
        super(Text.translatable("randomrun.battle.waiting"));
        this.parent = parent;
        this.roomCode = roomCode;
        this.isHost = isHost;
        this.isRadminMode = isRadminMode;
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    protected void init() {
        super.init();
        
        if (isHost) {
            initE4mcMonitoring();
        }
        
        int centerX = width / 2;
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room != null) {
            lastPlayerCount = room.getPlayers() != null ? room.getPlayers().size() : 1;
        }
        
        if (isHost && room != null) {
            int playerCount = room.getPlayers() != null ? room.getPlayers().size() : 1;
            int maxPlayers = room.getMaxPlayers();
            
            boolean canStart = playerCount >= maxPlayers;
            
            if (canStart) {
                addDrawableChild(new ButtonDefault(
                    centerX - 100, height - 55,
                    200, 20,
                    Text.translatable("randomrun.battle.start_game"),
                    button -> {
                        if (isRadminMode) {
                            MinecraftClient.getInstance().setScreen(new RadminVpnIpScreen(parent, roomCode));
                        } else {
                            BattleManager.getInstance().setStatusLoading();
                        }
                    },
                    0, 0.1f
                ));
            }
        }
        
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
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Сначала рендерим фон (черный полупрозрачный фон для видимости)
        super.renderBackground(context, mouseX, mouseY, delta);
    }
    
    private String getAnimatedCode() {
        long elapsed = System.currentTimeMillis() - startTime;
        
        if (elapsed >= CODE_ANIMATION_DURATION) {
            return roomCode;
        }
        
        StringBuilder animatedCode = new StringBuilder();
        
        for (int i = 0; i < roomCode.length(); i++) {
            long digitStartTime = i * DIGIT_REVEAL_INTERVAL;
            
            if (elapsed < digitStartTime) {
                animatedCode.append('*');
            } else if (elapsed < digitStartTime + DIGIT_REVEAL_INTERVAL) {
                long digitElapsed = elapsed - digitStartTime;
                float progress = (float) digitElapsed / DIGIT_REVEAL_INTERVAL;
                
                if (progress < 0.8f) {
                    char randomDigit = (char) ('0' + random.nextInt(10));
                    animatedCode.append(randomDigit);
                } else {
                    animatedCode.append(roomCode.charAt(i));
                }
            } else {
                animatedCode.append(roomCode.charAt(i));
            }
        }
        
        return animatedCode.toString();
    }
    
    private void render3DItem(DrawContext context, int x, int y, Item targetItem) {
        ItemStack stack = new ItemStack(targetItem);
        
        context.getMatrices().push();
        context.getMatrices().translate(x, y + levitationOffset, 100);
        context.getMatrices().scale(80f, -80f, 80f);
        
        context.getMatrices().multiply(new Quaternionf().rotateX((float) Math.toRadians(dragRotationX)));
        context.getMatrices().multiply(new Quaternionf().rotateY((float) Math.toRadians(rotationY + dragRotationY)));
        
        MinecraftClient client = MinecraftClient.getInstance();
        // BakedModel model = client.getItemRenderer().getModels().getModel(stack);
        
        DiffuseLighting.disableGuiDepthLighting();
        
        client.getItemRenderer().renderItem(
            stack,
            ModelTransformationMode.FIXED,
            15728880,
            OverlayTexture.DEFAULT_UV,
            context.getMatrices(),
            MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
            client.world,
            0
        );
        
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
        
        context.getMatrices().pop();
    }
    
    private void initE4mcMonitoring() { 
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room == null || !room.isSharedWorld()) {
            return;
        }
        
        // Если это Radmin или Separate, не запускаем мониторинг e4mc
        if ("rv".equals(room.getCreationMode()) || "sw".equals(room.getCreationMode())) {
             return;
        }

        com.randomrun.main.RandomRunMod.LOGGER.info("МОНИТОРИНГ E4MC ИНИЦИАЛИЗИРОВАН"); 
        
        BattleManager manager = BattleManager.getInstance(); 
        if (manager != null) { 
            manager.setAwaitingE4mcDomain(true);
        }
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (BattleManager.getInstance().getCurrentRoom() == null) {
            MinecraftClient.getInstance().setScreen(new com.randomrun.ui.screen.main.MainModScreen(new net.minecraft.client.gui.screen.TitleScreen()));
            return;
        }

        renderBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        long elapsed = System.currentTimeMillis() - startTime;
        long timeSinceLastDrag = System.currentTimeMillis() - lastDragTime;
        
        if (dragging) {
            rotationSpeed = 0f;
            levitationSpeed = 0f;
            frozenLevitationOffset = levitationOffset;
        } else if (timeSinceLastDrag > RESUME_DELAY) {
            rotationSpeed = Math.min(rotationSpeed + RESUME_ACCELERATION * delta, 2f);
            levitationSpeed = Math.min(levitationSpeed + RESUME_ACCELERATION * delta, 1f);
            
            rotationY += delta * rotationSpeed;
            targetLevitationOffset = (float) Math.sin(elapsed / 500.0) * 5f;
            levitationOffset += (targetLevitationOffset - levitationOffset) * levitationSpeed * delta * 0.1f;
        } else {
            levitationOffset = frozenLevitationOffset;
        }
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        
        if (room != null) {
            Item targetItem = Registries.ITEM.get(Identifier.of(room.getTargetItem()));
            if (targetItem != null) {
                render3DItem(context, centerX, 60, targetItem);
                
                String itemName = targetItem.getName().getString();
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(itemName),
                    centerX, 110, 0xFFFFFF);
            }
        }
        
        if (room != null && room.getPlayers() != null) {
            renderPlayers(context, room, centerX, centerY);
            
            int currentCount = room.getPlayers().size();
            if (currentCount != lastPlayerCount) {
                lastPlayerCount = currentCount;
                clearChildren();
                init();
            }
        }
        
        renderRoomInfoPanel(context, room, centerX, elapsed);

        super.render(context, mouseX, mouseY, delta);
    }
    
    private void renderRoomInfoPanel(DrawContext context, BattleRoom room, int centerX, long elapsed) {
        int panelY = height - 110;
        int panelWidth = 400;
        int panelHeight = 75;
        int panelX = centerX - panelWidth / 2;
        
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC000000);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, 0xFF6930c3);
        
        if (isHost) {
            // String animatedCode = getAnimatedCode();
            // 
            // float pulse = (float) Math.sin(elapsed / 200.0) * 0.15f + 0.85f;
            // int codeColor = elapsed >= CODE_ANIMATION_DURATION ? 
            //     (int) (255 * pulse) << 16 | (int) (215 * pulse) << 8 | 0 : 
            //     0xFFFFFF;
            // 
            // context.drawCenteredTextWithShadow(textRenderer,
            //     Text.translatable("randomrun.battle.room_code_display", animatedCode),
            //     centerX, panelY + 10, codeColor);
        }
        
        if (room != null) {
            // int currentPlayers = room.getPlayers() != null ? room.getPlayers().size() : 1;
            // int maxPlayers = room.getMaxPlayers();
            
            // context.drawCenteredTextWithShadow(textRenderer,
            //    Text.translatable("randomrun.battle.players_display", currentPlayers, maxPlayers),
            //    centerX, panelY + 28, 0xFFFFFF);
            
            // Вместо текста игроков показываем код комнаты
            String animatedCode = getAnimatedCode();
            
            float pulse = (float) Math.sin(elapsed / 200.0) * 0.15f + 0.85f;
            int codeColor = elapsed >= CODE_ANIMATION_DURATION ? 
                (int) (255 * pulse) << 16 | (int) (215 * pulse) << 8 | 0 : 
                0xFFFFFF;
            
            context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("randomrun.battle.room_code_display", animatedCode),
                centerX, panelY + 28, codeColor);
            
            int barWidth = 300;
            int barHeight = 8;
            int barX = centerX - barWidth / 2;
            int barY = panelY + 42;
            
            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF2a2a3c);
            
            int currentPlayers = room.getPlayers() != null ? room.getPlayers().size() : 1;
            int maxPlayers = room.getMaxPlayers();
            float progress = (float) currentPlayers / maxPlayers;
            int fillWidth = (int) (barWidth * progress);
            
            int fillColor = currentPlayers >= maxPlayers ? 0xFF00FF00 : 0xFF6930c3;
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);
            
            if (room.isSharedWorld() && isHost) {
                String serverAddr = room.getServerAddress();
                if (serverAddr != null) {
                    context.drawCenteredTextWithShadow(textRenderer,
                        Text.literal("§a✓ Сервер готов"),
                        centerX, panelY + 56, 0xFFFFFF);
                }
            } else if (room.isSharedWorld() && !isHost) {
                String serverAddr = room.getServerAddress();
                String status = serverAddr != null ? "§a✓ Ожидание подключения" : "§eПодключение к хосту...";
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(status),
                    centerX, panelY + 56, 0xFFFFFF);
            }
        }
    }
    
    private void renderPlayers(DrawContext context, BattleRoom room, int centerX, int centerY) {
        java.util.List<String> players = room.getPlayers();
        
        playerWidgets.keySet().removeIf(name -> !players.contains(name));
        for (String player : players) {
            playerWidgets.computeIfAbsent(player, PlayerSkinWidget::new);
        }
        
        int maxPlayers = room.getMaxPlayers();
        int spacing = 120;
        int totalWidth = (maxPlayers - 1) * spacing;
        int startX = centerX - totalWidth / 2;
        
        // Позиция для знака вопроса (пустой слот)
        int slotSize = 70;
        // Поднимаем слот (знак вопроса) выше
        int slotY = centerY - slotSize / 2 - 25;
        
        // Позиция для ника - ВНИЗУ под персонажем/слотом
        // Опускаем ник еще ниже, отвязывая от персонажа (увеличиваем отступ)
        int nameY = centerY + 75;
        
        for (int i = 0; i < maxPlayers; i++) {
            int x = startX + (i * spacing);
            
            if (i < players.size()) {
                String player = players.get(i);
                
                if (playerWidgets.containsKey(player)) {
                    // Персонаж - центрируем относительно slotY
                    int characterSize = 85; 
                    // render() принимает Y как "дно" (ноги)
                    // Ставим персонажа ровно по центру горизонтали (уже есть x)
                    // И выравниваем по вертикали (фиксировано относительно центра)
                    // Поднимаем персонажа выше
                    int characterY = centerY + 5;
                    
                    playerWidgets.get(player).render(context, x, characterY, characterSize, MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(true));
                    
                    int nameWidth = textRenderer.getWidth(player);
                    int panelWidth = nameWidth + 10;
                    int panelX = x - panelWidth / 2;
                    // Используем фиксированную позицию nameY для ников
                    int nameYPos = nameY;
                    
                    context.fill(panelX, nameYPos, panelX + panelWidth, nameYPos + 12, 0xCC000000);
                    context.drawCenteredTextWithShadow(textRenderer, player, x, nameYPos + 2, 0xFFFFFF);
                    
                    if (room.isPlayerReady(player)) {
                        int readyY = nameYPos + 15;
                        context.drawCenteredTextWithShadow(textRenderer, "§a✓ ГОТОВ", x, readyY, 0x00FF00);
                    }
                }
            } else {
                // Пустой слот - знак вопроса
                context.fill(x - slotSize/2, slotY, x + slotSize/2, slotY + slotSize, 0x40FFFFFF);
                context.drawBorder(x - slotSize/2, slotY, slotSize, slotSize, 0x80FFFFFF);
                context.drawCenteredTextWithShadow(textRenderer, "§7?", x, slotY + slotSize/2 - 4, 0xAAAAAA);
                
                // "Ожидание"
                int nameWidth = textRenderer.getWidth("Ожидание");
                int panelWidth = nameWidth + 10;
                int panelX = x - panelWidth / 2;
                // Используем ту же фиксированную позицию nameY
                int nameYPos = nameY;
                
                context.fill(panelX, nameYPos, panelX + panelWidth, nameYPos + 12, 0xCC000000);
                context.drawCenteredTextWithShadow(textRenderer, "§7Ожидание", x, nameYPos + 2, 0x808080);
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        
        // Prioritize item interaction
        int itemAreaSize = 60;
        if (mouseX >= centerX - itemAreaSize && mouseX <= centerX + itemAreaSize &&
            mouseY >= 40 && mouseY <= 120) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        // Handle player skin interaction
        for (PlayerSkinWidget widget : playerWidgets.values()) {
            if (widget.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Handle player skin interaction
        for (PlayerSkinWidget widget : playerWidgets.values()) {
            widget.mouseReleased();
        }

        if (button == 0 && dragging) {
            dragging = false;
            lastDragTime = System.currentTimeMillis();
            frozenLevitationOffset = levitationOffset;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Prioritize item interaction
        if (dragging) {
            dragRotationY -= (float) (mouseX - lastMouseX) * 0.5f;
            dragRotationX += (float) (mouseY - lastMouseY) * 0.5f;
            dragRotationX = Math.max(-90, Math.min(90, dragRotationX));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        // Handle player skin interaction
        for (PlayerSkinWidget widget : playerWidgets.values()) {
            if (widget.mouseDragged(mouseX, mouseY, deltaX)) {
                return true;
            }
        }
        
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public void close() {
        if (BattleManager.getInstance().isInBattle()) {
            BattleManager.getInstance().deleteRoom();
            BattleManager.getInstance().stopBattle();
        }
        super.close();
    }
}
