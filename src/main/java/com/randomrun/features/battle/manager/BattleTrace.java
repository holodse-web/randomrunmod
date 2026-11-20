package com.randomrun.features.battle.manager;

import com.randomrun.core.run.RunSession;
import com.randomrun.core.run.RunSessionManager;
import com.randomrun.network.RealtimeClient;

import net.minecraft.client.Minecraft;

public final class BattleTrace {
    private static final long INTERVAL_MS = 5000;

    private static long lastSent;
    private static int lastEntityId = -1;
    private static boolean wasDead;
    private static boolean respawned;
    private static String lastDimension;

    private BattleTrace() {}

    public static void tick(Minecraft client) {
        BattleManager manager = BattleManager.getInstance();
        RunSession session = RunSessionManager.getInstance().getCurrentSession();
        BattleRoom room = manager.getCurrentRoom();
        if (!manager.isInBattle() || room == null || session == null || !session.isBattle()
                || session.getStatus() != RunSession.RunStatus.RUNNING || client.player == null || client.level == null) {
            lastEntityId = -1;
            return;
        }
        if (client.player.isDeadOrDying()) wasDead = true;
        else if (wasDead) { wasDead = false; respawned = true; }
        if (lastEntityId != -1 && lastEntityId != client.player.getId()) respawned = true;
        lastEntityId = client.player.getId();
        String dimension = client.level.dimension().identifier().toString();
        boolean dimensionChanged = lastDimension != null && !lastDimension.equals(dimension);
        lastDimension = dimension;
        long now = System.currentTimeMillis();
        if (now - lastSent < INTERVAL_MS && !dimensionChanged) return;
        send(client, false);
    }

    public static void sendItemFound() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        send(client, true);
    }

    private static void send(Minecraft client, boolean item) {
        lastSent = System.currentTimeMillis();
        RealtimeClient.getInstance().sendTrace(client.player.getX(), client.player.getZ(),
            client.level.dimension().identifier().toString(), item, respawned);
        respawned = false;
    }

    public static java.util.Map<String, Object> itemTrace() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return null;
        return java.util.Map.of("x", Math.round(client.player.getX() * 10) / 10.0, "z", Math.round(client.player.getZ() * 10) / 10.0,
            "dim", client.level.dimension().identifier().toString(), "item", true);
    }
}
