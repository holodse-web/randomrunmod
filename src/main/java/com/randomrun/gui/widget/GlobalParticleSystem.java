package com.randomrun.gui.widget;

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
    }
    
    public void clearParticles() {
        particles.clear();
    }
    
    public void update() {
        // Spawn new particles
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
        
        Particle(float x, float y, float vx, float vy, int size, float alpha) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.alpha = alpha;
        }
        
        void update() {
            x += vx;
            y += vy;
            alpha -= 0.001f; // Медленнее затухание для плавности
        }
        
        void render(DrawContext context) {
            int alphaInt = (int) (alpha * 180);
            if (alphaInt > 0) {
                int baseColor = GlobalParticleSystem.getInstance().redMode ? 0xFF5555 : 0x6930c3;
                int color = (alphaInt << 24) | baseColor;
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
