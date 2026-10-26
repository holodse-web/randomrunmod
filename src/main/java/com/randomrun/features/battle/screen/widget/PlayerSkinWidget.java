package com.randomrun.features.battle.screen.widget;

import com.mojang.authlib.GameProfile;
import com.randomrun.infrastructure.RandomRunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PlayerSkinWidget {
    private static final int MAX_CACHE_SIZE = 50;
    private static final int FULL_BRIGHTNESS = 15728880;
    private static final float SCALE_MULTIPLIER = 0.55f;
    private static final float MODEL_Y_OFFSET = -2.3f;
    private static final float WALK_SPEED = 5f;
    private static final float WALK_AMPLITUDE = 0.5f;
    private static final float AUTO_ROTATION_SPEED = 2f;
    private static final long DRAG_RESUME_DELAY = 1000;
    private static final long DOUBLE_CLICK_DELAY = 300;
    private static final float MIN_ZOOM = 0.4f;
    private static final float MAX_ZOOM = 1.2f;
    private static final float ZOOM_STEP = 0.1f;
    private static final int HOVER_GLOW_COLOR = 0x40FFFFFF;
    private static final int LOADING_SPINNER_COLOR = 0xFFFFFFFF;
    
    private static final Map<String, GameProfile> profileCache = new LinkedHashMap<String, GameProfile>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, GameProfile> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    private static final Map<String, PlayerSkin> skinCache = new LinkedHashMap<String, PlayerSkin>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PlayerSkin> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    private static final Map<String, UUID> uuidCache = new HashMap<>();
    
    private final String playerName;
    private final UUID playerUUID;
    private GameProfile gameProfile;
    
    private PlayerModel model;
    private PlayerModel slimModel;
    
    private float rotationY = 0f;
    private final long creationTime;
    
    private boolean isDragging = false;
    private long lastDragTime = 0;
    private long lastClickTime = 0;
    private float currentAutoRotationSpeed = AUTO_ROTATION_SPEED;
    private float animationSpeed = 1.0f;
    private boolean autoRotationEnabled = true;
    private float zoomLevel = 1.0f;
    private AnimationPose currentPose = AnimationPose.WALK;
    
    private boolean isHovered = false;
    private boolean isLoading = true;
    
    private int lastX, lastY, lastSize;
    
    public enum AnimationPose {
        IDLE, WALK, RUN
    }

    public PlayerSkinWidget(String playerName) {
        this.playerName = playerName;
        this.playerUUID = getOrCreateUUID(playerName);
        this.creationTime = System.currentTimeMillis();
        loadProfile();
    }
    
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (mouseX >= lastX - lastSize / 2 && mouseX <= lastX + lastSize / 2 &&
            mouseY >= lastY - lastSize && mouseY <= lastY + lastSize / 2) {
            long currentTime = System.currentTimeMillis();
            
            if (currentTime - lastClickTime < DOUBLE_CLICK_DELAY) {
                resetRotation();
                playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value());
                lastClickTime = 0;
                return true;
            }
            
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
                cyclePose();
                playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value());
                return true;
            }
            
            if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
                isDragging = true;
                lastDragTime = currentTime;
                currentAutoRotationSpeed = 0f;
                playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value());
            }
            
            lastClickTime = currentTime;
            return true;
        }
        return false;
    }
    
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= lastX - lastSize / 2 && mouseX <= lastX + lastSize / 2 &&
            mouseY >= lastY - lastSize && mouseY <= lastY + lastSize / 2) {
            zoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, 
                zoomLevel + (float)amount * ZOOM_STEP));
            playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value());
            return true;
        }
        return false;
    }
    
    public void updateHover(double mouseX, double mouseY) {
        isHovered = mouseX >= lastX - lastSize / 2 && mouseX <= lastX + lastSize / 2 &&
                    mouseY >= lastY - lastSize && mouseY <= lastY + lastSize / 2;
    }

    public boolean mouseDragged(double mouseX, double mouseY, double deltaX) {
        if (isDragging) {
            lastDragTime = System.currentTimeMillis();
            rotationY -= deltaX;
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
            var loader = Minecraft.getInstance().getEntityModels();
            this.model = new PlayerModel(loader.bakeLayer(ModelLayers.PLAYER), false);
            this.slimModel = new PlayerModel(loader.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        } catch (Throwable e) {
            RandomRunMod.LOGGER.error("[PlayerSkin] Ошибка загрузки моделей: {}", e.getMessage());
        }
    }
    
    private static UUID getOrCreateUUID(String playerName) {
        return uuidCache.computeIfAbsent(playerName, 
            name -> UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
    }
    
    private void loadProfile() {
        if (profileCache.containsKey(playerName)) {
            this.gameProfile = profileCache.get(playerName);
            loadSkin();
            return;
        }
        
        Minecraft client = Minecraft.getInstance();
        if (client.getConnection() != null) {
            PlayerInfo entry = client.getConnection().getPlayerInfo(playerName);
            if (entry != null) {
                this.gameProfile = entry.getProfile();
                skinCache.put(playerName, entry.getSkin());
                profileCache.put(playerName, this.gameProfile);
                return;
            }
        }
        
        CompletableFuture.runAsync(() -> {
            GameProfile profile = new GameProfile(playerUUID, playerName);
            synchronized(profileCache) {
                profileCache.put(playerName, profile);
            }
            this.gameProfile = profile;
            loadSkin();
        });
    }
    
    private void loadSkin() {
        if (gameProfile == null) return;
        Minecraft client = Minecraft.getInstance();
        client.getSkinManager().get(gameProfile).thenAccept(textures -> {
            textures.ifPresent(skin -> client.execute(() -> skinCache.put(playerName, skin)));
        });
    }
    
    public void extractRenderState(GuiGraphicsExtractor context, int x, int y, int size, float delta) {
        updateRotation(delta);
        
        this.lastX = x;
        this.lastY = y;
        this.lastSize = size;
        
        if (isHovered) {
            renderHoverGlow(context, x, y, size);
        }
        
        if (isLoading && gameProfile == null) {
            renderLoadingSpinner(context, x, y, size, delta);
        } else {
            isLoading = false;
            renderModel(context, x, y, size);
        }
    }
    
    private void renderHoverGlow(GuiGraphicsExtractor context, int x, int y, int size) {
        int halfSize = size / 2;
        context.fill(x - halfSize - 2, y - size - 2, 
                    x + halfSize + 2, y + 2, HOVER_GLOW_COLOR);
    }
    
    private void renderLoadingSpinner(GuiGraphicsExtractor context, int x, int y, int size, float delta) {
        long time = System.currentTimeMillis();
        float angle = (time % 1000) / 1000f * 360f;
        
        int centerY = y - size / 2;
        int radius = size / 4;
        
        for (int i = 0; i < 8; i++) {
            float a = (float) Math.toRadians(angle + i * 45);
            int px = x + (int)(Math.cos(a) * radius);
            int py = centerY + (int)(Math.sin(a) * radius);
            
            int alpha = (int)(255 * (1 - i / 8f));
            int color = (alpha << 24) | (LOADING_SPINNER_COLOR & 0x00FFFFFF);
            
            context.fill(px - 2, py - 2, px + 2, py + 2, color);
        }
    }
    
    private void updateRotation(float delta) {
        if (!isDragging && autoRotationEnabled) {
            long timeSinceDrag = System.currentTimeMillis() - lastDragTime;
            if (timeSinceDrag > DRAG_RESUME_DELAY) {
                currentAutoRotationSpeed = Math.min(
                    currentAutoRotationSpeed + delta * 0.1f, 
                    AUTO_ROTATION_SPEED
                );
            }
            rotationY += delta * currentAutoRotationSpeed;
        }
    }
    
    private void renderModel(GuiGraphicsExtractor context, int x, int y, int size) {
        initModels();
        if (model == null || slimModel == null) return;
        PlayerSkin textures = skinCache.getOrDefault(playerName, DefaultPlayerSkin.get(playerUUID));
        PlayerModel currentModel = textures.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM ? slimModel : model;
        setupModelParts(currentModel);
        applyAnimation(currentModel);
        float scale = size * SCALE_MULTIPLIER * zoomLevel;
        int bottom = Math.round(y + size + (MODEL_Y_OFFSET + 1.601f) * scale);
        int radius = Math.max(size, (int) Math.ceil(scale * 2.3f));
        context.skin(new net.minecraft.client.model.Model.Simple(currentModel.root(), net.minecraft.client.renderer.rendertype.RenderTypes::entityTranslucent),
            textures.body().texturePath(), scale, 0, -rotationY, -1.0625f, x - radius, bottom - radius * 2, x + radius, bottom);
    }
    
private void setupModelParts(PlayerModel model) {
        model.allParts().forEach(part -> part.visible = true);
        model.hat.visible = true;
        model.jacket.visible = true;
        model.leftPants.visible = true;
        model.rightPants.visible = true;
        model.leftSleeve.visible = true;
        model.rightSleeve.visible = true;
        
        model.head.yRot = 0;
        model.head.xRot = 0;
        model.body.yRot = 0;
    }
    
    private void applyAnimation(PlayerModel model) {
        float time = (System.currentTimeMillis() - creationTime) / 1000f * animationSpeed;
        
        switch (currentPose) {
            case IDLE:
                applyIdleAnimation(model, time);
                break;
            case WALK:
                applyWalkAnimation(model, time);
                break;
            case RUN:
                applyRunAnimation(model, time);
                break;
        }
    }
    
    private void applyIdleAnimation(PlayerModel model, float time) {
        float breathe = (float) Math.sin(time * 2) * 0.05f;
        model.body.xRot = breathe;
        model.head.xRot = breathe * 0.5f;
        
        model.leftArm.xRot = 0;
        model.rightArm.xRot = 0;
        model.leftLeg.xRot = 0;
        model.rightLeg.xRot = 0;
    }
    
    private void applyWalkAnimation(PlayerModel model, float time) {
        float swing = (float) Math.cos(time * WALK_SPEED) * WALK_AMPLITUDE;
        
        model.leftArm.xRot = swing;
        model.rightArm.xRot = -swing;
        model.leftLeg.xRot = -swing;
        model.rightLeg.xRot = swing;
    }
    
    private void applyRunAnimation(PlayerModel model, float time) {
        float swing = (float) Math.cos(time * WALK_SPEED * 1.5f) * WALK_AMPLITUDE * 1.5f;
        
        model.leftArm.xRot = swing;
        model.rightArm.xRot = -swing;
        model.leftLeg.xRot = -swing;
        model.rightLeg.xRot = swing;
        
        model.body.xRot = -0.2f;
    }
    
    public void setAnimationSpeed(float speed) {
        this.animationSpeed = Math.max(0.1f, Math.min(3.0f, speed));
    }
    
    public void setAutoRotation(boolean enabled) {
        this.autoRotationEnabled = enabled;
        if (!enabled) {
            currentAutoRotationSpeed = 0f;
        }
    }
    
    public void resetRotation() {
        rotationY = 0f;
        currentAutoRotationSpeed = AUTO_ROTATION_SPEED;
    }
    
    public void resetZoom() {
        zoomLevel = 1.0f;
    }
    
    public void setPose(AnimationPose pose) {
        this.currentPose = pose;
    }
    
    public void cyclePose() {
        AnimationPose[] poses = AnimationPose.values();
        int currentIndex = Arrays.asList(poses).indexOf(currentPose);
        currentPose = poses[(currentIndex + 1) % poses.length];
    }
    
    public AnimationPose getCurrentPose() {
        return currentPose;
    }
    
    public boolean isLoading() {
        return isLoading;
    }
    
    private void playSound(net.minecraft.sounds.SoundEvent sound) {
        if (!RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) return;
        
        float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, 1.0f, volume * 0.3f)
        );
    }
}
