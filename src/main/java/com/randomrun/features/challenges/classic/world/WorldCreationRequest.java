package com.randomrun.features.challenges.classic.world;

import net.minecraft.world.item.Item;

public class WorldCreationRequest {
    private final Item targetItem;
    private final String seed;
    private final WorldMode mode;
    private final boolean randomDrop;
    private final boolean upgrader;
    
    private WorldCreationRequest(Builder builder) {
        this.targetItem = builder.targetItem;
        this.seed = builder.seed;
        this.mode = builder.mode;
        this.randomDrop = builder.randomDrop;
        this.upgrader = builder.upgrader;
    }
    
    public Item getTargetItem() {
        return targetItem;
    }
    
    public String getSeed() {
        return seed;
    }
    
    public WorldMode getMode() {
        return mode;
    }
    
    public boolean isRandomDrop() {
        return randomDrop;
    }

    public boolean isUpgrader() { return upgrader; }
    
    public static class Builder {
        private Item targetItem;
        private String seed = null;
        private WorldMode mode = WorldMode.STANDARD;
        private boolean randomDrop = false;
        private boolean upgrader = com.randomrun.infrastructure.RandomRunMod.getInstance()
            .getConfig().isUpgraderEnabled();
        
        public Builder targetItem(Item item) {
            this.targetItem = item;
            return this;
        }
        
        public Builder seed(String seed) {
            this.seed = seed != null ? seed.trim() : null;
            return this;
        }
        
        public Builder mode(WorldMode mode) {
            this.mode = mode;
            return this;
        }
        
        public Builder randomDrop(boolean randomDrop) {
            this.randomDrop = randomDrop;
            return this;
        }

        public Builder upgrader(boolean upgrader) {
            this.upgrader = upgrader;
            return this;
        }
        
        public WorldCreationRequest build() {
            return new WorldCreationRequest(this);
        }
    }
}
