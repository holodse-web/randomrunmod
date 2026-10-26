package com.randomrun.features.upgrader;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class UpgraderVerification {
    private static int checks;

    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var registries = net.minecraft.data.registries.VanillaRegistries.createWorldLookup();
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries)
            .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        UpgraderValues.initialize(null);
        checks += UpgraderModeVerification.verify();
        checks += UpgraderKeyBindingVerification.verify();
        checks += com.randomrun.infrastructure.version.VersionCheckVerification.verify();
        checks += com.randomrun.infrastructure.security.AntiCheatTickVerification.verify();
        checks += com.randomrun.features.battle.util.advancement.AdvancementLookupVerification.verify();
        checks += com.randomrun.core.service.StartupCacheVerification.verify();
        checks += UpgraderBattleModeVerification.verify();
        checks += UpgraderNetworkVerification.verify();
        checks += com.randomrun.features.battle.LobbyRoomStateVerification.verify();
        checks += com.randomrun.features.battle.BattleRuntimeVerification.verify();
        checks += com.randomrun.features.battle.manager.RoomPasswordVerification.verify();
        checks += com.randomrun.ui.util.PreviewRenderVerification.verify();
        checks += UpgraderUiVerification.verify();
        checks += UpgraderValueBalanceVerification.verify();
        checks += UpgraderStartupVerification.verify();
        checks += UpgraderSearchVerification.verify();
        checks += UpgraderWheelVerification.verify();
        checks += UpgraderPendingRewardVerification.verify();
        check(UpgraderMath.chance(1, 2) == 0.5, "50 percent is exact");
        check(UpgraderMath.chance(55, 100) == 0.55, "55 percent is exact");
        check(UpgraderMath.chance(75, 100) == 0.75, "75 percent is exact");
        check(UpgraderMath.chance(99, 100) == 0.75, "75 percent ceiling");
        check(UpgraderMath.chance(100, 100) == 0.75, "same-value target accepted at the cap");
        check(UpgraderMath.chance(101, 100) == 0.75, "cheaper target accepted at the cap");
        check(UpgraderMath.chance(Long.MAX_VALUE, 1) == 0.75, "large stake never exceeds the chance cap");
        check(UpgraderMath.chance(0, 100) == 0, "zero stake rejected");
        check(UpgraderMath.chance(-1, 100) == 0, "negative stake rejected");
        check(UpgraderMath.chance(100, 0) == 0, "zero-value target rejected");
        check(UpgraderMath.chance(100, -1) == 0, "negative-value target rejected");
        check(UpgraderMath.wins(0.499999, 0.5), "win just below boundary");
        check(!UpgraderMath.wins(0.5, 0.5), "loss exactly at boundary");
        check(!UpgraderMath.wins(Double.NaN, 0.5), "NaN rejected");
        check(UpgraderMath.wins(Math.nextDown(.75), .75), "roll just below the cap wins");
        check(!UpgraderMath.wins(.75, .75), "roll exactly at the cap loses");
        check(!UpgraderMath.wins(.9, .75), "raw high rolls still lose when stake exceeds the target price");
        check(!UpgraderMath.wins(.1, .95), "obsolete chance above the cap is rejected");
        Random samples = new Random(263);
        for (double probability : new double[] {0.01, 0.35, 0.55, 0.65, 0.75}) {
            int wins = 0;
            for (int i = 0; i < 200_000; i++) if (UpgraderMath.wins(samples.nextDouble(), probability)) wins++;
            check(Math.abs(wins / 200_000.0 - probability) < 0.004, "frequency at " + probability);
            boolean visualAgreement = true;
            int left = 0;
            int right = 0;
            for (int i = 0; i < 10_000; i++) {
                double sample = (i + 0.5) / 10_000.0;
                double turn = UpgraderMath.wheelTurn(sample, probability);
                boolean paintedWin = UpgraderMath.isWinningWheelTurn(turn, probability);
                visualAgreement &= paintedWin == UpgraderMath.wins(sample, probability);
                if (paintedWin) {
                    if (turn < .5) right++;
                    else left++;
                }
            }
            check(visualAgreement, "symmetric wheel agrees with server at " + probability);
            check(left == right, "equal bottom-centered sectors at " + probability);
        }
        check(UpgraderMath.wheelTurn(0.25, 0.5) == 0, "middle of winning interval lands at bottom");
        check(UpgraderMath.isWinningWheelTurn(0.99, 0.5), "left half of bottom arc wins");
        check(UpgraderMath.isWinningWheelTurn(0.01, 0.5), "right half of bottom arc wins");
        check(!UpgraderMath.isWinningWheelTurn(0.5, 0.75), "top gap loses even at the 75 percent cap");
        check(UpgraderMath.SPIN_MILLIS >= 6000, "extended roulette duration");
        check(UpgraderValues.unclassifiedVanillaItems().isEmpty(), "every 26.3 vanilla item classified");
        for (var item : UpgraderValues.targets()) {
            check(UpgraderValues.value(item.getDefaultInstance()) == UpgraderValues.unitValue(item),
                "target default stack can be valued: " + item);
        }
        check(!UpgraderValues.isAllowed(Items.AIR), "air forbidden");
        check(!UpgraderValues.isAllowed(Items.COMMAND_BLOCK), "command blocks forbidden");
        check(!UpgraderValues.isAllowed(Items.ZOMBIE_SPAWN_EGG), "spawn eggs forbidden");
        check(UpgraderValues.unitValue(Items.DIAMOND) > UpgraderValues.unitValue(Items.IRON_INGOT), "diamond above iron");
        check(UpgraderValues.unitValue(Items.IRON_INGOT) > UpgraderValues.unitValue(Items.COBBLESTONE), "iron above stone");
        check(UpgraderValues.unitValue(Items.DIAMOND_BLOCK) == 9 * UpgraderValues.unitValue(Items.DIAMOND), "diamond compression");
        check(UpgraderValues.unitValue(Items.IRON_BLOCK) == 9 * UpgraderValues.unitValue(Items.IRON_INGOT), "iron compression");
        check(UpgraderValues.unitValue(Items.IRON_INGOT) == 9 * UpgraderValues.unitValue(Items.IRON_NUGGET), "iron nuggets");
        check(points("copper_block") == points("exposed_copper"), "copper oxidation");
        check(points("copper_block") == points("weathered_copper"), "copper weathering");
        check(points("copper_block") == points("oxidized_copper"), "copper scraping");
        check(points("cut_copper") == points("oxidized_cut_copper"), "cut copper scraping");
        check(points("white_concrete") <= points("white_concrete_powder"), "concrete hydration");
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        long newValue = UpgraderValues.value(tool);
        tool.setDamageValue(tool.getMaxDamage() / 2);
        check(UpgraderValues.value(tool) > 0 && UpgraderValues.value(tool) < newValue, "damage reduces value");
        ItemStack filledBox = new ItemStack(Items.SHULKER_BOX);
        filledBox.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
        check(UpgraderValues.value(filledBox) == 0, "filled box contents protected");

        List<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.COBBLESTONE, 10));
        inventory.set(9, new ItemStack(Items.IRON_INGOT, 2));
        Map<Integer, Integer> selection = Map.of(0, 2, 9, 1);
        Map<Integer, ItemStack> snapshots = Map.of(0, inventory.get(0).copy(), 9, inventory.get(9).copy());
        var plan = UpgraderTransaction.plan(inventory, selection, snapshots, new ItemStack(Items.DIAMOND));
        check(inventory.get(0).getCount() == 10 && inventory.get(9).getCount() == 2, "planning never mutates inventory");
        check(plan.lossInventory().get(0).getCount() == 8 && plan.lossInventory().get(9).getCount() == 1, "only selected counts removed");
        check(count(plan.winInventory(), Items.DIAMOND) == 1, "one exact reward");
        check(count(plan.lossInventory(), Items.DIAMOND) == 0, "loss awards nothing");
        check(plan.stake() == UpgraderValues.value(new ItemStack(Items.COBBLESTONE, 2))
            + UpgraderValues.value(new ItemStack(Items.IRON_INGOT)), "multi-stack stake summed on server");
        var equalTarget = UpgraderTransaction.plan(inventory, Map.of(9, 1), snapshots,
            new ItemStack(Items.IRON_INGOT));
        check(UpgraderMath.chance(equalTarget.stake(), UpgraderValues.value(new ItemStack(Items.IRON_INGOT))) == .75,
            "server-priced equal-value transaction is allowed at 75 percent");
        check(count(equalTarget.winInventory(), Items.IRON_INGOT) == 2
            && count(equalTarget.lossInventory(), Items.IRON_INGOT) == 1,
            "equal target still consumes the exact stake before awarding one reward");
        var cheaperTarget = UpgraderTransaction.plan(inventory, Map.of(9, 1), snapshots,
            new ItemStack(Items.COBBLESTONE));
        check(UpgraderMath.chance(cheaperTarget.stake(), UpgraderValues.value(new ItemStack(Items.COBBLESTONE))) == .75,
            "server-priced cheaper transaction is allowed at 75 percent");
        check(count(cheaperTarget.winInventory(), Items.COBBLESTONE) == 11
            && count(cheaperTarget.lossInventory(), Items.COBBLESTONE) == 10,
            "cheaper target awards exactly one item on the winning branch");
        check(inventory.get(0).getCount() == 10 && inventory.get(9).getCount() == 2,
            "equal and cheaper transaction planning preserves the source inventory");
        check(UpgraderMath.chance(cheaperTarget.stake(), UpgraderValues.value(ItemStack.EMPTY)) == 0,
            "actual zero-value reward cannot acquire a chance from a positive stake");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(9, 3), snapshots, new ItemStack(Items.DIAMOND)), "overdraw below selection limit");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(0, -1), snapshots, new ItemStack(Items.DIAMOND)), "negative amount");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(36, 1), snapshots, new ItemStack(Items.DIAMOND)), "armor and offhand excluded");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(), snapshots, new ItemStack(Items.DIAMOND)), "empty stake");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(0, 1), Map.of(0, new ItemStack(Items.DIRT)), new ItemStack(Items.DIAMOND)), "stale slot rejected");
        List<ItemStack> full = emptyInventory();
        for (int i = 0; i < 36; i++) full.set(i, new ItemStack(Items.COBBLESTONE, 64));
        rejected(() -> UpgraderTransaction.plan(full, Map.of(0, 1), Map.of(0, full.get(0).copy()), new ItemStack(Items.DIAMOND)), "full inventory before roll");
        var consumption = UpgraderTransaction.consume(full, Map.of(0, 1), Map.of(0, full.get(0).copy()));
        check(consumption.stake() == UpgraderValues.value(new ItemStack(Items.COBBLESTONE)),
            "full inventory accepts a partial stake without reserving reward space");
        check(consumption.inventory().get(0).getCount() == 63
                && consumption.inventory().stream().noneMatch(ItemStack::isEmpty),
            "consumption can leave every inventory slot occupied");
        check(full.stream().allMatch(stack -> stack.getCount() == 64),
            "partial consumption planning leaves the source full inventory unchanged");
        consumption.inventory().get(0).shrink(1);
        check(full.get(0).getCount() == 64 && full.get(1).getCount() == 64,
            "consumed inventory owns its stack copies independently of the source");
        rejected(() -> UpgraderTransaction.consume(full, Map.of(0, 11), Map.of(0, full.get(0).copy())),
            "selection_limit", "consumption enforces the ten-item total selection limit");
        rejected(() -> UpgraderTransaction.consume(full, Map.of(0, 1), Map.of(0, new ItemStack(Items.DIRT))),
            "inventory_changed", "consumption rejects stale snapshots before committing");
        full.set(0, new ItemStack(Items.COBBLESTONE, 3));
        var freesSlot = UpgraderTransaction.plan(full, Map.of(0, 3), Map.of(0, full.get(0).copy()), new ItemStack(Items.DIAMOND));
        check(freesSlot.winInventory().get(0).is(Items.DIAMOND), "consumed stack frees reward slot");
        check(full.get(0).getCount() == 3, "capacity planning leaves the consumed source stack intact");
        verifySelectionLimit();
        verifyCompensation();
        System.out.println("UPGRADER_VERIFIED: " + checks + " checks; " + UpgraderValues.coveredCount()
            + " items classified; " + UpgraderValues.targets().size() + " targets; " + UpgraderValues.excludedCount() + " exclusions");
    }

    private static void verifySelectionLimit() {
        check(UpgraderTransaction.MAX_SELECTED_ITEMS == 10, "ten physical items per attempt");
        check(UpgraderTransaction.MAX_SELECTED_TYPES == 3, "three registry item types per attempt");
        List<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.DIRT, 20));
        inventory.set(9, new ItemStack(Items.IRON_INGOT, 20));
        inventory.set(17, new ItemStack(Items.COBBLESTONE, 20));
        inventory.set(35, new ItemStack(Items.DIAMOND, 20));
        Map<Integer, ItemStack> snapshots = Map.of(0, inventory.get(0).copy(), 9, inventory.get(9).copy(),
            17, inventory.get(17).copy(), 35, inventory.get(35).copy());
        for (int amount = 1; amount <= 10; amount++) {
            var plan = UpgraderTransaction.plan(inventory, Map.of(0, amount), snapshots,
                new ItemStack(Items.EMERALD));
            check(plan.stake() == UpgraderValues.value(new ItemStack(Items.DIRT, amount)),
                "one-slot stake accepts exactly " + amount + " physical items");
            check(plan.lossInventory().get(0).getCount() == 20 - amount,
                "one-slot loss consumes exactly " + amount + " physical items");
            check(count(plan.winInventory(), Items.EMERALD) == 1
                && count(plan.lossInventory(), Items.EMERALD) == 0,
                "one-slot selection preserves separate win and loss branches at " + amount);
        }
        var mixed = UpgraderTransaction.plan(inventory, Map.of(0, 3, 9, 3, 17, 4), snapshots,
            new ItemStack(Items.EMERALD));
        check(mixed.lossInventory().get(0).getCount() == 17 && mixed.lossInventory().get(9).getCount() == 17
                && mixed.lossInventory().get(17).getCount() == 16,
            "three plus three plus four consumes exactly ten physical items of three types");
        check(mixed.lossInventory().get(35).getCount() == 20, "mixed selection preserves unselected slots");
        check(mixed.stake() == UpgraderValues.value(new ItemStack(Items.DIRT, 3))
                + UpgraderValues.value(new ItemStack(Items.IRON_INGOT, 3))
                + UpgraderValues.value(new ItemStack(Items.COBBLESTONE, 4)), "three plus three plus four is priced per item exactly");
        var separate = UpgraderTransaction.plan(inventory, Map.of(0, 1, 9, 1, 35, 1), snapshots,
            new ItemStack(Items.EMERALD));
        check(separate.stake() == UpgraderValues.value(new ItemStack(Items.DIRT))
                + UpgraderValues.value(new ItemStack(Items.IRON_INGOT))
                + UpgraderValues.value(new ItemStack(Items.DIAMOND)),
            "one item from three different slots is accepted and priced exactly");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(0, 11), snapshots, new ItemStack(Items.EMERALD)),
            "selection_limit", "eleven from one stack exceed the physical total");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(0, 6, 9, 5), snapshots, new ItemStack(Items.EMERALD)),
            "selection_limit", "six plus five cannot bypass the physical total");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(0, 1, 9, 1, 17, 1, 35, 1), snapshots,
                new ItemStack(Items.EMERALD)), "selection_types_limit", "four physical items still cannot use four different item types");
        rejected(() -> UpgraderTransaction.plan(inventory, Map.of(0, Integer.MAX_VALUE, 9, Integer.MAX_VALUE, 17, 2),
                snapshots, new ItemStack(Items.EMERALD)), "selection_limit", "huge counts cannot overflow into an allowed sum");
        check(snapshots.entrySet().stream().allMatch(entry -> {
            ItemStack actual = inventory.get(entry.getKey());
            return actual.getCount() == entry.getValue().getCount()
                && ItemStack.isSameItemSameComponents(actual, entry.getValue());
        }), "accepted and rejected limit planning never changes the source inventory");

        long dirtPrice = UpgraderValues.unitValue(Items.DIRT);
        long ironPrice = UpgraderValues.unitValue(Items.IRON_INGOT);
        long stonePrice = UpgraderValues.unitValue(Items.COBBLESTONE);
        for (int dirt = 0; dirt <= 10; dirt++) for (int iron = 0; iron <= 10; iron++) for (int stone = 0; stone <= 10; stone++) {
            int total = dirt + iron + stone;
            if (total == 0 || total > 10) continue;
            Map<Integer, Integer> quantities = new java.util.HashMap<>();
            if (dirt > 0) quantities.put(0, dirt);
            if (iron > 0) quantities.put(9, iron);
            if (stone > 0) quantities.put(17, stone);
            var consumed = UpgraderTransaction.consume(inventory, quantities, snapshots);
            check(consumed.stake() == dirtPrice * dirt + ironPrice * iron + stonePrice * stone,
                "every legal three-type quantity split has its exact summed price");
            check(consumed.inventory().get(0).getCount() == 20 - dirt
                    && consumed.inventory().get(9).getCount() == 20 - iron
                    && consumed.inventory().get(17).getCount() == 20 - stone,
                "every legal quantity split consumes only its selected physical counts");
        }

        List<ItemStack> variants = emptyInventory();
        Map<Integer, Integer> variantAmounts = new java.util.LinkedHashMap<>();
        Map<Integer, ItemStack> variantSnapshots = new java.util.HashMap<>();
        for (int slot = 0; slot < 10; slot++) {
            ItemStack named = new ItemStack(Items.DIRT, 2);
            named.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Different name " + slot));
            variants.set(slot, named);
            variantAmounts.put(slot, 1); variantSnapshots.put(slot, named.copy());
        }
        var sameType = UpgraderTransaction.consume(variants, variantAmounts, variantSnapshots);
        check(sameType.stake() == 10 * points("dirt"), "ten different slots and names of one Item count as one type");
        for (int slot = 0; slot < 10; slot++) {
            check(sameType.inventory().get(slot).getCount() == 1
                    && ItemStack.isSameItemSameComponents(sameType.inventory().get(slot), variantSnapshots.get(slot)),
                "same-type aggregation preserves each slot's distinct components");
            check(variants.get(slot).getCount() == 2, "same-type consumption never mutates the source slot");
        }
        var staleNames = new java.util.HashMap<>(variantSnapshots);
        staleNames.put(7, variants.get(8).copy());
        rejected(() -> UpgraderTransaction.consume(variants, variantAmounts, staleNames), "inventory_changed",
            "counting one registry type never weakens per-slot component matching");
        List<ItemStack> protectedVariants = variants.stream().map(ItemStack::copy)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        net.minecraft.nbt.CompoundTag protectedData = new net.minecraft.nbt.CompoundTag(); protectedData.putInt("protected", 1);
        protectedVariants.get(7).set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(protectedData));
        var protectedSnapshots = new java.util.HashMap<>(variantSnapshots); protectedSnapshots.put(7, protectedVariants.get(7).copy());
        rejected(() -> UpgraderTransaction.consume(protectedVariants, variantAmounts, protectedSnapshots), "forbidden_item",
            "same-type named variants still reject protected custom data");
        check(protectedVariants.stream().allMatch(stack -> stack.isEmpty() || stack.getCount() == 2),
            "failed protected same-type selection leaves every source stack unchanged");
    }

    private static void verifyCompensation() {
        check(UpgraderCompensation.budget(499) == 0, "no consolation below threshold");
        check(UpgraderCompensation.budget(500) == 25, "consolation starts at 500");
        check(UpgraderCompensation.budget(Long.MAX_VALUE) == UpgraderCompensation.MAXIMUM_VALUE, "consolation ceiling");
        Random random = new Random(263);
        check(UpgraderCompensation.choose(499, Items.TORCH, Items.DIAMOND, random).isEmpty(), "small loss has no prize");
        for (long stake : new long[] {500, 501, 1000, 10000, 1_000_000}) {
            boolean safe = true;
            for (int trial = 0; trial < 1000; trial++) {
                var prize = UpgraderCompensation.choose(stake, Items.TORCH, Items.IRON_INGOT, random);
                long value = UpgraderValues.value(prize);
                safe &= !prize.isEmpty() && !prize.is(Items.TORCH) && !prize.is(Items.IRON_INGOT)
                    && value > 0 && value <= UpgraderCompensation.budget(stake) && prize.getCount() <= 4;
            }
            check(safe, "bounded consolation excluding run/upgrade targets at " + stake);
        }
        List<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.DIAMOND_BLOCK, 3));
        var base = UpgraderTransaction.plan(inventory, Map.of(0, 3),
            Map.of(0, inventory.get(0).copy()), new ItemStack(Items.DIAMOND));
        var prize = UpgraderCompensation.choose(base.stake(), Items.TORCH, Items.DIAMOND, random);
        check(!prize.isEmpty(), "test large stake qualifies");
        var compensated = UpgraderTransaction.withCompensation(base, prize);
        check(count(compensated.lossInventory(), prize.getItem()) == prize.getCount(), "loss includes one exact consolation");
        check(count(compensated.winInventory(), prize.getItem()) == 0, "winning branch never also gives consolation");
        check(count(compensated.winInventory(), Items.DIAMOND) == 1, "win reward unchanged");
        check(base.lossInventory().stream().allMatch(ItemStack::isEmpty), "consolation planning preserves base inventory");
        check(inventory.get(0).getCount() == 3, "no mutation before server commits loss");
        rejected(() -> UpgraderTransaction.withCompensation(base, new ItemStack(Items.DIAMOND, 64)), "oversized consolation refused");
        List<ItemStack> full = emptyInventory();
        for (int i = 0; i < 36; i++) full.set(i, new ItemStack(Items.COBBLESTONE, 64));
        long largeStake = base.stake();
        var noPrizeRoom = new UpgraderTransaction.Plan(largeStake, List.copyOf(full), List.copyOf(full));
        rejected(() -> UpgraderTransaction.withCompensation(noPrizeRoom, prize), "prize space reserved before outcome");
        check(full.stream().allMatch(stack -> stack.getCount() == 64), "failed prize reservation leaves inventory intact");
    }

    private static List<ItemStack> emptyInventory() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < 36; i++) result.add(ItemStack.EMPTY);
        return result;
    }

    private static long points(String id) {
        var key = net.minecraft.resources.Identifier.withDefaultNamespace(id);
        check(net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key), "known vanilla item: " + id);
        return UpgraderValues.unitValue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key));
    }

    private static int count(List<ItemStack> stacks, net.minecraft.world.item.Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void rejected(Runnable operation, String message) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }

    private static void rejected(Runnable operation, String code, String message) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { check(code.equals(expected.getMessage()), message); return; }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
