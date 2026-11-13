package com.randomrun.features.upgrader;

import com.randomrun.core.run.RunSession;
import com.randomrun.core.run.RunSession.RunStatus;
import com.randomrun.core.run.RunSessionManager;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.upgrader.UpgraderMath.UpgraderCompensation;
import com.randomrun.features.upgrader.client.UpgraderClient;
import com.randomrun.infrastructure.RandomRunMod;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class UpgraderServer {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private record Pending(MinecraftServer server, ServerPlayer player, long id, RunSession session,
                           UpgraderPendingReward reward, long acceptedNanos, Consumer<Boolean> completion) {}

    private UpgraderServer() {}

    public static boolean authorized(MinecraftServer server, ServerPlayer player, String roomId) {
        if (server == null || roomId == null || server != Minecraft.getInstance().getSingleplayerServer() || player == null
                || !player.isAlive() || player.isCreative() || player.isSpectator()) return false;
        RunSession session = RunSessionManager.getInstance().getCurrentSession();
        if (session == null || !session.isUpgrader() || session.getStatus() != RunStatus.RUNNING || session.isPaused()) return false;
        var localPlayer = Minecraft.getInstance().player;
        boolean local = localPlayer != null && player.getUUID().equals(localPlayer.getUUID());
        if (!session.isBattle()) return local && !server.isPublished() && roomId.isEmpty();
        BattleManager battle = BattleManager.getInstance();
        BattleRoom room = battle.getCurrentRoom();
        String name = player.getGameProfile().name();
        return room != null && room.isUpgrader() && room.getStatus() == BattleRoom.RoomStatus.STARTED
            && roomId.equals(session.getBattleRoomId()) && roomId.equals(battle.getCurrentRoomId())
            && room.hasPlayer(name) && !room.isPlayerEliminated(name) && !room.isPlayerDisconnected(name)
            && (server.isPublished() ? battle.isHost() && room.isSharedWorld() : local);
    }

    public static void request(MinecraftServer server, ServerPlayer player, long id, String roomId,
                               Map<Integer, Integer> amounts, Map<Integer, ItemStack> expected, Item target, int targetCount,
                               Consumer<UpgraderClient.RollResult> result, Consumer<Boolean> completed) {
        if (id <= 0 || !authorized(server, player, roomId) || PENDING.containsKey(player.getUUID())
                || player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()
                || target == null || !UpgraderValues.isAllowed(target)
                || targetCount < 1 || targetCount > maxTargetCount(target)) {
            result.accept(rejected("unavailable"));
            return;
        }
        UpgraderClient.RollResult accepted;
        try {
            List<ItemStack> inventory = inventory(player, false);
            UpgraderTransaction.Consumption plan = UpgraderTransaction.consume(inventory, amounts, expected);
            double chance = UpgraderMath.chance(plan.stake(), Math.multiplyExact(UpgraderValues.unitValue(target), (long) targetCount));
            if (chance <= 0) throw new IllegalArgumentException("invalid_target_value");
            RunSession session = RunSessionManager.getInstance().getCurrentSession();
            ItemStack compensation = UpgraderCompensation.choose(plan.stake(), session.getTargetItem(), target, RANDOM);
            double roll = RANDOM.nextDouble();
            double bonus = RANDOM.nextDouble() < UpgraderMath.BONUS_APPEAR_CHANCE ? UpgraderMath.bonusSize(chance) : 0;
            boolean bonusHit = UpgraderMath.inBonus(roll, chance, bonus);
            boolean success = bonusHit || UpgraderMath.wins(roll, chance);
            int streak = 0, flips = 0;
            if (bonusHit) {
                int stackLimit = Math.max(1, target.getDefaultMaxStackSize()) * 16;
                while (streak < UpgraderMath.MAX_STREAK && ((long) targetCount << (streak + 1)) <= stackLimit) {
                    flips++;
                    if (RANDOM.nextBoolean()) streak++;
                    else break;
                }
            }
            ItemStack reward = success ? new ItemStack(target, targetCount) : compensation;
            List<ItemStack> delivery = new ArrayList<>();
            if (success) {
                int total = targetCount << streak, max = Math.max(1, target.getDefaultMaxStackSize());
                while (total > 0) {
                    int part = Math.min(max, total);
                    delivery.add(new ItemStack(target, part));
                    total -= part;
                }
            } else if (!compensation.isEmpty()) delivery.add(compensation);
            Pending pending = new Pending(server, player, id, session,
                new UpgraderPendingReward(delivery, UpgraderMath.totalMillis(flips)), System.nanoTime(), completed);
            for (int slot = 0; slot < UpgraderTransaction.INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, plan.inventory().get(slot));
            }
            PENDING.put(player.getUUID(), pending);
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            accepted = new UpgraderClient.RollResult(true, success, chance, roll,
                success ? "success" : "lost", reward.copy(), bonus, bonusHit, streak, flips);
        } catch (IllegalArgumentException error) {
            result.accept(rejected(error.getMessage()));
            return;
        } catch (Exception error) {
            RandomRunMod.LOGGER.error("[Upgrader] Could not accept stake", error);
            result.accept(rejected("unavailable"));
            return;
        }
        result.accept(accepted);
    }

    public static int maxTargetCount(Item target) {
        return target == null ? 1 : Math.clamp(target.getDefaultMaxStackSize(), 1, 64);
    }

    public static void control(ServerPlayer player, long id, int action) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || pending.player() != player || pending.id() != id) return;
        if (action == 0) pending.reward().start(System.nanoTime());
        else if (action == 1) settle(pending, false, false);
        else if (action == 2) settle(pending, true, false);
    }

    public static void tick(MinecraftServer server) {
        for (Pending pending : List.copyOf(PENDING.values())) {
            if (pending.server() != server) continue;
            boolean abandoned = System.nanoTime() - pending.acceptedNanos() > 30_000_000_000L;
            boolean ended = pending.session() != RunSessionManager.getInstance().getCurrentSession()
                || pending.session().getStatus() != RunStatus.RUNNING;
            settle(pending, abandoned || ended || !pending.player().isAlive(), false);
        }
    }

    public static void closePlayer(ServerPlayer player) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending != null && pending.player() == player) settle(pending, true, true);
    }

    public static void stop(MinecraftServer server) {
        for (Pending pending : List.copyOf(PENDING.values())) {
            if (pending.server() == server) settle(pending, true, true);
        }
    }

    private static void settle(Pending pending, boolean skip, boolean beforeDeathDrop) {
        if (PENDING.get(pending.player().getUUID()) != pending) return;
        if (skip) pending.reward().skip();
        if (!pending.reward().ready(System.nanoTime())) return;
        ServerPlayer player = pending.player();
        boolean dropAtDeath = !player.isAlive() && !beforeDeathDrop;
        var delivery = pending.reward().settle(inventory(player, dropAtDeath), System.nanoTime());
        if (delivery.isEmpty()) return;
        PENDING.remove(player.getUUID());
        if (dropAtDeath) {
            for (ItemStack stack : delivery.get().inventory()) if (!stack.isEmpty()) player.drop(stack, false, Prediction.SERVER_ONLY);
        } else {
            for (int slot = 0; slot < UpgraderTransaction.INVENTORY_SLOTS; slot++) {
                player.getInventory().setItem(slot, delivery.get().inventory().get(slot));
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
        }
        for (ItemStack overflow : delivery.get().overflow()) player.drop(overflow, false, Prediction.SERVER_ONLY);
        try { pending.completion().accept(true); }
        catch (Exception error) {
            RandomRunMod.LOGGER.warn("[Upgrader] Reward delivered, completion notification unavailable", error);
        }
    }

    private static List<ItemStack> inventory(ServerPlayer player, boolean empty) {
        List<ItemStack> result = new ArrayList<>(UpgraderTransaction.INVENTORY_SLOTS);
        for (int slot = 0; slot < UpgraderTransaction.INVENTORY_SLOTS; slot++) {
            result.add(empty ? ItemStack.EMPTY : player.getInventory().getItem(slot));
        }
        return result;
    }

    private static UpgraderClient.RollResult rejected(String reason) {
        return new UpgraderClient.RollResult(false, false, 0, 0, reason, ItemStack.EMPTY);
    }

    public static final class UpgraderPendingReward {
        private final long durationNanos;
        private final List<ItemStack> rewards;
        private boolean started;
        private long startedNanos;
        private boolean skipped;
        private boolean settled;

        public UpgraderPendingReward(ItemStack reward) {
            this(Objects.requireNonNull(reward, "reward").isEmpty() ? List.of() : List.of(reward), UpgraderMath.SPIN_MILLIS);
        }

        public UpgraderPendingReward(List<ItemStack> rewards, long durationMillis) {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack stack : Objects.requireNonNull(rewards, "rewards")) if (!stack.isEmpty()) copies.add(stack.copy());
            this.rewards = List.copyOf(copies);
            this.durationNanos = Math.max(0, durationMillis) * 1_000_000L;
        }

        public synchronized void start(long startedNanos) {
            if (!started && !settled) {
                this.startedNanos = startedNanos;
                started = true;
            }
        }

        public synchronized void skip() {
            skipped = true;
        }

        public synchronized boolean ready(long nowNanos) {
            return !settled && (skipped || started && nowNanos - startedNanos >= durationNanos);
        }

        public synchronized Optional<Delivery> settle(List<ItemStack> currentInventory, long nowNanos) {
            if (!ready(nowNanos)) return Optional.empty();
            if (currentInventory == null || currentInventory.size() != UpgraderTransaction.INVENTORY_SLOTS) {
                throw new IllegalArgumentException("invalid_inventory");
            }
            List<ItemStack> inventory = new ArrayList<>(UpgraderTransaction.INVENTORY_SLOTS);
            for (ItemStack stack : currentInventory) {
                if (stack == null) throw new IllegalArgumentException("invalid_inventory");
                inventory.add(stack.copy());
            }
            List<ItemStack> overflow = new ArrayList<>();
            for (ItemStack reward : rewards) {
                ItemStack remaining = reward.copy();
                for (ItemStack stack : inventory) {
                    if (remaining.isEmpty()) break;
                    if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, remaining)) continue;
                    int moved = Math.min(remaining.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                    stack.grow(moved);
                    remaining.shrink(moved);
                }
                for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
                    if (!inventory.get(slot).isEmpty()) continue;
                    int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                    inventory.set(slot, remaining.copyWithCount(moved));
                    remaining.shrink(moved);
                }
                if (!remaining.isEmpty()) overflow.add(remaining);
            }
            Delivery delivery = new Delivery(List.copyOf(inventory), List.copyOf(overflow));
            settled = true;
            return Optional.of(delivery);
        }

        public record Delivery(List<ItemStack> inventory, List<ItemStack> overflow) {}
    }
}
