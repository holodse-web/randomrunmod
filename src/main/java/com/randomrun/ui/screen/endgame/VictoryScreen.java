package com.randomrun.ui.screen.endgame;

import com.randomrun.core.history.Run;
import com.randomrun.core.history.RunResult;
import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.features.leaderboard.LeaderboardService;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.infrastructure.security.ModScannerService;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

public class VictoryScreen extends EndgameScreen {
    private final String winnerName;
    private final boolean wasBattle;
    private final Run currentRun;

    public VictoryScreen(Item item, long time, String winnerName) {
        this(item, time, winnerName, BattleManager.getInstance().isInBattle(), null);
    }

    public VictoryScreen(Item item, long time) {
        this(item, time, BackendSession.playerName());
    }

    public VictoryScreen(Run record) {
        this(resolve(record.getTargetId()), record.getDuration(), BackendSession.playerName(), record.isBattle(), record);
    }

    private VictoryScreen(Item item, long time, String winnerName, boolean battle, Run record) {
        super(Kind.VICTORY, item, time, !battle,
            record != null ? record.isUpgrader() : !battle && WorldCreator.isLastCreatedUpgrader(),
            record != null ? record.isRandomDrop() : WorldCreator.isLastCreatedRandomDrop());
        this.winnerName = winnerName;
        this.wasBattle = battle;
        this.currentRun = record != null ? record : new Run.Builder()
            .targetId(item != null ? BuiltInRegistries.ITEM.getKey(item).toString() : "minecraft:air")
            .duration(time)
            .timestamp(System.currentTimeMillis())
            .result(RunResult.VICTORY)
            .customSeed(WorldCreator.getLastCreatedSeed())
            .isBattle(battle)
            .isHardcore(false)
            .isUpgrader(!battle && WorldCreator.isLastCreatedUpgrader())
            .isRandomDrop(WorldCreator.isLastCreatedRandomDrop())
            .build();
    }

    private static Item resolve(String id) {
        Identifier identifier = id == null ? null : Identifier.tryParse(id);
        return identifier != null ? BuiltInRegistries.ITEM.getValue(identifier) : null;
    }

    public boolean wasBattle() {
        return wasBattle;
    }

    @Override
    protected Component subtitle() {
        if (wasBattle && winnerName != null) return LanguageManager.text("randomrun.endgame.winner", winnerName);
        return upgrader ? Component.literal("RRM Upgrader") : null;
    }

    @Override
    protected void onOpened() {
        if (minecraft.player != null) minecraft.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    @Override
    protected void collectStats() {
        List<Run> runs = history();
        boolean counted = runs.stream().anyMatch(run -> run.getDuration() == time && run.getResult() == RunResult.VICTORY);
        long previousBest = runs.stream().filter(run -> run.getResult() == RunResult.VICTORY && run.getDuration() > 0 && run.getDuration() != time)
            .mapToLong(Run::getDuration).min().orElse(-1);
        if (previousBest > 0 && time < previousBest) {
            badge = LanguageManager.text("randomrun.victory.new_record");
            badgeColor = 0xFFD700;
        } else if (previousBest < 0 && runs.size() <= 1) {
            badge = LanguageManager.text("randomrun.endgame.first_win");
            badgeColor = 0x4ADE80;
        }
        if (previousBest > 0) {
            long diff = time - previousBest;
            String delta = (diff <= 0 ? "§a-" : "§c+") + RunDataManager.formatTime(Math.abs(diff));
            stats.add(new Stat(LanguageManager.text("randomrun.endgame.best"), Component.literal(RunDataManager.formatTime(Math.min(previousBest, time)) + " " + delta), 0xFFCC00));
        }
        addHistoryStats(runs, counted ? 0 : 1, counted ? 0 : 1);
        boolean trusted = ModScannerService.getInstance().isTrusted();
        addTrustStat();
        resolveSubmission(trusted);
    }

    private void resolveSubmission(boolean trusted) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) return;
        PlayerProfile profile = PlayerProfile.get();
        if (profile == null) return;
        String reason = staticReason(profile, trusted);
        if (reason != null) {
            setNotes(false, reason);
            return;
        }
        setNotes(true, null);
        LeaderboardService.getInstance().getTopRecords(currentRun.getTargetId(), 10).orTimeout(5, TimeUnit.SECONDS).thenAccept(top -> {
            if (top.size() >= 10 && currentRun.getDuration() >= top.get(9).getCompletionTime()) {
                String text = LanguageManager.translate("randomrun.endgame.reason.not_top10",
                    RunDataManager.formatTime(currentRun.getDuration()), RunDataManager.formatTime(top.get(9).getCompletionTime()));
                Minecraft.getInstance().execute(() -> setNotes(false, text));
            }
        }).exceptionally(error -> null);
    }

    private void setNotes(boolean submitted, String reason) {
        List<Component> lines = new ArrayList<>();
        lines.add(LanguageManager.text(submitted ? "randomrun.endgame.submitted" : "randomrun.endgame.not_submitted")
            .withStyle(style -> style.withColor(submitted ? 0x4ADE80 : 0xF87171)));
        if (reason != null) lines.add(Component.literal(reason).withStyle(style -> style.withColor(0xA1A1AA)));
        notes = List.copyOf(lines);
    }

    private String staticReason(PlayerProfile profile, boolean trusted) {
        if (upgrader) return LanguageManager.translate("randomrun.endgame.reason.upgrader");
        if (!trusted) return LanguageManager.translate("randomrun.endgame.reason.untrusted");
        if (!profile.canSubmitToLeaderboard()) {
            return LanguageManager.translate("randomrun.endgame.reason.low_trust", profile.getTrustScore(), PlayerProfile.LEADERBOARD_THRESHOLD);
        }
        if (!currentRun.isEligibleForLeaderboard()) {
            if (currentRun.isSurvivor()) return LanguageManager.translate("randomrun.endgame.reason.survivor");
            if (currentRun.isRandomDrop()) return LanguageManager.translate("randomrun.endgame.reason.random_drop");
            if (!currentRun.isHardcore()) return LanguageManager.translate("randomrun.endgame.reason.not_hardcore");
            if (!currentRun.isBattle() && currentRun.getCustomSeed() != null) return LanguageManager.translate("randomrun.endgame.reason.custom_seed");
        }
        return null;
    }
}
