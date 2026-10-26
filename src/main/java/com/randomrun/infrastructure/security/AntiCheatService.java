package com.randomrun.infrastructure.security;

import com.randomrun.core.session.RunSessionManager;
import com.randomrun.infrastructure.util.CompactLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

public class AntiCheatService {
    private static final String LOG_TAG = "[AntiCheat]";

    private static final double MAX_SPEED_PER_TICK = 1.2;
    private static final double TELEPORT_THRESHOLD = 15.0;
    private static final double NORMAL_SPEED_SQUARED = Math.nextDown(MAX_SPEED_PER_TICK * MAX_SPEED_PER_TICK);
    private static final double NORMAL_TELEPORT_SQUARED = Math.nextDown(TELEPORT_THRESHOLD * TELEPORT_THRESHOLD);
    private static final int SPEED_VIOLATION_THRESHOLD = 20;
    private static final int ATTACK_RATE_THRESHOLD = 25;
    private static final long CHECK_INTERVAL_MS = 50;

    private Vec3 lastPosition = null;
    private long lastCheckTime = 0;
    private int speedViolations = 0;
    private int teleportViolations = 0;
    private int attackCount = 0;
    private long attackWindowStart = 0;
    private int totalFlags = 0;
    private boolean enabled = true;

    private static class Holder {
        private static final AntiCheatService INSTANCE = new AntiCheatService();
    }

    public static AntiCheatService getInstance() {
        return Holder.INSTANCE;
    }

    public void tick() {
        if (!enabled) return;

        RunSessionManager sessionManager = RunSessionManager.getInstance();
        if (!sessionManager.hasActiveRun()) {
            reset();
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastCheckTime < CHECK_INTERVAL_MS) return;
        lastCheckTime = now;

        LocalPlayer player = client.player;
        Vec3 currentPos = player.position();

        if (lastPosition != null) {
            checkSpeed(player, currentPos);
            checkTeleport(currentPos);
        }

        checkAttackRate(now);

        lastPosition = currentPos;
    }

    private void checkSpeed(LocalPlayer player, Vec3 currentPos) {
        double dx = currentPos.x - lastPosition.x;
        double dz = currentPos.z - lastPosition.z;
        double horizontalSpeedSquared = dx * dx + dz * dz;

        if (player.isFallFlying() || player.isPassenger() || player.isUnderWater()) {
            return;
        }

        if (exceedsSpeedLimit(horizontalSpeedSquared)) {
            speedViolations++;
            if (speedViolations >= SPEED_VIOLATION_THRESHOLD) {
                flagViolation(player.getName().getString(),
                    String.format("Speed violation: %.2f blocks/tick (%d violations)", Math.sqrt(horizontalSpeedSquared), speedViolations));
                speedViolations = 0;
            }
        } else {
            if (speedViolations > 0) speedViolations--;
        }
    }

    private void checkTeleport(Vec3 currentPos) {
        double distanceSquared = currentPos.distanceToSqr(lastPosition);

        if (exceedsTeleportLimit(distanceSquared)) {
            teleportViolations++;
            if (teleportViolations >= 3) {
                Minecraft client = Minecraft.getInstance();
                String playerName = client.player != null ? client.player.getName().getString() : "unknown";
                flagViolation(playerName,
                    String.format("Teleport: %.1f blocks (%d times)", Math.sqrt(distanceSquared), teleportViolations));
                teleportViolations = 0;
            }
        } else {
            teleportViolations = 0;
        }
    }

    static boolean exceedsSpeedLimit(double squaredDistance) {
        return squaredDistance > NORMAL_SPEED_SQUARED && Math.sqrt(squaredDistance) > MAX_SPEED_PER_TICK;
    }

    static boolean exceedsTeleportLimit(double squaredDistance) {
        return squaredDistance > NORMAL_TELEPORT_SQUARED && Math.sqrt(squaredDistance) > TELEPORT_THRESHOLD;
    }

    public void registerAttack() {
        long now = System.currentTimeMillis();
        if (now - attackWindowStart > 1000) {
            attackWindowStart = now;
            attackCount = 0;
        }
        attackCount++;
    }

    private void checkAttackRate(long now) {
        if (now - attackWindowStart > 1000 && attackCount > 0) {
            if (attackCount > ATTACK_RATE_THRESHOLD) {
                Minecraft client = Minecraft.getInstance();
                String playerName = client.player != null ? client.player.getName().getString() : "unknown";
                flagViolation(playerName,
                    String.format("KillAura: %d attacks/sec", attackCount));
            }
            attackCount = 0;
            attackWindowStart = now;
        }
    }

    private void flagViolation(String playerName, String description) {
        totalFlags++;
        CompactLogger.warn("{} Player {}: {}", LOG_TAG, playerName, description);
        try {
            com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
            if (profile != null) {
                profile.applyTrustPenalty(
                    com.randomrun.core.profile.PlayerProfile.PENALTY_CHEAT,
                    description
                );
                profile.addSuspiciousFlag();
                com.randomrun.core.profile.PlayerProfile.save();
            }
        } catch (Exception e) {
            CompactLogger.error("{} Penalty error: {}", LOG_TAG, e.getMessage());
        }
    }

    public void reset() {
        lastPosition = null;
        speedViolations = 0;
        teleportViolations = 0;
        attackCount = 0;
        attackWindowStart = 0;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTotalFlags() {
        return totalFlags;
    }
}
