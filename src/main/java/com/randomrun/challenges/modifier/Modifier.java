package com.randomrun.challenges.modifier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Modifier {
    private final String id;
    private final Text name;
    private final Text description;
    private final ItemStack icon;
    private final Rarity rarity;
    
    public Modifier(String id, String nameKey, String descKey, Item item, Rarity rarity) {
        this.id = id;
        this.name = Text.translatable(nameKey);
        this.description = Text.translatable(descKey);
        this.icon = new ItemStack(item);
        this.rarity = rarity;
    }
    
    public String getId() { return id; }
    public Text getName() { return name; }
    public Text getDescription() { return description; }
    public ItemStack getIcon() { return icon; }
    public Rarity getRarity() { return rarity; }
    
    public enum Rarity {
        COMMON(Formatting.WHITE, 0xFFFFFF, 0xFFFFFF),
        UNCOMMON(Formatting.GREEN, 0x55FF55, 0x55FF55),
        RARE(Formatting.AQUA, 0x55FFFF, 0x00AAFF),
        EPIC(Formatting.LIGHT_PURPLE, 0xFF55FF, 0xAA00AA),
        LEGENDARY(Formatting.GOLD, 0xFFAA00, 0xFFAA00);
        
        public final Formatting formatting;
        public final int color;
        public final int particleColor;
        
        Rarity(Formatting formatting, int color, int particleColor) {
            this.formatting = formatting;
            this.color = color;
            this.particleColor = particleColor;
        }
    }
}
