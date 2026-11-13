package com.randomrun.features.voice.control;

import java.util.List;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Crafting by name through the recipe book: the same packets the vanilla
 * recipe button sends, so it works on any server that allows the book.
 * 2x2 recipes open the inventory; 3x3 ones need a crafting table in reach.
 */
final class VoiceCrafting {
    private VoiceCrafting() {}

    record Match(RecipeDisplayEntry entry, Item item, int resultCount, boolean needsTable) {}

    enum Problem { NONE, UNKNOWN_RECIPE, NO_RESOURCES }

    record Lookup(Match match, Problem problem) {}

    static Lookup find(LocalPlayer player, List<Item> candidates) {
        ClientRecipeBook book = player.getRecipeBook();
        ContextMap context = SlotDisplayContext.fromLevel(player.level());
        StackedItemContents contents = new StackedItemContents();
        player.getInventory().fillStackedContents(contents);
        if (player.containerMenu instanceof CraftingMenu || player.containerMenu == player.inventoryMenu) {
            // Items left in an open grid still count.
            for (int slot = 1; slot <= (player.containerMenu instanceof CraftingMenu ? 9 : 4); slot++) {
                contents.accountStack(player.containerMenu.getSlot(slot).getItem());
            }
        }
        Match best = null;
        int bestRank = Integer.MAX_VALUE;
        boolean known = false;
        for (RecipeCollection collection : book.getCollections()) {
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                RecipeDisplay display = entry.display();
                boolean needsTable;
                if (display instanceof ShapedCraftingRecipeDisplay shaped) {
                    needsTable = shaped.width() > 2 || shaped.height() > 2;
                } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
                    needsTable = shapeless.ingredients().size() > 4;
                } else {
                    continue;
                }
                List<ItemStack> results = entry.resultItems(context);
                if (results.isEmpty()) continue;
                ItemStack result = results.get(0);
                int rank = candidates.indexOf(result.getItem());
                if (rank < 0) continue;
                known = true;
                if (!entry.canCraft(contents)) continue;
                // Prefer the better candidate, then recipes that fit the 2x2 grid.
                int score = rank * 2 + (needsTable ? 1 : 0);
                if (score < bestRank) {
                    bestRank = score;
                    best = new Match(entry, result.getItem(), Math.max(1, result.getCount()), needsTable);
                }
            }
        }
        if (best != null) return new Lookup(best, Problem.NONE);
        return new Lookup(null, known ? Problem.NO_RESOURCES : Problem.UNKNOWN_RECIPE);
    }

    static final class CraftTask implements VoiceTasks.Task {
        private enum Phase { RESOLVE, OPEN_TABLE, PLACE, WAIT_RESULT, PAUSE, FINISH }

        private final String key;
        private final String tier;
        private final int count;
        private final boolean all;
        private Phase phase = Phase.RESOLVE;
        private Match match;
        private int crafts;
        private int made;
        private int wait;
        private boolean openedScreen;
        private VoiceTasks.AimTask aim;

        CraftTask(String key, String tier, int count, boolean all) {
            this.key = key;
            this.tier = tier;
            this.count = count;
            this.all = all;
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            LocalPlayer player = mc.player;
            switch (phase) {
                case RESOLVE -> {
                    Lookup lookup = find(player, VoiceWorld.items(key, tier));
                    if (lookup.match() == null) {
                        c.status(lookup.problem() == Problem.NO_RESOURCES ? "no_resources" : "no_recipe",
                            VoiceTasks.BAD, VoiceTasks.itemName(key));
                        return true;
                    }
                    match = lookup.match();
                    if (crafts == 0) crafts = all ? 1 : Math.max(1, (count + match.resultCount() - 1) / match.resultCount());
                    if (match.needsTable()) {
                        if (player.containerMenu instanceof CraftingMenu) {
                            phase = Phase.PLACE;
                        } else {
                            phase = Phase.OPEN_TABLE;
                            wait = 0;
                        }
                    } else {
                        if (player.containerMenu != player.inventoryMenu && !(player.containerMenu instanceof CraftingMenu)) {
                            player.closeContainer();
                        }
                        if (mc.gui.screen() == null) {
                            mc.gui.setScreen(new InventoryScreen(player));
                            openedScreen = true;
                        }
                        phase = Phase.PLACE;
                    }
                    return false;
                }
                case OPEN_TABLE -> {
                    if (player.containerMenu instanceof CraftingMenu) {
                        openedScreen = true;
                        phase = Phase.PLACE;
                        return false;
                    }
                    if (wait == 0) {
                        if (aim == null) {
                            if (VoiceWorld.findBlock(player, "crafting_table", player.blockInteractionRange()) == null) {
                                c.status("need_table", VoiceTasks.WARN);
                                return true;
                            }
                            if (mc.gui.screen() != null) player.closeContainer();
                            aim = new VoiceTasks.AimTask("crafting_table", true);
                        }
                        if (!aim.tick(c, mc)) return false;
                        if (aim.failed()) return true;
                        c.useClicks++;
                        wait = 1;
                        return false;
                    }
                    if (++wait > 40) {
                        c.status("need_table", VoiceTasks.WARN);
                        return true;
                    }
                    return false;
                }
                case PLACE -> {
                    AbstractContainerMenu menu = player.containerMenu;
                    mc.gameMode.handlePlaceRecipe(menu.containerId, match.entry().id(), all);
                    phase = Phase.WAIT_RESULT;
                    wait = 0;
                    return false;
                }
                case WAIT_RESULT -> {
                    AbstractContainerMenu menu = player.containerMenu;
                    ItemStack result = menu.getSlot(0).getItem();
                    if (!result.isEmpty() && result.getItem() == match.item()) {
                        mc.gameMode.handleContainerInput(menu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player);
                        made++;
                        phase = Phase.PAUSE;
                        wait = 0;
                        return false;
                    }
                    if (++wait > 30) {
                        c.status("no_resources", VoiceTasks.BAD, VoiceTasks.itemName(key));
                        phase = Phase.FINISH;
                    }
                    return false;
                }
                case PAUSE -> {
                    if (++wait < 3) return false;
                    if (made < crafts) {
                        Lookup again = find(player, List.of(match.item()));
                        if (again.match() != null) {
                            match = again.match();
                            phase = Phase.PLACE;
                            return false;
                        }
                    }
                    c.status("crafted", VoiceTasks.OK, new ItemStack(match.item()).getHoverName().getString(), made * match.resultCount());
                    phase = Phase.FINISH;
                    return false;
                }
                case FINISH -> {
                    boolean moreCrafting = c.peekNext() instanceof CraftTask;
                    if (!moreCrafting && (openedScreen || mc.gui.screen() != null) && player.containerMenu != null) {
                        player.closeContainer();
                    }
                    return true;
                }
            }
            return true;
        }

        @Override
        public String label() {
            String name = VoiceTasks.itemName(key);
            return VoiceTasks.text(count > 1 ? "craft_count" : "craft", name, count);
        }
    }
}
