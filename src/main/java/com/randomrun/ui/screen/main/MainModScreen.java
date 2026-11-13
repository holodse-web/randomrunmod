package com.randomrun.ui.screen.main;

import com.randomrun.core.data.DataSyncService.CoreInitializer;
import com.randomrun.core.data.PlayerProfile;
import com.randomrun.features.battle.screen.LobbyScreen;
import com.randomrun.features.battle.screen.RoomHostScreenCreateImproved;
import com.randomrun.features.leaderboard.LeaderboardEntry.LatestRunEntry;
import com.randomrun.features.leaderboard.LeaderboardService;
import com.randomrun.features.speedrun.SpeedrunScreen;
import com.randomrun.features.speedrun.WorldCreator;
import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.security.ModScannerService;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.network.RealtimeClient;
import com.randomrun.ui.screen.account.ModBlockScreen;
import com.randomrun.ui.screen.account.ProfileAuthScreen;
import com.randomrun.ui.screen.account.TrustScoreInfoScreen;
import com.randomrun.ui.screen.settings.SettingsScreen;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Logo;
import com.randomrun.ui.util.Previews.Heads;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.FriendsPanel;
import com.randomrun.ui.widget.OverlayWidgets.LanguageDropdownWidget;
import com.randomrun.ui.widget.OverlayWidgets.ThemeSwitchWidget;
import com.randomrun.ui.widget.ToggleSwitchWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonGreen;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import com.randomrun.ui.widget.styled.Buttons.ButtonYellow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class MainModScreen extends AbstractRandomRunScreen {
    private static final int LOGO_WIDTH = 120;
    private static final int LOGO_HEIGHT = 60;
    private static final long LEADERBOARD_REFRESH_MS = 60000L;
    private static final int MAX_UPDATES_PER_WINDOW = 5;
    private static final long UPDATE_WINDOW_MS = 180000L;
    private static final int GOLD_BG = 0x3D3410;
    private static final int GOLD_BORDER = 0xDAA520;
    private static final int GREEN_BG = 0x0D2B0D;
    private static final int GREEN_BORDER = 0x32CD32;

    private static String warnedFindings = "";
    private static long globalLastRunsFetchTime = 0L;
    private static long updateWindowStartTime = 0L;
    private static int updatesInCurrentWindow = 0;

    private final Screen parent;
    private float logoScale = 1.0F;
    private long openTime;
    private List<LatestRunEntry> latestRuns = new ArrayList<>();
    private boolean isFetchingRuns = false;
    private FriendsPanel friendsPanel;
    private int refreshX, refreshY, refreshW;

    public MainModScreen(Screen parent) {
        super(LanguageManager.text("randomrun.screen.main.title"));
        this.parent = parent;
    }

    private boolean online() {
        return RandomRunMod.getInstance().getConfig().isOnlineMode();
    }

    @Override
    protected void init() {
        // First launch: ask whether the mod may go online before anything is sent anywhere.
        if (!RandomRunMod.getInstance().getConfig().isPrivacyChoiceMade()) {
            minecraft.gui.setScreen(new PrivacyConsentScreen(parent));
            return;
        }
        if (online()) {
            com.randomrun.network.VersionGate.start(false);
            if (com.randomrun.network.VersionGate.state() == com.randomrun.network.VersionGate.State.UPDATE_REQUIRED) {
                minecraft.gui.setScreen(new UpdateRequiredScreen());
                return;
            }
        }
        String playerName = BackendSession.playerName();
        String banned = BackendSession.takeBanned();
        if (banned != null) {
            minecraft.gui.setScreen(new com.randomrun.ui.screen.account.BanScreen(parent, banned, com.randomrun.ui.screen.account.BanScreen.Kind.BANNED, this));
            return;
        }
        ModScannerService scanner = ModScannerService.getInstance();
        if (!scanner.isScanned()) scanner.scan();
        else scanner.refreshPacks();
        String findings = scanner.findings().keySet().toString();
        if (!scanner.isTrusted() && !findings.equals(warnedFindings)) {
            warnedFindings = findings;
            minecraft.gui.setScreen(new ModBlockScreen(this));
            return;
        }
        if (online() && !BackendSession.owns(playerName)) {
            minecraft.execute(() -> minecraft.gui.setScreen(new ProfileAuthScreen(parent, playerName, this)));
            return;
        }
        if (!CoreInitializer.isInitialized()) CoreInitializer.initialize(playerName);
        LanguageManager.ensureLanguage();
        if (minecraft.level == null && !WorldCreator.hasPendingRun()) {
            WorldCreator.clearPendingData();
            if (RandomRunMod.getInstance().getRunDataManager() != null) RandomRunMod.getInstance().getRunDataManager().cancelRun();
        }
        super.init();
        openTime = System.currentTimeMillis();
        if (online()) RealtimeClient.getInstance().setPresence("menu", null);
        checkFirstLaunchTrustScoreInfo();
        initMainButtons();
        initSidePanels();
        initCornerWidgets();
    }

    @Override
    public void removed() {
        super.removed();
    }

    @Override
    public void tick() {
        super.tick();
        if (minecraft == null || !minecraft.isWindowActive() || !online()) return;
        long now = System.currentTimeMillis();
        if (globalLastRunsFetchTime == 0L || now - globalLastRunsFetchTime > LEADERBOARD_REFRESH_MS) {
            if (!isFetchingRuns) fetchLatestRuns(false);
        }
    }

    private void fetchLatestRuns(boolean manual) {
        long now = System.currentTimeMillis();
        if (updateWindowStartTime == 0L || now - updateWindowStartTime >= UPDATE_WINDOW_MS) {
            updateWindowStartTime = now;
            updatesInCurrentWindow = 0;
        }
        if (updatesInCurrentWindow >= MAX_UPDATES_PER_WINDOW) {
            globalLastRunsFetchTime = now;
            return;
        }
        isFetchingRuns = true;
        globalLastRunsFetchTime = now;
        updatesInCurrentWindow++;
        if (manual) Ui.click();
        LeaderboardService.getInstance().getLatestRuns(5)
            .thenAccept(runs -> Minecraft.getInstance().execute(() -> {
                latestRuns = runs;
                isFetchingRuns = false;
            }))
            .exceptionally(error -> {
                isFetchingRuns = false;
                CompactLogger.warn("Failed to fetch runs: {}", error.getMessage());
                return null;
            });
    }

    public static void resetLeaderboardTimer() {
        globalLastRunsFetchTime = 0L;
    }

    private void checkFirstLaunchTrustScoreInfo() {
        if (!online()) return;
        PlayerProfile profile = PlayerProfile.get();
        if (profile == null || profile.isFirstTrustScoreShown()) return;
        profile.setFirstTrustScoreShown(true);
        PlayerProfile.save();
        minecraft.execute(() -> minecraft.gui.setScreen(new TrustScoreInfoScreen(this)));
    }

    private int contentTop() { return height / 2 - 100; }
    private int contentBottom() { return height / 2 + 85; }
    private int contentLeft() { return width / 2 - 120; }
    private int contentRight() { return width / 2 + 120; }
    private boolean sidePanels() { return online() && contentLeft() - 20 > 15 + 110; }

    private void initMainButtons() {
        int centerX = width / 2;
        int startY = height / 2 - 10;
        addRenderableWidget(new ButtonMenu(centerX - 100, startY, 200, 20,
            LanguageManager.text("randomrun.button.speedrun"),
            button -> minecraft.gui.setScreen(new SpeedrunScreen(this)), 0, 0.1F));
        addRenderableWidget(new ButtonMenu(centerX - 100, startY + 25, 200, 20,
            LanguageManager.text("randomrun.button.statistics", BackendSession.playerName()),
            button -> minecraft.gui.setScreen(new ResultsScreen(this)), 1, 0.15F));
        addRenderableWidget(new ButtonMenu(centerX - 100, startY + 50, 200, 20,
            LanguageManager.text("randomrun.button.settings"),
            button -> minecraft.gui.setScreen(new SettingsScreen(this)), 2, 0.2F));
        addRenderableWidget(new ButtonMenu(centerX - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"),
            button -> minecraft.gui.setScreen(parent), 3, 0.25F));
    }

    private void initSidePanels() {
        friendsPanel = null;
        if (!sidePanels()) return;
        int buttonY = contentBottom() - 22;
        int lpLeft = 15, lpRight = contentLeft() - 20;
        friendsPanel = new FriendsPanel(this, FriendsPanel.Context.MENU,
            () -> { if (com.randomrun.network.VersionGate.requireOnline()) minecraft.gui.setScreen(new RoomHostScreenCreateImproved(new LobbyScreen(this), null)); });
        friendsPanel.layout(lpLeft, contentTop(), lpRight, contentBottom(), this::addRenderableWidget);
        addRenderableWidget(new ButtonGreen(lpLeft + 4, buttonY, lpRight - lpLeft - 8, 18,
            LanguageManager.text("randomrun.main.lobby"),
            button -> { if (com.randomrun.network.VersionGate.requireOnline()) minecraft.gui.setScreen(new LobbyScreen(this)); }));
        int lbLeft = contentRight() + 20, lbRight = width - 15;
        addRenderableWidget(new ButtonYellow(lbLeft + 4, buttonY, lbRight - lbLeft - 8, 18,
            LanguageManager.text("randomrun.main.leaderboard_title"),
            button -> minecraft.gui.setScreen(new LeaderboardScreen(this))));
    }

    private void initCornerWidgets() {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        addRenderableWidget(new ToggleSwitchWidget(10, 10, config.isOnlineMode(), this::handleOnlineModeToggle));
        addRenderableWidget(new LanguageDropdownWidget(10, height - 30, this, 0.35F));
        addRenderableWidget(new ThemeSwitchWidget(34, height - 30, 0.4F));
    }

    private void handleOnlineModeToggle() {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        if (!config.isOnlineMode()) {
            enableOnlineMode();
            com.randomrun.network.VersionGate.start(true);
            return;
        }
        config.setOnlineMode(false);
        RandomRunMod.getInstance().saveConfig();
        CoreInitializer.reset();
        minecraft.gui.setScreen(new MainModScreen(parent));
    }

    private void enableOnlineMode() {
        RandomRunMod.getInstance().getConfig().setOnlineMode(true);
        RandomRunMod.getInstance().saveConfig();
        CoreInitializer.reset();
        minecraft.gui.setScreen(this);
    }

    private float fade() {
        return Math.min(1f, (System.currentTimeMillis() - openTime) / 350f);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        float fade = fade();
        Ui.panel(context, contentLeft(), contentTop(), contentRight(), contentBottom(), fade);
        if (sidePanels()) {
            Ui.panel(context, 15, contentTop(), contentLeft() - 20, contentBottom(), GREEN_BG, GREEN_BORDER, fade);
            Ui.panel(context, contentRight() + 20, contentTop(), width - 15, contentBottom(), GOLD_BG, GOLD_BORDER, fade);
        }
        renderLogo(context, width / 2 - LOGO_WIDTH / 2, height / 2 - 85, mouseX, mouseY, fade);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        float fade = fade();
        if (sidePanels()) {
            if (friendsPanel != null) friendsPanel.extract(context, mouseX, mouseY, fade);
            renderLeaderboardContent(context, contentRight() + 20, contentTop(), width - 15, contentBottom(), mouseX, mouseY, fade);
        }
        renderOnlineBadge(context, mouseX, mouseY, fade);
    }

    private void renderOnlineBadge(GuiGraphicsExtractor context, int mouseX, int mouseY, float fade) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode() || !BackendSession.isAuthenticated()) return;
        int count = com.randomrun.network.RealtimeClient.getInstance().onlineCount();
        boolean live = count > 0;
        String text = live ? LanguageManager.translate("randomrun.main.online_now", count) : LanguageManager.translate("randomrun.main.online_connecting");
        int w = font.width(text) + 20, h = 16;
        int x = width - 10 - w, y = 10;
        context.fill(x, y, x + w, y + h, Ui.argb(0x000000, 0.55f * fade));
        Ui.border(context, x, y, x + w, y + h, 1, Ui.argb(live ? 0x4ADE80 : 0x71717A, 0.35f * fade));
        int dot = live ? 0x4ADE80 : 0xA1A1AA;
        context.fill(x + 6, y + 6, x + 10, y + 10, Ui.argb(dot, fade));
        if (live) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 350.0);
            int ring = Math.round(pulse * 2);
            Ui.border(context, x + 5 - ring, y + 5 - ring, x + 11 + ring, y + 11 + ring, 1, Ui.argb(dot, (1f - pulse) * 0.6f * fade));
        }
        GuiText.text(context, font, text, x + 15, y + 4, Ui.argb(live ? 0xFFFFFF : 0xA1A1AA, fade), false);
        if (Ui.inside(mouseX, mouseY, x, y, w, h)) context.setTooltipForNextFrame(font, LanguageManager.text("randomrun.main.online_tooltip"), mouseX, mouseY);
    }

    private void renderLeaderboardContent(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int mouseX, int mouseY, float fade) {
        GuiText.text(context, font, LanguageManager.text("randomrun.main.latest_runs"), left + 8, top + 8, Ui.argb(Ui.GOLD, fade), true);
        int secondsLeft = (int) Math.max(0L, (LEADERBOARD_REFRESH_MS - (System.currentTimeMillis() - globalLastRunsFetchTime)) / 1000L);
        String timer = isFetchingRuns ? "…" : LanguageManager.translate("randomrun.main.refresh_in", secondsLeft);
        refreshW = font.width(timer);
        refreshX = right - 8 - refreshW;
        refreshY = top + 8;
        boolean hover = Ui.inside(mouseX, mouseY, refreshX - 2, refreshY - 2, refreshW + 4, 12);
        GuiText.text(context, font, timer, refreshX, refreshY, Ui.argb(hover ? 0xFFFFFF : 0xC9A030, fade), false);
        if (hover) context.setTooltipForNextFrame(font, LanguageManager.text("randomrun.main.refresh_now"), mouseX, mouseY);
        Ui.divider(context, left + 8, right - 8, top + 21, GOLD_BORDER, fade);

        int y = top + 28;
        if (latestRuns.isEmpty()) {
            Component empty = LanguageManager.text(isFetchingRuns ? "randomrun.main.loading" : "randomrun.main.no_data");
            GuiText.centeredText(context, font, empty, (left + right) / 2, y + 20, Ui.argb(0xAAAAAA, fade));
            return;
        }
        int index = 0;
        for (LatestRunEntry run : latestRuns) {
            if (y > bottom - 50) break;
            float appear = Ui.easeOut((System.currentTimeMillis() - openTime - 150L - index++ * 60L) / 320f);
            if (appear <= 0f) { y += 28; continue; }
            context.pose().pushMatrix();
            context.pose().translate((1f - appear) * -16f, 0);
            float rowFade = fade * appear;
            boolean rowHover = Ui.inside(mouseX, mouseY, left + 4, y - 3, right - left - 8, 26);
            if (rowHover) context.fill(left + 4, y - 3, right - 4, y + 23, Ui.argb(0xFFFFFF, 0.06f * rowFade));
            Heads.draw(context, run.getPlayerName(), left + 9, y, 18, Ui.argb(GOLD_BORDER, rowFade));
            int textX = left + 33;
            String time = run.getFormattedDuration();
            int timeWidth = font.width(time);
            GuiText.text(context, font, time, right - 9 - timeWidth, y + 1, Ui.argb(0xFFCC00, rowFade), true);
            GuiText.text(context, font, Ui.ellipsize(font, run.getPlayerName(), right - textX - timeWidth - 14), textX, y + 1, Ui.argb(0x55FFFF, rowFade), true);
            GuiText.text(context, font, Ui.ellipsize(font, LanguageManager.translateItem(run.getTargetId()), right - textX - 10), textX, y + 11, Ui.argb(0xDDDDDD, rowFade), false);
            context.pose().popMatrix();
            y += 28;
        }
    }

    private void renderLogo(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY, float fade) {
        boolean hovered = Ui.inside(mouseX, mouseY, x, y, LOGO_WIDTH, LOGO_HEIGHT);
        logoScale = Mth.lerp(0.2F, logoScale, hovered ? 1.06F : 1.0F);
        float centerX = x + LOGO_WIDTH / 2.0F;
        float centerY = y + LOGO_HEIGHT / 2.0F;
        context.pose().pushMatrix();
        context.pose().translate(centerX, centerY);
        context.pose().scale(logoScale, logoScale);
        Logo.draw(context, -LOGO_WIDTH / 2, -LOGO_HEIGHT / 2, LOGO_WIDTH, LOGO_HEIGHT, fade);
        context.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (sidePanels()) {
            if (friendsPanel != null && friendsPanel.mouseClicked(event.x(), event.y())) return true;
            if (Ui.inside(event.x(), event.y(), refreshX - 2, refreshY - 2, refreshW + 4, 12) && !isFetchingRuns) {
                fetchLatestRuns(true);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (friendsPanel != null && friendsPanel.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
