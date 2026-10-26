package com.randomrun.ui.screen.main;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.core.init.CoreInitializer;
import com.randomrun.core.profile.PlayerProfile;
import com.randomrun.features.leaderboard.LatestRunEntry;
import com.randomrun.features.leaderboard.LeaderboardService;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.infrastructure.security.ModScannerService;
import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.infrastructure.util.IdCompressor;
import com.randomrun.core.history.RunHistory;
import com.randomrun.network.HttpClient;
import com.randomrun.ui.screen.auth.ProfileAuthScreen;
import com.randomrun.ui.screen.info.TrustScoreInfoScreen;
import com.randomrun.ui.screen.info.ModBlockScreen;
import com.randomrun.ui.screen.settings.SettingsScreen;
import com.randomrun.ui.widget.overlay.ToggleSwitchWidget;
import com.randomrun.ui.widget.overlay.LanguageDropdownWidget;
import com.randomrun.ui.widget.styled.ButtonMenu;
import com.randomrun.ui.widget.styled.ButtonYellow;
import com.randomrun.features.challenges.classic.world.WorldCreator;
import com.randomrun.features.challenges.classic.screen.SpeedrunScreen;
import com.randomrun.infrastructure.util.PlayerSkinHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class MainModScreen extends AbstractRandomRunScreen {
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath("randomrun", "textures/gui/logo.png");
    
    private static final int LOGO_WIDTH = 120;
    private static final int LOGO_HEIGHT = 60;
    private static final float FADE_SPEED = 0.08F;
    private static final long LEADERBOARD_REFRESH_MS = 60000L;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;
    private static final int PANEL_PADDING = 15;
    private static final int SHADOW_LAYERS = 10;
    private static final int BORDER_WIDTH = 2;
    private static final int GRADIENT_LENGTH = 30;
    private static final long ANIMATION_CYCLE_MS = 5000L;
    private static final int COLOR_PANEL_BG = 0x150030;
    private static final int COLOR_LEADERBOARD_BG = 0x3D3410;  
    private static final int COLOR_ONLINE_BG = 0x0d2b0d;  
    private static final int COLOR_BORDER_PURPLE = 0x6A0DAD;
    private static final int COLOR_BORDER_GOLD = 0xDAA520;
    private static final int COLOR_BORDER_GREEN = 0x32CD32;  
    private static final int COLOR_GOLD = 0xFFD700;
    private static final int COLOR_GREEN = 0x32CD32;  
    private static final int COLOR_PLAYER_NAME = 0x55FFFF;  
    private static final int COLOR_TIME = 0xFFCC00;
    
    private final List<AbstractWidget> mainWidgets = new ArrayList<>();
    
    private final Screen parent;
    private boolean versionValidated;
    private float logoScale = 1.0F;
    private float targetLogoScale = 1.0F;
    private boolean logoHovered = false;
    private float fadeProgress = 0.0F;
    
    private long openTime;
    
    private static boolean profileAuthChecked = false;
    private static boolean profileAuthenticated = false;
    private static boolean hasSeenWarning = false;
    private static boolean profileCreatedThisSession = false;
    private static long globalLastRunsFetchTime = 0L;
    
    private static final int MAX_UPDATES_PER_WINDOW = 5;
    private static final long UPDATE_WINDOW_MS = 180000L;
    private static long updateWindowStartTime = 0L;
    private static int updatesInCurrentWindow = 0;
    
    private List<LatestRunEntry> latestRuns = new ArrayList<>();
    private boolean isFetchingRuns = false;
    
    private static final int REFRESH_BUTTON_SIZE = 16;
    private int refreshButtonX = 0;
    private int refreshButtonY = 0;
    private boolean isRefreshButtonHovered = false;

    private static final long AFK_TIMEOUT_MS = 180000L;
    private long lastUserActivityTime = System.currentTimeMillis();

    private boolean isPasswordWarningHovered = false;
    private int passwordWarningLeft = 0;
    private int passwordWarningRight = 0;
    private int passwordWarningTop = 0;
    private int passwordWarningBottom = 0;
    
    public MainModScreen(Screen parent) {
        super(LanguageManager.text("randomrun.screen.main.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        if (RandomRunMod.getInstance().getConfig().isOnlineMode() && !versionValidated) {
            minecraft.gui.setScreen(new VersionCheckScreen(parent, () -> {
                versionValidated = true;
                minecraft.gui.setScreen(this);
            }));
            return;
        }
        String playerName = Minecraft.getInstance().getUser().getName();
        
        if (!CoreInitializer.isInitialized()) {
            RunHistory.getInstance().clear();
            CoreInitializer.initialize(playerName);
        }
        
        ModScannerService scanner = ModScannerService.getInstance();
        if (!scanner.isScanned()) {
            scanner.scan();
        }
        
        if (!scanner.isTrusted() && !hasSeenWarning) {
            hasSeenWarning = true;
            Minecraft.getInstance().gui.setScreen(new ModBlockScreen(this));
            return;
        }
        
        if (RandomRunMod.getInstance().getConfig().isOnlineMode() && !profileAuthenticated) {
            if (!profileAuthChecked) {
                profileAuthChecked = true;
            }
            checkProfileAuth();
            return;
        }
        
        LanguageManager.ensureLanguage();
        
        if (this.minecraft != null && this.minecraft.level == null) {
            if (!WorldCreator.hasPendingRun()) {
                WorldCreator.clearPendingData();
                if (RandomRunMod.getInstance().getRunDataManager() != null) {
                    RandomRunMod.getInstance().getRunDataManager().cancelRun();
                }
            }
        }
        
        super.init();
        this.openTime = System.currentTimeMillis();
        initButtons();
    }
    
    @Override
    public void removed() {
        super.removed();
        versionValidated = false;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.minecraft == null || !this.minecraft.isWindowActive()) {
            return;
        }
        
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return;
        }
        
        long now = System.currentTimeMillis();

        long timeSinceActivity = now - lastUserActivityTime;
        boolean isAfk = timeSinceActivity > AFK_TIMEOUT_MS;
        
        if (globalLastRunsFetchTime == 0L) {
            globalLastRunsFetchTime = now;
            if (!this.isFetchingRuns && !isAfk) {
                fetchLatestRuns();
            }
            return;
        }
        
        long timeSinceLastFetch = now - globalLastRunsFetchTime;

        if (timeSinceLastFetch > LEADERBOARD_REFRESH_MS && !this.isFetchingRuns && !isAfk) {
            fetchLatestRuns();
        }
    }
    
    private void fetchLatestRuns() {
        long now = System.currentTimeMillis();
        
        if (updateWindowStartTime == 0L || now - updateWindowStartTime >= UPDATE_WINDOW_MS) {
            updateWindowStartTime = now;
            updatesInCurrentWindow = 0;
        }
        
        if (updatesInCurrentWindow >= MAX_UPDATES_PER_WINDOW) {
            globalLastRunsFetchTime = now;
            return;
        }
        
        this.isFetchingRuns = true;
        globalLastRunsFetchTime = now;
        updatesInCurrentWindow++;
        
        LeaderboardService.getInstance().getLatestRuns(5)
            .thenAccept(runs -> Minecraft.getInstance().execute(() -> {
                this.latestRuns = runs;
                this.isFetchingRuns = false;
            }))
            .exceptionally(e -> {
                this.isFetchingRuns = false;
                com.randomrun.infrastructure.util.CompactLogger.error("Failed to fetch runs: {}", e.getMessage());
                return null;
            });
    }
    
    private void initButtons() {
        continueToMainScreen();
    }
    
    private void checkProfileAuth() {
        String playerName = Minecraft.getInstance().getUser().getName();
        
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            profileAuthenticated = true;
            Minecraft.getInstance().execute(this::continueToMainScreen);
            return;
        }
        
        if (profileCreatedThisSession) {
            profileAuthenticated = true;
            Minecraft.getInstance().execute(this::continueToMainScreen);
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                JsonElement result = HttpClient.getInstance().get("/profiles/" + playerName + "/profile").join();

                if (result == null || !result.isJsonObject()) {
                    result = HttpClient.getInstance().get("/profiles/" + playerName).join();
                }
                
                boolean profileExists = false;
                
                if (result != null && result.isJsonObject()) {
                    JsonObject profile = result.getAsJsonObject();

                    if (profile.has("sec") && profile.getAsJsonObject("sec").has("pwd")) {
                        String passwordHash = profile.getAsJsonObject("sec").get("pwd").getAsString();
                        profileExists = (passwordHash != null && !passwordHash.isEmpty());
                    } else if (profile.has("profile") && profile.getAsJsonObject("profile").has("sec")) {
                        JsonObject innerProfile = profile.getAsJsonObject("profile");
                        if (innerProfile.has("sec") && innerProfile.getAsJsonObject("sec").has("pwd")) {
                            String passwordHash = innerProfile.getAsJsonObject("sec").get("pwd").getAsString();
                            profileExists = (passwordHash != null && !passwordHash.isEmpty());
                        }
                    } else if (profile.has("info") || profile.has("sec") || profile.has("stats")) {
                        profileExists = false;
                    }
                }
                
                boolean finalProfileExists = profileExists;
                
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().gui.setScreen(new ProfileAuthScreen(this.parent, playerName, finalProfileExists, this));
                });
            } catch (Exception e) {
                com.randomrun.infrastructure.util.CompactLogger.authError("Profile check failed: {}", e.getMessage());
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().gui.setScreen(new ProfileAuthScreen(this.parent, playerName, false, this));
                });
            }
        });
    }
    
    public static void onAuthenticationSuccess() {
        profileAuthenticated = true;
        profileCreatedThisSession = true;
    }
    
    public static void resetAuthentication() {
        profileAuthenticated = false;
        profileCreatedThisSession = false;
    }
    
    public static void resetLeaderboardTimer() {
        globalLastRunsFetchTime = 0L;
    }
    
    private void continueToMainScreen() {
        super.init();
        this.mainWidgets.clear();
        this.openTime = System.currentTimeMillis();
        this.fadeProgress = 0.0F;
        this.lastUserActivityTime = System.currentTimeMillis(); 
        
        if (!checkBanStatusBeforeMenu()) {
            return;
        }
        
        checkFirstLaunchTrustScoreInfo();
        
        initMainButtons();
        initLeftPanelButton();
        initLeaderboardButton();
        initSettingsWidgets();
    }
    
    private boolean checkBanStatusBeforeMenu() {
        return true;
    }
    
    private void checkFirstLaunchTrustScoreInfo() {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return;
        }
        
        PlayerProfile profile = PlayerProfile.get();
        if (profile == null) {
            return;
        }
        
        if (!profile.isFirstTrustScoreShown()) {
            profile.setFirstTrustScoreShown(true);
            PlayerProfile.save();
            
            Minecraft.getInstance().execute(() -> {
                if (this.minecraft != null) {
                    this.minecraft.gui.setScreen(new TrustScoreInfoScreen(this));
                }
            });
        }
    }
    
    private void initMainButtons() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 10;
        String playerName = Minecraft.getInstance().getUser().getName();
        
        this.mainWidgets.add(addRenderableWidget(new ButtonMenu(
            centerX - 100, startY, 200, 20,
            LanguageManager.text("randomrun.button.speedrun"),
            button -> Minecraft.getInstance().gui.setScreen(new SpeedrunScreen(this)),
            0, 0.1F
        )));
        
        this.mainWidgets.add(addRenderableWidget(new ButtonMenu(
            centerX - 100, startY + 25, 200, 20,
            Component.literal(playerName),
            button -> Minecraft.getInstance().gui.setScreen(new ResultsScreen(this)),
            1, 0.15F
        )));
        
        this.mainWidgets.add(addRenderableWidget(new ButtonMenu(
            centerX - 100, startY + 50, 200, 20,
            LanguageManager.text("randomrun.button.settings"),
            button -> Minecraft.getInstance().gui.setScreen(new SettingsScreen(this)),
            2, 0.2F
        )));
        
        this.mainWidgets.add(addRenderableWidget(new ButtonMenu(
            centerX - 100, this.height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"),
            button -> Minecraft.getInstance().gui.setScreen(this.parent),
            3, 0.25F
        )));
    }
    
    private void initLeaderboardButton() {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return;
        }
        
        int contentRight = this.width / 2 + 120;
        int lbLeft = contentRight + 20;
        int lbRight = this.width - 15;
        
        if (lbLeft < this.width - 50) {
            int lbWidth = lbRight - lbLeft;
            
            int contentBottom = this.height / 2 + 85;
            int buttonY = contentBottom - 18;
            
            this.mainWidgets.add(addRenderableWidget(new ButtonYellow(
                lbLeft, buttonY, lbWidth, 18,
                LanguageManager.text("randomrun.main.leaderboard_title"),
                button -> Minecraft.getInstance().gui.setScreen(new LeaderboardScreen(this))
            )));
        }
    }
    
    private void initLeftPanelButton() {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return;
        }
        
        int contentLeft = this.width / 2 - 120;
        int lpRight = contentLeft - 20;
        int lpLeft = 15;
        
        if (lpRight > lpLeft + 50) {
            int lpWidth = lpRight - lpLeft;
            
            int contentBottom = this.height / 2 + 85;
            int buttonY = contentBottom - 18;
            
            this.mainWidgets.add(addRenderableWidget(new com.randomrun.ui.widget.styled.ButtonGreen(
                lpLeft, buttonY, lpWidth, 18,
                Component.literal("Лобби"),
                button -> Minecraft.getInstance().gui.setScreen(new com.randomrun.features.battle.screen.lobby.LobbyScreen(this))
            )));
        }
    }
    
    private void initSettingsWidgets() {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        
        this.mainWidgets.add(addRenderableWidget(new ToggleSwitchWidget(
            10, 10,
            config.isOnlineMode(),
            this::handleOnlineModeToggle
        )));
        
        this.mainWidgets.add(addRenderableWidget(new LanguageDropdownWidget(
            10, this.height - 30,
            this, 0.35F
        )));
    }
    
    private void handleOnlineModeToggle() {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        boolean currentMode = config.isOnlineMode();
        
        if (!currentMode) {
            minecraft.gui.setScreen(new VersionCheckScreen(this, this::enableOnlineMode));
        } else {
            config.setOnlineMode(false);
            RandomRunMod.getInstance().saveConfig();
            CoreInitializer.reset();
            
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new MainModScreen(this.parent));
            }
        }
    }

    private void enableOnlineMode() {
        profileAuthenticated = false;
        profileCreatedThisSession = false;
        RandomRunMod.getInstance().getConfig().setOnlineMode(true);
        RandomRunMod.getInstance().saveConfig();
        CoreInitializer.reset();
        versionValidated = true;
        minecraft.gui.setScreen(this);
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        long elapsed = System.currentTimeMillis() - this.openTime;
        this.fadeProgress = Math.min(1.0F, (float)elapsed / 1000.0F * (1.0F / FADE_SPEED));

        lastUserActivityTime = System.currentTimeMillis();
        
        if (RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            this.isRefreshButtonHovered = (mouseX >= this.refreshButtonX && mouseX <= this.refreshButtonX + REFRESH_BUTTON_SIZE &&
                                          mouseY >= this.refreshButtonY && mouseY <= this.refreshButtonY + REFRESH_BUTTON_SIZE);
            
            this.isPasswordWarningHovered = (mouseX >= this.passwordWarningLeft && mouseX <= this.passwordWarningRight &&
                                            mouseY >= this.passwordWarningTop && mouseY <= this.passwordWarningBottom);
        }
        
        super.extractRenderState(context, mouseX, mouseY, delta);
        
        renderPasswordWarning(context);
        
        if (this.isPasswordWarningHovered) {
            java.util.List<Component> tooltipLines = new java.util.ArrayList<>();
            tooltipLines.add(LanguageManager.text("randomrun.main.password_tooltip_line1"));
            tooltipLines.add(Component.literal("§c" + LanguageManager.text("randomrun.main.password_tooltip_line2").getString()));
            context.setComponentTooltipForNextFrame(this.font, tooltipLines, mouseX, mouseY);
        }
        
        if (this.isRefreshButtonHovered && RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            context.setTooltipForNextFrame(this.font, Component.literal("Нажмите — обновить"), mouseX, mouseY);
        }
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);

        int logoX = this.width / 2 - 60;
        int logoY = this.height / 2 - 85;
        int contentTop = logoY - 15;
        int contentBottom = this.height / 2 + 85;
        int contentLeft = this.width / 2 - 120;
        int contentRight = this.width / 2 + 120;
        
        renderPanel(context, contentLeft, contentTop, contentRight, contentBottom, COLOR_PANEL_BG, COLOR_BORDER_PURPLE);
        renderLeftPanel(context, contentLeft, contentTop, contentBottom);
        renderLeaderboardPanel(context, contentRight, contentTop, contentBottom);
        renderLogo(context, logoX, logoY, mouseX, mouseY);
    }
    
    private void renderPanel(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int bgColor, int borderColor) {
        for (int i = 0; i < SHADOW_LAYERS; i++) {
            int shadowAlpha = (int)((SHADOW_LAYERS - i) * 2.5F * this.fadeProgress);
            context.fill(left - i, top - i, right + i, bottom + i, shadowAlpha << 24);
        }
        
        int panelAlpha = (int)(0xD0 * this.fadeProgress);
        context.fill(left, top, right, bottom, panelAlpha << 24 | bgColor);
        
        renderBorder(context, left, top, right, bottom, borderColor, false);
    }
    
    private void renderLeftPanel(GuiGraphicsExtractor context, int contentLeft, int contentTop, int contentBottom) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return;
        }
        
        int lpRight = contentLeft - 20;
        int lpLeft = 15;
        
        if (lpRight <= lpLeft + 50) {
            return;
        }
        
        renderPanel(context, lpLeft, contentTop, lpRight, contentBottom, COLOR_ONLINE_BG, COLOR_BORDER_GREEN);
        renderLeftPanelContent(context, lpLeft, contentTop, lpRight, contentBottom);
    }
    
    private void renderLeftPanelContent(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        MutableComponent titleText = Component.literal("Онлайн").withStyle(s -> s.withColor(COLOR_GREEN));
        int titleWidth = this.font.width(titleText);
        int titleX = left + (right - left) / 2 - titleWidth / 2;
        com.randomrun.ui.util.GuiText.text(context, this.font, titleText, titleX, top + 10, 0xFFFFFF, true);
        
        context.fill(left + 10, top + 25, right - 10, top + 27, 0xFF32CD32);  
        
        int yOffset = top + 35;

        MutableComponent emptyText = Component.literal("Скоро здесь будет контент").withStyle(s -> s.withColor(0xAAAAAA));
        int emptyWidth = this.font.width(emptyText);
        int emptyX = left + (right - left) / 2 - emptyWidth / 2;
        com.randomrun.ui.util.GuiText.text(context, this.font, emptyText, emptyX, yOffset, 0xFFFFFF, false);
    }
    
    private void renderLeaderboardPanel(GuiGraphicsExtractor context, int contentRight, int contentTop, int contentBottom) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return;
        }
        
        int lbLeft = contentRight + 20;
        int lbRight = this.width - 15;
        
        if (lbLeft >= this.width - 50) {
            return;
        }
        
        renderPanel(context, lbLeft, contentTop, lbRight, contentBottom, COLOR_LEADERBOARD_BG, COLOR_BORDER_GOLD);
        renderLeaderboardContent(context, lbLeft, contentTop, lbRight, contentBottom);
    }
    
    private void renderLeaderboardContent(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        MutableComponent titleText = LanguageManager.text("randomrun.main.leaderboard_title").withStyle(s -> s.withColor(COLOR_GOLD));
        int titleWidth = this.font.width(titleText);
        int titleX = left + (right - left) / 2 - titleWidth / 2;
        com.randomrun.ui.util.GuiText.text(context, this.font, titleText, titleX, top + 10, 0xFFFFFF, true);
        
        long timeSinceLastFetch = System.currentTimeMillis() - globalLastRunsFetchTime;
        long timeUntilNextRefresh = LEADERBOARD_REFRESH_MS - timeSinceLastFetch;
        int secondsLeft = (int)Math.max(0L, timeUntilNextRefresh / 1000L);
        
        this.refreshButtonX = right - 10 - this.font.width("" + secondsLeft + "с");
        this.refreshButtonY = top + 10;
        
        this.isRefreshButtonHovered = false;
        
        String timerStr = "" + secondsLeft + "с";
        int timerColor = this.isRefreshButtonHovered ? 0xFFFFFF : COLOR_GOLD;
        com.randomrun.ui.util.GuiText.text(context, this.font, Component.literal(timerStr).withStyle(s -> s.withColor(timerColor)), 
            this.refreshButtonX, this.refreshButtonY, 0xFFFFFF, true);
        
        context.fill(left + 10, top + 25, right - 10, top + 27, 0xFFFFD700);  
        
        int yOffset = top + 30;
        
        if (this.latestRuns.isEmpty()) {
            MutableComponent emptyText = this.isFetchingRuns ? 
                LanguageManager.text("randomrun.main.loading") : 
                LanguageManager.text("randomrun.main.no_data");
            com.randomrun.ui.util.GuiText.text(context, this.font, emptyText, left + 10, yOffset, 0xAAAAAA, false);
        } else {
            for (LatestRunEntry run : this.latestRuns) {
                if (yOffset > bottom - 40) {
                    break;
                }
                
                int headSize = 20;
                int headX = left + 10;
                int headY = yOffset - 2;
                renderPlayerHead(context, run.getPlayerName(), null, headX, headY, headSize);
                
                int textX = headX + headSize + 6;
                
                MutableComponent playerText = Component.literal(run.getPlayerName()).withStyle(s -> s.withColor(COLOR_PLAYER_NAME));
                MutableComponent itemText = Component.literal(getItemDisplayName(run.getTargetId()));
                MutableComponent timeText = Component.literal(run.getFormattedDuration()).withStyle(s -> s.withColor(COLOR_TIME));
                
                com.randomrun.ui.util.GuiText.text(context, this.font, playerText, textX, yOffset, 0xFFFFFF, true);
                com.randomrun.ui.util.GuiText.text(context, this.font, timeText, 
                    right - 10 - this.font.width(timeText), yOffset, 0xFFFFFF, true);
                com.randomrun.ui.util.GuiText.text(context, this.font, itemText, textX, yOffset + 10, 0xDDDDDD, false);
                
                yOffset += 28;
            }
        }
    }
    
    private void renderPlayerHead(GuiGraphicsExtractor context, String playerName, String playerId, int x, int y, int size) {
        try {
            UUID playerUuid = (playerId != null && !playerId.isEmpty()) ? 
                UUID.fromString(playerId) : 
                PlayerSkinHelper.getOfflineUUID(playerName);
            
            Identifier skinTexture = PlayerSkinHelper.getPlayerSkin(playerName, playerUuid);
            
            context.fill(x - 1, y - 1, x + size + 1, y, COLOR_BORDER_PURPLE);
            context.fill(x - 1, y + size, x + size + 1, y + size + 1, COLOR_BORDER_PURPLE);
            context.fill(x - 1, y, x, y + size, COLOR_BORDER_PURPLE);
            context.fill(x + size, y, x + size + 1, y + size, COLOR_BORDER_PURPLE);
            
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skinTexture, x, y, 8.0F, 8.0F, size, size, 8, 8, 64, 64);
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skinTexture, x, y, 40.0F, 8.0F, size, size, 8, 8, 64, 64);
        } catch (Exception e) {
        }
    }
    
    private String getItemDisplayName(String itemId) {
        try {
            String fullId = itemId;
            if (!itemId.contains(":")) {
                fullId = IdCompressor.decompress(itemId);
            }
            
            Identifier id = Identifier.tryParse(fullId);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.getValue(id);
                if (item != null && item != Items.AIR) {
                    return net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
                }
            }
        } catch (Exception e) {
            com.randomrun.infrastructure.util.CompactLogger.warn("Failed to get item name for: {}", itemId);
        }
        
        return LanguageManager.translateItem(itemId);
    }
    
    private void renderLogo(GuiGraphicsExtractor context, int x, int y, int mouseX, int mouseY) {
        this.logoHovered = (mouseX >= x && mouseX <= x + LOGO_WIDTH && mouseY >= y && mouseY <= y + LOGO_HEIGHT);
        
        this.targetLogoScale = this.logoHovered ? 1.08F : 1.0F;
        this.logoScale = Mth.lerp(0.2F, this.logoScale, this.targetLogoScale);

        context.pose().pushMatrix();
        
        float centerX = x + LOGO_WIDTH / 2.0F;
        float centerY = y + LOGO_HEIGHT / 2.0F;
        
        context.pose().translate(centerX, centerY);
        context.pose().scale(this.logoScale, this.logoScale);
        context.pose().translate(-centerX, -centerY);

        try {
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, x, y, 0.0F, 0.0F, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT, net.minecraft.util.ARGB.white(this.fadeProgress));
        } catch (Exception e) {
            com.randomrun.ui.util.GuiText.centeredText(context, this.font, "RandomRun", (int)centerX, (int)centerY, 0x6930C3);
        }
        
        context.pose().popMatrix();
    }

    private void renderPasswordWarning(GuiGraphicsExtractor context) {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            return;
        }
        
        try {
            com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
            if (profile == null || !profile.needsPasswordChange()) {
                return;
            }
            
            int contentLeft = this.width / 2 - 120;
            int contentRight = this.width / 2 + 120;
            
            int warningTop = 10;
            int warningHeight = 42;
            int warningBottom = warningTop + warningHeight;
            
            this.passwordWarningLeft = contentLeft;
            this.passwordWarningRight = contentRight;
            this.passwordWarningTop = warningTop;
            this.passwordWarningBottom = warningBottom;
            
            int bgAlpha = (int) (0xC0 * this.fadeProgress);
            int bgColor = this.isPasswordWarningHovered ? 
                (bgAlpha << 24) | 0x4D0000 :
                (bgAlpha << 24) | 0x3D0000;
            context.fill(contentLeft, warningTop, contentRight, warningBottom, bgColor);
            
            int borderColor = this.isPasswordWarningHovered ? 0xFFFF3333 : 0xFFFF0000;
            context.fill(contentLeft, warningTop, contentRight, warningTop + 2, borderColor);
            context.fill(contentLeft, warningBottom - 2, contentRight, warningBottom, borderColor);
            context.fill(contentLeft, warningTop, contentLeft + 2, warningBottom, borderColor);
            context.fill(contentRight - 2, warningTop, contentRight, warningBottom, borderColor);
            
            int centerX = this.width / 2;
            int line1Y = warningTop + 6;
            int line2Y = warningTop + 17;
            int line3Y = warningTop + 28;
            
            MutableComponent line1 = LanguageManager.text("randomrun.main.password_warning_line1");
            MutableComponent line2 = LanguageManager.text("randomrun.main.password_warning_line2");
            MutableComponent line3 = LanguageManager.text("randomrun.main.password_warning_line3");
            
            com.randomrun.ui.util.GuiText.centeredText(context, this.font, line1, centerX, line1Y, 0xFFFFFFFF);
            com.randomrun.ui.util.GuiText.centeredText(context, this.font, line2, centerX, line2Y, 0xFFCCCCCC);
            com.randomrun.ui.util.GuiText.centeredText(context, this.font, line3, centerX, line3Y, 0xFFAAAA88);
        } catch (Exception e) {
        }
    }
    
    private void renderBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int baseColor, boolean animated) {
        context.fill(left, top, right, top + BORDER_WIDTH, 0xFF000000 | baseColor);
        context.fill(left, bottom - BORDER_WIDTH, right, bottom, 0xFF000000 | baseColor);
        context.fill(left, top, left + BORDER_WIDTH, bottom, 0xFF000000 | baseColor);
        context.fill(right - BORDER_WIDTH, top, right, bottom, 0xFF000000 | baseColor);
        
        if (animated && RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) {
            renderAnimatedBorder(context, left, top, right, bottom);
        }
    }
    
    public static void renderStaticBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int borderWidth) {
        int borderColor = 0xFF6A0DAD;
        context.fill(left, top, right, top + borderWidth, borderColor);
        context.fill(left, bottom - borderWidth, right, bottom, borderColor);
        context.fill(left, top, left + borderWidth, bottom, borderColor);
        context.fill(right - borderWidth, top, right, bottom, borderColor);
    }
    
    public static void renderStaticYellowBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int borderWidth) {
        int borderColor = 0xFFDAA520;
        context.fill(left, top, right, top + borderWidth, borderColor);
        context.fill(left, bottom - borderWidth, right, bottom, borderColor);
        context.fill(left, top, left + borderWidth, bottom, borderColor);
        context.fill(right - borderWidth, top, right, bottom, borderColor);
    }
    
    public static void renderAnimatedBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int borderWidth) {
        if (!RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) {
            return;
        }
        
        long time = System.currentTimeMillis();
        float animationProgress = (float)(time % ANIMATION_CYCLE_MS) / (float)ANIMATION_CYCLE_MS;
        
        int width = right - left;
        int height = bottom - top;
        int perimeter = 2 * (width + height);
        float currentPos = animationProgress * perimeter;
        
        drawCircularBorder(context, left, top, width, height, borderWidth, currentPos, GRADIENT_LENGTH, perimeter, 0xFFFFFF);
    }
    
    public static void renderAnimatedYellowBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int borderWidth) {
        if (!RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) {
            return;
        }
        
        long time = System.currentTimeMillis();
        float animationProgress = (float)(time % ANIMATION_CYCLE_MS) / (float)ANIMATION_CYCLE_MS;
        
        int width = right - left;
        int height = bottom - top;
        int perimeter = 2 * (width + height);
        float currentPos = animationProgress * perimeter;
        
        drawCircularBorder(context, left, top, width, height, borderWidth, currentPos, GRADIENT_LENGTH, perimeter, 0xFFFF55);
    }
    
    private void renderAnimatedBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom) {
        long time = System.currentTimeMillis();
        float animationProgress = (float)(time % ANIMATION_CYCLE_MS) / (float)ANIMATION_CYCLE_MS;
        
        int width = right - left;
        int height = bottom - top;
        int perimeter = 2 * (width + height);
        float currentPos = animationProgress * perimeter;
        
        drawCircularBorder(context, left, top, width, height, BORDER_WIDTH, currentPos, GRADIENT_LENGTH, perimeter, 0xFFFFFF);
    }
    
    private static void drawCircularBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int thickness, 
                                          float currentPos, int gradientLength, int perimeter, int glowColor) {
        for (int i = 0; i < width; i++) {
            float dist = getCircularDistance(i, currentPos, perimeter);
            if (dist < gradientLength) {
                float intensity = 1.0F - dist / gradientLength;
                int alpha = (int)(255.0F * intensity);
                context.fill(x + i, y, x + i + 1, y + thickness, alpha << 24 | glowColor);
            }
        }
        
        int rightStart = width;
        for (int j = 0; j < height; j++) {
            float dist = getCircularDistance(rightStart + j, currentPos, perimeter);
            if (dist < gradientLength) {
                float intensity = 1.0F - dist / gradientLength;
                int alpha = (int)(255.0F * intensity);
                context.fill(x + width - thickness, y + j, x + width, y + j + 1, alpha << 24 | glowColor);
            }
        }
        
        int bottomStart = width + height;
        for (int k = 0; k < width; k++) {
            float dist = getCircularDistance(bottomStart + k, currentPos, perimeter);
            if (dist < gradientLength) {
                float intensity = 1.0F - dist / gradientLength;
                int alpha = (int)(255.0F * intensity);
                context.fill(x + width - k - 1, y + height - thickness, x + width - k, y + height, alpha << 24 | glowColor);
            }
        }
        
        int leftStart = 2 * width + height;
        for (int m = 0; m < height; m++) {
            float dist = getCircularDistance(leftStart + m, currentPos, perimeter);
            if (dist < gradientLength) {
                float intensity = 1.0F - dist / gradientLength;
                int alpha = (int)(255.0F * intensity);
                context.fill(x, y + height - m - 1, x + thickness, y + height - m, alpha << 24 | glowColor);
            }
        }
    }
    
    private static float getCircularDistance(int pos, float currentPos, int perimeter) {
        float dist = Math.abs(pos - currentPos);
        if (dist > (perimeter / 2)) {
            dist = perimeter - dist;
        }
        return dist;
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        
        lastUserActivityTime = System.currentTimeMillis();
        
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && RandomRunMod.getInstance().getConfig().isOnlineMode() &&
            mouseX >= this.refreshButtonX && mouseX <= (this.refreshButtonX + REFRESH_BUTTON_SIZE) &&
            mouseY >= this.refreshButtonY && mouseY <= (this.refreshButtonY + REFRESH_BUTTON_SIZE)) {
            if (!this.isFetchingRuns) {
                fetchLatestRuns();
            }
            return true;
        }
        
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && this.isPasswordWarningHovered) {
            String playerName = Minecraft.getInstance().getUser().getName();
            Minecraft.getInstance().gui.setScreen(new com.randomrun.ui.screen.auth.ChangePasswordScreen(this, playerName));
            return true;
        }
        
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }
    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        
        lastUserActivityTime = System.currentTimeMillis();
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        lastUserActivityTime = System.currentTimeMillis();
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
