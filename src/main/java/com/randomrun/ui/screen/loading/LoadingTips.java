package com.randomrun.ui.screen.loading;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LoadingTips {
    private static final List<String> FACTS = new ArrayList<>();
    private static final List<String> TIPS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    
    private static String currentTip = "";
    private static boolean isFact = false;
    private static long lastChangeTime = 0;
    
    // Cache keys to avoid constantly checking I18n
    private static final int FACT_COUNT = 15;
    private static final int TIP_COUNT = 15;
    
    public static void refreshTip() {
        if (FACTS.isEmpty() || TIPS.isEmpty()) {
            loadTips();
        }
        
        isFact = RANDOM.nextBoolean();
        int index = 1 + RANDOM.nextInt(isFact ? FACT_COUNT : TIP_COUNT);
        
        String key = isFact ? "randomrun.loading.fact." + index : "randomrun.loading.tip." + index;
        String prefixKey = isFact ? "randomrun.loading.fact.prefix" : "randomrun.loading.tip.prefix";
        
        // We use I18n directly here to get string for rendering
        String prefix = I18n.translate(prefixKey);
        String text = I18n.translate(key);
        
        currentTip = prefix + " §f" + text;
        lastChangeTime = System.currentTimeMillis();
    }
    
    private static void loadTips() {
        // Just dummy init to ensure list isn't empty if we were using static lists
        // But we pull from Lang file dynamically
        FACTS.add("dummy");
        TIPS.add("dummy");
    }
    
    public static String getCurrentTip() {
        if (currentTip.isEmpty() || System.currentTimeMillis() - lastChangeTime > 5000) {
            refreshTip();
        }
        return currentTip;
    }
}
