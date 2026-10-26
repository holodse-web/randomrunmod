package com.randomrun.infrastructure.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.randomrun.infrastructure.RandomRunMod;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class LanguageManager {
    private static final String LOG_TAG = "[RRM:Lang]";
    
    public enum Language {
        RUSSIAN("ru_ru", "RU", "Русский"),
        UKRAINIAN("uk_ua", "UA", "Українська"),
        ENGLISH("en_us", "EN", "English");
        
        private final String code;
        private final String shortCode;
        private final String displayName;
        
        Language(String code, String shortCode, String displayName) {
            this.code = code;
            this.shortCode = shortCode;
            this.displayName = displayName;
        }
        
        public String getCode() { return code; }
        public String getShortCode() { return shortCode; }
        public String getDisplayName() { return displayName; }
        
        public static Language fromCode(String code) {
            for (Language lang : values()) {
                if (lang.code.equals(code)) return lang;
            }
            return RUSSIAN;
        }
    }
    
    public interface LanguageChangeListener {
        void onLanguageChanged(String newLanguage);
    }
    
    private static final Map<String, Map<String, String>> allTranslations = new ConcurrentHashMap<>();
    private static final List<LanguageChangeListener> listeners = new ArrayList<>();
    
    private static String currentLanguage = null;
    private static boolean initialized = false;
    
    private static final Gson gson = new Gson();
    
    public static void cycleLanguage() {
        Language current = Language.fromCode(getCurrentLanguage());
        Language[] languages = Language.values();
        int currentIndex = current.ordinal();
        int nextIndex = (currentIndex + 1) % languages.length;
        setLanguage(languages[nextIndex].getCode());
    }
    
    public static void setLanguage(String newLanguage) {
        RandomRunMod.getInstance().getConfig().setLanguage(newLanguage);
        RandomRunMod.getInstance().saveConfig();
        
        currentLanguage = newLanguage;
        notifyListeners(newLanguage);
        
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gui.screen() != null) {
            net.minecraft.client.gui.screens.Screen currentScreen = client.gui.screen();
            client.execute(() -> {
                client.gui.setScreen(null);
                client.gui.setScreen(currentScreen);
                currentScreen.init(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight());
            });
        }
    }
    
    private static void initialize() {
        if (initialized) return;
        
        synchronized (LanguageManager.class) {
            if (initialized) return;
            
            try {
                long startTime = System.currentTimeMillis();
                currentLanguage = getCurrentLanguage();
                
                for (Language lang : Language.values()) {
                    loadLanguage(lang.getCode());
                }
                
                long duration = System.currentTimeMillis() - startTime;
                int totalTranslations = allTranslations.values().stream()
                    .mapToInt(Map::size)
                    .sum();
                
                initialized = true;
            } catch (Exception e) {
                CompactLogger.error("{} Init failed: {}", LOG_TAG, e.getMessage());
            }
        }
    }
    
    private static void loadLanguage(String langCode) {
        Map<String, String> langMap = new ConcurrentHashMap<>();
        
        try {
            String path = "/assets/randomrun/lang/" + langCode + ".json";
            var stream = LanguageManager.class.getResourceAsStream(path);
            
            if (stream != null) {
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<Map<String, String>>(){}.getType();
                    Map<String, String> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        langMap.putAll(loaded);
                        allTranslations.put(langCode, langMap);
                    } else {
                        CompactLogger.warn("{} Empty file: {}", LOG_TAG, path);
                    }
                }
            } else {
                CompactLogger.error("{} File not found: {}", LOG_TAG, path);
            }
        } catch (Exception e) {
            CompactLogger.error("{} Load failed {}: {}", LOG_TAG, langCode, e.getMessage());
        }
    }
    
    public static String translate(String key, Object... args) {
        initialize();
        
        Map<String, String> currentLangMap = allTranslations.get(currentLanguage);
        String value = currentLangMap != null ? currentLangMap.get(key) : null;
        
        if (value == null) {
            Map<String, String> englishMap = allTranslations.get(Language.ENGLISH.getCode());
            value = englishMap != null ? englishMap.get(key) : null;
        }
        
        if (value == null) {
            return key;
        }
        
        if (args.length > 0) {
            try {
                return String.format(value, args);
            } catch (Exception e) {
                return value;
            }
        }
        return value;
    }
    
    public static net.minecraft.network.chat.MutableComponent text(String key, Object... args) {
        return Component.literal(translate(key, args));
    }
    
    public static Map<String, String> translateBatch(List<String> keys) {
        initialize();
        return keys.stream()
            .collect(Collectors.toMap(k -> k, LanguageManager::translate));
    }
    
    public static boolean hasTranslation(String key) {
        initialize();
        Map<String, String> currentLangMap = allTranslations.get(currentLanguage);
        if (currentLangMap != null && currentLangMap.containsKey(key)) {
            return true;
        }
        Map<String, String> englishMap = allTranslations.get(Language.ENGLISH.getCode());
        return englishMap != null && englishMap.containsKey(key);
    }
    
    public static void ensureLanguage() {
        initialize();
    }
    
    public static String getCurrentLanguage() {
        if (currentLanguage != null) {
            return currentLanguage;
        }
        
        try {
            if (RandomRunMod.getInstance() != null && RandomRunMod.getInstance().getConfig() != null) {
                currentLanguage = RandomRunMod.getInstance().getConfig().getLanguage();
                return currentLanguage;
            }
        } catch (Exception e) {
            CompactLogger.warn("{} Config unavailable, using ru_ru", LOG_TAG);
        }
        
        currentLanguage = "ru_ru";
        return currentLanguage;
    }
    
    public static Language getCurrentLanguageEnum() {
        return Language.fromCode(getCurrentLanguage());
    }
    
    public static String getCurrentLanguageCode() {
        return getCurrentLanguageEnum().getShortCode();
    }
    
    public static int getLanguageIndex() {
        return getCurrentLanguageEnum().ordinal();
    }
    
    public static List<String> getAvailableLanguages() {
        return Arrays.stream(Language.values())
            .map(Language::getCode)
            .collect(Collectors.toList());
    }
    
    public static String getLanguageName(String code) {
        return Language.fromCode(code).getDisplayName();
    }
    
    public static void addListener(LanguageChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    public static void removeListener(LanguageChangeListener listener) {
        listeners.remove(listener);
    }
    
    private static void notifyListeners(String newLanguage) {
        for (LanguageChangeListener listener : listeners) {
            try {
                listener.onLanguageChanged(newLanguage);
            } catch (Exception e) {
                CompactLogger.error("{} Listener error: {}", LOG_TAG, e.getMessage());
            }
        }
    }
    
    public static void clearCache() {
    }
    
    public static int getTranslationCount() {
        Map<String, String> currentLangMap = allTranslations.get(currentLanguage);
        return currentLangMap != null ? currentLangMap.size() : 0;
    }
    
    public static int getTotalTranslationCount() {
        return allTranslations.values().stream()
            .mapToInt(Map::size)
            .sum();
    }
    
    public static String translateItem(String itemId) {
        try {
            String fullId = itemId;
            if (!itemId.contains(":")) {
                fullId = IdCompressor.decompress(itemId);
            }
            
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(fullId);
            if (id != null) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(id);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    return net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
                }
            }
        } catch (Exception e) {
            CompactLogger.warn("{} Item translate failed: {}", LOG_TAG, itemId);
        }
        
        return itemId;
    }
}
