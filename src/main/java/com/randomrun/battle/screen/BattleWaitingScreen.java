package com.randomrun.battle.screen;

import com.randomrun.battle.BattleManager;
import com.randomrun.battle.BattleRoom;
import com.randomrun.ui.widget.StyledButton2;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.util.Random;

public class BattleWaitingScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String roomCode;
    private final boolean isHost;
    private boolean guestJoined = false;
    private long startTime;
    
    // Анимация кода
    private static final long CODE_ANIMATION_DURATION = 2000; // 2 секунды
    private static final long DIGIT_REVEAL_INTERVAL = 400; // 400мс на каждую цифру
    private Random random = new Random();
    
    // 3D item animation
    private float rotationY = 0f;
    private float levitationOffset = 0f;
    private float targetLevitationOffset = 0f;
    private long lastDragTime = 0;
    private float rotationSpeed = 0f;
    private float levitationSpeed = 0f;
    private static final long RESUME_DELAY = 500;
    private static final float RESUME_ACCELERATION = 0.02f;
    
    // Mouse drag rotation
    private boolean dragging = false;
    private float dragRotationX = 0f;
    private float dragRotationY = 0f;
    private double lastMouseX, lastMouseY;
    private float frozenLevitationOffset = 0f;
    
    public BattleWaitingScreen(Screen parent, String roomCode, boolean isHost) {
        super(Text.translatable("randomrun.battle.waiting"));
        this.parent = parent;
        this.roomCode = roomCode;
        this.isHost = isHost;
        this.startTime = System.currentTimeMillis();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        
        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        
        if (isHost && room != null && room.getGuest() != null) {
            addDrawableChild(new StyledButton2(
                centerX - 100, height - 55,
                200, 20,
                Text.translatable("randomrun.battle.start_game"),
                button -> {
                    BattleManager.getInstance().setStatusLoading();
                },
                0, 0.1f
            ));
        }
        
        addDrawableChild(new StyledButton2(
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
        super.renderBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2 + 40; // Сместили вниз
        
        int contentLeft = width / 2 - 120;
        int contentTop = centerY - 50;
        int contentRight = width / 2 + 120;
        int contentBottom = centerY + 50;
        
        context.fill(contentLeft, contentTop, contentRight, contentBottom, 0xCC1a0b2e);
        
        // Border
        com.randomrun.ui.screen.MainModScreen.renderAnimatedBorder(context, contentLeft, contentTop, contentRight, contentBottom, 2);
        
        // Separator
        context.fill(centerX - 100, contentTop + 25, centerX + 100, contentTop + 26, 0xFFFFFFFF);
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
        BakedModel model = client.getItemRenderer().getModel(stack, null, null, 0);
        
        DiffuseLighting.disableGuiDepthLighting();
        
        client.getItemRenderer().renderItem(
            stack,
            ModelTransformationMode.GUI,
            false,
            context.getMatrices(),
            context.getVertexConsumers(),
            15728880,
            OverlayTexture.DEFAULT_UV,
            model
        );
        
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
        
        context.getMatrices().pop();
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2 + 40; // Сместили вниз
        int contentTop = centerY - 50;
        
        // Update animations
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
        
        // Рендерим 3D предмет сверху
        if (room != null) {
            Item targetItem = Registries.ITEM.get(new Identifier(room.getTargetItem()));
            if (targetItem != null) {
                // Заголовок над предметом
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Целевой предмет"),
                    centerX, 20, 0xFFFFFF);
                
                render3DItem(context, centerX, 70, targetItem);
                
                // Название предмета под 3D моделью
                String itemName = targetItem.getName().getString();
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(itemName),
                    centerX, 135, 0xFFFFFF);
            }
        }
        
        // Анимированный радужный цвет для заголовка
        float time = (System.currentTimeMillis() - startTime) / 1000.0f;
        float hue = (time * 0.5f) % 1.0f;
        int rainbowColor = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
        
        String title = Text.translatable("randomrun.battle.waiting").getString();
        context.drawCenteredTextWithShadow(textRenderer, 
            Text.literal(title), 
            centerX, contentTop + 10, rainbowColor);
        
        // Обновление кнопок при присоединении гостя
        if (room != null && room.getGuest() != null && !guestJoined) {
            guestJoined = true;
            clearChildren();
            init();
        }
        
        if (room != null) {
            int textY = contentTop + 35;
            
            if (isHost) {
                // Анимированный код комнаты
                String animatedCode = getAnimatedCode();
                elapsed = System.currentTimeMillis() - startTime;
                
                // Пульсация для раскрытых цифр
                float pulse = (float) Math.sin(elapsed / 200.0) * 0.15f + 0.85f;
                int codeColor = elapsed >= CODE_ANIMATION_DURATION ? 
                    (int) (255 * pulse) << 16 | (int) (215 * pulse) << 8 | 0 : // Золотой с пульсацией
                    0xFFFFFF; // Белый во время анимации
                
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("randomrun.battle.room_code", "§f§l" + animatedCode),
                    centerX, textY, codeColor);
                textY += 12;
                
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("randomrun.battle.send_code"),
                    centerX, textY, 0x888888);
                textY += 18;
            }
            
            if (room.getGuest() == null) {
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("randomrun.battle.waiting_player"),
                    centerX, textY, 0xAAAAAA);
            } else {
                context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("✓ " + Text.translatable("randomrun.battle.player_joined").getString()),
                    centerX, textY, 0x55FF55);
                textY += 12;
                
                if (isHost) {
                    context.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable("randomrun.battle.click_ready"),
                        centerX, textY, 0x888888);
                } else {
                    context.drawCenteredTextWithShadow(textRenderer,
                        Text.translatable("randomrun.battle.waiting_host"),
                        centerX, textY, 0x888888);
                }
            }
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int centerX = width / 2;
        
        // Проверяем клик по 3D предмету для вращения
        int itemAreaSize = 60;
        if (mouseX >= centerX - itemAreaSize && mouseX <= centerX + itemAreaSize &&
            mouseY >= 40 && mouseY <= 120) {
            dragging = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            lastDragTime = System.currentTimeMillis();
            frozenLevitationOffset = levitationOffset;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            dragRotationY += (float) (mouseX - lastMouseX) * 0.5f;
            dragRotationX += (float) (mouseY - lastMouseY) * 0.5f;
            dragRotationX = Math.max(-90, Math.min(90, dragRotationX));
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
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