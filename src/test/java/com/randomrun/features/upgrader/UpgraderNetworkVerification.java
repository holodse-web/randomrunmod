package com.randomrun.features.upgrader;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class UpgraderNetworkVerification {
    private static int checks;
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private UpgraderNetworkVerification() {}

    public static int verify() {
        checks = 0;
        var hello = new UpgraderPackets.Hello("12345");
        check(roundTrip(UpgraderPackets.Hello.CODEC, hello).equals(hello), "hello room round trip");
        ItemStack named = new ItemStack(Items.IRON_INGOT, 64);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Ставка / snapshot"));
        var slots = List.of(new UpgraderPackets.Slot(0, 3, new ItemStack(Items.DIRT, 64)),
            new UpgraderPackets.Slot(9, 3, named), new UpgraderPackets.Slot(35, 4, new ItemStack(Items.DIAMOND, 64)));
        var roll = new UpgraderPackets.Roll(263, "12345", "minecraft:emerald", slots);
        var received = roundTrip(UpgraderPackets.Roll.CODEC, roll);
        check(received.id() == roll.id() && received.room().equals(roll.room()) && received.target().equals(roll.target()),
            "roll identity and target round trip");
        check(received.slots().size() == 3, "three distinct selection snapshots fit the codec");
        for (int index = 0; index < 3; index++) {
            var original = slots.get(index);
            var copy = received.slots().get(index);
            check(copy.index() == original.index() && copy.count() == original.count()
                && sameStack(copy.expected(), original.expected()), "slot counts and components survive untrusted decoding");
        }
        acceptSelection(received, "three-plus-three-plus-four decoded items across three types are accepted");
        received.slots().get(1).expected().shrink(1);
        check(named.getCount() == 64, "network snapshots never alias source stacks");

        List<UpgraderPackets.Slot> tenSlots = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            var item = switch (index % 3) { case 0 -> Items.DIRT; case 1 -> Items.IRON_INGOT; default -> Items.DIAMOND; };
            tenSlots.add(new UpgraderPackets.Slot(index, 1, new ItemStack(item, 64)));
        }
        var tenReceived = roundTrip(UpgraderPackets.Roll.CODEC,
            new UpgraderPackets.Roll(264, "12345", "minecraft:emerald", tenSlots));
        check(tenReceived.slots().size() == 10, "ten physical items can span ten inventory slots");
        for (int index = 0; index < tenSlots.size(); index++) {
            var original = tenSlots.get(index);
            var copy = tenReceived.slots().get(index);
            check(copy.index() == original.index() && copy.count() == original.count()
                && sameStack(copy.expected(), original.expected()), "ten-slot snapshot survives native codec round trip");
        }
        acceptSelection(tenReceived, "ten decoded slots containing only three item types are accepted");

        List<UpgraderPackets.Slot> sameTypeSlots = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            ItemStack stack = new ItemStack(Items.DIRT, 64);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Distinct component " + index));
            sameTypeSlots.add(new UpgraderPackets.Slot(index, 1, stack));
        }
        acceptSelection(roundTrip(UpgraderPackets.Roll.CODEC,
            new UpgraderPackets.Roll(265, "12345", "minecraft:emerald", sameTypeSlots)),
            "four slots of the same item type remain legal despite different components");
        for (int action = 0; action <= 2; action++) {
            var control = new UpgraderPackets.Control(263, action);
            check(roundTrip(UpgraderPackets.Control.CODEC, control).equals(control), "start/finish/skip control round trip");
        }
        var reply = new UpgraderPackets.Reply(263, true, false, .75, .9, "lost", named);
        var result = roundTrip(UpgraderPackets.Reply.CODEC, reply);
        check(result.id() == 263 && result.accepted() && !result.success() && result.chance() == .75
            && result.roll() == .9 && result.message().equals("lost") && sameStack(result.reward(), named),
            "authoritative probability, raw sample and reward components round trip");
        var failed = roundTrip(UpgraderPackets.Reply.CODEC,
            new UpgraderPackets.Reply(264, false, false, 0, 0, "selection_limit", ItemStack.EMPTY));
        check(!failed.accepted() && failed.reward().isEmpty() && failed.message().equals("selection_limit"),
            "rejection packet carries no reward");
        for (boolean complete : new boolean[] {false, true}) {
            var settled = new UpgraderPackets.Settled(263, complete);
            check(roundTrip(UpgraderPackets.Settled.CODEC, settled).equals(settled), "settlement status round trip");
        }

        Map<String, Long> baseline = prices();
        List<UpgraderPackets.Price> priceList = baseline.entrySet().stream()
            .map(entry -> new UpgraderPackets.Price(entry.getKey(), entry.getValue())).toList();
        var catalog = roundTrip(UpgraderPackets.Catalog.CODEC, new UpgraderPackets.Catalog("12345", true, priceList));
        check(catalog.enabled() && catalog.room().equals("12345") && catalog.prices().equals(priceList),
            "complete native vanilla price catalog round trip");
        check(priceList.size() == UpgraderValues.coveredCount() && priceList.size() > 1500,
            "wire catalog covers every classified vanilla item including exclusions");
        check(roundTrip(UpgraderPackets.Catalog.CODEC, new UpgraderPackets.Catalog("12345", false, List.of()))
            .prices().isEmpty(), "disabled reply needs no price catalog");

        malformed(UpgraderPackets.Roll.CODEC, buffer -> writeRollHeader(buffer, 0), "zero-slot payload rejected");
        malformed(UpgraderPackets.Roll.CODEC, buffer -> writeRollHeader(buffer, -1), "negative-slot payload rejected");
        malformed(UpgraderPackets.Roll.CODEC, buffer -> writeRollHeader(buffer, 11), "eleven-slot payload rejected before snapshots");
        malformed(UpgraderPackets.Roll.CODEC, buffer -> writeRollHeader(buffer, Integer.MAX_VALUE), "unbounded selection allocation rejected");
        malformed(UpgraderPackets.Control.CODEC, buffer -> { buffer.writeVarLong(263); buffer.writeByte(3); },
            "unknown control action rejected");
        malformed(UpgraderPackets.Control.CODEC, buffer -> { buffer.writeVarLong(263); buffer.writeByte(255); },
            "negative signed control byte cannot bypass action validation");
        for (int size : new int[] {-1, 4097, Integer.MAX_VALUE}) {
            malformed(UpgraderPackets.Catalog.CODEC, buffer -> {
                buffer.writeUtf("12345"); buffer.writeBoolean(true); buffer.writeVarInt(size);
            }, "catalog allocation bound rejects " + size);
        }
        malformed(UpgraderPackets.Hello.CODEC, buffer -> buffer.writeUtf("x".repeat(33)), "overlong room ID rejected");
        for (int invalidCount : new int[] {0, -1, 11, Integer.MAX_VALUE}) {
            rejectSelection(roundTrip(UpgraderPackets.Roll.CODEC, new UpgraderPackets.Roll(263, "12345", "minecraft:emerald",
                List.of(new UpgraderPackets.Slot(0, invalidCount, new ItemStack(Items.DIRT, 64))))),
                invalidCount > 10 ? "selection_limit" : "invalid_selection",
                "decoded invalid count rejected authoritatively: " + invalidCount);
        }
        rejectSelection(roundTrip(UpgraderPackets.Roll.CODEC, new UpgraderPackets.Roll(263, "12345", "minecraft:emerald",
            List.of(new UpgraderPackets.Slot(0, 6, new ItemStack(Items.DIRT, 64)),
                new UpgraderPackets.Slot(9, 5, new ItemStack(Items.IRON_INGOT, 64))))),
            "selection_limit",
            "two individually valid slot counts cannot exceed ten physical items");
        var fourTypes = roundTrip(UpgraderPackets.Roll.CODEC,
            new UpgraderPackets.Roll(266, "12345", "minecraft:emerald", List.of(
                new UpgraderPackets.Slot(0, 1, new ItemStack(Items.DIRT, 64)),
                new UpgraderPackets.Slot(9, 1, new ItemStack(Items.IRON_INGOT, 64)),
                new UpgraderPackets.Slot(18, 1, new ItemStack(Items.DIAMOND, 64)),
                new UpgraderPackets.Slot(27, 1, new ItemStack(Items.COAL, 64)))));
        check(fourTypes.slots().size() == 4, "codec permits four slots for authoritative type validation");
        rejectSelection(fourTypes, "selection_types_limit", "four decoded item types are rejected even with only four physical items");
        verifyRemoteCatalog(baseline);
        return checks;
    }

    private static void verifyRemoteCatalog(Map<String, Long> baseline) {
        String dirt = "minecraft:dirt";
        long original = baseline.get(dirt);
        check(original > 0, "catalog fixture has a positive dirt price");
        try {
            Map<String, Long> reduced = new HashMap<>(baseline);
            reduced.put(dirt, original - 1);
            UpgraderValues.installRemoteCatalog(reduced);
            check(UpgraderValues.unitValue(Items.DIRT) == original - 1, "host recipe-normalized lower price installs");
            Map<String, Long> accepted = prices();
            Map<String, Long> missing = new HashMap<>(baseline); missing.remove(dirt);
            rejected(() -> UpgraderValues.installRemoteCatalog(missing), "incomplete host catalog rejected");
            check(prices().equals(accepted), "missing-price rejection preserves the previous snapshot atomically");
            Map<String, Long> negative = new HashMap<>(baseline); negative.put(dirt, -1L);
            rejected(() -> UpgraderValues.installRemoteCatalog(negative), "negative host price rejected");
            check(prices().equals(accepted), "negative-price rejection preserves the previous snapshot atomically");
            Map<String, Long> higher = new HashMap<>(baseline); higher.put(dirt, original + 1);
            rejected(() -> UpgraderValues.installRemoteCatalog(higher), "price above survival base rejected");
            check(prices().equals(accepted), "above-base rejection preserves the previous snapshot atomically");
            Map<String, Long> extra = new HashMap<>(baseline); extra.put("missing:unknown", 0L);
            rejected(() -> UpgraderValues.installRemoteCatalog(extra), "unknown extra catalog entry rejected");
            check(prices().equals(accepted), "extra-entry rejection preserves the previous snapshot atomically");
            rejected(() -> UpgraderValues.installRemoteCatalog(null), "null catalog rejected");
            check(prices().equals(accepted), "null rejection preserves the previous snapshot atomically");
            UpgraderValues.installRemoteCatalog(baseline);
            check(prices().equals(baseline), "a complete base catalog restores all prices");
        } finally {
            UpgraderValues.initialize(null);
        }
        check(prices().equals(baseline), "disconnect-style reset restores the base snapshot after remote prices");
    }

    private static Map<String, Long> prices() {
        Map<String, Long> result = new LinkedHashMap<>();
        BuiltInRegistries.ITEM.stream().filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))
            .forEach(item -> result.put(BuiltInRegistries.ITEM.getKey(item).toString(), UpgraderValues.unitValue(item)));
        return result;
    }

    private static void rejectSelection(UpgraderPackets.Roll roll, String expectedReason, String description) {
        SelectionFixture fixture = selectionFixture(roll);
        try {
            UpgraderTransaction.consume(fixture.inventory(), fixture.amounts(), fixture.snapshots());
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {
            check(expectedReason == null || expectedReason.equals(expected.getMessage()), description);
        }
        assertUnchanged(fixture, "invalid decoded stake leaves every source slot unchanged");
    }

    private static void acceptSelection(UpgraderPackets.Roll roll, String description) {
        SelectionFixture fixture = selectionFixture(roll);
        long expectedStake = 0;
        for (var slot : roll.slots()) expectedStake += UpgraderValues.value(slot.expected().copyWithCount(slot.count()));
        UpgraderTransaction.Consumption result = UpgraderTransaction.consume(fixture.inventory(), fixture.amounts(), fixture.snapshots());
        check(result.stake() == expectedStake && result.stake() > 0, description);
        for (var slot : roll.slots()) {
            check(result.inventory().get(slot.index()).getCount() == slot.expected().getCount() - slot.count(),
                "decoded selection consumes exactly its requested physical count");
        }
        assertUnchanged(fixture, "valid decoded stake plans without modifying source inventory");
    }

    private record SelectionFixture(List<ItemStack> inventory, List<ItemStack> before,
                                    Map<Integer, Integer> amounts, Map<Integer, ItemStack> snapshots) {}

    private static SelectionFixture selectionFixture(UpgraderPackets.Roll roll) {
        List<ItemStack> inventory = new ArrayList<>();
        for (int index = 0; index < UpgraderTransaction.INVENTORY_SLOTS; index++) inventory.add(ItemStack.EMPTY);
        Map<Integer, Integer> amounts = new HashMap<>();
        Map<Integer, ItemStack> snapshots = new HashMap<>();
        for (var slot : roll.slots()) {
            inventory.set(slot.index(), slot.expected().copy());
            amounts.put(slot.index(), slot.count()); snapshots.put(slot.index(), slot.expected());
        }
        return new SelectionFixture(inventory, inventory.stream().map(ItemStack::copy).toList(), amounts, snapshots);
    }

    private static void assertUnchanged(SelectionFixture fixture, String description) {
        boolean unchanged = true;
        for (int index = 0; index < fixture.inventory().size(); index++) {
            unchanged &= sameStack(fixture.inventory().get(index), fixture.before().get(index));
        }
        check(unchanged, description);
    }

    private static void writeRollHeader(RegistryFriendlyByteBuf buffer, int slots) {
        buffer.writeVarLong(263); buffer.writeUtf("12345"); buffer.writeUtf("minecraft:emerald"); buffer.writeVarInt(slots);
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        try {
            codec.encode(buffer, value);
            T result = codec.decode(buffer);
            check(buffer.readableBytes() == 0, "codec consumes exactly its packet bytes");
            return result;
        } finally { buffer.release(); }
    }

    private static <T> void malformed(StreamCodec<RegistryFriendlyByteBuf, T> codec,
                                      Consumer<RegistryFriendlyByteBuf> writer, String description) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        try {
            writer.accept(buffer);
            try { codec.decode(buffer); }
            catch (RuntimeException expected) { check(true, description); return; }
            throw new AssertionError(description);
        } finally { buffer.release(); }
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        return left.getCount() == right.getCount() && ItemStack.isSameItemSameComponents(left, right);
    }

    private static void rejected(Runnable operation, String description) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { check(true, description); return; }
        throw new AssertionError(description);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
}
