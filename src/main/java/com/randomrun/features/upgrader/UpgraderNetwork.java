package com.randomrun.features.upgrader;

import com.randomrun.core.session.RunSession;
import com.randomrun.core.session.RunSessionManager;
import com.randomrun.features.upgrader.client.UpgraderClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UpgraderNetwork {
    private static String enabledRoom;
    private static long nextHello;
    private static final Map<UUID, RemoteRequest> REQUESTS = new HashMap<>();
    private static final Map<UUID, Long> HELLOS = new HashMap<>();

    private static final class RemoteRequest {
        final long id;
        UpgraderPackets.Reply reply;
        boolean settled;
        RemoteRequest(long id) { this.id = id; }
    }

    private UpgraderNetwork() {}

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(UpgraderPackets.Hello.TYPE, UpgraderPackets.Hello.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpgraderPackets.Roll.TYPE, UpgraderPackets.Roll.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UpgraderPackets.Control.TYPE, UpgraderPackets.Control.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpgraderPackets.Catalog.TYPE, UpgraderPackets.Catalog.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpgraderPackets.Reply.TYPE, UpgraderPackets.Reply.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UpgraderPackets.Settled.TYPE, UpgraderPackets.Settled.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UpgraderPackets.Hello.TYPE, (packet, context) -> {
            UUID id = context.player().getUUID();
            long now = System.nanoTime();
            Long last = HELLOS.get(id);
            if (last != null && now - last < 1_000_000_000L) return;
            HELLOS.put(id, now);
            boolean allowed = UpgraderServer.authorized(context.server(), context.player(), packet.room());
            List<UpgraderPackets.Price> prices = allowed ? BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))
                .map(item -> new UpgraderPackets.Price(BuiltInRegistries.ITEM.getKey(item).toString(), UpgraderValues.unitValue(item)))
                .toList() : List.of();
            ServerPlayNetworking.send(context.player(), new UpgraderPackets.Catalog(packet.room(), allowed, prices));
        });
        ServerPlayNetworking.registerGlobalReceiver(UpgraderPackets.Roll.TYPE, (packet, context) -> {
            ServerPlayer player = context.player();
            RemoteRequest previous = REQUESTS.get(player.getUUID());
            if (previous != null && packet.id() <= previous.id) {
                if (packet.id() == previous.id && previous.reply != null) {
                    ServerPlayNetworking.send(player, previous.reply);
                    if (previous.settled) ServerPlayNetworking.send(player, new UpgraderPackets.Settled(packet.id(), true));
                }
                return;
            }
            if (previous != null && previous.reply != null && previous.reply.accepted() && !previous.settled) {
                ServerPlayNetworking.send(player, new UpgraderPackets.Reply(packet.id(), false, false, 0, 0, "unavailable", ItemStack.EMPTY));
                return;
            }
            RemoteRequest request = new RemoteRequest(packet.id());
            REQUESTS.put(player.getUUID(), request);
            Map<Integer, Integer> amounts = new HashMap<>();
            Map<Integer, ItemStack> expected = new HashMap<>();
            for (UpgraderPackets.Slot slot : packet.slots()) {
                if (amounts.put(slot.index(), slot.count()) != null) {
                    request.reply = new UpgraderPackets.Reply(packet.id(), false, false, 0, 0, "invalid_selection", ItemStack.EMPTY);
                    ServerPlayNetworking.send(player, request.reply);
                    return;
                }
                expected.put(slot.index(), slot.expected());
            }
            Identifier target = Identifier.tryParse(packet.target());
            UpgraderServer.request(context.server(), player, packet.id(), packet.room(), amounts, expected,
                target == null ? null : BuiltInRegistries.ITEM.getValue(target), result -> {
                    request.reply = new UpgraderPackets.Reply(packet.id(), result.accepted(), result.success(),
                        result.chance(), result.roll(), result.message(), result.reward());
                    ServerPlayNetworking.send(player, request.reply);
                }, completed -> {
                    request.settled = completed;
                    if (ServerPlayNetworking.canSend(player, UpgraderPackets.Settled.TYPE))
                        ServerPlayNetworking.send(player, new UpgraderPackets.Settled(packet.id(), completed));
                });
        });
        ServerPlayNetworking.registerGlobalReceiver(UpgraderPackets.Control.TYPE,
            (packet, context) -> UpgraderServer.control(context.player(), packet.id(), packet.action()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UpgraderServer.closePlayer(handler.player);
            REQUESTS.remove(handler.player.getUUID());
            HELLOS.remove(handler.player.getUUID());
        });
        ClientPlayNetworking.registerGlobalReceiver(UpgraderPackets.Catalog.TYPE, (packet, context) -> {
            RunSession session = RunSessionManager.getInstance().getCurrentSession();
            if (session == null || !packet.room().equals(session.getBattleRoomId())) return;
            enabledRoom = null;
            if (!packet.enabled()) return;
            Map<String, Long> prices = new HashMap<>();
            for (UpgraderPackets.Price price : packet.prices()) {
                if (prices.put(price.item(), price.value()) != null) return;
            }
            try {
                UpgraderValues.installRemoteCatalog(prices);
                enabledRoom = packet.room();
            } catch (IllegalArgumentException ignored) {
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(UpgraderPackets.Reply.TYPE, (packet, context) ->
            UpgraderClient.receiveResult(packet.id(), new UpgraderClient.RollResult(packet.accepted(), packet.success(),
                packet.chance(), packet.roll(), packet.message(), packet.reward())));
        ClientPlayNetworking.registerGlobalReceiver(UpgraderPackets.Settled.TYPE,
            (packet, context) -> UpgraderClient.receiveSettled(packet.id(), packet.completed()));
    }

    public static void tickClient() {
        Minecraft client = Minecraft.getInstance();
        RunSession session = RunSessionManager.getInstance().getCurrentSession();
        if (client.player == null || client.getSingleplayerServer() != null || session == null
                || !session.isBattle() || !session.isUpgrader() || session.getBattleRoomId() == null
                || session.getBattleRoomId().equals(enabledRoom) || System.nanoTime() < nextHello
                || !ClientPlayNetworking.canSend(UpgraderPackets.Hello.TYPE)) return;
        nextHello = System.nanoTime() + 2_000_000_000L;
        ClientPlayNetworking.send(new UpgraderPackets.Hello(session.getBattleRoomId()));
    }

    public static boolean available(String roomId) {
        return roomId != null && roomId.equals(enabledRoom)
            && ClientPlayNetworking.canSend(UpgraderPackets.Roll.TYPE) && ClientPlayNetworking.canSend(UpgraderPackets.Control.TYPE);
    }

    public static void send(UpgraderPackets.Roll packet) { ClientPlayNetworking.send(packet); }
    public static void control(long id, int action) {
        if (ClientPlayNetworking.canSend(UpgraderPackets.Control.TYPE)) ClientPlayNetworking.send(new UpgraderPackets.Control(id, action));
    }

    public static void resetClient() {
        if (enabledRoom != null) UpgraderValues.initialize(null);
        enabledRoom = null;
        nextHello = 0;
    }
}
