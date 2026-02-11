package com.randomrun.main.util;

import com.randomrun.main.RandomRunMod;

public class LanguageManager {
    
    private static final String[] LANGUAGES = {"ru_ru", "uk_ua", "en_us"};
    private static final String[] LANGUAGE_CODES = {"RU", "UA", "EN"};
    
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
        setLanguage(newLanguage);
    }
    
    public static void setLanguage(String newLanguage) {
        RandomRunMod.getInstance().getConfig().setLanguage(newLanguage);
        RandomRunMod.getInstance().saveConfig();
        
        // Применить смену языка к Minecraft
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (client != null && client.getLanguageManager() != null) {
            // В 1.21+ смена языка может требовать установки через options
            client.options.language = newLanguage;
            client.getLanguageManager().setLanguage(newLanguage);
            
            // Принудительно сохранить в options.txt
            client.options.write();
            
            // Перезагружаем ресурсы и обновляем экран после завершения
            client.reloadResources().thenRun(() -> {
                client.execute(() -> {
                    if (client.currentScreen != null) {
                        client.currentScreen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                    }
                });
            });
        }
    }
    
    public static void ensureLanguage() {
        String configLang = RandomRunMod.getInstance().getConfig().getLanguage();
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        
        if (client != null && client.getLanguageManager() != null) {
            String currentClientLang = client.getLanguageManager().getLanguage();
            
            // Если конфиг отличается от фактического языка клиента, принудительно обновить (регистронезависимо)
            if (!configLang.equalsIgnoreCase(currentClientLang)) {
                RandomRunMod.LOGGER.info("Обнаружено несовпадение языка (Конфиг: " + configLang + ", Игра: " + currentClientLang + "). Принудительное обновление...");
                
                // Устанавливаем язык в клиенте
                client.options.language = configLang;
                client.getLanguageManager().setLanguage(configLang);
                client.options.write();
                
                // Перезагружаем ресурсы
                client.reloadResources().thenRun(() -> {
                    client.execute(() -> {
                        if (client.currentScreen != null) {
                            client.currentScreen.init(client, client.getWindow().getScaledWidth(), client.getWindow().getScaledHeight());
                        }
                    });
                });
            }
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
