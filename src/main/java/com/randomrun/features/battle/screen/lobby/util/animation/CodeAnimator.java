package com.randomrun.features.battle.screen.lobby.util.animation;

import java.util.Random;

public class CodeAnimator {
    private static final long CODE_ANIMATION_DURATION = 2000;
    private static final long DIGIT_REVEAL_INTERVAL = 400;
    
    private final String roomCode;
    private final long startTime;
    private final Random random = new Random();
    private String cachedCode = null;
    private long lastCacheTime = 0;
    private static final long CACHE_DURATION = 16;
    
    public CodeAnimator(String roomCode) {
        this.roomCode = roomCode;
        this.startTime = System.currentTimeMillis();
    }
    
    public String getAnimatedCode() {
        long currentTime = System.currentTimeMillis();
        
        if (cachedCode != null && currentTime - lastCacheTime < CACHE_DURATION) {
            return cachedCode;
        }
        
        long elapsed = currentTime - startTime;
        
        if (elapsed >= CODE_ANIMATION_DURATION) {
            cachedCode = roomCode;
            lastCacheTime = currentTime;
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
        
        cachedCode = animatedCode.toString();
        lastCacheTime = currentTime;
        return cachedCode;
    }
    
    public long getElapsedTime() {
        return System.currentTimeMillis() - startTime;
    }
    
    public boolean isAnimationComplete() {
        return getElapsedTime() >= CODE_ANIMATION_DURATION;
    }
    
    public long getAnimationDuration() {
        return CODE_ANIMATION_DURATION;
    }
}
