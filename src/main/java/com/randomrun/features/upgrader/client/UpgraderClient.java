package com.randomrun.features.upgrader.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.randomrun.core.session.RunSession;
import com.randomrun.core.session.RunSessionManager;
import com.randomrun.core.session.RunStatus;
import com.randomrun.features.upgrader.UpgraderMath;
import com.randomrun.features.upgrader.UpgraderNetwork;
import com.randomrun.features.upgrader.UpgraderPackets;
import com.randomrun.features.upgrader.UpgraderServer;
import com.randomrun.features.upgrader.UpgraderTransaction;
import com.randomrun.features.upgrader.UpgraderValues;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LegacyKeyCodes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class UpgraderClient {
    private static boolean openKeyDown;
    private static int observedOpenKey = -1;
    private static long busyUntil, animationStartedNanos, nextId;
    private static Attempt active;
    private static RunSession needleSession;
    private static double restingNeedleTurn;

    private static final class Attempt {
        final long id = ++nextId;
        final MinecraftServer server;
        final RunSession session;
        final CompletableFuture<RollResult> result = new CompletableFuture<>();
        final CompletableFuture<Boolean> settled = new CompletableFuture<>();
        final long requestedNanos = System.nanoTime();
        boolean skipped;
        double visualRoll;
        Attempt(MinecraftServer server, RunSession session) { this.server = server; this.session = session; }
    }

    public record Offer(int slot, ItemStack stack, long value) {}
    public record RollResult(boolean accepted, boolean success, double chance, double roll,
                             String message, ItemStack reward) {}

    private UpgraderClient() {}

    public static void register() {
        UpgraderNetwork.register();
        ServerLifecycleEvents.SERVER_STARTED.register(UpgraderValues::initialize);
        ServerTickEvents.END_SERVER_TICK.register(UpgraderServer::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(UpgraderServer::stop);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            UpgraderNetwork.tickClient();
            Attempt attempt = active;
            if (attempt != null && !attempt.settled.isDone()) {
                if (attempt.session != RunSessionManager.getInstance().getCurrentSession()
                        || !(client.gui.screen() instanceof UpgraderScreen)) skipAnimation();
                if (!attempt.result.isDone() && System.nanoTime() - attempt.requestedNanos > 15_000_000_000L) {
                    skipAnimation();
                    attempt.result.complete(failure("unavailable"));
                }
            }
            checkOpenKey(client);
        });
    }

    public static boolean available() {
        Minecraft client = Minecraft.getInstance();
        RunSession session = RunSessionManager.getInstance().getCurrentSession();
        if (client.player == null || client.level == null || !client.player.isAlive()
                || client.player.isSpectator() || client.player.isCreative() || session == null
                || !session.isUpgrader() || session.getStatus() != RunStatus.RUNNING || session.isPaused()) return false;
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) return session.isBattle() && UpgraderNetwork.available(session.getBattleRoomId());
        return !server.isPublished() || session.isBattle();
    }

    private static void checkOpenKey(Minecraft client) {
        int key = LegacyKeyCodes.toInputKey(RandomRunMod.getInstance().getConfig().getUpgraderKey());
        boolean down = key > 0 && client.isWindowActive() && InputConstants.isKeyDown(key);
        if (key != observedOpenKey) {
            observedOpenKey = key;
            openKeyDown = down;
            return;
        }
        boolean pressed = down && !openKeyDown;
        openKeyDown = down;
        if (pressed && client.gui.screen() == null && available()) client.gui.setScreen(new UpgraderScreen());
    }

    public static boolean matchesOpenKey(KeyEvent event) {
        int key = LegacyKeyCodes.toInputKey(RandomRunMod.getInstance().getConfig().getUpgraderKey());
        return key > 0 && event.key() == key;
    }
    public static Component keyName() {
        int key = LegacyKeyCodes.toInputKey(RandomRunMod.getInstance().getConfig().getUpgraderKey());
        return key > 0 ? InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName()
            : com.randomrun.infrastructure.util.LanguageManager.text("randomrun.control.unbound");
    }
    public static void onScreenOpened() {
        RandomRunMod mod = RandomRunMod.getInstance();
        if (mod != null && mod.getConfig().markUpgraderHintSeen()) mod.saveConfig();
    }
    public static boolean isAnimating() { return active != null && (!active.settled.isDone() || System.nanoTime() < busyUntil); }
    public static long animationRemainingMillis() {
        Attempt attempt = active;
        if (attempt == null || attempt.skipped) return 0;
        long end = !attempt.settled.isDone() && animationStartedNanos > 0
            ? animationStartedNanos + (UpgraderMath.SPIN_MILLIS + UpgraderMath.RESULT_HOLD_MILLIS) * 1_000_000L : busyUntil;
        return Math.max(0, (end - System.nanoTime()) / 1_000_000L);
    }
    public static long animationStartedNanos() { return animationStartedNanos; }
    public static double animationVisualRoll() { return active == null ? 0 : active.visualRoll; }

    public static double restingNeedleTurn() {
        RunSession session = RunSessionManager.getInstance().getCurrentSession();
        if (needleSession != session) { needleSession = session; restingNeedleTurn = 0; }
        return restingNeedleTurn;
    }

    public static List<Offer> offers() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return List.of();
        List<Offer> result = new ArrayList<>();
        for (int slot = 0; slot < UpgraderTransaction.INVENTORY_SLOTS; slot++) {
            ItemStack stack = client.player.getInventory().getItem(slot);
            long value = UpgraderValues.value(stack);
            if (!stack.isEmpty() && value > 0) result.add(new Offer(slot, stack.copy(), value));
        }
        return List.copyOf(result);
    }

    public static List<Item> targets() { return UpgraderValues.targets(); }
    public static long value(ItemStack stack) { return UpgraderValues.value(stack); }
    public static long unitValue(Item item) { return UpgraderValues.unitValue(item); }

    public static CompletableFuture<RollResult> upgrade(Map<Integer, Integer> amounts, Item target) {
        Minecraft client = Minecraft.getInstance();
        if (!available() || isAnimating() || target == null) return CompletableFuture.completedFuture(failure("unavailable"));
        final Map<Integer, Integer> selection;
        try { selection = Map.copyOf(amounts); }
        catch (RuntimeException error) { return CompletableFuture.completedFuture(failure("invalid_selection")); }
        Map<Integer, ItemStack> expected = new HashMap<>();
        Set<Item> types = Collections.newSetFromMap(new IdentityHashMap<>());
        long count = 0;
        for (var entry : selection.entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= UpgraderTransaction.INVENTORY_SLOTS || entry.getValue() <= 0)
                return CompletableFuture.completedFuture(failure("invalid_selection"));
            count += entry.getValue();
            ItemStack snapshot = client.player.getInventory().getItem(entry.getKey()).copy();
            expected.put(entry.getKey(), snapshot);
            if (!snapshot.isEmpty()) types.add(snapshot.getItem());
        }
        if (count > UpgraderTransaction.MAX_SELECTED_ITEMS) return CompletableFuture.completedFuture(failure("selection_limit"));
        if (types.size() > UpgraderTransaction.MAX_SELECTED_TYPES) return CompletableFuture.completedFuture(failure("selection_types_limit"));
        if (count == 0) return CompletableFuture.completedFuture(failure("invalid_selection"));
        Attempt attempt = new Attempt(client.getSingleplayerServer(), RunSessionManager.getInstance().getCurrentSession());
        active = attempt;
        animationStartedNanos = busyUntil = 0;
        String room = attempt.session.isBattle() ? attempt.session.getBattleRoomId() : "";
        if (attempt.server != null) {
            var playerId = client.player.getUUID();
            attempt.server.execute(() -> UpgraderServer.request(attempt.server,
                attempt.server.getPlayerList().getPlayer(playerId), attempt.id, room, selection, expected, target,
                result -> client.execute(() -> receiveResult(attempt.id, result)),
                completed -> client.execute(() -> receiveSettled(attempt.id, completed))));
        } else {
            List<UpgraderPackets.Slot> slots = selection.entrySet().stream()
                .map(entry -> new UpgraderPackets.Slot(entry.getKey(), entry.getValue(), expected.get(entry.getKey())))
                .toList();
            UpgraderNetwork.send(new UpgraderPackets.Roll(attempt.id, room, BuiltInRegistries.ITEM.getKey(target).toString(), slots));
        }
        return attempt.result;
    }

    public static void receiveResult(long id, RollResult result) {
        Attempt attempt = active;
        if (attempt == null || attempt.id != id) return;
        if (attempt.result.isDone()) {
            if (result.accepted() && attempt.skipped) control(attempt, 2);
            else if (!result.accepted()) attempt.settled.complete(false);
            return;
        }
        if (result.accepted()) {
            attempt.visualRoll = UpgraderWheel.presentationRoll(result.roll(), result.chance(), ThreadLocalRandom.current().nextDouble());
            if (RunSessionManager.getInstance().getCurrentSession() == attempt.session) {
                restingNeedleTurn();
                restingNeedleTurn = UpgraderWheel.stoppedTurn(attempt.visualRoll, result.chance());
            }
            animationStartedNanos = System.nanoTime();
            control(attempt, attempt.skipped ? 2 : 0);
        } else {
            attempt.settled.complete(false);
        }
        attempt.result.complete(result);
    }

    public static void receiveSettled(long id, boolean completed) {
        Attempt attempt = active;
        if (attempt == null || attempt.id != id) return;
        busyUntil = attempt.skipped ? 0 : System.nanoTime() + UpgraderMath.RESULT_HOLD_MILLIS * 1_000_000L;
        attempt.settled.complete(completed);
    }

    public static CompletableFuture<Boolean> finishAnimation() {
        Attempt attempt = active;
        if (attempt == null) return CompletableFuture.completedFuture(false);
        if (!attempt.settled.isDone()) control(attempt, 1);
        return attempt.settled;
    }

    public static void skipAnimation() {
        Attempt attempt = active;
        if (attempt == null) return;
        busyUntil = 0;
        if (attempt.skipped) return;
        attempt.skipped = true;
        control(attempt, 2);
    }

    private static void control(Attempt attempt, int action) {
        if (attempt.server == null) UpgraderNetwork.control(attempt.id, action);
        else {
            var client = Minecraft.getInstance();
            if (client.player == null) return;
            var playerId = client.player.getUUID();
            attempt.server.execute(() -> {
                ServerPlayer player = attempt.server.getPlayerList().getPlayer(playerId);
                if (player != null) UpgraderServer.control(player, attempt.id, action);
            });
        }
    }

    public static void settleForPlayer(ServerPlayer player) { UpgraderServer.closePlayer(player); }

    private static RollResult failure(String reason) { return new RollResult(false, false, 0, 0, reason, ItemStack.EMPTY); }

    private static void reset() {
        skipAnimation();
        busyUntil = animationStartedNanos = 0;
        active = null;
        needleSession = null;
        restingNeedleTurn = 0;
        UpgraderNetwork.resetClient();
    }
}
