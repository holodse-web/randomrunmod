package com.randomrun.features.speedrun;

import com.randomrun.core.data.PlayerProfile;
import com.randomrun.core.run.RunSession;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.security.ModScannerService;
import com.randomrun.network.BackendSession;
import com.randomrun.network.RunVerifier;

public final class RunEligibility {
    private RunEligibility() {}

    public static String statusKey(RunSession session) {
        if (session == null || session.isBattle()) return null;
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode() || !BackendSession.isAuthenticated()) return "randomrun.eligibility.offline";
        if (session.isUpgrader()) return "randomrun.eligibility.upgrader";
        if (session.isRandomDrop()) return "randomrun.eligibility.random_drop";
        if (com.randomrun.features.voice.control.VoiceController.isVoiceRun(session)) return "randomrun.eligibility.voice";
        if (session.getCustomSeed() != null && !session.getCustomSeed().isEmpty()) return "randomrun.eligibility.custom_seed";
        if (!ModScannerService.getInstance().isTrusted()) return "randomrun.eligibility.mods";
        if (!RunVerifier.isActive(session.getTargetId().replace("minecraft:", ""))) return "randomrun.eligibility.server";
        PlayerProfile profile = PlayerProfile.get();
        if (profile != null && !profile.canSubmitToLeaderboard()) return "randomrun.eligibility.trust";
        return "randomrun.eligibility.ok";
    }
}
