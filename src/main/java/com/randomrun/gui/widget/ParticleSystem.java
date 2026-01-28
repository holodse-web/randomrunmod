package com.randomrun.gui.widget;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ParticleSystem {
    private final List<Particle> particles = new ArrayList<>();
    private final int screenWidth;
    private final int screenHeight;
    private final Random random = new Random();
    
    private static final int MAX_PARTICLES = 50;
    private static final int SPAWN_RATE = 2; // particles per update
    
    public ParticleSystem(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        
        // Initialize with some particles
        for (int i = 0; i < MAX_PARTICLES / 2; i++) {
            spawnParticle();
        }
    }
    
    public void update() {
        // Spawn new particles
        if (particles.size() < MAX_PARTICLES) {
            for (int i = 0; i < SPAWN_RATE; i++) {
                if (particles.size() < MAX_PARTICLES) {
                    spawnParticle();
                }
            }
        }
        
        // Update existing particles
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.update();
            
            // Remove dead particles
            if (particle.isDead()) {
                iterator.remove();
            }
        }
    }
    
    private void spawnParticle() {
        float x = random.nextFloat() * screenWidth;
        float y = random.nextFloat() * screenHeight;
        float vx = (random.nextFloat() - 0.5f) * 0.5f;
        float vy = (random.nextFloat() - 0.5f) * 0.5f;
        float size = 1 + random.nextFloat() * 2;
        int lifetime = 100 + random.nextInt(200);
        int color = getRandomColor();
        
        particles.add(new Particle(x, y, vx, vy, size, lifetime, color));
    }
    
    private int getRandomColor() {
        // Purple-ish colors matching the theme
        int[] colors = {
            0x6930c3, // Purple
            0x8B5CF6, // Light purple
            0x302b63, // Dark purple
            0x5B21B6, // Violet
            0x7C3AED  // Bright purple
        };
        return colors[random.nextInt(colors.length)];
    }
    
    public void render(DrawContext context) {
        for (Particle particle : particles) {
            particle.render(context);
        }
    }
    
    private static class Particle {
        private float x, y;
        private float vx, vy;
        private float size;
        private int lifetime;
        private int maxLifetime;
        private int color;
        
        public Particle(float x, float y, float vx, float vy, float size, int lifetime, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.lifetime = lifetime;
            this.maxLifetime = lifetime;
            this.color = color;
        }
        
        public void update() {
            x += vx;
            y += vy;
            lifetime--;
            
            // Slight upward drift
            vy -= 0.01f;
        }
        
        public boolean isDead() {
            return lifetime <= 0;
        }
        
        public void render(DrawContext context) {
            // Calculate alpha based on lifetime
            float lifeRatio = lifetime / (float) maxLifetime;
            int alpha = (int) (lifeRatio * 100);
            
            // Fade in at start, fade out at end
            if (lifeRatio > 0.8f) {
                alpha = (int) ((1f - lifeRatio) * 5 * 100);
            }
            
            int finalColor = (alpha << 24) | (color & 0x00FFFFFF);
            
            int px = (int) x;
            int py = (int) y;
            int s = (int) size;
            
            context.fill(px, py, px + s, py + s, finalColor);
        }
    }
}
