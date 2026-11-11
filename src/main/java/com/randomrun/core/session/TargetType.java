package com.randomrun.core.session;

import net.minecraft.network.chat.Component;

public enum TargetType {
    ITEM;
    
    public String getTranslationKey() {
        return switch (this) {
            case ITEM -> "randomrun.target.type.item";
        };
    }
    
    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
}
