package com.randomrun.features.battle.manager;

import com.google.gson.annotations.SerializedName;

public enum GameMode {
    @SerializedName("default")
    DEFAULT("default", "Обычный", "Стандартное соревнование", true),
    
    @SerializedName("random_drop")
    RANDOM_DROP("random_drop", "Random Drop", "Случайные предметы из блоков", false);
    
    private final String id;
    private final String displayName;
    private final String description;
    private final boolean countsForLeaderboard;
    
    GameMode(String id, String displayName, String description, boolean countsForLeaderboard) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.countsForLeaderboard = countsForLeaderboard;
    }
    
    public String getId() {
        return id;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean countsForLeaderboard() {
        return countsForLeaderboard;
    }
    
    public static GameMode fromId(String id) {
        if (id == null) return DEFAULT;
        for (GameMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        return DEFAULT;
    }
    
    public static GameMode fromBoolean(boolean randomDrop) {
        return randomDrop ? RANDOM_DROP : DEFAULT;
    }
}
