package com.randomrun.core.session;

import net.minecraft.network.chat.Component;

public enum TargetType {
    ITEM,
    MOB;
    
    public String getTranslationKey() {
        return switch (this) {
            case ITEM -> "randomrun.target.type.item";
            case MOB -> "randomrun.target.type.mob";
        };
    }
    
    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
}
