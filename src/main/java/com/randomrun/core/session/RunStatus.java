package com.randomrun.core.session;

import net.minecraft.network.chat.Component;

public enum RunStatus {
    INACTIVE,
    FROZEN,
    RUNNING,
    COMPLETED,
    FAILED;
    
    public boolean isActive() {
        return this == RUNNING || this == FROZEN;
    }
    
    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
    
    public boolean canTransitionTo(RunStatus newStatus) {
        return switch (this) {
            case INACTIVE -> newStatus == FROZEN || newStatus == RUNNING;
            case FROZEN -> newStatus == RUNNING || newStatus == FAILED || newStatus == INACTIVE;
            case RUNNING -> newStatus == COMPLETED || newStatus == FAILED || newStatus == FROZEN || newStatus == INACTIVE;
            case COMPLETED, FAILED -> newStatus == INACTIVE;
        };
    }
    
    public String getTranslationKey() {
        return "randomrun.status." + name().toLowerCase();
    }
    
    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
}
