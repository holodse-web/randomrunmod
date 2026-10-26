package com.randomrun.ui.screen.loading;

import com.randomrun.infrastructure.RandomRunMod;
import java.util.Random;
import net.minecraft.client.resources.language.I18n;

public class LoadingTips {
    private static final Random RANDOM = new Random();
    private static final int TIP_COUNT = 15;
    private static final long TIP_REFRESH_INTERVAL = 8000;
    
    private static String currentTip = "";
    private static long lastChangeTime = 0;
    
    public static void refreshTip() {
        int index = 1 + RANDOM.nextInt(TIP_COUNT);
        
        String key = "randomrun.loading.tip." + index;
        String prefixKey = "randomrun.loading.tip.prefix";
        
        try {
            String prefix = translateSafe(prefixKey, "Tip:");
            String text = translateSafe(key, "Loading...");
            currentTip = prefix + " §f" + text;
            lastChangeTime = System.currentTimeMillis();
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[LoadingTips] Ошибка загрузки - {}", e.getMessage());
            currentTip = "Loading...";
        }
    }
    
    private static String translateSafe(String key, String fallback) {
        try {
            String translated = I18n.get(key);
            if (translated.equals(key) || translated.contains("Format error")) {
                return fallback;
            }
            return translated;
        } catch (Exception e) {
            return fallback;
        }
    }
    
    public static String getCurrentTip() {
        if (shouldRefresh()) {
            refreshTip();
        }
        return currentTip;
    }
    
    private static boolean shouldRefresh() {
        return currentTip.isEmpty() || 
               System.currentTimeMillis() - lastChangeTime > TIP_REFRESH_INTERVAL;
    }
}
