package com.randomrun.features.voice.control;

import com.randomrun.features.voice.command.VoiceCommand;
import com.randomrun.infrastructure.util.LanguageManager;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One task per voice command. {@link Task#tick} returns true when the task is finished. */
final class VoiceTasks {
    static final int OK = 0xFF7CF29A;
    static final int WARN = 0xFFFFCF52;
    static final int BAD = 0xFFFF6B6B;

    interface Task {
        boolean tick(VoiceController c, Minecraft mc);
        default void cancel(VoiceController c) {}
        String label();
    }

    private VoiceTasks() {}

    static Task of(VoiceCommand command) {
        return switch (command) {
            case VoiceCommand.Move m -> new MoveTask(m);
            case VoiceCommand.Sprint s -> instant(text("sprint"), c -> c.sprint = s.on());
            case VoiceCommand.Jump j -> new JumpTask(j.hold(), j.times());
            case VoiceCommand.Swim w -> new SwimTask(w.mode());
            case VoiceCommand.JumpOff j -> instant(text("jump_off"), c -> { c.jumpHold = false; c.jumpPulse = 0; });
            case VoiceCommand.Sneak s -> instant(text(s.on() ? "sneak" : "stand"), c -> c.sneak = s.on());
            case VoiceCommand.Turn t -> new TurnTask(t.degrees());
            case VoiceCommand.Pitch p -> new PitchTask(p.degrees(), p.relative());
            case VoiceCommand.Face f -> new FaceTask(f.cardinal());
            case VoiceCommand.Aim a -> new AimTask(a.target(), false);
            case VoiceCommand.Hit h -> new HitTask(h.target(), h.all());
            case VoiceCommand.Use u -> new UseTask(u.target());
            case VoiceCommand.Place p -> new PlaceTask(p);
            case VoiceCommand.Eat e -> new EatTask();
            case VoiceCommand.Select s -> new SelectTask(s.item(), s.tier(), s.slot());
            case VoiceCommand.Get g -> new GetTask(g.item(), g.tier(), g.count());
            case VoiceCommand.Inventory i -> new InventoryTask(i.open());
            case VoiceCommand.Craft cr -> new VoiceCrafting.CraftTask(cr.item(), cr.tier(), cr.count(), cr.all());
            case VoiceCommand.Drop d -> new DropTask(d.item(), d.all());
            case VoiceCommand.Stop s -> instant(text("stop"), c -> c.stopAll(true));
            case VoiceCommand.Repeat r -> instant(text("repeat"), c -> {});
            case VoiceCommand.Approach a -> new ApproachTask(a.target(), a.little());
            case VoiceCommand.Pickup p -> new PickupTask(p.all());
            case VoiceCommand.Refine r -> new RefineTask(r.dir());
            case VoiceCommand.NewSeed n -> instant(text("seed"), c -> {
                if (com.randomrun.features.speedrun.WorldCreator.SeedChanger.changeSeed()) return;
                long wait = com.randomrun.features.speedrun.WorldCreator.SeedChanger.cooldownLeft();
                if (wait > 0) c.status("seed_wait", WARN, (wait + 999) / 1000);
                else c.status("seed_unavailable", BAD);
            });
            case VoiceCommand.UpgraderOpen u -> new VoiceUpgrader.OpenTask(u.open());
            case VoiceCommand.UpgraderChance u -> new VoiceUpgrader.ActionTask(text("upgrader_chance", Math.round(100 / u.ratio())), (c, screen) -> {
                if (!screen.voicePreset(u.ratio())) c.status("upgrader_stake_first", WARN);
            });
            case VoiceCommand.UpgraderTarget u -> new VoiceUpgrader.ActionTask(text("upgrader_target"), (c, screen) -> {
                List<Item> items = VoiceWorld.resolveItems(u.item(), u.tier(), u.target(), u.words());
                if (!screen.voiceTarget(items, u.count())) c.status("upgrader_no_target", BAD);
            });
            case VoiceCommand.UpgraderRoll u -> new VoiceUpgrader.ActionTask(text("upgrader_roll"), (c, screen) -> {
                String problem = screen.voiceRoll();
                if (problem == null) return;
                switch (problem) {
                    case "stake" -> c.status("upgrader_stake_first", WARN);
                    case "target" -> c.status("upgrader_pick_target", WARN);
                    case "busy" -> c.status("upgrader_busy", WARN);
                    default -> c.status("raw", WARN, problem);
                }
            });
            case VoiceCommand.UpgraderClear u -> new VoiceUpgrader.ActionTask(text("upgrader_clear"), (c, screen) -> screen.voiceClear());
            case VoiceCommand.UpgraderSkip u -> instant(text("upgrader_skip"), c -> com.randomrun.features.upgrader.client.UpgraderClient.skipAnimation());
            case VoiceCommand.Start s -> instant(text("start"), c -> {
                var player = Minecraft.getInstance().player;
                if (com.randomrun.core.run.RunSessionManager.getInstance().isFrozen() && player != null) {
                    com.randomrun.infrastructure.handler.StartRunHandler.tryStartRun(player);
                }
            });
        };
    }

    static String text(String key, Object... args) {
        return LanguageManager.translate("randomrun.voice.task." + key, args);
    }

    static String targetName(String key) {
        return key == null ? "" : LanguageManager.translate("randomrun.voice.target." + key);
    }

    static String itemName(String key) {
        return key == null ? "" : LanguageManager.translate("randomrun.voice.item." + key);
    }

    private static Task instant(String label, java.util.function.Consumer<VoiceController> action) {
        return new Task() {
            @Override
            public boolean tick(VoiceController c, Minecraft mc) {
                action.accept(c);
                return true;
            }

            @Override
            public String label() {
                return label;
            }
        };
    }

    // ------------------------------------------------------------- movement

    static final class MoveTask implements Task {
        private final VoiceCommand.Move move;
        private Vec3 start;
        private Vec3 lastPos;
        private int stuckTicks;

        MoveTask(VoiceCommand.Move move) { this.move = move; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            if (start == null) {
                start = player.position();
                lastPos = start;
                c.moveDir = move.dir();
                c.sprint = move.sprint();
                if (move.blocks() <= 0) return true;
            }
            if (c.moveDir != move.dir()) return true; // replaced by a later command
            Vec3 pos = player.position();
            double dx = pos.x - start.x, dz = pos.z - start.z;
            if (Math.sqrt(dx * dx + dz * dz) >= move.blocks() - 0.15) {
                c.moveDir = null;
                c.sprint = false;
                return true;
            }
            stuckTicks = pos.distanceToSqr(lastPos) < 0.0004 ? stuckTicks + 1 : 0;
            lastPos = pos;
            if (stuckTicks > 30) {
                c.moveDir = null;
                c.status("stuck", WARN);
                return true;
            }
            return false;
        }

        @Override
        public void cancel(VoiceController c) {
            if (start != null) c.moveDir = null;
        }

        @Override
        public String label() {
            String dir = LanguageManager.translate("randomrun.voice.dir." + move.dir().name().toLowerCase(java.util.Locale.ROOT));
            return move.blocks() > 0 ? text("move_blocks", dir, move.blocks()) : text(move.sprint() ? "run" : "move", dir);
        }
    }

    static final class JumpTask implements Task {
        private final boolean hold;
        private final int times;
        private int done;
        private int wait;
        private int ticks;

        JumpTask(boolean hold, int times) {
            this.hold = hold;
            this.times = Math.max(1, times);
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            if (hold) {
                c.jumpHold = true;
                return true;
            }
            if (++ticks > 20 * 15) return true;
            if (wait > 0) {
                wait--;
                return false;
            }
            // In water a tap does nothing: swim up for a moment instead.
            if (player.isInWater()) {
                c.jumpPulse = 10;
                wait = 10;
                return ++done >= times;
            }
            if (!player.onGround() && !player.onClimbable()) return false;
            c.jumpPulse = 3;
            wait = 4;
            return ++done >= times;
        }

        @Override
        public String label() {
            if (hold) return text("jump_hold");
            return times > 1 ? text("jump_times", times) : text("jump");
        }
    }

    /** "всплывай", "ныряй", "вылезь из воды". */
    static final class SwimTask implements Task {
        private final String mode;
        private BlockPos shore;
        private boolean searched;
        private int ticks;
        private int stuck;
        private Vec3 lastPos;

        SwimTask(String mode) { this.mode = mode; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            ticks++;
            switch (mode) {
                case "down" -> {
                    c.floatInWater = false;
                    c.jumpHold = false;
                    if (!player.isInWater()) {
                        c.status("not_in_water", WARN);
                        return true;
                    }
                    c.sneak = true;
                    if (player.onGround() || ticks > 20 * 8) {
                        c.sneak = false;
                        return true;
                    }
                    return false;
                }
                case "out" -> {
                    c.floatInWater = true;
                    c.sneak = false;
                    if (!searched) {
                        searched = true;
                        shore = findShore(player);
                        if (shore == null) {
                            if (!player.isInWater()) return true;
                            c.status("no_shore", WARN);
                            return true; // still floats up
                        }
                    }
                    if (shore == null) return true;
                    Vec3 point = Vec3.atBottomCenterOf(shore).add(0, 1.2, 0);
                    steer(c, player, point, 0.4);
                    c.moveDir = VoiceCommand.Dir.FORWARD;
                    if (player.horizontalCollision) c.jumpPulse = 4;
                    boolean landed = !player.isInWater() && player.onGround();
                    Vec3 pos = player.position();
                    stuck = lastPos != null && pos.distanceToSqr(lastPos) < 0.0004 ? stuck + 1 : 0;
                    lastPos = pos;
                    if (landed || stuck > 60 || ticks > 20 * 25) {
                        c.moveDir = null;
                        c.sprint = false;
                        if (!landed) c.status("stuck", WARN);
                        return true;
                    }
                    return false;
                }
                default -> {
                    c.sneak = false;
                    if (!player.isInWater()) {
                        c.status("not_in_water", WARN);
                        return true;
                    }
                    c.floatInWater = true;
                    // Done once the head is out; floating keeps it there.
                    return !player.isUnderWater() || ticks > 20 * 10;
                }
            }
        }

        /** Nearest block to stand on: solid ground with two free, dry blocks above. */
        private static BlockPos findShore(LocalPlayer player) {
            BlockPos origin = player.blockPosition();
            var level = player.level();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int dx = -14; dx <= 14; dx++) {
                for (int dz = -14; dz <= 14; dz++) {
                    for (int dy = -3; dy <= 4; dy++) {
                        BlockPos feet = origin.offset(dx, dy, dz);
                        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) continue;
                        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
                        if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) continue;
                        BlockPos ground = feet.below();
                        if (!level.getFluidState(ground).isEmpty()) continue;
                        if (!level.getBlockState(ground).isFaceSturdy(level, ground, net.minecraft.core.Direction.UP)) continue;
                        double d = feet.distSqr(origin) + Math.abs(dy) * 2;
                        if (d < bestDistance) {
                            bestDistance = d;
                            best = feet;
                        }
                    }
                }
            }
            return best;
        }

        @Override
        public void cancel(VoiceController c) {
            c.sneak = false;
            c.moveDir = null;
            if (!mode.equals("up")) c.floatInWater = false;
        }

        @Override
        public String label() {
            return text("swim_" + mode);
        }
    }

    static final class TurnTask implements Task {
        private final float degrees;
        private boolean started;

        TurnTask(float degrees) { this.degrees = degrees; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (!started) {
                started = true;
                float base = c.yawTarget != null ? c.yawTarget : mc.player.getYRot();
                c.yawTarget = base + degrees;
                return false;
            }
            return c.yawTarget == null;
        }

        @Override
        public String label() {
            return text("turn", Math.round(degrees));
        }
    }

    static final class PitchTask implements Task {
        private final float degrees;
        private final boolean relative;
        private boolean started;

        PitchTask(float degrees, boolean relative) {
            this.degrees = degrees;
            this.relative = relative;
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (!started) {
                started = true;
                c.pitchTarget = relative ? mc.player.getXRot() + degrees : degrees;
                return false;
            }
            return c.pitchTarget == null;
        }

        @Override
        public String label() {
            return text(degrees < 0 ? "look_up" : degrees > 0 ? "look_down" : "look_level");
        }
    }

    static final class FaceTask implements Task {
        private final VoiceCommand.Cardinal cardinal;
        private boolean started;

        FaceTask(VoiceCommand.Cardinal cardinal) { this.cardinal = cardinal; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (!started) {
                started = true;
                c.yawTarget = switch (cardinal) {
                    case SOUTH -> 0f;
                    case WEST -> 90f;
                    case NORTH -> 180f;
                    case EAST -> -90f;
                };
                return false;
            }
            return c.yawTarget == null;
        }

        @Override
        public String label() {
            return text("face", LanguageManager.translate("randomrun.voice.cardinal." + cardinal.name().toLowerCase(java.util.Locale.ROOT)));
        }
    }

    // ------------------------------------------------------------ targeting

    /** Turns the camera to the nearest matching block or mob and keeps tracking it until settled. */
    static final class AimTask implements Task {
        private final String target;
        private final boolean needReach;
        private VoiceWorld.Found found;
        private boolean failed;
        private int ticks;

        AimTask(String target, boolean needReach) {
            this.target = target;
            this.needReach = needReach;
        }

        /** Aim at an already chosen block or mob (used by "выше", "другой"). */
        AimTask(String target, VoiceWorld.Found preset) {
            this.target = target;
            this.needReach = false;
            this.found = preset;
        }

        boolean failed() { return failed; }
        VoiceWorld.Found found() { return found; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            if (found == null) {
                double reach = VoiceWorld.isEntityTarget(target) ? player.entityInteractionRange() : player.blockInteractionRange();
                found = VoiceWorld.find(player, target, reach);
                if (found == null) found = VoiceWorld.find(player, target, 24);
                if (found == null) {
                    failed = true;
                    c.status("not_found", BAD, targetName(target));
                    return true;
                }
                c.rememberAim(target, found);
                if (found.distance() > reach + 0.5) {
                    c.status("far", WARN, targetName(target), Math.round(found.distance()));
                    if (needReach) {
                        aimAt(c, player, point());
                        failed = true;
                        return true;
                    }
                }
            }
            if (found.isEntity() && !found.entity().isAlive()) return true;
            ticks++;
            float[] want = VoiceWorld.anglesTo(player.getEyePosition(), point());
            boolean onTarget = Math.abs(net.minecraft.util.Mth.wrapDegrees(want[0] - player.getYRot())) < 1.5f
                && Math.abs(want[1] - player.getXRot()) < 1.5f;
            if (onTarget || ticks > 40) return true;
            c.yawTarget = want[0];
            c.pitchTarget = want[1];
            return false;
        }

        private Vec3 point() {
            return found.isEntity() ? found.entity().getBoundingBox().getCenter() : found.point();
        }

        @Override
        public String label() {
            return text("aim", targetName(target));
        }
    }

    static void aimAt(VoiceController c, LocalPlayer player, Vec3 point) {
        float[] angles = VoiceWorld.anglesTo(player.getEyePosition(), point);
        c.yawTarget = angles[0];
        c.pitchTarget = angles[1];
    }

    /**
     * Steers toward a point: turns, walks, hops over one-block steps.
     * Returns true once the eye is within {@code stopAt} of the point.
     */
    static boolean steer(VoiceController c, LocalPlayer player, Vec3 point, double stopAt) {
        Vec3 eye = player.getEyePosition();
        double distance = eye.distanceTo(point);
        float[] angles = VoiceWorld.anglesTo(eye, point);
        c.yawTarget = angles[0];
        c.pitchTarget = net.minecraft.util.Mth.clamp(angles[1], -45f, 60f);
        if (distance <= stopAt) {
            c.moveDir = null;
            c.sprint = false;
            return true;
        }
        float yawDiff = Math.abs(net.minecraft.util.Mth.wrapDegrees(angles[0] - player.getYRot()));
        c.moveDir = yawDiff < 50f ? VoiceCommand.Dir.FORWARD : null;
        c.sprint = distance > 9 && yawDiff < 20f;
        if (player.horizontalCollision && player.onGround() && c.moveDir != null) c.jumpPulse = 3;
        return false;
    }

    /** "подойди", "подойди к дереву", "иди к корове": walk until it is in reach. */
    static final class ApproachTask implements Task {
        private final String target;
        private final boolean little;
        private VoiceWorld.Found goal;
        private Task fallback;
        private Vec3 lastPos;
        private int stuck;
        private int ticks;
        boolean failed;

        ApproachTask(String target, boolean little) {
            this.target = target;
            this.little = little;
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            if (fallback != null) return fallback.tick(c, mc);
            if (goal == null) {
                if (target != null) {
                    goal = VoiceWorld.find(player, target, 32);
                    if (goal == null) {
                        c.status("not_found", BAD, targetName(target));
                        failed = true;
                        return true;
                    }
                    c.rememberAim(target, goal);
                } else if (c.hasRecentAim()) {
                    goal = c.aimFound;
                } else {
                    // Nothing in mind: just a few steps forward.
                    fallback = new MoveTask(new VoiceCommand.Move(VoiceCommand.Dir.FORWARD, false, little ? 1 : 3));
                    return fallback.tick(c, mc);
                }
            }
            if (goal.isEntity() && (!goal.entity().isAlive() || goal.entity().isRemoved())) {
                c.moveDir = null;
                return true;
            }
            Vec3 point = goal.isEntity() ? goal.entity().getBoundingBox().getCenter() : goal.point();
            double reach = (goal.isEntity() ? player.entityInteractionRange() : player.blockInteractionRange()) - 0.9;
            if (little) reach = Math.max(reach, player.getEyePosition().distanceTo(point) - 2.5);
            if (steer(c, player, point, reach)) return true;
            Vec3 pos = player.position();
            stuck = lastPos != null && c.moveDir != null && pos.distanceToSqr(lastPos) < 0.0004 ? stuck + 1 : 0;
            lastPos = pos;
            if (stuck > 40 || ++ticks > 20 * 25) {
                c.moveDir = null;
                c.sprint = false;
                c.status("stuck", WARN);
                return true;
            }
            return false;
        }

        @Override
        public void cancel(VoiceController c) {
            if (fallback != null) fallback.cancel(c);
            c.moveDir = null;
            c.sprint = false;
        }

        @Override
        public String label() {
            String name = target != null ? targetName(target) : "";
            return name.isEmpty() ? text("approach_last") : text("approach", name);
        }
    }

    /** "подбери": walk over the nearest dropped items. */
    static final class PickupTask implements Task {
        private final boolean all;
        private Entity item;
        private int picked;
        private int ticks;
        private int stuck;
        private Vec3 lastPos;

        PickupTask(boolean all) { this.all = all; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            if (item == null || !item.isAlive() || item.isRemoved()) {
                if (item != null) picked++;
                if (item != null && (!all || picked >= 30)) return finish(c);
                item = nearest(player);
                ticks = 0;
                if (item == null) {
                    if (picked == 0) c.status("no_drops", WARN);
                    return finish(c);
                }
            }
            steer(c, player, item.position().add(0, 0.25, 0), 0.6);
            c.moveDir = VoiceCommand.Dir.FORWARD;
            if (player.horizontalCollision && player.onGround()) c.jumpPulse = 3;
            Vec3 pos = player.position();
            stuck = lastPos != null && pos.distanceToSqr(lastPos) < 0.0004 ? stuck + 1 : 0;
            lastPos = pos;
            if (stuck > 40 || ++ticks > 20 * 10) {
                c.status("stuck", WARN);
                return finish(c);
            }
            return false;
        }

        private static Entity nearest(LocalPlayer player) {
            Entity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(14),
                    e -> e instanceof net.minecraft.world.entity.item.ItemEntity && e.isAlive())) {
                double d = e.distanceToSqr(player);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = e;
                }
            }
            return best;
        }

        private boolean finish(VoiceController c) {
            c.moveDir = null;
            c.sprint = false;
            return true;
        }

        @Override
        public void cancel(VoiceController c) {
            finish(c);
        }

        @Override
        public String label() {
            return text(all ? "pickup_all" : "pickup");
        }
    }

    /** Breaks the block or hits the mob under the crosshair, aiming first when a target is named. */
    static final class HitTask implements Task {
        private static final int BLOCK_TIMEOUT = 20 * 15;
        private final String target;
        private final boolean all;
        private AimTask aim;
        private BlockPos mining;
        private BlockState miningState;
        private Vec3 miningPoint;
        private Entity victim;
        private int ticks;
        private int done;
        private boolean aimed;
        private ApproachTask approach;

        HitTask(String target, boolean all) {
            this.target = target;
            this.all = all;
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            if (!aimed && (target != null || (mc.hitResult == null || mc.hitResult.getType() == HitResult.Type.MISS) && c.hasRecentAim())) {
                aimed = true;
                approach = new ApproachTask(target, false);
            }
            if (approach != null) {
                if (!approach.tick(c, mc)) return false;
                boolean lost = approach.failed;
                approach = null;
                if (lost || c.aimFound == null || !c.hasRecentAim()) return true;
                aim = new AimTask(c.aimKey, c.aimFound);
            }
            if (aim != null) {
                if (!aim.tick(c, mc)) return false;
                AimTask finished = aim;
                aim = null;
                if (finished.failed()) return true;
                if (finished.found() != null && finished.found().isEntity()) victim = finished.found().entity();
                else if (finished.found() != null) miningPoint = finished.found().point();
            }

            if (victim != null) return hitEntity(c, mc, player);

            if (mining == null) {
                HitResult hit = mc.hitResult;
                if (hit instanceof EntityHitResult entityHit) {
                    victim = entityHit.getEntity();
                    return hitEntity(c, mc, player);
                }
                if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                    mining = blockHit.getBlockPos();
                    miningState = player.level().getBlockState(mining);
                    if (miningPoint == null) miningPoint = hit.getLocation();
                    ticks = 0;
                } else {
                    c.attackClicks++;
                    return true;
                }
            }

            ticks++;
            c.attackHeld = true;
            BlockState now = player.level().getBlockState(mining);
            if (now.isAir() || now.getBlock() != miningState.getBlock()) {
                c.attackHeld = false;
                done++;
                mining = null;
                miningPoint = null;
                if (all && target != null && done < 64) {
                    approach = new ApproachTask(target, false);
                    return false;
                }
                return true;
            }
            if (!(mc.hitResult instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(mining)) {
                if (miningPoint != null) aimAt(c, player, miningPoint);
            }
            if (ticks > BLOCK_TIMEOUT) {
                c.attackHeld = false;
                c.status("too_hard", WARN);
                return true;
            }
            return false;
        }

        private boolean hitEntity(VoiceController c, Minecraft mc, LocalPlayer player) {
            ticks++;
            if (!victim.isAlive() || victim.isRemoved()) {
                victim = null;
                done++;
                if (all && target != null && done < 32) {
                    approach = new ApproachTask(target, false);
                    return false;
                }
                return true;
            }
            aimAt(c, player, victim.getBoundingBox().getCenter());
            if (player.distanceTo(victim) > player.entityInteractionRange() - 0.3) {
                // Chase it.
                steer(c, player, victim.getBoundingBox().getCenter(), player.entityInteractionRange() - 0.6);
                if (player.distanceTo(victim) > 24) {
                    c.moveDir = null;
                    c.status("far", WARN, targetName(target == null ? "hostile" : target), Math.round(player.distanceTo(victim)));
                    return true;
                }
                return ticks > 20 * 20;
            }
            c.moveDir = null;
            boolean onTarget = mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() == victim;
            if (onTarget && player.getAttackStrengthScale(0f) >= 0.92f && c.attackClicks == 0) c.attackClicks++;
            return ticks > 20 * 20;
        }

        @Override
        public void cancel(VoiceController c) {
            c.attackHeld = false;
            c.moveDir = null;
            if (approach != null) approach.cancel(c);
        }

        @Override
        public String label() {
            return target == null ? text("hit") : text(all ? "hit_all" : "hit_target", targetName(target));
        }
    }

    static final class UseTask implements Task {
        private final String target;
        private ApproachTask approach;
        private AimTask aim;
        private int wait = -1;

        UseTask(String target) { this.target = target; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (target != null && wait < 0) {
                if (approach == null) approach = new ApproachTask(target, false);
                if (!approach.tick(c, mc)) return false;
                if (approach.failed || c.aimFound == null) return true;
                if (aim == null) aim = new AimTask(target, c.aimFound);
                if (!aim.tick(c, mc)) return false;
            }
            if (wait < 0) {
                c.useClicks++;
                wait = 3;
            }
            return --wait <= 0;
        }

        @Override
        public String label() {
            return target == null ? text("use") : text("use_target", targetName(target));
        }
    }

    static final class PlaceTask implements Task {
        private final VoiceCommand.Place place;
        private boolean selected;
        private int ticks;
        private int wait = -1;

        PlaceTask(VoiceCommand.Place place) { this.place = place; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            // With the upgrader open, "поставь" means put items on the stake.
            if (mc.gui.screen() instanceof com.randomrun.features.upgrader.client.UpgraderScreen screen) {
                List<Item> items = VoiceWorld.resolveItems(place.item(), place.tier(), place.target(), place.words());
                int added = screen.voiceStake(items, place.count());
                if (added <= 0) c.status("upgrader_no_items", BAD);
                else c.status("upgrader_staked", OK, added);
                return true;
            }
            if (!selected) {
                selected = true;
                List<Item> items = VoiceWorld.resolveItems(place.item(), place.tier(), place.target(), place.words());
                if ((place.item() != null || place.target() != null || !place.words().isEmpty())
                    && !select(c, mc, items, place.item() != null ? place.item() : place.target())) return true;
            }
            if (wait >= 0) return --wait <= 0;
            ticks++;
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                c.useClicks++;
                wait = 3;
                return false;
            }
            if (ticks == 1) c.pitchTarget = 70f;
            if (ticks > 25) {
                c.status("nowhere_to_place", WARN);
                return true;
            }
            return false;
        }

        @Override
        public String label() {
            String name = place.item() != null ? itemName(place.item()) : place.target() != null ? targetName(place.target()) : place.words();
            return text("place", name);
        }
    }

    /** "выше / ниже / левее / правее / дальше / ближе / другой": another block or mob of the same kind. */
    static final class RefineTask implements Task {
        private final String dir;
        private Task next;

        RefineTask(String dir) { this.dir = dir; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (next != null) return next.tick(c, mc);
            if (!c.hasRecentAim()) {
                next = switch (dir) {
                    case "up" -> new PitchTask(-15f, true);
                    case "down" -> new PitchTask(15f, true);
                    case "left" -> new TurnTask(-20f);
                    case "right" -> new TurnTask(20f);
                    default -> null;
                };
                if (next == null) {
                    c.status("nothing_to_refine", WARN);
                    return true;
                }
                return next.tick(c, mc);
            }
            VoiceWorld.Found pick = pick(c, mc.player);
            if (pick == null) {
                c.status("no_other", WARN, targetName(c.aimKey));
                return true;
            }
            next = new AimTask(c.aimKey, pick);
            c.rememberAim(c.aimKey, pick);
            return next.tick(c, mc);
        }

        private VoiceWorld.Found pick(VoiceController c, LocalPlayer player) {
            VoiceWorld.Found current = c.aimFound;
            Vec3 eye = player.getEyePosition();
            float[] currentAngles = VoiceWorld.anglesTo(eye, current.point());
            VoiceWorld.Found best = null;
            double bestScore = Double.MAX_VALUE;
            for (VoiceWorld.Found f : VoiceWorld.findAll(player, c.aimKey, 20)) {
                if (f.pos().equals(current.pos()) && f.entity() == current.entity()) continue;
                Vec3 p = f.point();
                float yawDiff = net.minecraft.util.Mth.wrapDegrees(VoiceWorld.anglesTo(eye, p)[0] - currentAngles[0]);
                double fromCurrent = p.distanceTo(current.point());
                double score = switch (dir) {
                    case "up" -> p.y > current.point().y + 0.5 ? fromCurrent : Double.MAX_VALUE;
                    case "down" -> p.y < current.point().y - 0.5 ? fromCurrent : Double.MAX_VALUE;
                    case "left" -> yawDiff < -2 ? Math.abs(yawDiff) + fromCurrent * 0.5 : Double.MAX_VALUE;
                    case "right" -> yawDiff > 2 ? Math.abs(yawDiff) + fromCurrent * 0.5 : Double.MAX_VALUE;
                    case "far" -> f.distance() > current.distance() + 0.5 ? f.distance() : Double.MAX_VALUE;
                    case "near" -> f.distance() < current.distance() - 0.5 ? -f.distance() : Double.MAX_VALUE;
                    default -> c.aimTried.contains(f.pos()) ? Double.MAX_VALUE : f.distance();
                };
                if (score < bestScore) {
                    bestScore = score;
                    best = f;
                }
            }
            return best;
        }

        @Override
        public void cancel(VoiceController c) {
            if (next != null) next.cancel(c);
        }

        @Override
        public String label() {
            return text("refine_" + dir);
        }
    }

    static final class EatTask implements Task {
        private int ticks;
        private boolean started;

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            if (ticks++ == 0) {
                int slot = VoiceWorld.findFoodSlot(player);
                if (slot < 0) {
                    c.status("no_food", BAD);
                    return true;
                }
                if (!player.canEat(false)) {
                    c.status("not_hungry", WARN);
                    return true;
                }
                moveToHand(mc, player, slot);
                return false;
            }
            c.useHeld = true;
            if (player.isUsingItem()) started = true;
            if ((started && !player.isUsingItem()) || ticks > 20 * 5) {
                c.useHeld = false;
                return true;
            }
            return false;
        }

        @Override
        public void cancel(VoiceController c) {
            c.useHeld = false;
        }

        @Override
        public String label() {
            return text("eat");
        }
    }

    static final class SelectTask implements Task {
        private final String item;
        private final String tier;
        private final int slot;

        SelectTask(String item, String tier, int slot) {
            this.item = item;
            this.tier = tier;
            this.slot = slot;
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (item == null) {
                mc.player.getInventory().setSelectedSlot(Math.max(0, Math.min(8, slot - 1)));
                return true;
            }
            select(c, mc, VoiceWorld.items(item, tier), item);
            return true;
        }

        @Override
        public String label() {
            return item == null ? text("slot", slot) : text("take", itemName(item));
        }
    }

    /** An item name on its own: hold it if we have one, craft it if we don't. */
    static final class GetTask implements Task {
        private final String item;
        private final String tier;
        private final int count;
        private Task craft;

        GetTask(String item, String tier, int count) {
            this.item = item;
            this.tier = tier;
            this.count = count;
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (craft == null) {
                List<Item> candidates = VoiceWorld.items(item, tier);
                if (VoiceWorld.findSlot(mc.player.getInventory(), candidates) >= 0) {
                    select(c, mc, candidates, item);
                    return true;
                }
                craft = new VoiceCrafting.CraftTask(item, tier, count, false);
            }
            return craft.tick(c, mc);
        }

        @Override
        public void cancel(VoiceController c) {
            if (craft != null) craft.cancel(c);
        }

        @Override
        public String label() {
            return craft != null ? craft.label() : text("take", itemName(item));
        }
    }

    static boolean select(VoiceController c, Minecraft mc, List<Item> candidates, String key) {
        LocalPlayer player = mc.player;
        int slot = VoiceWorld.findSlot(player.getInventory(), candidates);
        if (slot < 0) {
            c.status("no_item", BAD, itemName(key));
            return false;
        }
        moveToHand(mc, player, slot);
        return true;
    }

    /** Puts inventory slot {@code slot} (0..35) into the hand. */
    static void moveToHand(Minecraft mc, LocalPlayer player, int slot) {
        if (slot < 9) {
            player.getInventory().setSelectedSlot(slot);
            return;
        }
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot,
            player.getInventory().getSelectedSlot(), ContainerInput.SWAP, player);
    }

    static final class InventoryTask implements Task {
        private final boolean open;

        InventoryTask(boolean open) { this.open = open; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (open) {
                if (!(mc.gui.screen() instanceof InventoryScreen)) mc.gui.setScreen(new InventoryScreen(mc.player));
            } else if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                mc.player.closeContainer();
            } else if (mc.gui.screen() != null) {
                mc.gui.screen().onClose();
            }
            return true;
        }

        @Override
        public String label() {
            return text(open ? "inventory_open" : "inventory_close");
        }
    }

    static final class DropTask implements Task {
        private final String item;
        private final boolean all;

        DropTask(String item, boolean all) {
            this.item = item;
            this.all = all;
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (item != null && !select(c, mc, VoiceWorld.items(item, null), item)) return true;
            mc.gameMode.dropItem(mc.player, all);
            return true;
        }

        @Override
        public String label() {
            return text("drop", item == null ? "" : itemName(item));
        }
    }
}
