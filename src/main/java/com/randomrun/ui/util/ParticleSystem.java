package com.randomrun.ui.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ParticleSystem {
    private static final int MAX_PARTICLES = 50;
    private static final Random RANDOM = new Random();
    private final List<Particle> particles = new ArrayList<>(MAX_PARTICLES);
    
    public void spawnParticles(int x, int y, int count, int color1, int color2) {
        int actualCount = Math.min(count, MAX_PARTICLES - particles.size());
        for (int i = 0; i < actualCount; i++) {
            float angle = RANDOM.nextFloat() * (float) Math.PI * 2;
            float speed = 1 + RANDOM.nextFloat() * 2;
            particles.add(new Particle(x, y, 
                (float) Math.cos(angle) * speed, 
                (float) Math.sin(angle) * speed, 
                interpolateColor(color1, color2, RANDOM.nextFloat())));
        }
    }
    
    public void spawnCelebrationParticles(int x, int y, int count, int color1, int color2) {
        int actualCount = Math.min(count, MAX_PARTICLES - particles.size());
        for (int i = 0; i < actualCount; i++) {
            float angle = RANDOM.nextFloat() * (float) Math.PI * 2;
            float speed = 2 + RANDOM.nextFloat() * 4;
            particles.add(new Particle(x, y, 
                (float) Math.cos(angle) * speed, 
                (float) Math.sin(angle) * speed, 
                interpolateColor(color1, color2, RANDOM.nextFloat())));
        }
    }
    
    public void update(float delta) {
        particles.removeIf(p -> {
            p.update(delta);
            return p.life <= 0;
        });
    }
    
    public void extractRenderState(GuiGraphicsExtractor context, float delta) {
        for (Particle p : particles) {
            int alpha = (int) (p.life * 255);
            int color = (p.color & 0x00FFFFFF) | (alpha << 24);
            int size = Math.max(2, (int)(3 * p.life) + 1);
            context.fill((int) p.x, (int) p.y, (int) p.x + size, (int) p.y + size, color);
        }
    }
    
    public void clear() {
        particles.clear();
    }
    
    public static int interpolateColor(int color1, int color2, float ratio) {
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
    
    private static class Particle {
        float x, y;
        float vx, vy;
        float life = 1.0f;
        int color;
        
        Particle(float x, float y, float vx, float vy, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
        }
        
        void update(float delta) {
            x += vx * delta;
            y += vy * delta;
            vy += 0.08f * delta;
            life -= 0.02f * delta;
        }
    }
}
