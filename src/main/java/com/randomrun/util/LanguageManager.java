package com.randomrun.util;

import com.randomrun.RandomRunMod;

public class LanguageManager {
    
    private static final String[] LANGUAGES = {"ru_ru", "uk_ua", "en_us", "be_by"};
    private static final String[] LANGUAGE_CODES = {"RU", "UA", "EN", "BY"};
    
    public static void cycleLanguage() {
        String current = RandomRunMod.getInstance().getConfig().getLanguage();
        int currentIndex = 0;
        
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i].equals(current)) {
                currentIndex = i;
                break;
            }
        }
        
        int nextIndex = (currentIndex + 1) % LANGUAGES.length;
        String newLanguage = LANGUAGES[nextIndex];
        RandomRunMod.getInstance().getConfig().setLanguage(newLanguage);
        RandomRunMod.getInstance().saveConfig();
        
        // Apply language change to Minecraft
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.getLanguageManager() != null) {
            client.getLanguageManager().setLanguage(newLanguage);
            client.reloadResources();
        }
    }
    
    public static void setLanguage(String newLanguage) {
        RandomRunMod.getInstance().getConfig().setLanguage(newLanguage);
        RandomRunMod.getInstance().saveConfig();
        
        // Apply language change to Minecraft
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.getLanguageManager() != null) {
            client.getLanguageManager().setLanguage(newLanguage);
            client.reloadResources();
        }
    }
    
    public static String getCurrentLanguage() {
        return RandomRunMod.getInstance().getConfig().getLanguage();
    }
    
    public static String getCurrentLanguageCode() {
        String current = getCurrentLanguage();
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i].equals(current)) {
                return LANGUAGE_CODES[i];
            }
        }
        return "RU";
    }
    
    public static int getLanguageIndex() {
        String current = getCurrentLanguage();
        for (int i = 0; i < LANGUAGES.length; i++) {
            if (LANGUAGES[i].equals(current)) {
                return i;
            }
        }
        return 0;
    }
}
