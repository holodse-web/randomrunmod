package com.randomrun.core.history;

import com.randomrun.infrastructure.util.LanguageManager;

public enum RunResult {
    VICTORY("V"),
    DEATH("D"),
    GAVE_UP("G"),
    TIMEOUT("T"),
    FAILED("F");
    
    private final String code;
    
    RunResult(String code) {
        this.code = code;
    }
    
    public String getCode() {
        return code;
    }
    
    public static RunResult fromCode(String code) {
        return switch (code) {
            case "V" -> VICTORY;
            case "D" -> DEATH;
            case "G" -> GAVE_UP;
            case "T" -> TIMEOUT;
            default -> FAILED;
        };
    }
    
    public String getDisplayName() {
        return LanguageManager.translate("randomrun.screen.history." + name().toLowerCase());
    }
}
