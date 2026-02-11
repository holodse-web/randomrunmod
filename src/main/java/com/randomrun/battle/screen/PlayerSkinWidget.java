package com.randomrun.battle.screen;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerSkinWidget {
    
    private static final Map<String, GameProfile> profileCache = new HashMap<>();
    private static final Map<String, SkinTextures> skinCache = new HashMap<>();
    
    private final String playerName;
    private GameProfile gameProfile;
    
    // Manual model fallback
    private PlayerEntityModel model;
    private PlayerEntityModel slimModel;
    
    private float rotationY = 0f;
    private final long creationTime;
    
    // Interaction state
    private boolean isDragging = false;
    private long lastDragTime = 0;
    private float autoRotationSpeed = 2f;
    private float currentAutoRotationSpeed = 2f;
    
    // Mouse hit area for last render
    private int lastX, lastY, lastSize;

    public PlayerSkinWidget(String playerName) {
        this.playerName = playerName;
        this.creationTime = System.currentTimeMillis();
        loadProfile();
    }
    
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Проверка попадания на основе нового рендеринга (центр по x, ширина=размер)
        // Рендер смещается в (x, y + size)
        // Хитбокс: (x - size/2) до (x + size/2) горизонтально
        //         (y) до (y + size) вертикально
        
        if (mouseX >= lastX - lastSize / 2 && mouseX <= lastX + lastSize / 2 &&
            mouseY >= lastY - lastSize && mouseY <= lastY) {
            isDragging = true;
            lastDragTime = System.currentTimeMillis();
            currentAutoRotationSpeed = 0f;
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, double deltaX) {
        if (isDragging) {
            lastDragTime = System.currentTimeMillis();
            rotationY += deltaX;
            return true;
        }
        return false;
    }
    
    public void mouseReleased() {
        if (isDragging) {
            isDragging = false;
            lastDragTime = System.currentTimeMillis();
        }
    }
    
    private void initModels() {
        if (model != null && slimModel != null) return;
        
        try {
            var loader = MinecraftClient.getInstance().getLoadedEntityModels();
            this.model = new PlayerEntityModel(loader.getModelPart(EntityModelLayers.PLAYER), false);
            this.slimModel = new PlayerEntityModel(loader.getModelPart(EntityModelLayers.PLAYER_SLIM), true);
        } catch (Throwable e) { // Ловим Throwable для обработки LinkageError, если класс отсутствует
            System.err.println("Не удалось загрузить модели игрока: " + e.getMessage());
        }
    }
    
    private void loadProfile() {
        if (profileCache.containsKey(playerName)) {
            this.gameProfile = profileCache.get(playerName);
            loadSkin();
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() != null) {
            PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(playerName);
            if (entry != null) {
                this.gameProfile = entry.getProfile();
                skinCache.put(playerName, entry.getSkinTextures());
                profileCache.put(playerName, this.gameProfile);
                return;
            }
        }
        
        CompletableFuture.runAsync(() -> {
            GameProfile profile = new GameProfile(
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes()), 
                playerName
            );
            
            synchronized(profileCache) {
                profileCache.put(playerName, profile);
            }
            this.gameProfile = profile;
            loadSkin();
        });
    }
    
    private void loadSkin() {
        if (gameProfile == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        SkinTextures textures = client.getSkinProvider().getSkinTextures(gameProfile);
        skinCache.put(playerName, textures);
    }
    
    public void render(DrawContext context, int x, int y, int size, float delta) {
        // Update interaction state
        if (!isDragging) {
             long timeSinceDrag = System.currentTimeMillis() - lastDragTime;
             if (timeSinceDrag > 1000) { // Resume after 1s
                 currentAutoRotationSpeed = Math.min(currentAutoRotationSpeed + delta * 0.1f, autoRotationSpeed);
             }
             rotationY += delta * currentAutoRotationSpeed;
        }
        
        // Store bounds for interaction
        this.lastX = x;
        this.lastY = y + size; // Bottom
        this.lastSize = size;

        renderManual(context, x, y, size);
    }
    
    private void renderManual(DrawContext context, int x, int y, int size) {
        initModels();
        if (model == null || slimModel == null) return;

        SkinTextures textures = skinCache.get(playerName);
        if (textures == null) {
            textures = DefaultSkinHelper.getSkinTextures(
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes())
            );
        }

        Identifier texture = textures.texture();
        boolean isSlim = textures.model() == SkinTextures.Model.SLIM;
        PlayerEntityModel currentModel = isSlim ? slimModel : model;

        context.getMatrices().push();
        
        // Настройка позиции
        // Использовать 'x' напрямую, 'y + size' для низа (ног)
        context.getMatrices().translate(x, y + size, 100);
        
        // Масштабирование
        // Используем масштаб ~30-40 для слота 60-70px.
        // Увеличиваем масштаб для размера 85px
        float scale = size * 0.55f; // Было 0.5f
        context.getMatrices().scale(scale, scale, -scale);
        
        // Смещение модели вверх, чтобы ноги были в точке (0,0)
        // Модель игрока (Raw) имеет голову в 0, ноги в +1.5 (24 пикселя / 16).
        // Нам нужно сместить модель вверх на высоту игрока, чтобы ноги оказались в точке привязки.
        // 1.5 - 1.8. Попробуем -2.0 для центрирования.
        // Коррекция: чтобы поднять модель выше, нужно увеличить отрицательный Y смещения.
        // Было -2.2f. Ставим -2.3f для компенсации масштаба.
        context.getMatrices().translate(0, -2.3f, 0);
        
        // Вращение вокруг Y
        Quaternionf rotation = new Quaternionf().rotateY((float)Math.toRadians(rotationY));
        context.getMatrices().multiply(rotation);
        
        // Настройка состояния рендера
        DiffuseLighting.enableGuiDepthLighting();
        
        // Настройка частей модели
        currentModel.setVisible(true);
        currentModel.hat.visible = true;
        currentModel.jacket.visible = true;
        currentModel.leftPants.visible = true;
        currentModel.rightPants.visible = true;
        currentModel.leftSleeve.visible = true;
        currentModel.rightSleeve.visible = true;
        
        // Walking Animation (Slower)
        float time = (System.currentTimeMillis() - creationTime) / 1000f;
        float speed = 5f; // Slower walking speed (was 10f)
        float amplitude = 1.0f; // Walking amplitude
        
        currentModel.leftArm.pitch = (float) Math.cos(time * speed) * amplitude * 0.5f;
        currentModel.rightArm.pitch = -(float) Math.cos(time * speed) * amplitude * 0.5f;
        currentModel.leftLeg.pitch = -(float) Math.cos(time * speed) * amplitude * 0.5f;
        currentModel.rightLeg.pitch = (float) Math.cos(time * speed) * amplitude * 0.5f;
        
        currentModel.head.yaw = 0;
        currentModel.head.pitch = 0;
        currentModel.body.yaw = 0;
        
        // Рендер
        VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vertexConsumer = immediate.getBuffer(RenderLayer.getEntityTranslucent(texture));
        
        int light = 15728880; // Full brightness
        currentModel.render(context.getMatrices(), vertexConsumer, light, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        
        immediate.draw();
        
        DiffuseLighting.disableGuiDepthLighting();
        
        context.getMatrices().pop();
    }
}