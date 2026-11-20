package com.randomrun.features.upgrader;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class UpgraderPackets {
    private UpgraderPackets() {}
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("randomrun", "upgrader_" + path));
    }

    public record Slot(int index, int count, ItemStack expected) {}
    public record Price(String item, long value) {}

    public record Hello(String room) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = UpgraderPackets.type("hello_v1");
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = StreamCodec.of(
            (b, p) -> b.writeUtf(p.room, 32), b -> new Hello(b.readUtf(32)));
        @Override public Type<Hello> type() { return TYPE; }
    }

    public record Catalog(String room, boolean enabled, List<Price> prices) implements CustomPacketPayload {
        public static final Type<Catalog> TYPE = UpgraderPackets.type("catalog_v1");
        public static final StreamCodec<RegistryFriendlyByteBuf, Catalog> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.room, 32); b.writeBoolean(p.enabled); b.writeVarInt(p.prices.size());
            for (Price price : p.prices) { b.writeUtf(price.item, 128); b.writeVarLong(price.value); }
        }, b -> {
            String room = b.readUtf(32); boolean enabled = b.readBoolean(); int count = b.readVarInt();
            if (count < 0 || count > 4096) throw new DecoderException("Invalid upgrader catalog size");
            List<Price> prices = new ArrayList<>(count);
            for (int i = 0; i < count; i++) prices.add(new Price(b.readUtf(128), b.readVarLong()));
            return new Catalog(room, enabled, List.copyOf(prices));
        });
        @Override public Type<Catalog> type() { return TYPE; }
    }

    public record Roll(long id, String room, String target, int targetCount, boolean fast, List<Slot> slots) implements CustomPacketPayload {
        public static final Type<Roll> TYPE = UpgraderPackets.type("roll_v3");
        public static final StreamCodec<RegistryFriendlyByteBuf, Roll> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarLong(p.id); b.writeUtf(p.room, 32); b.writeUtf(p.target, 128); b.writeVarInt(p.targetCount); b.writeBoolean(p.fast); b.writeVarInt(p.slots.size());
            for (Slot slot : p.slots) {
                b.writeVarInt(slot.index); b.writeVarInt(slot.count);
                ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.encode(b, slot.expected);
            }
        }, b -> {
            long id = b.readVarLong(); String room = b.readUtf(32); String target = b.readUtf(128); int targetCount = b.readVarInt(); boolean fast = b.readBoolean(); int count = b.readVarInt();
            if (targetCount < 1 || targetCount > 64) throw new DecoderException("Invalid upgrader target count");
            if (count < 1 || count > UpgraderTransaction.MAX_SELECTED_ITEMS) throw new DecoderException("Invalid upgrader selection size");
            List<Slot> slots = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                slots.add(new Slot(b.readVarInt(), b.readVarInt(), ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC.decode(b)));
            }
            return new Roll(id, room, target, targetCount, fast, List.copyOf(slots));
        });
        @Override public Type<Roll> type() { return TYPE; }
    }

    public record Control(long id, int action) implements CustomPacketPayload {
        public static final Type<Control> TYPE = UpgraderPackets.type("control_v1");
        public static final StreamCodec<RegistryFriendlyByteBuf, Control> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarLong(p.id); b.writeByte(p.action);
        }, b -> {
            long id = b.readVarLong(); int action = b.readUnsignedByte();
            if (action > 2) throw new DecoderException("Invalid upgrader control");
            return new Control(id, action);
        });
        @Override public Type<Control> type() { return TYPE; }
    }

    public record Reply(long id, boolean accepted, boolean success, double chance, double roll,
                        String message, ItemStack reward, double bonus, boolean bonusHit, int streak, int flips,
                        List<ItemStack> ladder) implements CustomPacketPayload {
        public Reply(long id, boolean accepted, boolean success, double chance, double roll, String message, ItemStack reward) {
            this(id, accepted, success, chance, roll, message, reward, 0, false, 0, 0, List.of());
        }
        public static final Type<Reply> TYPE = UpgraderPackets.type("reply_v3");
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarLong(p.id); b.writeBoolean(p.accepted); b.writeBoolean(p.success);
            b.writeDouble(p.chance); b.writeDouble(p.roll); b.writeUtf(p.message, 96);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(b, p.reward);
            b.writeDouble(p.bonus); b.writeBoolean(p.bonusHit); b.writeVarInt(p.streak); b.writeVarInt(p.flips);
            b.writeVarInt(p.ladder.size());
            for (ItemStack stack : p.ladder) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(b, stack.copyWithCount(1));
                b.writeVarInt(stack.getCount());
            }
        }, b -> {
            long id = b.readVarLong(); boolean accepted = b.readBoolean(), success = b.readBoolean();
            double chance = b.readDouble(), roll = b.readDouble(); String message = b.readUtf(96);
            ItemStack reward = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
            double bonus = b.readDouble(); boolean bonusHit = b.readBoolean(); int streak = b.readVarInt(), flips = b.readVarInt();
            int size = b.readVarInt();
            if (!Double.isFinite(bonus) || bonus < 0 || bonus > .25
                    || streak < 0 || streak > UpgraderMath.MAX_STREAK
                    || flips < 0 || flips > UpgraderMath.MAX_STREAK + 1
                    || size < 0 || size > UpgraderMath.MAX_STREAK + 1) throw new DecoderException("Invalid upgrader reply");
            List<ItemStack> ladder = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(b);
                int count = b.readVarInt();
                if (count < 1 || count > 64 * 16) throw new DecoderException("Invalid upgrader reply");
                ladder.add(stack.copyWithCount(count));
            }
            if (bonusHit && success && ladder.size() <= streak) throw new DecoderException("Invalid upgrader reply");
            return new Reply(id, accepted, success, chance, roll, message, reward, bonus, bonusHit, streak, flips, List.copyOf(ladder));
        });
        @Override public Type<Reply> type() { return TYPE; }
    }

    public record Settled(long id, boolean completed) implements CustomPacketPayload {
        public static final Type<Settled> TYPE = UpgraderPackets.type("settled_v1");
        public static final StreamCodec<RegistryFriendlyByteBuf, Settled> CODEC = StreamCodec.of((b, p) -> {
            b.writeVarLong(p.id); b.writeBoolean(p.completed);
        }, b -> new Settled(b.readVarLong(), b.readBoolean()));
        @Override public Type<Settled> type() { return TYPE; }
    }
}
