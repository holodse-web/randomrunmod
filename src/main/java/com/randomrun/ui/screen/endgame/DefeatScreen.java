package com.randomrun.ui.screen.endgame;

import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.core.session.RunSession;
import com.randomrun.core.session.RunSessionManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.infrastructure.util.LanguageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

public class DefeatScreen extends EndgameScreen {
    private final String reason;

    public DefeatScreen(Item item, long time, String reason) {
        this(item, time, reason, BattleManager.getInstance().isInBattle(), RunSessionManager.getInstance().getCurrentSession());
    }

    public DefeatScreen(Item item, long time) {
        this(item, time, null);
    }

    private DefeatScreen(Item item, long time, String reason, boolean battle, RunSession session) {
        super(Kind.DEFEAT, item, time, !battle,
            !battle && (session != null ? session.isUpgrader() : WorldCreator.isLastCreatedUpgrader()),
            session != null ? session.isRandomDrop() : WorldCreator.isLastCreatedRandomDrop());
        this.reason = reason;
    }

    @Override
    protected Component subtitle() {
        return reason == null || reason.isEmpty() ? null : Component.literal(reason);
    }

    @Override
    protected void onOpened() {
        if (minecraft.player != null) minecraft.player.playSound(SoundEvents.WITHER_SPAWN, 0.6f, 1.0f);
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
