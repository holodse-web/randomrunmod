package com.randomrun.ui.widget;

import com.randomrun.infrastructure.RandomRunMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class GlobalParticleSystem {
    private static GlobalParticleSystem instance;
    private final List<Particle> particles;
    private final ParticlePool pool;
    private final Random random = new Random();
    
    private int screenWidth;
    private int screenHeight;
    private ColorMode colorMode = ColorMode.DEFAULT;
    private int mouseX = 0;
    private int mouseY = 0;
    private boolean simplifiedUI = false;
    private long lastSimplifiedCheck = 0;
    
    private static final int MAX_PARTICLES = 60;
    private static final int SPAWN_RATE = 1;
    private static final int CURSOR_BOUNCE_RADIUS = 30;
    private static final float TWO_PI = (float)(Math.PI * 2);
    private static final float EXPLOSION_SPEED_MIN = 1.0f;
    private static final float EXPLOSION_SPEED_MAX = 3.0f;
    private static final long SIMPLIFIED_CHECK_INTERVAL = 1000;
    
    private GlobalParticleSystem() {
        this.particles = new ArrayList<>(MAX_PARTICLES);
        this.pool = new ParticlePool(MAX_PARTICLES);
    }
    
    public static GlobalParticleSystem getInstance() {
        if (instance == null) {
            instance = new GlobalParticleSystem();
        }
        return instance;
    }
    
    public void updateScreenSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }
    
    public void updateMousePosition(int mouseX, int mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }
    
    public void setColorMode(ColorMode mode) {
        this.colorMode = mode;
    }
    
    public void setRedMode(boolean red) {
        colorMode = red ? ColorMode.RED : ColorMode.DEFAULT;
    }
    
    public void setGreenMode(boolean green) {
        colorMode = green ? ColorMode.GREEN : ColorMode.DEFAULT;
    }
    
    public void setYellowMode(boolean yellow) {
        colorMode = yellow ? ColorMode.YELLOW : ColorMode.DEFAULT;
    }
    
    public void clearParticles() {
        for (Particle particle : particles) {
            pool.free(particle);
        }
        particles.clear();
    }

    public void addExplosion(int x, int y, int count, int color) {
        if (isSimplifiedUI()) return;
        
        for (int i = 0; i < count; i++) {
            if (particles.size() >= MAX_PARTICLES) break;
            
            float angle = random.nextFloat() * TWO_PI;
            float speed = random.nextFloat() * EXPLOSION_SPEED_MAX + EXPLOSION_SPEED_MIN;
            
            Particle p = pool.obtain();
            p.init(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                random.nextInt(3) + 2,
                1.0f,
                color,
                false
            );
            particles.add(p);
        }
    }
    
    public void addFastExplosion(int x, int y, int count, int color) {
        if (isSimplifiedUI()) return;
        
        for (int i = 0; i < count; i++) {
            if (particles.size() >= MAX_PARTICLES) break;
            
            float angle = random.nextFloat() * TWO_PI;
            float speed = random.nextFloat() * EXPLOSION_SPEED_MAX + EXPLOSION_SPEED_MIN;
            
            Particle p = pool.obtain();
            p.init(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                random.nextInt(3) + 2,
                1.0f,
                color,
                true
            );
            particles.add(p);
        }
    }
    
    public void update(float delta) {
        if (screenWidth <= 0 || screenHeight <= 0) return;
        
        if (isSimplifiedUI()) {
            clearParticles();
            return;
        }
        
        float deltaTime = delta * 60.0f;
        
        spawnAmbientParticles(deltaTime);
        updateAndCleanParticles(deltaTime);
    }
    
    private void spawnAmbientParticles(float deltaTime) {
        if (deltaTime > 0.5f) {
            for (int i = 0; i < SPAWN_RATE && particles.size() < MAX_PARTICLES; i++) {
                Particle p = pool.obtain();
                p.init(
                    random.nextInt(screenWidth),
                    random.nextInt(screenHeight),
                    (random.nextFloat() - 0.5f) * 0.5f,
                    (random.nextFloat() - 0.5f) * 0.5f,
                    random.nextInt(3) + 2,
                    random.nextFloat() * 0.6f + 0.4f,
                    -1,
                    false
                );
                particles.add(p);
            }
        }
    }
    
    private void updateAndCleanParticles(float deltaTime) {
        particles.removeIf(particle -> {
            particle.update(deltaTime, mouseX, mouseY, CURSOR_BOUNCE_RADIUS);
            boolean remove = particle.isOutOfBounds(screenWidth, screenHeight);
            if (remove) {
                pool.free(particle);
            }
            return remove;
        });
    }
    
    public void extractRenderState(GuiGraphicsExtractor context) {
        if (particles.isEmpty()) return;
        
        int baseColor = colorMode.getColor();
        for (Particle particle : particles) {
            particle.extractRenderState(context, baseColor);
        }
    }
    
    private boolean isSimplifiedUI() {
        long now = System.currentTimeMillis();
        if (now - lastSimplifiedCheck > SIMPLIFIED_CHECK_INTERVAL) {
            lastSimplifiedCheck = now;
            try {
                simplifiedUI = RandomRunMod.getInstance().getConfig().getUIStyle() == 
                    com.randomrun.infrastructure.config.ModConfig.UIStyle.SIMPLIFIED;
            } catch (Exception e) {
                simplifiedUI = false;
            }
        }
        return simplifiedUI;
    }
    
    public enum ColorMode {
        DEFAULT(0x6930c3),
        RED(0xFF5555),
        GREEN(0x55FF55),
        YELLOW(0xFFFF55);
        
        private final int color;
        
        ColorMode(int color) {
            this.color = color;
        }
        
        public int getColor() {
            return color;
        }
    }
    
    private static class ParticlePool {
        private final List<Particle> pool;
        private final int maxSize;
        
        public ParticlePool(int maxSize) {
            this.maxSize = maxSize;
            this.pool = new ArrayList<>(maxSize);
            for (int i = 0; i < maxSize; i++) {
                pool.add(new Particle());
            }
        }
        
        public Particle obtain() {
            if (pool.isEmpty()) {
                return new Particle();
            }
            return pool.remove(pool.size() - 1);
        }
        
        public void free(Particle particle) {
            if (pool.size() < maxSize) {
                pool.add(particle);
            }
        }
    }
    
    private static class Particle {
        float x, y;
        float vx, vy;
        int size;
        float alpha;
        int colorOverride;
        boolean fastFade;
        
        private static final float FRICTION = 0.97f;
        private static final float GRAVITY = 0.05f;
        private static final float FADE_SPEED_NORMAL = 0.005f;
        private static final float FADE_SPEED_FAST = 0.015f;
        
        public Particle() {
            this.colorOverride = -1;
            this.fastFade = false;
        }
        
        public void init(float x, float y, float vx, float vy, int size, float alpha, int colorOverride, boolean fastFade) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.alpha = alpha;
            this.colorOverride = colorOverride;
            this.fastFade = fastFade;
        }
        
        public void update(float deltaTime, int cursorX, int cursorY, int bounceRadius) {
            x += (vx / 60.0f) * deltaTime;
            y += (vy / 60.0f) * deltaTime;
            
            float fadeSpeed = fastFade ? FADE_SPEED_FAST : FADE_SPEED_NORMAL;
            alpha -= (fadeSpeed / 60.0f) * deltaTime;
            
            float friction = (float) Math.pow(FRICTION, deltaTime / 60.0f);
            vx *= friction;
            vy *= friction;
            
            if (colorOverride != -1) {
                vy += (GRAVITY / 60.0f) * deltaTime;
            }
            
            float dx = x - cursorX;
            float dy = y - cursorY;
            float distSq = dx * dx + dy * dy;
            float bounceRadiusSq = bounceRadius * bounceRadius;
            
            if (distSq < bounceRadiusSq && distSq > 0.01f) {
                float dist = (float) Math.sqrt(distSq);
                float nx = dx / dist;
                float ny = dy / dist;
                float force = (bounceRadius - dist) / bounceRadius * 1.5f;
                vx += nx * force * (deltaTime / 60.0f);
                vy += ny * force * (deltaTime / 60.0f);
            }
        }
        
        public void extractRenderState(GuiGraphicsExtractor context, int baseColor) {
            int alphaInt = (int) (alpha * 255);
            if (alphaInt > 0) {
                int finalColor = colorOverride != -1 ? colorOverride : baseColor;
                int color = (alphaInt << 24) | (finalColor & 0xFFFFFF);
                int x1 = (int) Math.floor(x);
                int y1 = (int) Math.floor(y);
                int x2 = (int) Math.ceil(x + size);
                int y2 = (int) Math.ceil(y + size);
                context.fill(x1, y1, x2, y2, color);
            }
        }
        
        public boolean isOutOfBounds(int screenWidth, int screenHeight) {
            return x < -20 || x > screenWidth + 20 || y < -20 || y > screenHeight + 20 || alpha <= 0;
        }
    }
}
