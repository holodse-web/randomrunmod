package com.randomrun.features.challenges.classic.world;

public enum WorldMode {
    STANDARD("Стандарт");
    
    private final String displayName;
    
    WorldMode(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
}
