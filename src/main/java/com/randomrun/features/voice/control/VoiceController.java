package com.randomrun.features.voice.control;

import com.randomrun.core.run.RunSession;
import com.randomrun.core.run.RunSessionManager;
import com.randomrun.features.voice.VoiceSettings;
import com.randomrun.features.voice.command.VoiceCommand;
import com.randomrun.features.voice.command.VoiceParser;
import com.randomrun.features.voice.engine.VoiceEngine;
import com.randomrun.infrastructure.util.LanguageManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Plays the game from voice commands during a voice run. Owns the virtual
 * "keys" (movement, attack, use) that the input mixins read instead of the
 * keyboard and mouse, the command queue, and the camera smoothing.
 */
public final class VoiceController implements VoiceEngine.Listener {
    private static final VoiceController INSTANCE = new VoiceController();
    private static boolean registered;

    // Virtual input, read by the mixins on the client thread.
    VoiceCommand.Dir moveDir;
    boolean sprint;
    boolean sneak;
    boolean jumpHold;
    /** Keep swimming up while in water ("всплывай"). */
    boolean floatInWater;
    private int dryTicks;
    int jumpPulse;
    boolean attackHeld;
    int attackClicks;
    boolean useHeld;
    int useClicks;
    Float yawTarget;
    Float pitchTarget;

    private final Deque<VoiceTasks.Task> queue = new ArrayDeque<>();
    private VoiceTasks.Task current;
    private final ConcurrentLinkedQueue<String> incoming = new ConcurrentLinkedQueue<>();
    private volatile boolean stopRequested;
    private volatile String livePartial = "";
    private volatile long livePartialAt;
    /** A phrase that sounded cut off, waiting for the rest. */
    private String pendingText = "";
    private long pendingAt;
    /** Part of the current phrase that already ran from a stable partial result. */
    private String consumedPartial = "";
    private List<VoiceCommand> lastBatch = List.of();

    // Last thing we aimed at, so "выше", "левее", "другой" can pick a neighbour.
    String aimKey;
    VoiceWorld.Found aimFound;
    long aimAt;
    final java.util.Set<net.minecraft.core.BlockPos> aimTried = new java.util.HashSet<>();

    private boolean active;
    private RunSession voiceSession;
    private RunSession lastVoiceSession;
    private long voiceEndedAt;

    private String heard = "";
    private long heardAt;
    private String status = "";
    private int statusColor = 0xFFFFFFFF;
    private long statusAt;

    private VoiceController() {}

    public static VoiceController get() {
        return INSTANCE;
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.tick(client));
    }

    // ------------------------------------------------------------------ state

    public boolean isActive() {
        return active;
    }

    /** True for a run that was played with voice control; such runs stay off the online leaderboard. */
    public static boolean isVoiceRun(RunSession session) {
        return session != null && (session == INSTANCE.voiceSession || session == INSTANCE.lastVoiceSession);
    }

    /** Battle in a voice room: voice is the only control, even if the model is missing (then the HUD says so). */
    private static boolean isVoiceBattle(RunSession session) {
        com.randomrun.features.battle.manager.BattleManager battle = com.randomrun.features.battle.manager.BattleManager.getInstance();
        com.randomrun.features.battle.manager.BattleRoom room = battle.getCurrentRoom();
        return battle.isInBattle() && room != null && room.isVoice()
            && java.util.Objects.equals(session.getBattleRoomId(), battle.getCurrentRoomId());
    }

    /** Whether the run that just ended (or is still going) was played by voice. */
    public static boolean recentVoiceRun() {
        return INSTANCE.lastVoiceSession != null
            && (INSTANCE.voiceSession != null || System.currentTimeMillis() - INSTANCE.voiceEndedAt < 60_000);
    }

    /** Whether the next run will be a voice run (used to skip online verification). */
    public static boolean voiceRunPending() {
        VoiceSettings settings = VoiceSettings.get();
        return settings.isVoiceRunEnabled() && settings.isReady();
    }

    public String heard() { return heard; }
    public long heardAt() { return heardAt; }
    public String status() { return status; }
    public int statusColor() { return statusColor; }
    public long statusAt() { return statusAt; }

    public List<String> queueLabels(int max) {
        List<String> labels = new ArrayList<>();
        if (current != null) labels.add(current.label());
        for (VoiceTasks.Task task : queue) {
            if (labels.size() >= max) break;
            labels.add(task.label());
        }
        return labels;
    }

    public int queueSize() {
        return queue.size() + (current == null ? 0 : 1);
    }

    public VoiceCommand.Dir moveDir() { return moveDir; }
    public boolean sprinting() { return sprint; }

    void status(String key, int color, Object... args) {
        status = LanguageManager.translate("randomrun.voice.status." + key, args);
        statusColor = color;
        statusAt = System.currentTimeMillis();
    }

    // ------------------------------------------------------------- listener

    @Override
    public void onPartial(String text) {
        livePartial = text;
        livePartialAt = System.currentTimeMillis();
        if (VoiceParser.containsStop(text, VoiceSettings.get().language().id)) stopRequested = true;
    }

    @Override
    public void onFinal(String text) {
        livePartial = "";
        incoming.add(text);
    }

    void rememberAim(String key, VoiceWorld.Found found) {
        if (!key.equals(aimKey)) aimTried.clear();
        aimKey = key;
        aimFound = found;
        aimAt = System.currentTimeMillis();
        if (found != null) aimTried.add(found.pos());
    }

    private boolean jumping() {
        if (jumpHold || jumpPulse > 0) return true;
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
        return floatInWater && player != null && player.isInWater();
    }

    boolean hasRecentAim() {
        return aimKey != null && aimFound != null && System.currentTimeMillis() - aimAt < 30_000;
    }

    // ------------------------------------------------------------------ tick

    private void tick(Minecraft mc) {
        updateActivation(mc);
        if (!active) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.isPaused()) return;

        if (stopRequested) {
            stopRequested = false;
            stopAll(true);
        }

        String text;
        while ((text = incoming.poll()) != null) handleFinal(text);
        runStablePartial();
        flushPending();

        int steps = 0;
        while (steps++ < 6) {
            if (current == null) {
                current = queue.poll();
                if (current == null) break;
            }
            boolean done;
            try {
                done = current.tick(this, mc);
            } catch (Exception e) {
                com.randomrun.infrastructure.util.CompactLogger.warn("[Voice] Task failed: " + e);
                done = true;
            }
            if (!done) break;
            current = null;
        }

        rotate(player);
        if (jumpPulse > 0) jumpPulse--;
        if (floatInWater) {
            dryTicks = player.isInWater() ? 0 : dryTicks + 1;
            if (dryTicks > 40) floatInWater = false;
        }
    }

    /**
     * Vosk only finalizes a phrase after a pause. If the partial text has not
     * changed for a moment and already means something, run it right away; the
     * final result then only adds whatever came after it.
     */
    private void runStablePartial() {
        String live = livePartial;
        long now = System.currentTimeMillis();
        // Run early only after a real pause and only if the phrase already sounds whole.
        if (live.isEmpty() || live.equals(consumedPartial) || now - livePartialAt < 650) return;
        if (!consumedPartial.isEmpty() && !live.startsWith(consumedPartial)) return;
        String fresh = consumedPartial.isEmpty() ? live : live.substring(consumedPartial.length()).strip();
        if (fresh.isEmpty()) return;
        String lang = VoiceSettings.get().language().id;
        if (!pendingText.isEmpty() || VoiceParser.looksUnfinished(fresh, lang)) return;
        List<VoiceCommand> commands = VoiceParser.parse(fresh, lang);
        if (commands.isEmpty() || !commands.stream().allMatch(VoiceController::quick)) return;
        consumedPartial = live;
        heard = live;
        heardAt = now;
        run(fresh, commands, "early");
    }

    private void handleFinal(String text) {
        String consumed = consumedPartial;
        consumedPartial = "";
        heard = text;
        heardAt = System.currentTimeMillis();
        String rest = text;
        if (!consumed.isEmpty() && text.startsWith(consumed)) rest = text.substring(consumed.length()).strip();
        if (rest.isEmpty()) return;
        String lang = VoiceSettings.get().language().id;
        if (!pendingText.isEmpty()) {
            rest = pendingText + " " + rest;
            pendingText = "";
        }
        if (VoiceParser.looksUnfinished(rest, lang)) {
            // "наведись на" + pause + "дерево": wait for the end of the sentence.
            pendingText = rest;
            pendingAt = System.currentTimeMillis();
            return;
        }
        run(rest, VoiceParser.parse(rest, lang), "heard");
    }

    /** Runs a held phrase if nothing followed it. */
    private void flushPending() {
        if (pendingText.isEmpty() || System.currentTimeMillis() - pendingAt < 1800) return;
        if (!livePartial.isEmpty() && System.currentTimeMillis() - livePartialAt < 1800) return; // still talking
        String text = pendingText;
        pendingText = "";
        run(text, VoiceParser.parse(text, VoiceSettings.get().language().id), "heard");
    }

    /** Commands that are safe to start before the phrase is finalized. */
    private static boolean quick(VoiceCommand command) {
        return switch (command) {
            case VoiceCommand.Stop s -> true;
            case VoiceCommand.Move m -> true;
            case VoiceCommand.Sprint s -> true;
            case VoiceCommand.Jump j -> true;
            case VoiceCommand.JumpOff j -> true;
            case VoiceCommand.Sneak s -> true;
            case VoiceCommand.Turn t -> true;
            case VoiceCommand.Pitch p -> true;
            case VoiceCommand.Refine r -> true;
            case VoiceCommand.Repeat r -> true;
            case VoiceCommand.Swim s -> true;
            case VoiceCommand.Start s -> true;
            default -> false;
        };
    }

    private void run(String text, List<VoiceCommand> commands, String how) {
        com.randomrun.infrastructure.util.CompactLogger.core("[Voice] {} \"{}\" -> {}", how, text, commands);
        if (commands.isEmpty()) return;
        // Show the command over our head for the other players in a shared world.
        com.randomrun.features.voice.VoiceSpeech.broadcast(text);
        List<VoiceCommand> batch = new ArrayList<>();
        boolean first = true;
        for (VoiceCommand command : commands) {
            if (command instanceof VoiceCommand.Stop) {
                // Usually already handled from the partial result.
                stopAll(first);
                first = false;
                continue;
            }
            first = false;
            if (command instanceof VoiceCommand.Repeat repeat) {
                if (lastBatch.isEmpty()) status("nothing_to_repeat", VoiceTasks.WARN);
                for (int i = 0; i < repeat.times(); i++) for (VoiceCommand again : lastBatch) queue.add(VoiceTasks.of(again));
                continue;
            }
            batch.add(command);
            queue.add(VoiceTasks.of(command));
        }
        if (!batch.isEmpty()) lastBatch = List.copyOf(batch);
    }

    void stopAll(boolean announce) {
        pendingText = "";
        if (current != null) current.cancel(this);
        for (VoiceTasks.Task task : queue) task.cancel(this);
        current = null;
        queue.clear();
        moveDir = null;
        sprint = false;
        jumpHold = false;
        jumpPulse = 0;
        floatInWater = false;
        attackHeld = false;
        attackClicks = 0;
        useHeld = false;
        useClicks = 0;
        yawTarget = null;
        pitchTarget = null;
        if (announce) status("stopped", 0xFFFF6B6B);
    }

    /** Next queued task, used by crafting to keep the grid open between crafts. */
    VoiceTasks.Task peekNext() {
        return queue.peek();
    }

    private void updateActivation(Minecraft mc) {
        RunSessionManager sessions = RunSessionManager.getInstance();
        RunSession session = sessions.getCurrentSession();
        VoiceSettings settings = VoiceSettings.get();
        boolean should = mc.player != null && session != null && session.isActive()
            && (active ? session == voiceSession
                : session.isBattle() ? isVoiceBattle(session) : settings.isVoiceRunEnabled() && settings.isReady());
        if (should && !active) {
            active = true;
            voiceSession = session;
            lastVoiceSession = session;
            heard = "";
            lastBatch = List.of();
            aimKey = null;
            aimFound = null;
            status("listening", 0xFF7CF29A);
            VoiceEngine engine = VoiceEngine.get();
            engine.setListener(this);
            engine.start(settings.language(), settings.microphone(), settings.grammar());
        } else if (!should && active) {
            active = false;
            voiceEndedAt = System.currentTimeMillis();
            voiceSession = null;
            stopAll(false);
            VoiceEngine.get().setListener(null);
            VoiceEngine.get().stop();
            KeyMapping.releaseAll();
        }
    }

    private void rotate(LocalPlayer player) {
        if (yawTarget != null) {
            float diff = Mth.wrapDegrees(yawTarget - player.getYRot());
            float step = Mth.clamp(Math.abs(diff) * 0.35f, 2.5f, 28f);
            if (Math.abs(diff) <= step) {
                player.setYRot(player.getYRot() + diff);
                yawTarget = null;
            } else {
                player.setYRot(player.getYRot() + Math.signum(diff) * step);
            }
        }
        if (pitchTarget != null) {
            float target = Mth.clamp(pitchTarget, -90f, 90f);
            float diff = target - player.getXRot();
            float step = Mth.clamp(Math.abs(diff) * 0.35f, 2.5f, 20f);
            if (Math.abs(diff) <= step) {
                player.setXRot(target);
                pitchTarget = null;
            } else {
                player.setXRot(player.getXRot() + Math.signum(diff) * step);
            }
        }
    }

    boolean rotationSettled() {
        return yawTarget == null && pitchTarget == null;
    }

    // --------------------------------------------------------------- mixins

    /** Movement for KeyboardInput while voice controls the player. */
    public Input input() {
        boolean fwd = moveDir == VoiceCommand.Dir.FORWARD;
        boolean back = moveDir == VoiceCommand.Dir.BACK;
        boolean left = moveDir == VoiceCommand.Dir.LEFT;
        boolean right = moveDir == VoiceCommand.Dir.RIGHT;
        return new Input(fwd, back, left, right, jumping(), sneak, sprint && fwd);
    }

    public Vec2 moveVector() {
        float forward = moveDir == VoiceCommand.Dir.FORWARD ? 1f : moveDir == VoiceCommand.Dir.BACK ? -1f : 0f;
        float strafe = moveDir == VoiceCommand.Dir.LEFT ? 1f : moveDir == VoiceCommand.Dir.RIGHT ? -1f : 0f;
        return new Vec2(strafe, forward).normalized();
    }

    /** Replacement for KeyMapping.isDown, or null to leave the key alone. */
    public Boolean keyDown(KeyMapping key) {
        if (!active) return null;
        Options o = Minecraft.getInstance().options;
        if (key == o.keyAttack) return attackHeld;
        if (key == o.keyUse) return useHeld;
        if (key == o.keySprint) return sprint && moveDir == VoiceCommand.Dir.FORWARD;
        if (key == o.keyJump) return jumping();
        if (key == o.keyShift) return sneak;
        if (key == o.keyUp) return moveDir == VoiceCommand.Dir.FORWARD;
        if (key == o.keyDown) return moveDir == VoiceCommand.Dir.BACK;
        if (key == o.keyLeft) return moveDir == VoiceCommand.Dir.LEFT;
        if (key == o.keyRight) return moveDir == VoiceCommand.Dir.RIGHT;
        if (isBlocked(key, o)) return false;
        return null;
    }

    /** Replacement for KeyMapping.consumeClick, or null to leave the key alone. */
    public Boolean consumeClick(KeyMapping key) {
        if (!active) return null;
        Options o = Minecraft.getInstance().options;
        if (key == o.keyAttack) {
            if (attackClicks > 0) { attackClicks--; return true; }
            return false;
        }
        if (key == o.keyUse) {
            if (useClicks > 0) { useClicks--; return true; }
            return false;
        }
        if (isBlocked(key, o) || key == o.keyJump || key == o.keySprint || key == o.keyShift
            || key == o.keyUp || key == o.keyDown || key == o.keyLeft || key == o.keyRight) return false;
        return null;
    }

    private static boolean isBlocked(KeyMapping key, Options o) {
        if (key == o.keyInventory || key == o.keyDrop || key == o.keySwapOffhand || key == o.keyPickItem) return true;
        for (KeyMapping hotbar : o.keyHotbarSlots) if (key == hotbar) return true;
        return false;
    }

    /** Mouse look, scroll and clicks in containers are off: the voice is the only controller. */
    public boolean blocksMouse() {
        return active;
    }
}
