package com.randomrun.ui.widget;

import net.minecraft.client.gui.DrawContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GlobalParticleSystem {
    private static GlobalParticleSystem instance;
    private List<Particle> particles = new ArrayList<>();
    private Random random = new Random();
    private int screenWidth;
    private int screenHeight;
    private boolean redMode = false;
    private boolean greenMode = false;
    private boolean yellowMode = false;
    
    private static final int MAX_PARTICLES = 100;
    private static final int SPAWN_RATE = 2;
    
    private GlobalParticleSystem() {}
    
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
    
    public void setRedMode(boolean redMode) {
        this.redMode = redMode;
        if (redMode) {
            this.greenMode = false;
            this.yellowMode = false;
        }
    }

    public void setGreenMode(boolean greenMode) {
        this.greenMode = greenMode;
        if (greenMode) {
            this.redMode = false;
            this.yellowMode = false;
        }
    }

    public void setYellowMode(boolean yellowMode) {
        this.yellowMode = yellowMode;
        if (yellowMode) {
            this.redMode = false;
            this.greenMode = false;
        }
    }
    
    public void clearParticles() {
        particles.clear();
    }
    
    public void addExplosion(int x, int y, int count, int color) {
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float)Math.PI * 2;
            float speed = random.nextFloat() * 5.0f + 2.0f;
            particles.add(new Particle(
                x, y,
                (float)Math.cos(angle) * speed,
                (float)Math.sin(angle) * speed,
                random.nextInt(3) + 2,
                1.0f,
                color
            ));
        }
    }
    
    public void update() {
        // Spawn new particles (always active)
        for (int i = 0; i < SPAWN_RATE && particles.size() < MAX_PARTICLES; i++) {
            particles.add(new Particle(
                random.nextInt(screenWidth),
                random.nextInt(screenHeight),
                (random.nextFloat() - 0.5f) * 0.5f,
                (random.nextFloat() - 0.5f) * 0.5f,
                random.nextInt(3) + 2,
                random.nextFloat() * 0.6f + 0.4f
            ));
        }
        
        // Update existing particles
        particles.removeIf(particle -> {
            particle.update();
            return particle.x < 0 || particle.x > screenWidth || 
                   particle.y < 0 || particle.y > screenHeight ||
                   particle.alpha <= 0;
        });
    }
    
    public void render(DrawContext context) {
        for (Particle particle : particles) {
            particle.render(context);
        }
    }
    
    private static class Particle {
        float x, y;
        float vx, vy;
        int size;
        float alpha;
        int colorOverride = -1;
        
        Particle(float x, float y, float vx, float vy, int size, float alpha) {
            this(x, y, vx, vy, size, alpha, -1);
        }

        Particle(float x, float y, float vx, float vy, int size, float alpha, int colorOverride) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.alpha = alpha;
            this.colorOverride = colorOverride;
        }
        
        void update() {
            x += vx;
            y += vy;
            alpha -= 0.005f; // Slightly faster fade for explosion
            vx *= 0.95f; // Drag
            vy *= 0.95f; // Drag
            if (colorOverride != -1) vy += 0.1f; // Gravity for explosion particles
        }
        
        void render(DrawContext context) {
            int alphaInt = (int) (alpha * 255);
            if (alphaInt > 0) {
                int baseColor;
                if (colorOverride != -1) {
                    baseColor = colorOverride;
                } else {
                    baseColor = GlobalParticleSystem.getInstance().redMode ? 0xFF5555 : 
                               (GlobalParticleSystem.getInstance().greenMode ? 0x55FF55 : 
                               (GlobalParticleSystem.getInstance().yellowMode ? 0xFFFF55 : 0x6930c3));
                }
                int color = (alphaInt << 24) | (baseColor & 0xFFFFFF);
                // Используем float позиции для плавного движения
                int x1 = (int) Math.floor(x);
                int y1 = (int) Math.floor(y);
                int x2 = (int) Math.ceil(x + size);
                int y2 = (int) Math.ceil(y + size);
                context.fill(x1, y1, x2, y2, color);
            }
        }
    }
}
