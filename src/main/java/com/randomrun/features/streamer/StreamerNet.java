package com.randomrun.features.streamer;

import com.randomrun.infrastructure.util.CompactLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;











public final class StreamerNet {
    private static final long PHRASE_GAP_MS = 2000;
    private static final double HEARING_RANGE = 10.0;

    private StreamerNet() {}

    public record Profile(String data) implements CustomPacketPayload {
        public static final Type<Profile> TYPE = new Type<>(Identifier.fromNamespaceAndPath("randomrun", "streamer_profile_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Profile> CODEC = StreamCodec.of(
            (b, p) -> b.writeUtf(p.data, 256), b -> new Profile(b.readUtf(256)));
        @Override public Type<Profile> type() { return TYPE; }
    }

    public record ProfileOf(UUID player, String data) implements CustomPacketPayload {
        public static final Type<ProfileOf> TYPE = new Type<>(Identifier.fromNamespaceAndPath("randomrun", "streamer_profile_of_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ProfileOf> CODEC = StreamCodec.of(
            (b, p) -> { UUIDUtil.STREAM_CODEC.encode(b, p.player); b.writeUtf(p.data, 256); },
            b -> new ProfileOf(UUIDUtil.STREAM_CODEC.decode(b), b.readUtf(256)));
        @Override public Type<ProfileOf> type() { return TYPE; }
    }

    public record Phrase(String text) implements CustomPacketPayload {
        public static final Type<Phrase> TYPE = new Type<>(Identifier.fromNamespaceAndPath("randomrun", "streamer_phrase_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Phrase> CODEC = StreamCodec.of(
            (b, p) -> b.writeUtf(p.text, 400), b -> new Phrase(b.readUtf(400)));
        @Override public Type<Phrase> type() { return TYPE; }
    }

    public record PhraseOf(UUID player, String text) implements CustomPacketPayload {
        public static final Type<PhraseOf> TYPE = new Type<>(Identifier.fromNamespaceAndPath("randomrun", "streamer_phrase_of_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PhraseOf> CODEC = StreamCodec.of(
            (b, p) -> { UUIDUtil.STREAM_CODEC.encode(b, p.player); b.writeUtf(p.text, 400); },
            b -> new PhraseOf(UUIDUtil.STREAM_CODEC.decode(b), b.readUtf(400)));
        @Override public Type<PhraseOf> type() { return TYPE; }
    }




    private static final Map<UUID, String> SERVER_PROFILES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PHRASE = new HashMap<>();

    private static final Map<UUID, StreamerProfile> CLIENT_PROFILES = new ConcurrentHashMap<>();

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Profile.TYPE, Profile.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Phrase.TYPE, Phrase.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ProfileOf.TYPE, ProfileOf.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PhraseOf.TYPE, PhraseOf.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(Profile.TYPE, (packet, context) -> onProfile(context.player(), packet.data()));
        ServerPlayNetworking.registerGlobalReceiver(Phrase.TYPE, (packet, context) -> onPhrase(context.player(), packet.text()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer joined = handler.getPlayer();
            SERVER_PROFILES.forEach((id, data) -> {
                if (!id.equals(joined.getUUID()) && ServerPlayNetworking.canSend(joined, ProfileOf.TYPE)) {
                    ServerPlayNetworking.send(joined, new ProfileOf(id, data));
                }
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            if (SERVER_PROFILES.remove(id) != null) {
                for (ServerPlayer other : server.getPlayerList().getPlayers()) {
                    if (!other.getUUID().equals(id) && ServerPlayNetworking.canSend(other, ProfileOf.TYPE)) {
                        ServerPlayNetworking.send(other, new ProfileOf(id, ""));
                    }
                }
            }
            LAST_PHRASE.remove(id);
        });

        ClientPlayNetworking.registerGlobalReceiver(ProfileOf.TYPE, (packet, context) -> {
            StreamerProfile profile = packet.data().isEmpty() ? null : StreamerProfile.decode(packet.data());
            if (profile == null) CLIENT_PROFILES.remove(packet.player());
            else CLIENT_PROFILES.put(packet.player(), profile);
        });
        ClientPlayNetworking.registerGlobalReceiver(PhraseOf.TYPE, (packet, context) -> {
            Minecraft mc = Minecraft.getInstance();
            Entity speaker = mc.level == null ? null : mc.level.getPlayerByUUID(packet.player());
            if (speaker == mc.player || !canHear(speaker, mc.player)) return;
            StreamerProfile profile = CLIENT_PROFILES.get(packet.player());
            String name = profile != null ? profile.character().nickname() : nameOf(packet.player());
            String[] parts = splitPhrase(packet.text());
            String text = StreamerCharacter.textForSound(parts[0]);
            if (text == null) return;
            showInChat(name, speaker.getName().getString(), text);
            StreamerVoice.playFrom(parts[0], packet.player().toString(), speaker);
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(StreamerNet::sendOwnProfile));
        com.randomrun.network.RealtimeClient.getInstance().addSayListener(message -> Minecraft.getInstance().execute(() -> onRoomPhrase(message)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CLIENT_PROFILES.clear());
    }



    private static void onProfile(ServerPlayer player, String data) {
        StreamerProfile profile = data.isEmpty() ? null : StreamerProfile.decode(data);
        String stored = profile == null ? "" : profile.encode();
        if (profile == null) SERVER_PROFILES.remove(player.getUUID());
        else SERVER_PROFILES.put(player.getUUID(), stored);
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player && ServerPlayNetworking.canSend(other, ProfileOf.TYPE)) {
                ServerPlayNetworking.send(other, new ProfileOf(player.getUUID(), stored));
            }
        }
    }

    private static void onPhrase(ServerPlayer player, String raw) {
        String[] parts = splitPhrase(raw);
        String known = StreamerCharacter.textForSound(parts[0]);
        if (known == null || !SERVER_PROFILES.containsKey(player.getUUID())) return;
        String text = parts[0] + "|" + known;
        long now = System.currentTimeMillis();
        Long last = LAST_PHRASE.get(player.getUUID());
        if (last != null && now - last < PHRASE_GAP_MS) return;
        LAST_PHRASE.put(player.getUUID(), now);
        PhraseOf out = new PhraseOf(player.getUUID(), text);
        for (ServerPlayer other : player.level().getServer().getPlayerList().getPlayers()) {
            if (other != player && canHear(player, other) && ServerPlayNetworking.canSend(other, PhraseOf.TYPE)) {
                ServerPlayNetworking.send(other, out);
            }
        }
    }

    static boolean canHear(Entity speaker, Entity listener) {
        return speaker != null && listener != null && !speaker.isRemoved() && !listener.isRemoved()
            && speaker.level() == listener.level() && speaker.distanceToSqr(listener) <= HEARING_RANGE * HEARING_RANGE;
    }




    public static void sendOwnProfile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        StreamerProfile own = StreamerProfile.get();
        String data = own.isEnabled() ? own.encode() : "";
        try {
            if (ClientPlayNetworking.canSend(Profile.TYPE)) ClientPlayNetworking.send(new Profile(data));
        } catch (Exception e) {
            CompactLogger.warn("[Streamer] Profile not sent: " + e.getMessage());
        }
    }


    public static StreamerProfile profileOf(UUID player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().equals(player)) {
            StreamerProfile own = StreamerProfile.get();
            return own.isEnabled() ? own : null;
        }
        StreamerProfile profile = CLIENT_PROFILES.get(player);
        return profile != null ? profile : roomProfileOf(player);
    }


    private static StreamerProfile roomProfileOf(UUID player) {
        Minecraft mc = Minecraft.getInstance();
        var room = com.randomrun.features.battle.manager.BattleManager.getInstance().getCurrentRoom();
        if (room == null || mc.level == null) return null;
        var entity = mc.level.getPlayerByUUID(player);
        if (entity == null) return null;
        String streamer = room.streamerOf(entity.getName().getString());
        return streamer == null ? null : StreamerProfile.decode("3;" + streamer);
    }


    public static String roomStreamer() {
        StreamerProfile own = StreamerProfile.get();
        return own.isEnabled() ? own.character().name() : null;
    }


    static void syncRoom() {
        var manager = com.randomrun.features.battle.manager.BattleManager.getInstance();
        var room = manager.getCurrentRoom();
        String code = manager.getCurrentRoomId();
        String name = com.randomrun.network.BackendSession.playerName();
        if (room == null || code == null || name == null || name.isEmpty()) return;
        String streamer = roomStreamer();
        room.setStreamer(name, streamer);
        String side = room.getHostData().keySet().stream().anyMatch(name::equalsIgnoreCase) ? "h" : "g";
        java.util.Map<String, Object> update = new java.util.HashMap<>();
        update.put(side + "/" + name + "/sc", streamer == null ? "" : streamer);
        com.randomrun.network.HttpClient.getInstance().patch("/rooms/" + code, update)
            .exceptionally(error -> { CompactLogger.warn("[Streamer] Room not updated: " + error.getMessage()); return false; });
    }

    public static void sayPhrase(StreamerCharacter.Phrase phrase) {
        Minecraft mc = Minecraft.getInstance();
        StreamerProfile own = StreamerProfile.get();
        showInChat(own.character().nickname(), null, phrase.text());
        if (mc.player != null) StreamerVoice.play(phrase.sound(), mc.player);
        String sound = phrase.sound() == null ? "" : phrase.sound();
        if (throughRoom()) {
            com.randomrun.network.RealtimeClient.getInstance().sendSay(sound, phrase.text());
            return;
        }
        try {
            if (mc.getConnection() != null && ClientPlayNetworking.canSend(Phrase.TYPE)) ClientPlayNetworking.send(new Phrase(sound + "|" + phrase.text()));
        } catch (Exception ignored) {

        }
    }





    private static boolean throughRoom() {
        var manager = com.randomrun.features.battle.manager.BattleManager.getInstance();
        var room = manager.getCurrentRoom();
        if (room == null || manager.getCurrentRoomId() == null || !manager.isInBattle()) return false;
        if (!room.isSharedWorld()) return true;
        return Minecraft.getInstance().getConnection() == null || !ClientPlayNetworking.canSend(Phrase.TYPE);
    }


    private static void onRoomPhrase(com.google.gson.JsonObject message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var manager = com.randomrun.features.battle.manager.BattleManager.getInstance();
        var room = manager.getCurrentRoom();
        if (room == null || !manager.isInBattle()) return;
        String from = message.has("from") ? message.get("from").getAsString() : "";
        if (from.isEmpty() || from.equalsIgnoreCase(mc.player.getName().getString())) return;
        StreamerCharacter character = message.has("streamer") ? StreamerCharacter.byName(message.get("streamer").getAsString()) : null;
        String id = message.has("id") ? message.get("id").getAsString() : "";
        String[] parts = splitPhrase(id + "|");
        String text = StreamerCharacter.textForSound(parts[0]);
        if (text == null) return;
        Entity speaker = null;
        for (var player : mc.level.players()) {
            if (player.getName().getString().equalsIgnoreCase(from)) speaker = player;
        }
        if (room.isSharedWorld() && !canHear(speaker, mc.player)) return;
        showInChat(character != null ? character.nickname() : from, from, text);
        if (!room.isSharedWorld()) return;
        StreamerVoice.playFrom(parts[0], speaker.getUUID().toString(), speaker);
    }


    private static String[] splitPhrase(String raw) {
        String data = raw == null ? "" : raw;
        int bar = data.indexOf('|');
        String sound = bar < 0 ? "" : data.substring(0, bar);
        String text = bar < 0 ? data : data.substring(bar + 1);
        if (!sound.matches("[a-z0-9_.]{0,48}")) sound = "";
        text = text.replace('§', ' ').replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").strip();
        if (text.length() > 160) text = text.substring(0, 160);
        return new String[]{sound, text};
    }


    private static void showInChat(String name, String player, String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null) return;
        String who = player == null || player.isEmpty() || player.equalsIgnoreCase(name) ? name : name + " · " + player;
        Component line = Component.literal("[" + who + "] ").withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal(text).withStyle(ChatFormatting.WHITE));
        mc.gui.chatListener().handleSystemMessage(line, false);
    }

    private static String nameOf(UUID player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            var entity = mc.level.getPlayerByUUID(player);
            if (entity != null) return entity.getName().getString();
        }
        return "?";
    }
}
