package com.randomrun.ui.screen.loading;

import com.randomrun.infrastructure.util.LanguageManager;
import java.util.concurrent.ThreadLocalRandom;

public final class LoadingTips {
    private static final int TIP_COUNT = 24;
    private static final long TIP_REFRESH_INTERVAL = 8000;
    private static String currentTip = "";
    private static long lastChangeTime;
    private static int lastIndex = -1;

    private LoadingTips() {}

    public static void refreshTip() {
        int index;
        do index = 1 + ThreadLocalRandom.current().nextInt(TIP_COUNT); while (index == lastIndex && TIP_COUNT > 1);
        lastIndex = index;
        String key = "randomrun.loading.tip." + index;
        String text = LanguageManager.translate(key);
        currentTip = text.equals(key) ? "" : LanguageManager.translate("randomrun.loading.tip.prefix") + " §f" + text;
        lastChangeTime = System.currentTimeMillis();
    }

    public static String getCurrentTip() {
        if (currentTip.isEmpty() || System.currentTimeMillis() - lastChangeTime > TIP_REFRESH_INTERVAL) refreshTip();
        return currentTip;
    }
}
