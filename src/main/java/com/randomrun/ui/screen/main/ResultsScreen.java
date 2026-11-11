package com.randomrun.ui.screen.main;



import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.util.Previews.PreviewItems;
import com.randomrun.core.history.Run;
import com.randomrun.core.history.RunHistory;
import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.core.service.DataSyncService;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.data.RunDataManager;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Heads;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ResultsScreen extends AbstractRandomRunScreen {
    private static final int ROW_HEIGHT = 26;
    private static final int MARGIN = 20;
    private static final long MAX_VALID_TIME = 359_999_000L;

    private enum Sort { TIME, NAME, ATTEMPTS }

    private record Summary(String targetId, Item item, ItemStack stack, String name, long bestTime, int attempts, int wins) {
        boolean hasBest() { return bestTime > 0 && bestTime <= MAX_VALID_TIME; }
        int winRate() { return attempts > 0 ? Math.round(wins * 100f / attempts) : 0; }
    }

    private record Segment(int x, int y, int w, int h, Runnable action) {}

    private final Screen parent;
    private static boolean upgraderMode;
    private static Sort sort = Sort.TIME;
    private boolean loading = true;
    private List<Run> records = List.of();
    private final List<Summary> all = new ArrayList<>();
    private final List<Summary> filtered = new ArrayList<>();
    private final List<Segment> segments = new ArrayList<>();
    private TextFieldStyled searchField;
    private double scroll;
    private double targetScroll;
    private int totalRuns, totalWins;
    private long totalTime, bestTime = -1, avgTime;
    private String bestItem;
    private long openTime;

    public ResultsScreen(Screen parent) {
        super(LanguageManager.text("randomrun.screen.results.title"));
        this.parent = parent;
    }

    public static void clearCache() {}

    @Override
    protected void init() {
        super.init();
        if (openTime == 0) {
            openTime = System.currentTimeMillis();
            DataSyncService.getInstance().loadHistoryOnDemand().whenComplete((ok, error) -> minecraft.execute(() -> {
                records = RunHistory.getInstance().getAllRecords();
                loading = false;
                rebuildData();
            }));
        }
        int toolbarY = toolbarY();
        String previous = searchField != null ? searchField.getValue() : "";
        searchField = new TextFieldStyled(font, MARGIN, toolbarY, Math.min(170, width / 3), 18, LanguageManager.text("randomrun.search"), 0f);
        searchField.setCenteredPlaceholder(LanguageManager.text("randomrun.search.placeholder"));
        searchField.setValue(previous);
        searchField.setResponder(value -> applyFilter());
        addRenderableWidget(searchField);
        addRenderableWidget(new ButtonDefault(width / 2 - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> onClose(), 0, 0.1f));
    }

    private int toolbarY() { return 76; }
    private int listTop() { return toolbarY() + 26; }
    private int listBottom() { return height - 36; }

    private void rebuildData() {
        Map<String, List<Run>> byTarget = records.stream()
            .filter(run -> run.isUpgrader() == upgraderMode)
            .collect(Collectors.groupingBy(Run::getTargetId));
        all.clear();
        totalRuns = 0;
        totalWins = 0;
        totalTime = 0;
        bestTime = -1;
        bestItem = null;
        long winTime = 0;
        Map<String, String> names = new HashMap<>();
        for (Map.Entry<String, List<Run>> entry : byTarget.entrySet()) {
            List<Run> runs = entry.getValue();
            long best = runs.stream().filter(Run::isVictory).mapToLong(Run::getDuration).filter(d -> d > 0).min().orElse(-1);
            int wins = (int) runs.stream().filter(Run::isVictory).count();
            Item item = resolve(entry.getKey());
            String name = names.computeIfAbsent(entry.getKey(), id -> item == null ? id : Component.translatable(item.getDescriptionId()).getString());
            all.add(new Summary(entry.getKey(), item, item == null ? ItemStack.EMPTY : PreviewItems.stack(item), name, best, runs.size(), wins));
            totalRuns += runs.size();
            totalWins += wins;
            for (Run run : runs) {
                if (run.getDuration() > 0 && run.getDuration() <= MAX_VALID_TIME) totalTime += run.getDuration();
                if (run.isVictory() && run.getDuration() > 0) winTime += run.getDuration();
            }
            if (best > 0 && best <= MAX_VALID_TIME && (bestTime < 0 || best < bestTime)) {
                bestTime = best;
                bestItem = name;
            }
        }
        avgTime = totalWins > 0 ? winTime / totalWins : 0;
        applySort();
    }

    private static Item resolve(String id) {
        try {
            Identifier identifier = Identifier.tryParse(id);
            Item item = identifier == null ? null : BuiltInRegistries.ITEM.getValue(identifier);
            return item == null || item == Items.AIR ? null : item;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private void applySort() {
        Comparator<Summary> comparator = switch (sort) {
            case TIME -> Comparator.comparingLong((Summary s) -> s.hasBest() ? s.bestTime() : Long.MAX_VALUE).thenComparing(Summary::name);
            case NAME -> Comparator.comparing(Summary::name, String.CASE_INSENSITIVE_ORDER);
            case ATTEMPTS -> Comparator.comparingInt(Summary::attempts).reversed().thenComparing(Summary::name);
        };
        all.sort(comparator);
        applyFilter();
    }

    private void applyFilter() {
        String query = searchField == null ? "" : searchField.getValue().toLowerCase(Locale.ROOT).trim();
        filtered.clear();
        for (Summary summary : all) {
            if (query.isEmpty() || summary.name().toLowerCase(Locale.ROOT).contains(query) || summary.targetId().contains(query)) filtered.add(summary);
        }
        scroll = targetScroll = 0;
    }

    private float fade() {
        return Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        segments.clear();
        float fade = fade();
        Theme theme = Theme.current();
        renderHeader(context, theme, fade);
        renderCards(context, theme, fade);
        renderToolbar(context, theme, mouseX, mouseY, fade);
        renderList(context, theme, mouseX, mouseY, fade);
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private void renderHeader(GuiGraphicsExtractor context, Theme theme, float fade) {
        String name = BackendSession.playerName();
        Heads.draw(context, name, MARGIN, 10, 18, Ui.argb(theme.accent, fade));
        GuiText.text(context, font, Component.literal(name), MARGIN + 24, 10, Ui.argb(0xFFFFFF, fade), true);
        GuiText.text(context, font, LanguageManager.text("randomrun.results.subtitle"), MARGIN + 24, 20, Ui.argb(0xA1A1AA, fade), false);
        if (RandomRunMod.getInstance().getConfig().isOnlineMode() && PlayerProfile.get() != null) {
            int trust = PlayerProfile.get().getTrustScore();
            int color = trust >= 150 ? 0x4ADE80 : trust >= 100 ? 0xFACC15 : trust >= PlayerProfile.LEADERBOARD_THRESHOLD ? 0xFB923C : 0xF87171;
            Component chip = Component.literal("Trust " + trust + "/200");
            int w = font.width(chip) + 8;
            Ui.chip(context, font, chip, width - MARGIN - w, 12, Ui.argb(0x000000, 0.55f * fade), Ui.argb(color, fade));
        }
        String mode = upgraderMode ? "RRM Upgrader" : LanguageManager.translate("randomrun.results.mode.classic");
        Component title = LanguageManager.text("randomrun.results.heading", mode);
        GuiText.centeredText(context, font, title, width / 2, 14, Ui.argb(0xFFFFFF, fade));
    }

    private void renderCards(GuiGraphicsExtractor context, Theme theme, float fade) {
        List<String[]> cards = new ArrayList<>();
        cards.add(new String[] {LanguageManager.translate("randomrun.results.card.items"), String.valueOf(all.size()), "C4B5FD"});
        cards.add(new String[] {LanguageManager.translate("randomrun.results.card.runs"), totalRuns + "  §a" + totalWins + "§7/§c" + (totalRuns - totalWins), "FFFFFF"});
        int rate = totalRuns > 0 ? Math.round(totalWins * 100f / totalRuns) : 0;
        cards.add(new String[] {LanguageManager.translate("randomrun.results.card.winrate"), rate + "%", rate >= 50 ? "4ADE80" : rate >= 25 ? "FACC15" : "F87171"});
        cards.add(new String[] {LanguageManager.translate("randomrun.results.card.best"), bestTime > 0 ? RunDataManager.formatTime(bestTime) : "—", "FFD700"});
        cards.add(new String[] {LanguageManager.translate("randomrun.results.card.avg"), avgTime > 0 ? RunDataManager.formatTime(avgTime) : "—", "FACC15"});
        cards.add(new String[] {LanguageManager.translate("randomrun.results.card.time"), formatPlaytime(totalTime), "67E8F9"});
        int available = width - MARGIN * 2;
        int count = cards.size();
        while (count > 3 && (available - (count - 1) * 6) / count < 78) count--;
        int cardW = (available - (count - 1) * 6) / count;
        int y = 38;
        for (int i = 0; i < count; i++) {
            int x = MARGIN + i * (cardW + 6);
            String[] card = cards.get(i);
            int color = Integer.parseInt(card[2], 16);
            Ui.card(context, x, y, x + cardW, y + 30, color, fade);
            GuiText.text(context, font, Ui.ellipsize(font, card[0], cardW - 10), x + 6, y + 5, Ui.argb(0xA1A1AA, fade), false);
            GuiText.text(context, font, Component.literal(card[1]), x + 6, y + 17, Ui.argb(color, fade), true);
        }
    }

    private static String formatPlaytime(long millis) {
        long minutes = millis / 60000;
        if (minutes < 60) return minutes + LanguageManager.translate("randomrun.results.unit.min");
        return (minutes / 60) + LanguageManager.translate("randomrun.results.unit.hour") + " " + (minutes % 60) + LanguageManager.translate("randomrun.results.unit.min");
    }

    private void renderToolbar(GuiGraphicsExtractor context, Theme theme, int mouseX, int mouseY, float fade) {
        int y = toolbarY();
        int right = width - MARGIN;
        String[] sorts = {LanguageManager.translate("randomrun.sort.time"), LanguageManager.translate("randomrun.sort.name"), LanguageManager.translate("randomrun.sort.attempts")};
        int x = right;
        for (int i = sorts.length - 1; i >= 0; i--) {
            int w = font.width(sorts[i]) + 14;
            x -= w;
            final Sort value = Sort.values()[i];
            segment(context, theme, sorts[i], x, y, w, sort == value, mouseX, mouseY, fade, () -> { sort = value; applySort(); });
        }
        GuiText.text(context, font, LanguageManager.text("randomrun.results.sort_by"), x - font.width(LanguageManager.translate("randomrun.results.sort_by")) - 6, y + 5, Ui.argb(0x8A8A93, fade), false);
        int modeX = searchField.getX() + searchField.getWidth() + 10;
        String classic = LanguageManager.translate("randomrun.results.mode.classic");
        int w1 = font.width(classic) + 14;
        segment(context, theme, classic, modeX, y, w1, !upgraderMode, mouseX, mouseY, fade, () -> { upgraderMode = false; rebuildData(); });
        int w2 = font.width("Upgrader") + 14;
        segment(context, theme, "Upgrader", modeX + w1, y, w2, upgraderMode, mouseX, mouseY, fade, () -> { upgraderMode = true; rebuildData(); });
    }

    private void segment(GuiGraphicsExtractor context, Theme theme, String label, int x, int y, int w, boolean active, int mouseX, int mouseY, float fade, Runnable action) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 18);
        context.fill(x, y, x + w, y + 18, Ui.argb(active ? theme.buttonHover : hover ? theme.buttonBase : 0x000000, (active ? 0.95f : hover ? 0.85f : 0.45f) * fade));
        Ui.border(context, x, y, x + w, y + 18, 1, Ui.argb(active ? theme.accent : theme.buttonBase, fade));
        GuiText.centeredText(context, font, label, x + w / 2, y + 5, Ui.argb(active ? 0xFFFFFF : 0xC4C4CC, fade));
        segments.add(new Segment(x, y, w, 18, action));
    }

    private void renderList(GuiGraphicsExtractor context, Theme theme, int mouseX, int mouseY, float fade) {
        int top = listTop(), bottom = listBottom();
        int left = MARGIN, right = width - MARGIN;
        context.fill(left, top, right, bottom, Ui.argb(0x000000, 0.45f * fade));
        Ui.border(context, left, top, right, bottom, 1, Ui.argb(theme.border, 0.6f * fade));
        if (loading) {
            Ui.spinner(context, width / 2, (top + bottom) / 2 - 6, 6, theme.accent);
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.results.loading"), width / 2, (top + bottom) / 2 + 8, Ui.argb(0xA1A1AA, fade));
            return;
        }
        if (filtered.isEmpty()) {
            GuiText.centeredText(context, font, LanguageManager.text(all.isEmpty() ? "randomrun.results.empty" : "randomrun.results.no_match"), width / 2, (top + bottom) / 2 - 4, Ui.argb(0x8A8A93, fade));
            return;
        }
        double max = Math.max(0, filtered.size() * ROW_HEIGHT - (bottom - top - 4));
        targetScroll = Math.clamp(targetScroll, 0, max);
        scroll += (targetScroll - scroll) * 0.35;
        if (Math.abs(targetScroll - scroll) < 0.5) scroll = targetScroll;
        context.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        int first = Math.max(0, (int) (scroll / ROW_HEIGHT));
        int last = Math.min(filtered.size() - 1, (int) ((scroll + bottom - top) / ROW_HEIGHT) + 1);
        for (int i = first; i <= last; i++) {
            int y = top + 2 + i * ROW_HEIGHT - (int) scroll;
            renderRow(context, theme, filtered.get(i), i, left + 2, y, right - 2 - (max > 0 ? 6 : 0), mouseX, mouseY, top, bottom, fade);
        }
        context.disableScissor();
        if (max > 0) {
            int trackH = bottom - top - 4;
            int thumbH = Math.max(16, (int) (trackH * (trackH / (double) (filtered.size() * ROW_HEIGHT))));
            int thumbY = top + 2 + (int) ((trackH - thumbH) * (scroll / max));
            context.fill(right - 6, top + 2, right - 3, bottom - 2, Ui.argb(0xFFFFFF, 0.06f * fade));
            context.fill(right - 6, thumbY, right - 3, thumbY + thumbH, Ui.argb(theme.accent, fade));
        }
    }

    private void renderRow(GuiGraphicsExtractor context, Theme theme, Summary summary, int index, int left, int y, int right, int mouseX, int mouseY, int clipTop, int clipBottom, float fade) {
        boolean hover = Ui.inside(mouseX, mouseY, left, y, right - left, ROW_HEIGHT - 2) && mouseY >= clipTop && mouseY < clipBottom;
        context.fill(left, y, right, y + ROW_HEIGHT - 2, Ui.argb(hover ? theme.buttonBase : 0xFFFFFF, hover ? 0.55f * fade : (index % 2 == 0 ? 0.03f : 0.0f) * fade));
        if (hover) context.fill(left, y, left + 2, y + ROW_HEIGHT - 2, Ui.argb(theme.accent, fade));
        GuiText.text(context, font, "#" + (index + 1), left + 6, y + 8, Ui.argb(0x71717A, fade), false);
        if (!summary.stack().isEmpty()) context.item(summary.stack(), left + 30, y + 4);
        int textX = left + 52;
        int statsWidth = Math.min(170, (right - left) / 3);
        GuiText.text(context, font, Ui.ellipsize(font, summary.name(), right - textX - statsWidth - 10), textX, y + 3, Ui.argb(0xFFFFFF, fade), false);
        if (summary.hasBest()) {
            com.randomrun.ui.util.Icons.CLOCK.draw(context, textX, y + 13, Ui.argb(0x4ADE80, fade));
            GuiText.text(context, font, RunDataManager.formatTime(summary.bestTime()), textX + 10, y + 13, Ui.argb(0x4ADE80, fade), false);
        }
        else GuiText.text(context, font, LanguageManager.text("randomrun.results.no_wins"), textX, y + 13, Ui.argb(0xF87171, fade), false);

        int barRight = right - 16;
        int barLeft = barRight - statsWidth + 10;
        String info = LanguageManager.translate("randomrun.results.row", summary.attempts(), summary.winRate());
        GuiText.text(context, font, info, barRight - font.width(info), y + 3, Ui.argb(0xC4C4CC, fade), false);
        context.fill(barLeft, y + 15, barRight, y + 18, Ui.argb(0xF87171, 0.45f * fade));
        int fill = (int) ((barRight - barLeft) * (summary.attempts() > 0 ? summary.wins() / (float) summary.attempts() : 0));
        if (fill > 0) context.fill(barLeft, y + 15, barLeft + fill, y + 18, Ui.argb(0x4ADE80, fade));
        com.randomrun.ui.util.Icons.CHEVRON_RIGHT.drawCentered(context, right - 10, y, 4, ROW_HEIGHT - 2, Ui.argb(hover ? 0xFFFFFF : 0x71717A, fade));
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        for (Segment segment : List.copyOf(segments)) {
            if (Ui.inside(event.x(), event.y(), segment.x(), segment.y(), segment.w(), segment.h())) {
                Ui.click();
                segment.action().run();
                return true;
            }
        }
        if (super.mouseClicked(event, doubleClick)) return true;
        int top = listTop(), bottom = listBottom();
        if (!loading && event.y() >= top && event.y() < bottom && event.x() >= MARGIN && event.x() < width - MARGIN) {
            int index = (int) ((event.y() - top - 2 + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < filtered.size()) {
                Summary summary = filtered.get(index);
                Ui.click();
                minecraft.gui.setScreen(new RunHistoryScreen(this, new RunDataManager.RunResult(summary.targetId(), summary.bestTime()), upgraderMode));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        targetScroll -= verticalAmount * ROW_HEIGHT * 2;
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent instanceof MainModScreen ? parent : new MainModScreen(null));
    }
}
