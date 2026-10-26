package com.randomrun.features.upgrader.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.randomrun.features.upgrader.UpgraderMath;
import com.randomrun.features.upgrader.UpgraderTransaction;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.util.ItemPreviewRenderer;
import com.randomrun.ui.util.GuiQuadBatch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class UpgraderScreen extends Screen {
    private static final int GOLD = UpgraderPalette.GOLD;
    private static final int TEXT = UpgraderPalette.TEXT;
    private static final int MUTED = UpgraderPalette.MUTED;
    private static final int PANEL = UpgraderPalette.PANEL;
    private static final int EDGE = UpgraderPalette.EDGE;
    private static final int GREEN = UpgraderPalette.GREEN;
    private static final int RED = UpgraderPalette.RED;
    private static final double TAU = Math.PI * 2;

    private final Map<Integer, Integer> selected = new LinkedHashMap<>();
    private final Map<Integer, UpgraderClient.Offer> offers = new HashMap<>();
    private final Map<Item, ItemStack> previews = new HashMap<>();
    private final List<PixelButton> selectionButtons = new ArrayList<>();
    private final List<SlotButton> inventoryButtons = new ArrayList<>();
    private final List<SlotButton> targetButtons = new ArrayList<>();
    private List<Item> allTargets = List.of();
    private List<Item> filteredTargets = List.of();
    private final ItemStack[] inventory = new ItemStack[36];
    private EditBox search;
    private PixelButton upgradeButton;
    private Item target;
    private CompletableFuture<UpgraderClient.RollResult> pending;
    private CompletableFuture<Boolean> settlement;
    private UpgraderClient.RollResult result;
    private boolean rolling;
    private boolean revealed;
    private long rollStarted;
    private long opened;
    private long lastTickSound;
    private long stake;
    private long rollingStake;
    private double rollingChance;
    private double visualRoll;
    private double needleStartTurn;
    private double restingNeedleTurn;
    private double displayedChance;
    private double chanceFrom;
    private double chanceTarget;
    private long chanceChanged;
    private int ticks;
    private int targetRow;
    private UpgraderScreenLayout layout;
    private int x, top, panelWidth, sideWidth, centerWidth, heroHeight;
    private int lowerY, lowerHeight, leftWidth, rightX, rightWidth, slotSize;
    private int inventoryX, gridY, targetX, targetColumns, targetRows;
    private String searchText = "";
    private String message = "";
    private int messageColor = MUTED;
    private UpgraderRingRaster chanceRaster;
    private UpgraderPixelMesh chanceMesh;
    private UpgraderWheelGeometry cachedWheel;
    private UpgraderPixelMesh discMesh, outlineMesh, accentMesh, tickMesh, needleMesh;
    private List<ItemStack> stakePreviews = List.of();
    private int selectedCount;
    private final Set<Item> selectedTypes = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean skipSignalled;
    private long celebrationStarted;
    private boolean celebrationTwinkle;
    private final float[] sparkX = new float[48];
    private final float[] sparkY = new float[48];
    private final float[] sparkSpeed = new float[48];

    public UpgraderScreen() {
        super(Component.literal("RRM Upgrader"));
        needleStartTurn = restingNeedleTurn = UpgraderClient.restingNeedleTurn();
    }

    @Override
    protected void init() {
        opened = System.nanoTime();
        UpgraderClient.onScreenOpened();
        layout = UpgraderScreenLayout.fit(width, height);
        panelWidth = layout.contentWidth();
        x = layout.contentX();
        top = layout.contentTop();
        slotSize = layout.slotSize();
        lowerHeight = layout.lowerHeight();
        lowerY = layout.lowerY();
        heroHeight = layout.heroHeight();
        sideWidth = layout.sideWidth();
        centerWidth = layout.centerWidth();
        leftWidth = layout.leftWidth();
        rightX = x + leftWidth + 8;
        rightWidth = layout.rightWidth();
        inventoryX = layout.inventoryX();
        gridY = layout.gridY();
        targetX = layout.targetX();
        targetColumns = layout.targetColumns();
        targetRows = 4;

        selectionButtons.clear();
        inventoryButtons.clear();
        targetButtons.clear();
        if (!rolling) refreshInventory();
        allTargets = UpgraderClient.targets().stream()
            .sorted(Comparator.comparingLong(UpgraderClient::unitValue)
                .thenComparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
            .toList();
        filterTargets();

        addRenderableWidget(new PixelButton(layout.windowX() + layout.windowWidth() - 21,
            layout.windowY() + 5, 16, 16,
            Component.literal("x"), button -> onClose(), false));
        PixelButton clear = new PixelButton(x, layout.actionY(), sideWidth, layout.actionHeight(),
            Component.literal(tr("Сбросить", "Clear stake")), button -> {
                selected.clear();
                selectionChanged();
            }, false);
        clear.setTooltip(Tooltip.create(Component.literal(tr("Убрать выбранные предметы из ставки", "Clear the selected stake"))));
        selectionButtons.add(addRenderableWidget(clear));
        upgradeButton = addRenderableWidget(new PixelButton(x + sideWidth + 8, layout.actionY(),
            centerWidth, layout.actionHeight(), Component.literal(tr("УЛУЧШИТЬ", "UPGRADE")), button -> beginRoll(), true));
        upgradeButton.setTooltip(Tooltip.create(Component.literal(tr(
            "Шанс до 75%.\nНеудача — потеря ставки.",
            "Chance up to 75%.\nA loss consumes the stake."))));

        String[] presets = {"x2", "x4", "x10", "30%", "50%", "75%"};
        double[] ratios = {2, 4, 10, 1 / .30, 1 / .50, 1 / .75};
        for (int i = 0; i < presets.length; i++) {
            final double ratio = ratios[i];
            PixelButton preset = new PixelButton(layout.presetX(i % 3),
                layout.actionY() + (i / 3) * 14, layout.presetWidth(i % 3), 12,
                Component.literal(presets[i]), button -> choosePreset(ratio), false);
            preset.setTooltip(Tooltip.create(Component.literal(tr("Ближайшая доступная цель: ", "Nearest available target: ") + presets[i])));
            selectionButtons.add(addRenderableWidget(preset));
        }

        search = new EditBox(font, rightX + 10, lowerY + 7, rightWidth - 50, 9,
            Component.literal(tr("Поиск предмета", "Search items")));
        search.setBordered(false);
        search.setTextColor(TEXT);
        search.setTextColorUneditable(MUTED);
        search.setMaxLength(80);
        search.setHint(Component.literal(tr("Имя или ценность…", "Name or value…"))
            .withStyle(style -> style.withColor(MUTED & 0xFFFFFF)));
        search.setTooltip(Tooltip.create(Component.literal(tr(
            "Название или ID предмета. Число (например, 580) покажет ближайшие по ценности цели.",
            "Search an item name or ID. A number, such as 580, shows the nearest target values."))));
        search.setValue(searchText);
        search.setResponder(value -> {
            searchText = value;
            targetRow = 0;
            filterTargets();
        });
        addRenderableWidget(search);
        addRenderableWidget(new PixelButton(rightX + rightWidth - 32, lowerY + 4, 12, 15,
            Component.literal("<"), button -> scrollTargets(-targetRows), false));
        addRenderableWidget(new PixelButton(rightX + rightWidth - 18, lowerY + 4, 12, 15,
            Component.literal(">"), button -> scrollTargets(targetRows), false));

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row == 3 ? col : 9 + row * 9 + col;
                SlotButton button = new SlotButton(inventoryX + col * slotSize,
                    gridY + row * slotSize + (row == 3 ? 2 : 0), slot, -1);
                inventoryButtons.add(addRenderableWidget(button));
            }
        }
        for (int i = 0; i < targetColumns * targetRows; i++) {
            SlotButton button = new SlotButton(targetX + i % targetColumns * slotSize,
                gridY + i / targetColumns * slotSize + (i / targetColumns == 3 ? 2 : 0), -1, i);
            targetButtons.add(addRenderableWidget(button));
        }
        updateControls();
    }

    private static String tr(String russian, String english) {
        return LanguageManager.getCurrentLanguage().startsWith("ru") ? russian : english;
    }

    private boolean animations() {
        return RandomRunMod.getInstance().getConfig().isAnimationsEnabled();
    }

    private static String number(long value) {
        return String.format(Locale.ROOT, "%,d", value).replace(',', ' ');
    }

    private static String percent(double chance) {
        if (chance > 0 && chance < .0001) return String.format(Locale.ROOT, "%.3g%%", chance * 100);
        return String.format(Locale.ROOT, "%.2f%%", chance * 100);
    }

    private ItemStack preview(Item item) {
        return previews.computeIfAbsent(item, ItemStack::new);
    }

    private void refreshInventory() {
        offers.clear();
        for (UpgraderClient.Offer offer : UpgraderClient.offers()) offers.put(offer.slot(), offer);
        for (int i = 0; i < inventory.length; i++) {
            inventory[i] = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getInventory().getItem(i).copy();
        }
        selected.entrySet().removeIf(entry -> !offers.containsKey(entry.getKey()));
        int remaining = UpgraderTransaction.MAX_SELECTED_ITEMS;
        Set<Item> types = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var iterator = selected.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            ItemStack stack = offers.get(entry.getKey()).stack();
            boolean typeAvailable = UpgraderSelection.canAddType(types.contains(stack.getItem()), types.size());
            int count = typeAvailable ? Math.clamp(entry.getValue(), 0, Math.min(remaining, stack.getCount())) : 0;
            if (count == 0) iterator.remove();
            else {
                entry.setValue(count);
                remaining -= count;
                types.add(stack.getItem());
            }
        }
        recalculateStake();
    }

    private void recalculateStake() {
        stake = 0;
        selectedCount = 0;
        selectedTypes.clear();
        List<ItemStack> snapshots = new ArrayList<>();
        Map<Item, ItemStack> grouped = new IdentityHashMap<>();
        for (var entry : selected.entrySet()) {
            UpgraderClient.Offer offer = offers.get(entry.getKey());
            if (offer != null && entry.getValue() > 0) {
                ItemStack snapshot = offer.stack().copyWithCount(entry.getValue());
                stake = Math.addExact(stake, UpgraderClient.value(snapshot));
                selectedCount += entry.getValue();
                selectedTypes.add(snapshot.getItem());
                ItemStack existing = grouped.get(snapshot.getItem());
                if (existing == null) {
                    grouped.put(snapshot.getItem(), snapshot);
                    snapshots.add(snapshot);
                } else existing.grow(snapshot.getCount());
            }
        }
        stakePreviews = List.copyOf(snapshots);
    }

    private void filterTargets() {
        filteredTargets = UpgraderSearch.apply(allTargets, searchText, stake, UpgraderClient::unitValue,
            item -> preview(item).getHoverName().getString(), item -> BuiltInRegistries.ITEM.getKey(item).toString());
        targetRow = Math.clamp(targetRow, 0, maxTargetRow());
    }

    private int maxTargetRow() {
        return Math.max(0, (filteredTargets.size() + Math.max(1, targetColumns) - 1)
            / Math.max(1, targetColumns) - targetRows);
    }

    private Item visibleTarget(int index) {
        int offset = targetRow * targetColumns + index;
        return offset < filteredTargets.size() ? filteredTargets.get(offset) : null;
    }

    private double chance() {
        return rolling ? rollingChance : target == null ? 0 : UpgraderMath.chance(stake, UpgraderClient.unitValue(target));
    }

    private void selectionChanged() {
        recalculateStake();
        filterTargets();
        revealed = false;
        message = "";
        updateControls();
    }

    private void choosePreset(double ratio) {
        if (rolling || stake <= 0 || UpgraderClient.isAnimating()) return;
        double desired = stake * ratio;
        target = allTargets.stream().filter(item -> UpgraderClient.unitValue(item) > 0)
            .min(Comparator.comparingDouble(item -> Math.abs(UpgraderClient.unitValue(item) - desired)))
            .orElse(null);
        if (target != null) {
            search.setValue("");
            targetRow = Math.clamp(filteredTargets.indexOf(target) / targetColumns, 0, maxTargetRow());
            revealed = false;
            message = "";
        }
        updateControls();
    }

    private void changeSelection(int slot, int button, boolean all) {
        if (rolling || UpgraderClient.isAnimating()) return;
        UpgraderClient.Offer offer = offers.get(slot);
        if (offer == null) return;
        int count = selected.getOrDefault(slot, 0);
        boolean typeAvailable = UpgraderSelection.canAddType(selectedTypes.contains(offer.stack().getItem()), selectedTypes.size());
        if (!typeAvailable && count == 0 && (all || button == InputConstants.MOUSE_BUTTON_LEFT)) {
            message = rejection("selection_types_limit");
            messageColor = GOLD;
            return;
        }
        count = UpgraderSelection.nextCount(count, offer.stack().getCount(), selectedCount - count, typeAvailable, button, all);
        if (count == 0) selected.remove(slot);
        else selected.put(slot, count);
        selectionChanged();
    }

    private void beginRoll() {
        if (rolling || !upgradeButton.active || target == null) return;
        needleStartTurn = restingNeedleTurn = UpgraderClient.restingNeedleTurn();
        rollingStake = stake;
        rollingChance = chance();
        settleChance(rollingChance);
        result = null;
        visualRoll = 0;
        settlement = null;
        celebrationStarted = 0;
        revealed = false;
        rolling = true;
        skipSignalled = false;
        message = "";
        rollStarted = 0;
        lastTickSound = -1;
        pending = UpgraderClient.upgrade(Map.copyOf(selected), target);
        sound(SoundEvents.ENCHANTMENT_TABLE_USE, 1.1f, .6f);
        updateControls();
    }

    @Override
    public void tick() {
        super.tick();
        if (!rolling) restingNeedleTurn = UpgraderClient.restingNeedleTurn();
        if (rolling && pending != null && pending.isDone() && result == null) {
            try {
                result = pending.join();
            } catch (RuntimeException error) {
                result = new UpgraderClient.RollResult(false, false, 0, 0, "unavailable", ItemStack.EMPTY);
            }
            if (!result.accepted()) {
                visualRoll = 0;
                rolling = false;
                message = rejection(result.message());
                messageColor = RED;
                refreshInventory();
                filterTargets();
            } else {
                visualRoll = UpgraderClient.animationVisualRoll();
                restingNeedleTurn = UpgraderWheel.stoppedTurn(visualRoll, result.chance());
                rollingChance = result.chance();
                long sharedStart = UpgraderClient.animationStartedNanos();
                rollStarted = sharedStart > 0 ? sharedStart : System.nanoTime();
                settleChance(rollingChance);
            }
        }
        if (rolling && result != null && elapsedMillis() >= UpgraderMath.SPIN_MILLIS) {
            if (settlement == null) {
                revealed = true;
                message = tr("Завершение апгрейда…", "Completing the upgrade…");
                messageColor = MUTED;
                settlement = UpgraderClient.finishAnimation();
            }
            if (settlement.isDone()) {
                boolean completed;
                try { completed = Boolean.TRUE.equals(settlement.join()); }
                catch (RuntimeException error) { completed = false; }
                rolling = false;
                if (completed) {
                    selected.clear();
                    message = result.success() ? tr("Успех! Предмет уже в инвентаре.", "Success! The item is in your inventory.")
                        : result.reward().isEmpty() ? tr("Неудача. Предметы ставки потеряны.", "Unlucky. The staked items were consumed.")
                        : tr("Неудача. Утешение: ", "Lost. Consolation: ") + result.reward().getHoverName().getString()
                            + (result.reward().getCount() > 1 ? " x" + result.reward().getCount() : "");
                    messageColor = result.success() ? GREEN : RED;
                    if (result.success()) startCelebration();
                    else sound(SoundEvents.ANVIL_LAND, .8f, .3f);
                } else {
                    revealed = false;
                    message = tr("Не удалось завершить апгрейд.", "The upgrade could not be completed.");
                    messageColor = RED;
                }
                refreshInventory();
                filterTargets();
            }
        }
        if (!rolling && ++ticks % 5 == 0) {
            long previous = stake;
            refreshInventory();
            if (previous != stake) filterTargets();
        }
        if (rolling && result != null && result.accepted() && animations()) {
            long segment = (long) (Math.abs(needleTurns()) * 16);
            if (segment != lastTickSound) {
                lastTickSound = segment;
                sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0f + (float) rollProgress() * .6f, .13f);
            }
        }
        if (celebrationStarted > 0 && !celebrationTwinkle
                && System.nanoTime() - celebrationStarted >= 150_000_000L) {
            celebrationTwinkle = true;
            sound(SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.25f, .28f);
            sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5f, .32f);
        }
        updateControls();
    }

    private String rejection(String reason) {
        if (reason == null) return tr("Не удалось выполнить апгрейд.", "The upgrade could not be completed.");
        return switch (reason) {
            case "invalid_target_value", "target_too_cheap" -> tr("Выберите доступный предмет.", "Choose an available item.");
            case "inventory_changed" -> tr("Инвентарь изменился. Выберите ставку заново.", "Your inventory changed. Select the stake again.");
            case "inventory_full" -> tr("Освободите место для награды.", "Make room for the reward.");
            case "forbidden_item" -> tr("Этот предмет нельзя использовать.", "This item cannot be used.");
            case "invalid_selection" -> tr("Выберите предметы из инвентаря.", "Select items from your inventory.");
            case "selection_limit" -> tr("В ставке может быть до 10 предметов.", "Stake up to 10 items in total.");
            case "selection_types_limit" -> tr("Можно выбрать до 3 разных типов предметов.", "Choose up to 3 different item types.");
            default -> tr("Нужен активный забег с включённым апгрейдером.", "Requires an active run with the Upgrader enabled.");
        };
    }

    private void updateControls() {
        boolean editable = !rolling && !UpgraderClient.isAnimating() && UpgraderClient.available();
        if (upgradeButton != null) {
            upgradeButton.active = editable && stake > 0 && target != null && chance() > 0;
            upgradeButton.setMessage(Component.literal(rolling ? tr("АПГРЕЙД…", "UPGRADING…")
                : tr("УЛУЧШИТЬ", "UPGRADE")));
        }
        for (PixelButton button : selectionButtons) button.active = editable;
        for (SlotButton button : inventoryButtons) button.active = editable && offers.containsKey(button.slot);
        for (SlotButton button : targetButtons) button.active = editable && visibleTarget(button.index) != null;
    }

    private long elapsedMillis() {
        return (System.nanoTime() - rollStarted) / 1_000_000L;
    }

    private double rollProgress() {
        if (rolling && (result == null || !result.accepted())) return 0;
        return Math.clamp(elapsedMillis() / (double) UpgraderMath.SPIN_MILLIS, 0, 1);
    }

    private double needleTurns() {
        if (result == null || !result.accepted()) return rolling ? needleStartTurn : restingNeedleTurn;
        if (rolling && animations()) return UpgraderWheel.spinTurns(rollProgress(), visualRoll, result.chance(), needleStartTurn);
        return rolling && !revealed ? needleStartTurn : restingNeedleTurn;
    }

    private void settleChance(double value) {
        displayedChance = chanceFrom = chanceTarget = value;
        chanceChanged = System.nanoTime();
    }

    private double animatedChance() {
        double desired = revealed && result != null ? result.chance() : chance();
        if (!animations()) {
            settleChance(desired);
            return desired;
        }
        long now = System.nanoTime();
        double progress = Math.clamp((now - chanceChanged) / 360_000_000.0, 0, 1);
        double eased = progress * progress * (3 - 2 * progress);
        displayedChance = chanceFrom + (chanceTarget - chanceFrom) * eased;
        if (Math.abs(desired - chanceTarget) > 1e-12) {
            chanceFrom = displayedChance;
            chanceTarget = desired;
            chanceChanged = now;
        }
        return displayedChance;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        extractBlurredBackground(graphics);
        graphics.fill(0, 0, width, height, 0x500A100B);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        double entrance = animations() ? Math.clamp((System.nanoTime() - opened) / 220_000_000.0, 0, 1) : 1;
        graphics.fill(layout.windowX() + 4, layout.windowY() + 4,
            layout.windowX() + layout.windowWidth() + 4, layout.windowY() + layout.windowHeight() + 4, 0x65000000);
        panel(graphics, layout.windowX(), layout.windowY(), layout.windowWidth(), layout.windowHeight(),
            UpgraderPalette.WINDOW, UpgraderPalette.WINDOW_EDGE);
        int accentHalf = (int) ((layout.windowWidth() - 10) / 2.0 * (1 - Math.pow(1 - entrance, 3)));
        graphics.fill(width / 2 - accentHalf, layout.windowY() + 1,
            width / 2 + accentHalf, layout.windowY() + 2, GOLD);
        int titleY = layout.windowY() + 6;
        chevron(graphics, width / 2 - font.width(title) / 2 - 16, titleY, GOLD);
        graphics.centeredText(font, title, width / 2, titleY, GOLD);
        String status = message;
        int color = messageColor;
        if (status.isEmpty()) {
            color = MUTED;
            if (rolling) status = result == null ? tr("Проверка ставки…", "Checking the stake…")
                : tr("Колесо вращается…", "The wheel is spinning…");
            else if (!UpgraderClient.available()) {
                status = tr("Нужен активный забег с апгрейдером", "Start a run with the Upgrader enabled");
                color = RED;
            } else if (UpgraderClient.isAnimating()) status = tr("Предыдущий апгрейд завершается…", "The previous upgrade is finishing…");
            else status = tr("Шанс = ставка / цель • до 75% • неудача сжигает ставку",
                "Chance = stake / target • 75% cap • losing consumes the stake");
        }
        scaledCentered(graphics, status, width / 2, layout.windowY() + 18,
            .65f, color, panelWidth - 24);

        drawStake(graphics);
        drawWheel(graphics);
        drawTarget(graphics);
        panel(graphics, x, lowerY, leftWidth, lowerHeight, PANEL, EDGE);
        panel(graphics, rightX, lowerY, rightWidth, lowerHeight, PANEL, EDGE);
        graphics.fill(x + 3, lowerY + 3, x + leftWidth - 3, lowerY + 20, UpgraderPalette.HEADER);
        graphics.fill(rightX + 3, lowerY + 3, rightX + rightWidth - 3, lowerY + 20, UpgraderPalette.HEADER);
        graphics.text(font, tr("Инвентарь", "Inventory"), x + 7, lowerY + 7, TEXT, false);
        String picked = selectedCount + "/" + UpgraderTransaction.MAX_SELECTED_ITEMS + " • "
            + selectedTypes.size() + "/" + UpgraderTransaction.MAX_SELECTED_TYPES + tr(" типа", " types");
        scaledRight(graphics, picked, x + leftWidth - 6, lowerY + 7, .7f, MUTED);
        panel(graphics, rightX + 6, lowerY + 4, rightWidth - 42, 15, UpgraderPalette.INK,
            search.isFocused() ? UpgraderPalette.TEAL : UpgraderPalette.EDGE);
        if (filteredTargets.isEmpty()) {
            scaledCentered(graphics, tr("Нет подходящих предметов", "No matching items"), rightX + rightWidth / 2,
                gridY + slotSize, .7f, MUTED, rightWidth - 12);
        }
        drawScrollbar(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        drawCelebration(graphics);
        String hint = tr("ЛКМ +1 / ПКМ −1 / Shift: до 10 • 3 типа • ", "Left +1 / Right −1 / Shift: up to 10 • 3 types • ")
            + UpgraderClient.keyName().getString() + tr(" / Esc: закрыть", " / Esc: close");
        scaledCentered(graphics, hint, width / 2, layout.windowY() + layout.windowHeight() - 10,
            .63f, MUTED, panelWidth - 4);
        itemTooltips(graphics, mouseX, mouseY);
    }

    private void drawStake(GuiGraphicsExtractor graphics) {
        panel(graphics, x, top, sideWidth, heroHeight, UpgraderPalette.STAKE, UpgraderPalette.STAKE_EDGE);
        scaledCentered(graphics, tr("ВАША СТАВКА", "YOUR STAKE"), x + sideWidth / 2, top + 9,
            heroHeight < 90 ? .72f : .85f, GOLD, sideWidth - 10);
        List<ItemStack> stacks = stakePreviews;
        int itemY = top + Math.max(19, heroHeight / 2 - 11);
        if (stacks.isEmpty()) {
            int previewSize = heroHeight < 70 ? 20 : 26;
            emptySlot(graphics, x + sideWidth / 2 - previewSize / 2, itemY - 3, previewSize);
            int plusX = x + sideWidth / 2;
            int plusY = itemY - 3 + previewSize / 2;
            graphics.fill(plusX - 4, plusY - 1, plusX + 4, plusY + 1, GOLD);
            graphics.fill(plusX - 1, plusY - 4, plusX + 1, plusY + 4, GOLD);
        } else {
            int visible = Math.min(stacks.size(), UpgraderTransaction.MAX_SELECTED_TYPES);
            int spacing = Math.min(34, (sideWidth - 12) / visible);
            float scale = heroHeight < 90 ? 1f : 1.55f;
            for (int i = 0; i < visible; i++) {
                int itemX = x + sideWidth / 2 + (int) ((i - (visible - 1) / 2.0) * spacing);
                bigItem(graphics, stacks.get(i), itemX, itemY + 8, scale);
                if (stacks.get(i).getCount() > 1) scaledRight(graphics,
                    "x" + stacks.get(i).getCount(), itemX + 14, itemY + 15, .7f, TEXT);
            }
        }
        if (heroHeight >= 100) scaledCentered(graphics, stacks.isEmpty()
            ? tr("Выберите предметы ниже", "Select items below")
            : selectedTypes.size() + tr(" типа • ", " types • ") + selectedCount + tr(" шт.", " items"),
            x + sideWidth / 2, top + heroHeight - 35, .75f, MUTED, sideWidth - 12);
        scaledCentered(graphics, number(rolling ? rollingStake : stake) + " " + tr("ед.", "pts"),
            x + sideWidth / 2, top + heroHeight - 15, .9f, GOLD, sideWidth - 12);
    }

    private void startCelebration() {
        celebrationStarted = System.nanoTime();
        celebrationTwinkle = false;
        for (int index = 0; index < sparkX.length; index++) {
            int ray = index % 16;
            double angle = TAU * ray / 16 + index / 16 * .19;
            sparkX[index] = (float) Math.cos(angle);
            sparkY[index] = (float) Math.sin(angle);
            sparkSpeed[index] = 22 + index % 5 * 5;
        }
        sound(SoundEvents.FIREWORK_ROCKET_BLAST, 1.15f, .26f);
        sound(SoundEvents.NOTE_BLOCK_CHIME.value(), 1f, .42f);
    }

    private void drawCelebration(GuiGraphicsExtractor graphics) {
        if (celebrationStarted == 0 || !animations()) return;
        double elapsed = (System.nanoTime() - celebrationStarted) / 1_000_000_000.0;
        if (elapsed >= 1.2) return;
        int cx = x + sideWidth + 8 + centerWidth / 2;
        int cy = top + heroHeight / 2;
        GuiQuadBatch.Builder sparks = new GuiQuadBatch.Builder(48);
        for (int index = 0; index < sparkX.length; index++) {
            double age = elapsed - index / 16 * .08;
            if (age < 0 || age > 1) continue;
            double distance = sparkSpeed[index] * (1 - Math.exp(-age * 3));
            int px = cx + (int) Math.round(sparkX[index] * distance);
            int py = cy + (int) Math.round(sparkY[index] * distance + age * age * 16);
            int size = index % 4 == 0 ? 2 : 1;
            int left = Math.max(x + 2, px), right = Math.min(x + panelWidth - 2, px + size);
            int upper = Math.max(top + 2, py), lower = Math.min(top + heroHeight - 2, py + size);
            int alpha = (int) (255 * Math.max(0, 1 - age));
            int color = index % 3 == 0 ? GOLD : index % 3 == 1 ? GREEN : TEXT;
            sparks.fill(left, upper, right, lower, alpha << 24 | color & 0xFFFFFF);
        }
        sparks.build().extract(graphics);
    }

    private void drawTarget(GuiGraphicsExtractor graphics) {
        int targetPanelX = layout.targetPanelX();
        boolean consolation = revealed && result != null && !result.success() && !result.reward().isEmpty();
        ItemStack shown = consolation ? result.reward() : target == null ? ItemStack.EMPTY : preview(target);
        panel(graphics, targetPanelX, top, sideWidth, heroHeight, UpgraderPalette.TARGET, UpgraderPalette.TARGET_EDGE);
        scaledCentered(graphics, consolation ? tr("УТЕШИТЕЛЬНЫЙ ПРЕДМЕТ", "SMALL CONSOLATION")
            : tr("ПРЕДМЕТ НА ВЫХОДЕ", "UPGRADE TARGET"), targetPanelX + sideWidth / 2,
            top + 9, heroHeight < 90 ? .65f : .8f, consolation ? TEXT : UpgraderPalette.TEAL, sideWidth - 10);
        int itemY = top + Math.max(19, heroHeight / 2 - 11);
        if (shown.isEmpty()) {
            int previewSize = heroHeight < 70 ? 20 : 26;
            emptySlot(graphics, targetPanelX + sideWidth / 2 - previewSize / 2, itemY - 3, previewSize);
            chevron(graphics, targetPanelX + sideWidth / 2 - 5, itemY + 2, UpgraderPalette.TEAL);
        } else {
            bigItem(graphics, shown, targetPanelX + sideWidth / 2, itemY + 7,
                consolation ? heroHeight < 90 ? 1f : 1.3f : heroHeight < 90 ? 1.25f : 2f);
        }
        boolean nearest = shown.isEmpty() && !filteredTargets.isEmpty() && UpgraderSearch.numericValue(searchText).isPresent();
        if (heroHeight >= 100) scaledCentered(graphics, nearest ? tr("Ближайшая ценность", "Nearest target value")
            : shown.isEmpty() ? tr("Выберите цель ниже", "Select a target below")
            : shown.getHoverName().getString(), targetPanelX + sideWidth / 2,
            top + heroHeight - 35, .8f, TEXT, sideWidth - 12);
        scaledCentered(graphics, nearest ? "≈ " + number(UpgraderClient.unitValue(filteredTargets.getFirst())) + " " + tr("ед.", "pts")
            : shown.isEmpty() ? "—" : number(consolation ? UpgraderClient.value(shown)
                : UpgraderClient.unitValue(target)) + " " + tr("ед.", "pts"),
            targetPanelX + sideWidth / 2, top + heroHeight - 15, .9f,
            consolation ? TEXT : UpgraderPalette.TEAL, sideWidth - 12);
    }

    private void drawWheel(GuiGraphicsExtractor graphics) {
        int cx = x + sideWidth + 8 + centerWidth / 2;
        int cy = top + heroHeight / 2;
        int radius = layout.wheelRadius();
        double winChance = animatedChance();
        int outcome = revealed ? result.success() ? GREEN : RED : GOLD;
        int fill = revealed ? result.success() ? UpgraderPalette.SUCCESS_FILL : UpgraderPalette.FAILURE_FILL
            : UpgraderPalette.DISC;
        int scale = Math.max(1, minecraft.getWindow().getGuiScale());
        UpgraderWheelGeometry wheel = new UpgraderWheelGeometry(radius, scale);
        cacheWheel(wheel);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, cy);
        graphics.pose().scale(wheel.rasterToGui(), wheel.rasterToGui());
        graphics.pose().translate(-.5f, -.5f);
        discMesh.extract(graphics, fill, fill);
        outlineMesh.extract(graphics, UpgraderPalette.RING_EDGE, UpgraderPalette.RING_EDGE);
        chanceRing(graphics, wheel.coloredOuter(), wheel.coloredInner(), winChance,
            outcome, UpgraderPalette.RING_TRACK);
        int accent = revealed ? outcome : UpgraderPalette.EDGE;
        accentMesh.extract(graphics, accent, accent);
        tickMesh.extract(graphics, UpgraderPalette.TEAL, UpgraderPalette.RING_EDGE);
        graphics.pose().translate(.5f, .5f);
        graphics.pose().rotate((float) (TAU * UpgraderWheel.wrap01(needleTurns())));
        graphics.pose().translate(-.5f, -.5f);
        needleMesh.extract(graphics, UpgraderPalette.INK, outcome);
        graphics.pose().popMatrix();
        float textScale = radius >= 53 ? 1.6f : radius >= 35 ? 1.15f : .83f;
        String center = revealed ? result.success() ? tr("УСПЕХ", "SUCCESS") : tr("НЕУДАЧА", "LOST") : percent(winChance);
        int percentageColor = UpgraderPalette.chanceColor(winChance);
        scaledCentered(graphics, center, cx, cy - (radius > 35 ? 8 : 3), textScale,
            revealed ? outcome : percentageColor, radius * 2 - 15);
        if (radius > 35) scaledCentered(graphics, revealed ? percent(winChance) : tr("шанс успеха", "success chance"),
            cx, cy + 10, .7f, revealed ? percentageColor : MUTED, radius * 2 - 18);
        if (rolling && !animations()) {
            int barX = cx - radius / 2;
            graphics.fill(barX, cy + 16, barX + radius, cy + 18, UpgraderPalette.EDGE);
            graphics.fill(barX, cy + 16, barX + (int) (radius * rollProgress()), cy + 18, GOLD);
        }
    }

    private void cacheWheel(UpgraderWheelGeometry wheel) {
        if (wheel.equals(cachedWheel)) return;
        cachedWheel = wheel;
        UpgraderPixelMesh.Builder disc = new UpgraderPixelMesh.Builder();
        int discRadius = wheel.coloredInner() - wheel.units(1);
        for (int y = -discRadius; y <= discRadius; y++) {
            int extent = (int) Math.sqrt((long) discRadius * discRadius - (long) y * y);
            disc.fill(-extent, y, extent + 1, y + 1, 0);
        }
        discMesh = disc.build();
        UpgraderPixelMesh.Builder outline = new UpgraderPixelMesh.Builder();
        uniformRing(outline, wheel.radius() + wheel.units(3), wheel.radius() + wheel.units(2));
        outlineMesh = outline.build();
        UpgraderPixelMesh.Builder accent = new UpgraderPixelMesh.Builder();
        uniformRing(accent, wheel.coloredInner() - 1, wheel.coloredInner() - 2);
        accentMesh = accent.build();
        UpgraderPixelMesh.Builder ticks = new UpgraderPixelMesh.Builder();
        for (int i = 0; i < 64; i++) {
            double angle = TAU * i / 64.0 + Math.PI / 2;
            boolean major = i % 8 == 0;
            int start = wheel.radius() + wheel.units(4);
            int end = start + (major ? wheel.units(2) : 1);
            for (int distance = start; distance < end; distance++) {
                int px = (int) Math.round(Math.cos(angle) * distance);
                int py = (int) Math.round(Math.sin(angle) * distance);
                ticks.fill(px, py, px + 1, py + 1, major ? 0 : 1);
            }
        }
        tickMesh = ticks.build();
        UpgraderPixelMesh.Builder needle = new UpgraderPixelMesh.Builder();
        rotatingNeedle(needle, wheel);
        needleMesh = needle.build();
    }

    private static void rotatingNeedle(UpgraderPixelMesh.Builder mesh, UpgraderWheelGeometry wheel) {
        int tip = wheel.pointerTip();
        int base = wheel.pointerBase();
        int length = wheel.pointerHeadLength();
        int half = wheel.pointerHeadHalfWidth();
        int shaftHalf = wheel.pointerLineWidth() / 2;
        mesh.fill(-shaftHalf - 1, tip + length - 2, shaftHalf + 2, base + 2, 0);
        for (int row = 0; row < length; row++) {
            int headHalf = row * half / (length - 1);
            mesh.fill(-headHalf - 1, tip + row, headHalf + 2, tip + row + 2, 0);
        }
        mesh.fill(-shaftHalf, tip + length - 2, shaftHalf + 1, base + 1, 1);
        for (int row = 0; row < length; row++) {
            int headHalf = row * half / (length - 1);
            mesh.fill(-headHalf, tip + row, headHalf + 1, tip + row + 1, 1);
        }
    }

    private void chanceRing(GuiGraphicsExtractor graphics, int outer, int inner,
                            double chance, int successColor, int otherColor) {
        if (chanceRaster == null || !chanceRaster.matches(outer, inner, chance)) {
            chanceRaster = new UpgraderRingRaster(outer, inner, chance);
            UpgraderPixelMesh.Builder mesh = new UpgraderPixelMesh.Builder();
            for (UpgraderRingRaster.Span span : chanceRaster.spans())
                mesh.fill(span.x(), span.y(), span.endX(), span.y() + 1, span.success() ? 0 : 1);
            chanceMesh = mesh.build();
        }
        chanceMesh.extract(graphics, successColor, otherColor);
    }

    private static void uniformRing(UpgraderPixelMesh.Builder mesh, int outer, int inner) {
        long outside = (long) outer * outer;
        long inside = (long) inner * inner;
        for (int y = -outer; y <= outer; y++) {
            int extent = (int) Math.sqrt(outside - (long) y * y);
            int hole = Math.abs(y) < inner ? (int) Math.ceil(Math.sqrt(inside - (long) y * y)) : 0;
            if (hole == 0) mesh.fill(-extent, y, extent + 1, y + 1, 0);
            else {
                if (-extent <= -hole) mesh.fill(-extent, y, -hole + 1, y + 1, 0);
                if (hole <= extent) mesh.fill(hole, y, extent + 1, y + 1, 0);
            }
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int barX = rightX + rightWidth - 7;
        int barHeight = slotSize * targetRows + 2;
        graphics.fill(barX, gridY, barX + 3, gridY + barHeight, UpgraderPalette.INK);
        int rows = (filteredTargets.size() + targetColumns - 1) / targetColumns;
        int thumb = Math.max(10, barHeight * targetRows / Math.max(targetRows, rows));
        int offset = maxTargetRow() == 0 ? 0 : (barHeight - thumb) * targetRow / maxTargetRow();
        graphics.fill(barX, gridY + offset, barX + 3, gridY + offset + thumb, UpgraderPalette.TEAL);
    }

    private void itemTooltips(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (SlotButton button : inventoryButtons) {
            if (!button.isMouseOver(mouseX, mouseY)) continue;
            ItemStack stack = inventory[button.slot];
            if (stack == null || stack.isEmpty()) return;
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, stack));
            long value = UpgraderClient.value(stack.copyWithCount(1));
            lines.add(Component.literal(value > 0 ? "§6" + tr("Ценность: ", "Value: ") + number(value)
                + tr(" ед. / шт.", " pts / item") : "§8" + tr("Не используется в апгрейдере", "Excluded from the Upgrader")));
            lines.add(Component.literal("§7" + tr("В ставке: ", "Selected: ") + selected.getOrDefault(button.slot, 0)));
            lines.add(Component.literal("§8" + tr("ЛКМ +1 / ПКМ −1 / Shift: до 10", "Left +1 / Right −1 / Shift: up to 10")));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
        for (SlotButton button : targetButtons) {
            Item item = visibleTarget(button.index);
            if (item == null || !button.isMouseOver(mouseX, mouseY)) continue;
            List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, preview(item)));
            lines.add(Component.literal("§6" + number(UpgraderClient.unitValue(item)) + tr(" ед. • 1 предмет", " pts • 1 item")));
            lines.add(Component.literal("§7" + tr("Шанс: ", "Chance: ") + percent(UpgraderMath.chance(stake, UpgraderClient.unitValue(item)))));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
    }

    private static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int fill, int border) {
        graphics.fill(x, y, x + width, y + height, UpgraderPalette.INK);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, border);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 3, bevel(fill, border));
        graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, UpgraderPalette.INK);
    }

    private static int bevel(int fill, int border) {
        int red = ((fill >> 16 & 255) * 3 + (border >> 16 & 255)) / 4;
        int green = ((fill >> 8 & 255) * 3 + (border >> 8 & 255)) / 4;
        int blue = ((fill & 255) * 3 + (border & 255)) / 4;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static void emptySlot(GuiGraphicsExtractor graphics, int x, int y, int size) {
        graphics.fill(x, y, x + size, y + size, UpgraderPalette.INK);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, UpgraderPalette.SLOT);
        graphics.fill(x + 1, y + size - 2, x + size - 1, y + size - 1, UpgraderPalette.SLOT_EDGE);
        graphics.fill(x + size - 2, y + 1, x + size - 1, y + size - 1, UpgraderPalette.SLOT_EDGE);
    }

    private static void chevron(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int i = 0; i < 5; i++) {
            graphics.fill(x + i, y + 4 - i, x + i + 1, y + 7 - i, color);
            graphics.fill(x + 5 + i, y + i, x + 6 + i, y + 3 + i, color);
        }
    }

    private void bigItem(GuiGraphicsExtractor graphics, ItemStack stack, int cx, int cy, float scale) {
        ItemPreviewRenderer.extractLargeGui(graphics, stack, cx, cy, 16f * scale);
    }

    private void scaledCentered(GuiGraphicsExtractor graphics, String text, int cx, int y,
                                float scale, int color, int maxWidth) {
        if (text.isEmpty()) return;
        int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        int preferredPixels = Math.max(1, Math.round(scale * guiScale));
        int fittingPixels = Math.max(1, (int) Math.floor(maxWidth * guiScale / (double) Math.max(1, font.width(text))));
        scale = Math.min(preferredPixels, fittingPixels) / (float) guiScale;
        if (font.width(text) * scale > maxWidth) {
            int textWidth = Math.max(1, (int) (maxWidth / scale));
            text = font.plainSubstrByWidth(text, Math.max(0, textWidth - font.width("…"))) + "…";
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text) / 2, 0, color, false);
        graphics.pose().popMatrix();
    }

    private void scaledRight(GuiGraphicsExtractor graphics, String text, int right, int y, float scale, int color) {
        scale = crispScale(scale);
        graphics.pose().pushMatrix();
        graphics.pose().translate(right, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, text, -font.width(text), 0, color, false);
        graphics.pose().popMatrix();
    }

    private float crispScale(float requested) {
        int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
        return Math.max(1, Math.round(requested * guiScale)) / (float) guiScale;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && event.x() >= rightX + 6 && event.x() < rightX + rightWidth - 36
                && event.y() >= lowerY + 4 && event.y() < lowerY + 19) {
            setFocused(search);
            search.setFocused(true);
            search.onClick(event, doubleClick);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void scrollTargets(int rows) {
        targetRow = Math.clamp(targetRow + rows, 0, maxTargetRow());
        updateControls();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (mouseX >= rightX && mouseX < rightX + rightWidth && mouseY >= lowerY && mouseY < lowerY + lowerHeight) {
            scrollTargets((int) -Math.signum(vertical) * 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) return super.keyPressed(event);
        if (search != null && search.isFocused()) return super.keyPressed(event);
        if (UpgraderClient.matchesOpenKey(event) || minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
            scrollTargets(event.key() == InputConstants.KEY_PAGEDOWN ? targetRows : -targetRows);
            return true;
        }
        if (event.key() == InputConstants.KEY_F && (event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        skipPendingAnimation();
        super.onClose();
    }

    @Override
    public void removed() {
        skipPendingAnimation();
        super.removed();
    }

    private void skipPendingAnimation() {
        if (!skipSignalled && rolling) {
            skipSignalled = true;
            UpgraderClient.skipAnimation();
        }
    }

    @Override
    public boolean isInGameUi() { return true; }

    private void sound(SoundEvent event, float pitch, float volume) {
        var config = RandomRunMod.getInstance().getConfig();
        if (!config.isSoundEffectsEnabled()) return;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume * config.getSoundVolume() / 100f));
    }

    private final class PixelButton extends Button {
        private final boolean gold;
        private double hover;
        private long lastFrame = System.nanoTime();

        private PixelButton(int x, int y, int width, int height, Component message, OnPress action, boolean gold) {
            super(x, y, width, height, message, action, DEFAULT_NARRATION);
            this.gold = gold;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            long now = System.nanoTime();
            double blend = animations() ? 1 - Math.exp(-(now - lastFrame) / 65_000_000.0) : 1;
            lastFrame = now;
            hover += ((isHoveredOrFocused() && active ? 1 : 0) - hover) * blend;
            int base = gold ? active ? UpgraderPalette.PRIMARY : UpgraderPalette.PRIMARY_DISABLED
                : active ? UpgraderPalette.BUTTON : UpgraderPalette.BUTTON_DISABLED;
            int light = gold ? active ? GOLD : UpgraderPalette.PRIMARY_DISABLED_EDGE
                : active ? UpgraderPalette.BUTTON_EDGE : UpgraderPalette.BUTTON_DISABLED_EDGE;
            panel(graphics, getX(), getY(), getWidth(), getHeight(), base, light);
            if (hover > .01) graphics.fill(getX() + 2, getY() + 2, getRight() - 2, getBottom() - 2,
                ((int) (hover * 26) << 24) | 0xFFF2B9);
            if (isFocused()) graphics.outline(getX(), getY(), getWidth(), getHeight(), GOLD);
            scaledCentered(graphics, getMessage().getString(), getX() + getWidth() / 2,
                getY() + (getHeight() < 15 ? 3 : (getHeight() - 8) / 2),
                getHeight() < 15 ? .75f : 1f, gold && active ? UpgraderPalette.INK : active || gold ? TEXT : MUTED,
                getWidth() - 5);
        }

        @Override
        public void playDownSound(SoundManager soundManager) { sound(SoundEvents.UI_BUTTON_CLICK.value(), 1f, .35f); }
    }

    private final class SlotButton extends Button {
        private final int slot;
        private final int index;
        private ItemStack narratedStack;

        private SlotButton(int x, int y, int slot, int index) {
            super(x, y, slotSize, slotSize, Component.empty(), button -> {}, DEFAULT_NARRATION);
            this.slot = slot;
            this.index = index;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            Item visible = slot < 0 ? visibleTarget(index) : null;
            ItemStack stack = slot >= 0 ? inventory[slot] : visible == null ? ItemStack.EMPTY : preview(visible);
            boolean chosen = slot >= 0 ? selected.containsKey(slot) : visible != null && visible == target;
            emptySlot(graphics, getX(), getY(), slotSize - 1);
            if (chosen) {
                graphics.fill(getX() + 1, getY() + 1, getRight() - 2, getBottom() - 2,
                    slot >= 0 ? UpgraderPalette.SELECTED : UpgraderPalette.TARGET_SELECTED);
                graphics.outline(getX(), getY(), slotSize - 1, slotSize - 1,
                    slot >= 0 ? GOLD : UpgraderPalette.TEAL);
            } else if (isHoveredOrFocused()) {
                graphics.fill(getX() + 1, getY() + 1, getRight() - 2, getBottom() - 2, UpgraderPalette.SLOT_HOVER);
            }
            if (stack == null || stack.isEmpty()) {
                if (narratedStack != null) {
                    narratedStack = null;
                    setMessage(Component.empty());
                }
                return;
            }
            float itemOffset = (slotSize - 1 - 16) / 2f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(getX() + itemOffset, getY() + itemOffset);
            graphics.item(stack, 0, 0);
            if (slot >= 0) graphics.itemDecorations(font, stack, 0, 0);
            graphics.pose().popMatrix();
            if (slot >= 0 && chosen) {
                String count = selected.get(slot).toString();
                float countScale = crispScale(.65f);
                int countWidth = (int) Math.ceil(font.width(count) * countScale) + 2;
                int countHeight = (int) Math.ceil(8 * countScale) + 2;
                graphics.fill(getX() + 1, getY() + 1, getX() + 1 + countWidth,
                    getY() + 1 + countHeight, GOLD);
                graphics.pose().pushMatrix();
                graphics.pose().translate(getX() + 2, getY() + 2);
                graphics.pose().scale(countScale, countScale);
                graphics.text(font, count, 0, 0, UpgraderPalette.INK, false);
                graphics.pose().popMatrix();
            }
            if (slot >= 0 && !offers.containsKey(slot)) {
                graphics.fill(getX() + 2, getY() + 2, getX() + 4, getY() + 4, RED);
            }
            if (narratedStack != stack) {
                narratedStack = stack;
                setMessage(Component.literal(stack.getHoverName().getString() + ", " + tr("ценность ", "value ")
                    + number(UpgraderClient.value(stack))));
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (!active || !isMouseOver(event.x(), event.y()) || !UpgraderSelection.accepts(event.button())) return false;
            if (slot >= 0) changeSelection(slot, event.button(), event.hasShiftDown());
            else selectTarget();
            playDownSound(minecraft.getSoundManager());
            return true;
        }

        @Override
        public void onPress(net.minecraft.client.input.InputWithModifiers input) {
            if (slot >= 0) changeSelection(slot, InputConstants.MOUSE_BUTTON_LEFT, input.hasShiftDown());
            else selectTarget();
        }

        private void selectTarget() {
            Item visible = visibleTarget(index);
            if (visible == null || rolling) return;
            target = visible;
            revealed = false;
            UpgraderScreen.this.message = "";
            updateControls();
        }

        @Override
        public void playDownSound(SoundManager soundManager) { sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, .2f); }
    }
}
