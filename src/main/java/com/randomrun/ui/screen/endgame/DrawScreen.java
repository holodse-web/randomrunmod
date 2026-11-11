package com.randomrun.ui.screen.endgame;

import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.infrastructure.util.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

public class DrawScreen extends EndgameScreen {
    private final String reason;

    public DrawScreen(Item item, long time, String reason) {
        super(Kind.DRAW, item, time, false, false, false);
        this.reason = reason;
    }

    @Override
    protected Component subtitle() {
        return reason == null || reason.isEmpty() ? LanguageManager.text("randomrun.endgame.draw_subtitle") : Component.literal(reason);
    }

    @Override
    protected void onOpened() {
        if (minecraft.player != null) minecraft.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.5f);
    }

    @Override
    protected void collectStats() {
        var runs = history();
        long best = bestVictory(runs);
        if (best > 0) stats.add(new Stat(LanguageManager.text("randomrun.endgame.best"), Component.literal(com.randomrun.infrastructure.data.RunDataManager.formatTime(best)), 0xFFCC00));
        addHistoryStats(runs, 0, 0);
        addTrustStat();
    }
}
