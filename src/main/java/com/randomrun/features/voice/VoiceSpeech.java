package com.randomrun.features.voice;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shares what a voice player said with everyone around them, so in a shared
 * battle world each opponent gets a speech bubble over their head.
 * Client sends {@link Say}; the hosting server relays {@link Said} to nearby players.
 */
public final class VoiceSpeech {
    public static final int MAX_LENGTH = 72;
    private static final double RANGE = 64.0;
    private static final long MIN_GAP_MS = 250;

    private VoiceSpeech() {}

    public record Say(String text) implements CustomPacketPayload {
        public static final Type<Say> TYPE = new Type<>(Identifier.fromNamespaceAndPath("randomrun", "voice_say_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Say> CODEC = StreamCodec.of(
            (b, p) -> b.writeUtf(p.text, MAX_LENGTH * 4), b -> new Say(b.readUtf(MAX_LENGTH * 4)));
        @Override public Type<Say> type() { return TYPE; }
    }

    public record Said(UUID player, String text) implements CustomPacketPayload {
        public static final Type<Said> TYPE = new Type<>(Identifier.fromNamespaceAndPath("randomrun", "voice_said_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Said> CODEC = StreamCodec.of(
            (b, p) -> { UUIDUtil.STREAM_CODEC.encode(b, p.player); b.writeUtf(p.text, MAX_LENGTH * 4); },
            b -> new Said(UUIDUtil.STREAM_CODEC.decode(b), b.readUtf(MAX_LENGTH * 4)));
        @Override public Type<Said> type() { return TYPE; }
    }

    /** One line over a player's head. */
    public record Line(String text, long at) {}

    private static final Map<UUID, Long> LAST_SAY = new HashMap<>();
    private static final Map<UUID, Deque<Line>> LINES = new ConcurrentHashMap<>();

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Say.TYPE, Say.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Said.TYPE, Said.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Say.TYPE, (packet, context) -> relay(context.player(), packet.text()));
        ClientPlayNetworking.registerGlobalReceiver(Said.TYPE, (packet, context) -> show(packet.player(), packet.text()));
    }

    private static void relay(ServerPlayer sender, String raw) {
        String text = clean(raw);
        if (text.isEmpty()) return;
        long now = System.currentTimeMillis();
        Long last = LAST_SAY.get(sender.getUUID());
        if (last != null && now - last < MIN_GAP_MS) return;
        LAST_SAY.put(sender.getUUID(), now);
        Said said = new Said(sender.getUUID(), text);
        for (ServerPlayer other : sender.level().players()) {
            if (other == sender || other.distanceToSqr(sender) > RANGE * RANGE) continue;
            if (ServerPlayNetworking.canSend(other, Said.TYPE)) ServerPlayNetworking.send(other, said);
        }
    }

    /** Sends a recognized phrase to the server; does nothing where the server does not run this mod. */
    public static void broadcast(String text) {
        String clean = clean(text);
        Minecraft mc = Minecraft.getInstance();
        if (clean.isEmpty() || mc.getConnection() == null) return;
        try {
            if (ClientPlayNetworking.canSend(Say.TYPE)) ClientPlayNetworking.send(new Say(clean));
        } catch (Exception ignored) {
            // Not connected to a server with this channel.
        }
    }

    public static void show(UUID player, String text) {
        String clean = clean(text);
        if (clean.isEmpty()) return;
        Deque<Line> lines = LINES.computeIfAbsent(player, id -> new ArrayDeque<>());
        synchronized (lines) {
            lines.addLast(new Line(clean, System.currentTimeMillis()));
            while (lines.size() > 2) lines.removeFirst();
        }
    }

    public static Map<UUID, Deque<Line>> lines() {
        return LINES;
    }

    public static void clear() {
        LINES.clear();
    }

    private static String clean(String text) {
        if (text == null) return "";
        String out = text.replace('§', ' ').replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        if (out.length() > MAX_LENGTH) out = out.substring(0, MAX_LENGTH - 1).strip() + "…";
        return out;
    }
}
