package com.randomrun.ui.screen.main;

import com.randomrun.features.leaderboard.LeaderboardEntry;
import com.randomrun.features.leaderboard.LeaderboardService;
import com.randomrun.features.speedrun.ItemDifficulty;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.ui.util.GuiQuadBatch;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Previews.Heads;
import com.randomrun.ui.util.Previews.ItemPreviewRenderer;
import com.randomrun.ui.util.Previews.PreviewItems;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.GlobalParticleSystem;
import com.randomrun.ui.widget.styled.Buttons.ButtonYellow;
import com.randomrun.ui.widget.styled.TextFieldStyled;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class LeaderboardScreen extends AbstractRandomRunScreen {
    private static final int PAD = 12;
    private static final int TOP = 36;
    private static final int CELL = 20;
    private static final int ROW = 24;
    private static final int TOP_LIMIT = 10;
    private static final long SEARCH_DEBOUNCE_MS = 200L;
    private static final long GLOW_CYCLE_MS = 5000L;
    private static final int GLOW_LENGTH = 40;

    private static final int GOLD = 0xFFD700;
    private static final int GOLD_TEXT = 0xFFAA00;
    private static final int GOLD_BORDER = 0xDAA520;
    private static final int PANEL_BG = 0x1A1305;
    private static final int CELL_BG = 0x3D2F06;
    private static final int CELL_HOVER = 0x5D4F16;
    private static final int SILVER = 0xC0C0C0;
    private static final int BRONZE = 0xCD7F32;
    private static final int MUTED = 0x9A8F70;
    private static final int TEXT = 0xF5EED8;
    private static final int OWN = 0x4ADE80;

    private enum View { MINE, PLAYERS, ITEM }
    private enum Filter { RECORDS, ALL }

    private record Mine(String itemId, long time, int rank, long gap) {}
    private record PlayerStat(String name, int wins, int podiums, int entries) {}
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private static View lastView = View.MINE;
    private static Filter lastFilter = Filter.RECORDS;
    private static String lastSelected;

    private final Screen parent;
    private final List<String> allTargets = new ArrayList<>();
    private final Map<String, ItemStack> stacks = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final List<Hit> hits = new ArrayList<>();

    private Map<String, List<LeaderboardEntry>> boards = new HashMap<>();
    private List<String> gridItems = new ArrayList<>();
    private List<Mine> mine = new ArrayList<>();
    private List<PlayerStat> players = new ArrayList<>();
    private int totalRecords;

    private View view = lastView;
    private Filter filter = lastFilter;
    private String selected = lastSelected;
    private boolean loading;
    private boolean loaded;
    private String error;

    private TextFieldStyled search;
    private String query = "";
    private String pendingQuery = "";
    private long queryChangedAt;

    private float gridScroll, gridScrollTarget;
    private float listScroll, listScrollTarget;
    private int dragging;
    private int lastMouseX, lastMouseY;
    private double dragOffset;
    private long viewChangedAt = System.currentTimeMillis();

    public LeaderboardScreen(Screen parent) {
        super(LanguageManager.text("randomrun.leaderboard.title"));
        this.parent = parent;
        boolean unobtainable = RandomRunMod.getInstance().getConfig().isAllowUnobtainableItems();
        for (Item item : ItemDifficulty.getAllItems(unobtainable)) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) allTargets.add(id.toString());
        }
    }

    @Override
    protected void init() {
        super.init();
        GlobalParticleSystem.getInstance().setYellowMode(true);
        int leftRight = leftRight();
        search = new TextFieldStyled(font, PAD + 8, TOP + 22, leftRight - PAD - 16, 16, LanguageManager.text("randomrun.search"), 0f);
        search.setColors(CELL_BG, GOLD_TEXT);
        search.setBorderColor(GOLD_BORDER);
        search.setCenteredPlaceholder(LanguageManager.text("randomrun.leaderboard.search_placeholder").withStyle(style -> style.withColor(MUTED)));
        search.setValue(query);
        search.setResponder(text -> {
            pendingQuery = text;
            queryChangedAt = System.currentTimeMillis();
        });
        addRenderableWidget(search);
        addRenderableWidget(new ButtonYellow(width - PAD - 90, 10, 90, 18, LanguageManager.text("randomrun.leaderboard.refresh"), button -> reload()));
        addRenderableWidget(new ButtonYellow(width / 2 - 100, height - 30, 200, 20, LanguageManager.text("randomrun.button.back"), button -> onClose()));
        if (!loaded && !loading) load(false);
        else rebuildGrid();
    }

    private int leftRight() {
        return PAD + Math.max(180, Math.min(250, (int) (width * 0.36f)));
    }

    private int panelBottom() {
        return height - 36;
    }

    private void reload() {
        LeaderboardService.getInstance().clearCache();
        load(true);
    }

    private void load(boolean force) {
        loading = true;
        error = null;
        LeaderboardService.getInstance().loadAllLeaderboards().whenComplete((result, failure) -> minecraft.execute(() -> {
            loading = false;
            loaded = true;
            if (failure != null || result == null) {
                error = LanguageManager.translate("randomrun.leaderboard.error");
                return;
            }
            Map<String, List<LeaderboardEntry>> sorted = new HashMap<>();
            result.forEach((id, list) -> {
                List<LeaderboardEntry> copy = new ArrayList<>(list);
                copy.sort(Comparator.comparingLong(LeaderboardEntry::getCompletionTime));
                if (!copy.isEmpty() && !stack(id).isEmpty()) sorted.put(id, copy);
            });
            boards = sorted;
            computeStats();
            rebuildGrid();
            if (force) viewChangedAt = System.currentTimeMillis();
        }));
    }

    private String me() {
        return BackendSession.playerName();
    }

    private void computeStats() {
        String me = me();
        mine = new ArrayList<>();
        Map<String, int[]> stats = new LinkedHashMap<>();
        totalRecords = 0;
        for (Map.Entry<String, List<LeaderboardEntry>> board : boards.entrySet()) {
            List<LeaderboardEntry> list = board.getValue();
            totalRecords += list.size();
            long best = list.getFirst().getCompletionTime();
            for (int i = 0; i < list.size(); i++) {
                LeaderboardEntry entry = list.get(i);
                int[] stat = stats.computeIfAbsent(entry.getPlayerName(), name -> new int[3]);
                if (i == 0) stat[0]++;
                if (i < 3) stat[1]++;
                stat[2]++;
                if (entry.getPlayerName() != null && entry.getPlayerName().equals(me) && mine.stream().noneMatch(m -> m.itemId().equals(board.getKey()))) {
                    mine.add(new Mine(board.getKey(), entry.getCompletionTime(), i + 1, entry.getCompletionTime() - best));
                }
            }
        }
        mine.sort(Comparator.comparingInt(Mine::rank).thenComparingLong(Mine::time));
        players = new ArrayList<>();
        stats.forEach((name, stat) -> { if (name != null) players.add(new PlayerStat(name, stat[0], stat[1], stat[2])); });
        players.sort(Comparator.comparingInt(PlayerStat::wins).reversed().thenComparing(Comparator.comparingInt(PlayerStat::podiums).reversed()).thenComparing(Comparator.comparingInt(PlayerStat::entries).reversed()));
    }

    private void rebuildGrid() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<String> source = filter == Filter.RECORDS ? new ArrayList<>(boards.keySet()) : allTargets;
        List<String> result = new ArrayList<>();
        for (String id : source) {
            if (q.isEmpty() || id.toLowerCase(Locale.ROOT).contains(q) || name(id).toLowerCase(Locale.ROOT).contains(q)) result.add(id);
        }
        if (filter == Filter.RECORDS) result.sort(Comparator.comparingInt((String id) -> boards.get(id).size()).reversed().thenComparing(this::name));
        gridItems = result;
        gridScrollTarget = Math.min(gridScrollTarget, maxGridScroll());
    }

    private ItemStack stack(String id) {
        return stacks.computeIfAbsent(id, key -> {
            Identifier identifier = Identifier.tryParse(key);
            return identifier == null ? ItemStack.EMPTY : PreviewItems.stack(BuiltInRegistries.ITEM.getValue(identifier));
        });
    }

    private String name(String id) {
        return names.computeIfAbsent(id, key -> {
            ItemStack stack = stack(key);
            return stack.isEmpty() ? key : stack.getHoverName().getString();
        });
    }

    private static String time(long millis) {
        long hours = millis / 3_600_000L, minutes = millis / 60_000L % 60, seconds = millis / 1000 % 60, ms = millis % 1000;
        return hours > 0 ? String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, ms) : String.format("%d:%02d.%03d", minutes, seconds, ms);
    }

    private static String date(long timestamp) {
        return timestamp <= 0 ? "" : new SimpleDateFormat("dd.MM.yy").format(new Date(timestamp));
    }

    private static int rankColor(int rank) {
        return switch (rank) { case 1 -> GOLD; case 2 -> SILVER; case 3 -> BRONZE; default -> MUTED; };
    }

    private int gridColumns() {
        return Math.max(1, (leftRight() - PAD - 22) / CELL);
    }

    private int gridX() {
        int left = PAD, right = leftRight();
        int used = gridColumns() * CELL - 2;
        return left + 4 + Math.max(0, (right - 10 - left - 4 - used) / 2);
    }

    private int gridTop() {
        return TOP + 62;
    }

    private int gridBottom() {
        return panelBottom() - 16;
    }

    private float maxGridScroll() {
        int rows = (gridItems.size() + gridColumns() - 1) / gridColumns();
        return Math.max(0, rows * CELL - (gridBottom() - gridTop()));
    }

    private int listTop() {
        return TOP + (view == View.ITEM ? 70 : 44);
    }

    private int listBottom() {
        return panelBottom() - (view == View.ITEM ? 30 : 8);
    }

    private int listCount() {
        return switch (view) {
            case MINE -> mine.size();
            case PLAYERS -> players.size();
            case ITEM -> selected == null || !boards.containsKey(selected) ? 0 : Math.min(TOP_LIMIT, boards.get(selected).size());
        };
    }

    private float maxListScroll() {
        return Math.max(0, listCount() * ROW - (listBottom() - listTop()));
    }

    private void open(View value, String item) {
        view = value;
        lastView = value;
        if (item != null) {
            selected = item;
            lastSelected = item;
        }
        listScroll = listScrollTarget = 0;
        viewChangedAt = System.currentTimeMillis();
        Ui.click();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, width, height / 2, 0xFF141000, 0xFF886600);
        context.fillGradient(0, height / 2, width, height, 0xFF886600, 0xFF332200);
        GlobalParticleSystem particles = GlobalParticleSystem.getInstance();
        particles.update(delta);
        particles.extractRenderState(context);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (!pendingQuery.equals(query) && System.currentTimeMillis() - queryChangedAt >= SEARCH_DEBOUNCE_MS) {
            query = pendingQuery;
            gridScrollTarget = 0;
            rebuildGrid();
        }
        gridScroll += (gridScrollTarget - gridScroll) * 0.35f;
        listScroll += (listScrollTarget - listScroll) * 0.35f;
        hits.clear();

        renderHeader(context);
        int leftRight = leftRight();
        int rightLeft = leftRight + PAD;
        panel(context, PAD, TOP, leftRight, panelBottom());
        panel(context, rightLeft, TOP, width - PAD, panelBottom());
        super.extractRenderState(context, mouseX, mouseY, delta);
        renderLeft(context, mouseX, mouseY, leftRight);
        renderRight(context, mouseX, mouseY, rightLeft, width - PAD);
    }

    private void panel(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        Ui.shadow(context, left, top, right, bottom, 1f);
        context.fill(left, top, right, bottom, 0xE0000000 | PANEL_BG);
        Ui.border(context, left, top, right, bottom, 2, 0xFF000000 | GOLD_BORDER);
        renderGlow(context, left, top, right, bottom);
    }

    private void renderGlow(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        if (!RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) return;
        int w = right - left, h = bottom - top, perimeter = 2 * (w + h);
        float head = (System.currentTimeMillis() % GLOW_CYCLE_MS) / (float) GLOW_CYCLE_MS * perimeter;
        GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(GLOW_LENGTH * 2 + 2);
        for (int i = -GLOW_LENGTH; i <= GLOW_LENGTH; i++) {
            int pos = Math.floorMod((int) head + i, perimeter);
            float intensity = 1f - Math.abs(i) / (float) GLOW_LENGTH;
            int color = 0xFF000000 | Ui.lerp(0xFF000000 | GOLD_TEXT, 0xFFFFFFFF, intensity) & 0xFFFFFF;
            if (pos < w) batch.fill(left + pos, top, left + pos + 1, top + 2, color);
            else if (pos < w + h) { int p = pos - w; batch.fill(right - 2, top + p, right, top + p + 1, color); }
            else if (pos < 2 * w + h) { int p = pos - w - h; batch.fill(right - p - 1, bottom - 2, right - p, bottom, color); }
            else { int p = pos - 2 * w - h; batch.fill(left, bottom - p - 1, left + 2, bottom - p, color); }
        }
        batch.build().extract(context);
    }

    private void renderHeader(GuiGraphicsExtractor context) {
        Ui.centeredScaled(context, font, LanguageManager.text("randomrun.leaderboard.title"), PAD + font.width(LanguageManager.text("randomrun.leaderboard.title")) * 0.75f, 13, 1.5f, 0xFF000000 | GOLD);
        int x = PAD + Math.round(font.width(LanguageManager.text("randomrun.leaderboard.title")) * 1.5f) + 12;
        if (!loaded) return;
        String[] chips = {
            LanguageManager.translate("randomrun.leaderboard.stat.items", boards.size()),
            LanguageManager.translate("randomrun.leaderboard.stat.records", totalRecords),
            LanguageManager.translate("randomrun.leaderboard.stat.players", players.size())
        };
        for (String chip : chips) {
            int w = font.width(chip) + 10;
            if (x + w > width - PAD - 100) break;
            context.fill(x, 12, x + w, 26, 0x90000000);
            Ui.border(context, x, 12, x + w, 26, 1, 0x60000000 | GOLD_BORDER);
            GuiText.text(context, font, chip, x + 5, 15, 0xFF000000 | TEXT, false);
            x += w + 4;
        }
    }

    private void renderLeft(GuiGraphicsExtractor context, int mouseX, int mouseY, int right) {
        int left = PAD;
        GuiText.text(context, font, LanguageManager.text("randomrun.leaderboard.items_panel"), left + 8, TOP + 8, 0xFF000000 | GOLD, true);
        String count = String.valueOf(gridItems.size());
        GuiText.text(context, font, count, right - 8 - font.width(count), TOP + 8, 0xFF000000 | MUTED, false);

        int segY = TOP + 42, segW = (right - left - 16) / 2;
        segment(context, left + 8, segY, segW, LanguageManager.translate("randomrun.leaderboard.filter.records"), filter == Filter.RECORDS, mouseX, mouseY, () -> setFilter(Filter.RECORDS));
        segment(context, left + 8 + segW, segY, right - left - 16 - segW, LanguageManager.translate("randomrun.leaderboard.filter.all"), filter == Filter.ALL, mouseX, mouseY, () -> setFilter(Filter.ALL));

        int top = gridTop(), bottom = gridBottom();
        int columns = gridColumns();
        int gridX = gridX();
        String hovered = null;
        context.enableScissor(left + 2, top, right - 2, bottom);
        for (int i = 0; i < gridItems.size(); i++) {
            int col = i % columns, row = i / columns;
            int x = gridX + col * CELL, y = top + row * CELL - Math.round(gridScroll);
            if (y + CELL < top || y > bottom) continue;
            String id = gridItems.get(i);
            boolean records = boards.containsKey(id);
            boolean hover = Ui.inside(mouseX, mouseY, x, y, CELL - 2, CELL - 2) && mouseY >= top && mouseY < bottom;
            boolean active = id.equals(selected) && view == View.ITEM;
            context.fill(x, y, x + CELL - 2, y + CELL - 2, 0xFF000000 | (hover ? CELL_HOVER : CELL_BG));
            if (active) Ui.border(context, x, y, x + CELL - 2, y + CELL - 2, 1, 0xFF000000 | GOLD);
            context.item(stack(id), x + 1, y + 1);
            if (!records && filter == Filter.ALL) context.fill(x, y, x + CELL - 2, y + CELL - 2, 0x99000000);
            if (records) {
                boolean own = mine.stream().anyMatch(m -> m.itemId().equals(id));
                context.fill(x + CELL - 6, y + 1, x + CELL - 3, y + 4, 0xFF000000 | (own ? OWN : GOLD));
            }
            if (hover) hovered = id;
            if (y >= top - CELL / 2 && y + CELL / 2 <= bottom) hits.add(new Hit(x, Math.max(y, top), CELL - 2, Math.min(y + CELL - 2, bottom) - Math.max(y, top), () -> open(View.ITEM, id)));
        }
        context.disableScissor();
        if (gridItems.isEmpty()) {
            Component empty = LanguageManager.text(loading ? "randomrun.leaderboard.loading" : filter == Filter.RECORDS && query.isBlank() ? "randomrun.leaderboard.no_records" : "randomrun.leaderboard.nothing_found");
            GuiText.centeredText(context, font, empty, (left + right) / 2, (top + bottom) / 2, 0xFF000000 | MUTED);
        }
        float max = maxGridScroll();
        if (max > 0) {
            int trackH = bottom - top;
            int thumbH = Math.max(16, (int) (trackH * trackH / (trackH + max)));
            int thumbY = top + (int) ((trackH - thumbH) * (gridScroll / max));
            thumb(context, 1, right, top, bottom, thumbY, thumbH, mouseX, mouseY);
        }
        String a = LanguageManager.translate("randomrun.leaderboard.legend.records"), b = LanguageManager.translate("randomrun.leaderboard.legend.mine");
        int total = 6 + font.width(a) + 12 + 6 + font.width(b);
        int lx = (left + right) / 2 - total / 2, ly = bottom + 4;
        context.fill(lx, ly + 2, lx + 4, ly + 6, 0xFF000000 | GOLD);
        GuiText.text(context, font, a, lx + 6, ly, 0xFF000000 | MUTED, false);
        lx += 6 + font.width(a) + 12;
        context.fill(lx, ly + 2, lx + 4, ly + 6, 0xFF000000 | OWN);
        GuiText.text(context, font, b, lx + 6, ly, 0xFF000000 | MUTED, false);
        if (hovered != null) itemTooltip(context, hovered, mouseX, mouseY);
    }

    private void itemTooltip(GuiGraphicsExtractor context, String id, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name(id)).withStyle(style -> style.withColor(GOLD)));
        List<LeaderboardEntry> board = boards.get(id);
        if (board == null) lines.add(LanguageManager.text("randomrun.leaderboard.no_records").withStyle(style -> style.withColor(MUTED)));
        else {
            lines.add(LanguageManager.text("randomrun.leaderboard.tooltip.best", time(board.getFirst().getCompletionTime()), board.getFirst().getPlayerName()).withStyle(style -> style.withColor(TEXT)));
            lines.add(LanguageManager.text("randomrun.leaderboard.tooltip.count", board.size()).withStyle(style -> style.withColor(MUTED)));
        }
        context.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    private void setFilter(Filter value) {
        if (filter == value) return;
        filter = value;
        lastFilter = value;
        gridScroll = gridScrollTarget = 0;
        rebuildGrid();
        Ui.click();
    }

    private void segment(GuiGraphicsExtractor context, int x, int y, int w, String label, boolean active, int mouseX, int mouseY, Runnable action) {
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 14);
        context.fill(x, y, x + w, y + 14, 0xFF000000 | (active ? 0x6B5410 : hover ? CELL_HOVER : CELL_BG));
        if (active) context.fill(x, y + 13, x + w, y + 14, 0xFF000000 | GOLD);
        GuiText.centeredText(context, font, Ui.ellipsize(font, label, w - 6), x + w / 2, y + 3, 0xFF000000 | (active ? 0xFFFFFF : MUTED));
        hits.add(new Hit(x, y, w, 14, action));
    }

    private void renderRight(GuiGraphicsExtractor context, int mouseX, int mouseY, int left, int right) {
        int tabY = TOP + 8;
        int x = left + 8;
        x += tab(context, x, tabY, LanguageManager.translate("randomrun.leaderboard.tab.mine"), view == View.MINE, mouseX, mouseY, () -> open(View.MINE, null));
        x += tab(context, x, tabY, LanguageManager.translate("randomrun.leaderboard.tab.players"), view == View.PLAYERS, mouseX, mouseY, () -> open(View.PLAYERS, null));
        if (selected != null) tab(context, x, tabY, Ui.ellipsize(font, name(selected), right - x - 30), view == View.ITEM, mouseX, mouseY, () -> open(View.ITEM, null));
        context.fill(left + 8, tabY + 16, right - 8, tabY + 17, 0x80000000 | GOLD_BORDER);

        if (loading && !loaded) {
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.loading"), (left + right) / 2, (TOP + panelBottom()) / 2, 0xFF000000 | MUTED);
            return;
        }
        if (error != null) {
            GuiText.centeredText(context, font, Component.literal(error), (left + right) / 2, (TOP + panelBottom()) / 2, 0xFFFF5555);
            return;
        }
        switch (view) {
            case MINE -> renderMine(context, mouseX, mouseY, left, right);
            case PLAYERS -> renderPlayers(context, mouseX, mouseY, left, right);
            case ITEM -> renderItem(context, mouseX, mouseY, left, right);
        }
    }

    private int tab(GuiGraphicsExtractor context, int x, int y, String label, boolean active, int mouseX, int mouseY, Runnable action) {
        int w = font.width(label) + 14;
        boolean hover = Ui.inside(mouseX, mouseY, x, y, w, 16);
        if (active || hover) context.fill(x, y, x + w, y + 16, 0xFF000000 | (active ? 0x6B5410 : CELL_BG));
        if (active) context.fill(x, y + 14, x + w, y + 16, 0xFF000000 | GOLD);
        GuiText.text(context, font, label, x + 7, y + 4, 0xFF000000 | (active ? 0xFFFFFF : hover ? TEXT : MUTED), false);
        hits.add(new Hit(x, y, w, 16, action));
        return w + 2;
    }

    private float rowAppear(int index) {
        return Ui.easeOut((System.currentTimeMillis() - viewChangedAt - Math.min(index, 12) * 35L) / 260f);
    }

    private void beginList(GuiGraphicsExtractor context, int left, int right) {
        context.enableScissor(left + 2, listTop(), right - 2, listBottom());
    }

    private void endList(GuiGraphicsExtractor context, int right) {
        context.disableScissor();
        float max = maxListScroll();
        if (max <= 0) return;
        int top = listTop(), bottom = listBottom(), trackH = bottom - top;
        int thumbH = Math.max(16, (int) (trackH * trackH / (trackH + max)));
        int thumbY = top + (int) ((trackH - thumbH) * (listScroll / max));
        thumb(context, 2, right, top, bottom, thumbY, thumbH, lastMouseX, lastMouseY);
    }

    private void thumb(GuiGraphicsExtractor context, int which, int right, int top, int bottom, int thumbY, int thumbH, int mouseX, int mouseY) {
        boolean active = dragging == which || Ui.inside(mouseX, mouseY, right - 11, top, 10, bottom - top);
        context.fill(right - 7, top, right - 4, bottom, 0x40000000);
        int x0 = active ? right - 8 : right - 7, x1 = active ? right - 3 : right - 4;
        context.fill(x0, thumbY, x1, thumbY + thumbH, 0xFF000000 | (active ? GOLD : GOLD_BORDER));
    }

    private void rowFrame(GuiGraphicsExtractor context, int left, int y, int right, boolean hover, boolean own, float fade) {
        context.fill(left, y, right, y + ROW - 3, Ui.argb(hover ? CELL_HOVER : 0x000000, (hover ? 0.85f : 0.4f) * fade));
        if (own) Ui.border(context, left, y, right, y + ROW - 3, 1, Ui.argb(OWN, 0.8f * fade));
        else if (hover) Ui.border(context, left, y, right, y + ROW - 3, 1, Ui.argb(GOLD_BORDER, fade));
    }

    private void rankBadge(GuiGraphicsExtractor context, int x, int y, int rank, float fade) {
        String text = String.valueOf(rank);
        int color = rankColor(rank);
        int width = Math.max(16, font.width(text) + 8);
        if (rank <= 3) {
            context.fill(x, y, x + width, y + 14, Ui.argb(Ui.lerp(0xFF000000 | color, 0xFF000000, 0.35f), fade));
            context.fill(x + 1, y + 1, x + width - 1, y + 13, Ui.argb(color, fade));
            context.fill(x + 1, y + 1, x + width - 1, y + 2, Ui.argb(Ui.lerp(0xFF000000 | color, 0xFFFFFFFF, 0.45f), fade));
            GuiText.text(context, font, text, x + (width - font.width(text) + 1) / 2, y + 3, Ui.argb(0x1A1305, fade), false);
        } else {
            String label = rank < 100 ? "#" + text : text;
            int w = font.width(label);
            if (w <= 18) {
                GuiText.text(context, font, label, x + (16 - w + 1) / 2, y + 3, Ui.argb(color, fade), false);
            } else {
                float scale = 18f / w;
                context.pose().pushMatrix();
                context.pose().translate(x - 1, y + 3 + (8 - 8 * scale) / 2f);
                context.pose().scale(scale, scale);
                GuiText.text(context, font, label, 0, 0, Ui.argb(color, fade), false);
                context.pose().popMatrix();
            }
        }
    }

    private void renderMine(GuiGraphicsExtractor context, int mouseX, int mouseY, int left, int right) {
        int top = TOP + 30;
        long wins = mine.stream().filter(m -> m.rank() == 1).count();
        long podiums = mine.stream().filter(m -> m.rank() <= 3).count();
        GuiText.text(context, font, LanguageManager.text("randomrun.leaderboard.mine.summary", wins, podiums, mine.size()), left + 10, top, 0xFF000000 | MUTED, false);
        if (mine.isEmpty()) {
            int cy = (listTop() + listBottom()) / 2;
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.no_personal_records"), (left + right) / 2, cy - 6, 0xFF000000 | TEXT);
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.mine.hint"), (left + right) / 2, cy + 6, 0xFF000000 | MUTED);
            return;
        }
        beginList(context, left, right);
        int rowLeft = left + 8, rowRight = right - 12;
        for (int i = 0; i < mine.size(); i++) {
            int y = listTop() + i * ROW - Math.round(listScroll);
            if (y + ROW < listTop() || y > listBottom()) continue;
            float fade = rowAppear(i);
            if (fade <= 0) continue;
            Mine entry = mine.get(i);
            boolean hover = Ui.inside(mouseX, mouseY, rowLeft, y, rowRight - rowLeft, ROW - 3) && mouseY >= listTop() && mouseY < listBottom();
            context.pose().pushMatrix();
            context.pose().translate((1f - fade) * 14f, 0);
            rowFrame(context, rowLeft, y, rowRight, hover, false, fade);
            rankBadge(context, rowLeft + 4, y + 3, entry.rank(), fade);
            context.item(stack(entry.itemId()), rowLeft + 26, y + 2);
            GuiText.text(context, font, Ui.ellipsize(font, name(entry.itemId()), rowRight - rowLeft - 170), rowLeft + 46, y + 7, Ui.argb(TEXT, fade), false);
            String t = time(entry.time());
            GuiText.text(context, font, t, rowRight - 8 - font.width(t), y + 7, Ui.argb(GOLD_TEXT, fade), true);
            if (entry.gap() > 0) {
                String gap = "+" + time(entry.gap());
                GuiText.text(context, font, gap, rowRight - 16 - font.width(t) - font.width(gap), y + 7, Ui.argb(MUTED, fade), false);
            }
            context.pose().popMatrix();
            int hy = Math.max(y, listTop()), hb = Math.min(y + ROW - 3, listBottom());
            if (hb > hy) hits.add(new Hit(rowLeft, hy, rowRight - rowLeft, hb - hy, () -> open(View.ITEM, entry.itemId())));
        }
        endList(context, right);
    }

    private void renderPlayers(GuiGraphicsExtractor context, int mouseX, int mouseY, int left, int right) {
        int top = TOP + 30;
        int rowLeft = left + 8, rowRight = right - 12;
        int colWins = rowRight - 150, colPodium = rowRight - 92, colEntries = rowRight - 34;
        GuiText.text(context, font, LanguageManager.text("randomrun.leaderboard.player"), rowLeft + 46, top, 0xFF000000 | MUTED, false);
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.col.wins"), colWins, top, 0xFF000000 | GOLD);
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.col.podiums"), colPodium, top, 0xFF000000 | MUTED);
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.col.entries"), colEntries, top, 0xFF000000 | MUTED);
        if (players.isEmpty()) {
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.no_records"), (left + right) / 2, (listTop() + listBottom()) / 2, 0xFF000000 | MUTED);
            return;
        }
        String me = me();
        beginList(context, left, right);
        for (int i = 0; i < players.size(); i++) {
            int y = listTop() + i * ROW - Math.round(listScroll);
            if (y + ROW < listTop() || y > listBottom()) continue;
            float fade = rowAppear(i);
            if (fade <= 0) continue;
            PlayerStat stat = players.get(i);
            boolean own = stat.name().equals(me);
            context.pose().pushMatrix();
            context.pose().translate((1f - fade) * 14f, 0);
            rowFrame(context, rowLeft, y, rowRight, false, own, fade);
            rankBadge(context, rowLeft + 4, y + 3, i + 1, fade);
            Heads.draw(context, stat.name(), rowLeft + 26, y + 2, 16, 0);
            GuiText.text(context, font, Ui.ellipsize(font, stat.name(), colWins - rowLeft - 70), rowLeft + 46, y + 7, Ui.argb(own ? OWN : TEXT, fade), false);
            GuiText.centeredText(context, font, String.valueOf(stat.wins()), colWins, y + 7, Ui.argb(GOLD, fade));
            GuiText.centeredText(context, font, String.valueOf(stat.podiums()), colPodium, y + 7, Ui.argb(TEXT, fade));
            GuiText.centeredText(context, font, String.valueOf(stat.entries()), colEntries, y + 7, Ui.argb(MUTED, fade));
            context.pose().popMatrix();
        }
        endList(context, right);
    }

    private void renderItem(GuiGraphicsExtractor context, int mouseX, int mouseY, int left, int right) {
        if (selected == null) {
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.pick_item"), (left + right) / 2, (TOP + panelBottom()) / 2, 0xFF000000 | MUTED);
            return;
        }
        List<LeaderboardEntry> board = boards.getOrDefault(selected, List.of());
        int headerY = TOP + 26;
        context.fill(left + 8, headerY, left + 44, headerY + 36, 0xFF000000 | CELL_BG);
        Ui.border(context, left + 8, headerY, left + 44, headerY + 36, 1, 0xFF000000 | GOLD_BORDER);
        ItemPreviewRenderer.extractLargeGui(context, stack(selected), left + 26, headerY + 18, 28);
        GuiText.text(context, font, Ui.ellipsize(font, name(selected), right - left - 70), left + 52, headerY + 4, 0xFF000000 | GOLD, true);
        if (board.isEmpty()) {
            GuiText.text(context, font, LanguageManager.text("randomrun.leaderboard.no_records"), left + 52, headerY + 18, 0xFF000000 | MUTED, false);
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.leaderboard.be_first"), (left + right) / 2, (listTop() + listBottom()) / 2, 0xFF000000 | TEXT);
            return;
        }
        LeaderboardEntry best = board.getFirst();
        GuiText.text(context, font, LanguageManager.text("randomrun.leaderboard.item.record", time(best.getCompletionTime()), best.getPlayerName()), left + 52, headerY + 16, 0xFF000000 | TEXT, false);
        GuiText.text(context, font, LanguageManager.text("randomrun.leaderboard.item.count", board.size()), left + 52, headerY + 27, 0xFF000000 | MUTED, false);

        String me = me();
        int rowLeft = left + 8, rowRight = right - 12;
        int shown = Math.min(TOP_LIMIT, board.size());
        beginList(context, left, right);
        for (int i = 0; i < shown; i++) {
            int y = listTop() + i * ROW - Math.round(listScroll);
            if (y + ROW < listTop() || y > listBottom()) continue;
            float fade = rowAppear(i);
            if (fade <= 0) continue;
            LeaderboardEntry entry = board.get(i);
            boolean own = entry.getPlayerName() != null && entry.getPlayerName().equals(me);
            context.pose().pushMatrix();
            context.pose().translate((1f - fade) * 14f, 0);
            rowFrame(context, rowLeft, y, rowRight, false, own, fade);
            rankBadge(context, rowLeft + 4, y + 3, i + 1, fade);
            Heads.draw(context, entry.getPlayerName(), rowLeft + 26, y + 2, 16, 0);
            String t = time(entry.getCompletionTime());
            String day = date(entry.getTimestamp());
            int dateW = font.width(day);
            GuiText.text(context, font, Ui.ellipsize(font, entry.getPlayerName(), rowRight - rowLeft - 200), rowLeft + 46, y + 7, Ui.argb(own ? OWN : TEXT, fade), false);
            GuiText.text(context, font, day, rowRight - 8 - dateW, y + 7, Ui.argb(MUTED, fade), false);
            int timeRight = rowRight - 16 - dateW;
            GuiText.text(context, font, t, timeRight - font.width(t), y + 7, Ui.argb(i == 0 ? GOLD : GOLD_TEXT, fade), true);
            if (i > 0) {
                String gap = "+" + time(entry.getCompletionTime() - best.getCompletionTime());
                GuiText.text(context, font, gap, timeRight - font.width(t) - 6 - font.width(gap), y + 7, Ui.argb(MUTED, fade), false);
            }
            context.pose().popMatrix();
        }
        endList(context, right);

        int footY = panelBottom() - 22;
        int place = -1;
        for (int i = 0; i < board.size(); i++) if (board.get(i).getPlayerName() != null && board.get(i).getPlayerName().equals(me)) { place = i; break; }
        Component foot;
        if (place >= 0) foot = LanguageManager.text("randomrun.leaderboard.item.your_place", place + 1, time(board.get(place).getCompletionTime()));
        else foot = LanguageManager.text("randomrun.leaderboard.item.not_ranked");
        context.fill(rowLeft, footY, rowRight, footY + 16, 0x60000000);
        GuiText.centeredText(context, font, foot, (rowLeft + rowRight) / 2, footY + 4, 0xFF000000 | (place >= 0 ? OWN : MUTED));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            if (startDrag(1, event.x(), event.y()) || startDrag(2, event.x(), event.y())) return true;
            for (Hit hit : new ArrayList<>(hits)) {
                if (Ui.inside(event.x(), event.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
            if (search != null && !search.isMouseOver(event.x(), event.y())) search.setFocused(false);
        }
        return super.mouseClicked(event, doubleClick);
    }

    private int[] scrollbar(int which) {
        boolean grid = which == 1;
        float max = grid ? maxGridScroll() : maxListScroll();
        if (max <= 0) return null;
        int right = grid ? leftRight() : width - PAD;
        int top = grid ? gridTop() : listTop(), bottom = grid ? gridBottom() : listBottom(), trackH = bottom - top;
        int thumbH = Math.max(16, (int) (trackH * trackH / (trackH + max)));
        int thumbY = top + (int) ((trackH - thumbH) * ((grid ? gridScroll : listScroll) / max));
        return new int[]{right - 11, top, right - 1, bottom, thumbY, thumbH};
    }

    private boolean startDrag(int which, double mouseX, double mouseY) {
        int[] bar = scrollbar(which);
        if (bar == null || mouseX < bar[0] || mouseX >= bar[2] || mouseY < bar[1] || mouseY >= bar[3]) return false;
        dragging = which;
        dragOffset = mouseY >= bar[4] && mouseY < bar[4] + bar[5] ? mouseY - bar[4] : bar[5] / 2.0;
        drag(mouseY);
        return true;
    }

    private void drag(double mouseY) {
        int[] bar = scrollbar(dragging);
        if (bar == null) return;
        float max = dragging == 1 ? maxGridScroll() : maxListScroll();
        float t = (float) ((mouseY - dragOffset - bar[1]) / Math.max(1, bar[3] - bar[1] - bar[5]));
        float value = Math.max(0, Math.min(max, t * max));
        if (dragging == 1) gridScroll = gridScrollTarget = value;
        else listScroll = listScrollTarget = value;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (dragging != 0) {
            drag(event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = 0;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX < leftRight()) gridScrollTarget = Math.max(0, Math.min(maxGridScroll(), gridScrollTarget - (float) verticalAmount * CELL * 2));
        else listScrollTarget = Math.max(0, Math.min(maxListScroll(), listScrollTarget - (float) verticalAmount * ROW * 2));
        return true;
    }

    @Override
    public void onClose() {
        GlobalParticleSystem.getInstance().setYellowMode(false);
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        GlobalParticleSystem.getInstance().setYellowMode(false);
        super.removed();
    }
}
