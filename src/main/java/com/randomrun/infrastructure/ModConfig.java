package com.randomrun.infrastructure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

public class ModConfig {
    private static final String LOG_TAG = "[Config]";

    public static final String OWNERSHIP = "PROTECTED CODE: (c) 2026 Stanislav Kholod. Unauthorized copying is prohibited.";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILENAME = "randomrun.json";
    private static final String CONFIG_BACKUP_SUFFIX = ".backup";
    private static final String CONFIG_TEMP_SUFFIX = ".tmp";
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir();
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve(CONFIG_FILENAME);
    private static final Path CONFIG_BACKUP = CONFIG_DIR.resolve(CONFIG_FILENAME + CONFIG_BACKUP_SUFFIX);
    private static final Path CONFIG_TEMP = CONFIG_DIR.resolve(CONFIG_FILENAME + CONFIG_TEMP_SUFFIX);

    private static final int MIN_VOLUME = 0;
    private static final int MAX_VOLUME = 100;

    public static final int DEFAULT_UPGRADER_KEY = 85;
    private static final long SAVE_DEBOUNCE_MS = 1000;

    private static final Object SAVE_LOCK = new Object();
    private static final java.util.concurrent.ScheduledExecutorService SAVER = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "RandomRun-Config");
        thread.setDaemon(true);
        return thread;
    });
    private transient long lastSaveTime = 0;
    private transient boolean savePending;

    @SerializedName("configVersion")
    private int configVersion = 1;

    @SerializedName("hudPosition")
    private HudPosition hudPosition = HudPosition.TOP_LEFT;

    @SerializedName("customHudX")
    private int customHudX = 10;

    @SerializedName("customHudY")
    private int customHudY = 10;

    @SerializedName("soundEffectsEnabled")
    private boolean soundEffectsEnabled = true;

    @SerializedName("soundVolume")
    private int soundVolume = 100;

    @SerializedName("language")
    private String language = "ru_ru";

    @SerializedName("allowUnobtainableItems")
    private boolean allowUnobtainableItems = false;

    @SerializedName("victoryScreenMode")
    private VictoryScreenMode victoryScreenMode = VictoryScreenMode.SHOW;

    @SerializedName("deleteWorldsAfterSpeedrun")
    private boolean deleteWorldsAfterSpeedrun = false;

    @SerializedName("askForCustomSeed")
    private boolean askForCustomSeed = false;

    @SerializedName("customSeed")
    private String customSeed = "";



    @SerializedName("upgraderKey")
    private int upgraderKey = DEFAULT_UPGRADER_KEY;

    @SerializedName("onlineMode")
    private boolean onlineMode = true;


    @SerializedName("privacyChoiceMade")
    private boolean privacyChoiceMade = false;

    @SerializedName("hardcoreModeEnabled")
    private boolean hardcoreModeEnabled = true;

    @SerializedName("upgraderEnabled")
    private boolean upgraderEnabled = false;

    @SerializedName("upgraderHintSeen")
    private boolean upgraderHintSeen = false;

    @SerializedName("upgraderFastSpin")
    private boolean upgraderFastSpin = false;

    @SerializedName("uiStyle")
    private UIStyle uiStyle = UIStyle.DETAILED;

    @SerializedName("hudStyle")
    private HudStyle hudStyle = HudStyle.PLEASANT;

    @SerializedName("theme")
    private String theme = "amethyst";

    @SerializedName("interfaceStyle")
    private String interfaceStyle = "modern";

    @SerializedName("interfaceStyleChosen")
    private boolean interfaceStyleChosen = false;

    @SerializedName("hudSize")
    private HudSize hudSize = HudSize.STANDARD;

    public enum HudSize {
        SMALL(0.8f), STANDARD(1f), LARGE(1.25f);
        public final float scale;
        HudSize(float scale) { this.scale = scale; }
    }

    public HudSize getHudSize() { return hudSize == null ? HudSize.STANDARD : hudSize; }
    public void setHudSize(HudSize value) { hudSize = value == null ? HudSize.STANDARD : value; }

    public enum HudStyle {
        PROFESSIONAL, PLEASANT;

        public static HudStyle getDefault() {
            return PLEASANT;
        }

        public static HudStyle fromString(String value) {
            try {
                if ("SIMPLIFIED".equalsIgnoreCase(value)) return PLEASANT;
                return valueOf(value.toUpperCase());
            } catch (Exception e) {
                return getDefault();
            }
        }
    }

    public enum UIStyle {
        SIMPLIFIED, DETAILED;

        public static UIStyle getDefault() {
            return DETAILED;
        }

        public static UIStyle fromString(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (Exception e) {
                return getDefault();
            }
        }
    }



    public enum HudPosition {
        TOP_LEFT, TOP_RIGHT, TOP_CENTER, CUSTOM;

        public static HudPosition getDefault() {
            return TOP_LEFT;
        }

        public static HudPosition fromString(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (Exception e) {
                return getDefault();
            }
        }
    }

    public enum VictoryScreenMode {
        SHOW, HIDE, SHOW_AFTER_10_SECONDS;

        public static VictoryScreenMode getDefault() {
            return SHOW;
        }

        public static VictoryScreenMode fromString(String value) {
            try {
                return valueOf(value.toUpperCase());
            } catch (Exception e) {
                return getDefault();
            }
        }
    }

    public HudPosition getHudPosition() { return hudPosition; }
    public void setHudPosition(HudPosition hudPosition) { this.hudPosition = hudPosition; }

    public int getCustomHudX() { return customHudX; }
    public void setCustomHudX(int customHudX) { this.customHudX = customHudX; }

    public int getCustomHudY() { return customHudY; }
    public void setCustomHudY(int customHudY) { this.customHudY = customHudY; }

    public boolean isSoundEffectsEnabled() { return soundEffectsEnabled; }
    public void setSoundEffectsEnabled(boolean soundEffectsEnabled) { this.soundEffectsEnabled = soundEffectsEnabled; }

    public int getSoundVolume() { return soundVolume; }
    public void setSoundVolume(int soundVolume) { this.soundVolume = clamp(soundVolume, MIN_VOLUME, MAX_VOLUME); }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public boolean isAllowUnobtainableItems() { return allowUnobtainableItems; }
    public void setAllowUnobtainableItems(boolean allowUnobtainableItems) { this.allowUnobtainableItems = allowUnobtainableItems; }

    public VictoryScreenMode getVictoryScreenMode() { return victoryScreenMode; }
    public void setVictoryScreenMode(VictoryScreenMode victoryScreenMode) { this.victoryScreenMode = victoryScreenMode; }

    public boolean isDeleteWorldsAfterSpeedrun() { return deleteWorldsAfterSpeedrun; }
    public void setDeleteWorldsAfterSpeedrun(boolean deleteWorldsAfterSpeedrun) { this.deleteWorldsAfterSpeedrun = deleteWorldsAfterSpeedrun; }

    public boolean isAskForCustomSeed() { return askForCustomSeed; }
    public void setAskForCustomSeed(boolean askForCustomSeed) { this.askForCustomSeed = askForCustomSeed; }

    public String getCustomSeed() { return customSeed; }
    public void setCustomSeed(String customSeed) { this.customSeed = customSeed; }



    public int getUpgraderKey() { return upgraderKey; }
    public void setUpgraderKey(int upgraderKey) {
        synchronized (SAVE_LOCK) {
            this.upgraderKey = upgraderKey;
            lastSaveTime = 0;
        }
    }


    public boolean isOnlineMode() {
        return onlineMode && privacyChoiceMade;
    }

    public boolean isPrivacyChoiceMade() {
        return privacyChoiceMade;
    }


    public void choosePrivacy(boolean online) {
        setOnlineMode(online);
    }


    public void setOnlineMode(boolean onlineMode) {
        privacyChoiceMade = true;
        if (this.onlineMode != onlineMode) {

            com.randomrun.network.BackendSession.clear();
            this.onlineMode = onlineMode;
            com.randomrun.core.data.PlayerProfile.clear();
            reloadPlayerProfile();
        }
    }

    public boolean isHardcoreModeEnabled() { return hardcoreModeEnabled; }
    public void setHardcoreModeEnabled(boolean hardcoreModeEnabled) {
        this.hardcoreModeEnabled = hardcoreModeEnabled;
    }

    public HudStyle getHudStyle() { return hudStyle; }
    public void setHudStyle(HudStyle hudStyle) { this.hudStyle = hudStyle; }

    public UIStyle getUIStyle() { return uiStyle; }
    public void setUIStyle(UIStyle uiStyle) {
        this.uiStyle = uiStyle;
    }

    public boolean isAnimationsEnabled() { return uiStyle == UIStyle.DETAILED; }
    public boolean isParticlesEnabled() { return uiStyle == UIStyle.DETAILED; }

    public boolean isUpgraderEnabled() { return upgraderEnabled; }
    public boolean hasSeenUpgraderHint() { return upgraderHintSeen; }
    public boolean isUpgraderFastSpin() { return upgraderFastSpin; }
    public void setUpgraderFastSpin(boolean value) { upgraderFastSpin = value; }

    public boolean markUpgraderHintSeen() {
        synchronized (SAVE_LOCK) {
            if (upgraderHintSeen) return false;
            upgraderHintSeen = true;
            lastSaveTime = 0;
            return true;
        }
    }

    public void setUpgraderEnabled(boolean upgraderEnabled) {
        synchronized (SAVE_LOCK) {
            if (this.upgraderEnabled != upgraderEnabled) {
                this.upgraderEnabled = upgraderEnabled;
                this.lastSaveTime = 0;
            }
        }
    }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getInterfaceStyle() { return "classic".equals(interfaceStyle) ? "classic" : "modern"; }
    public void setInterfaceStyle(String style) {
        synchronized (SAVE_LOCK) {
            interfaceStyle = "classic".equals(style) ? "classic" : "modern";
            lastSaveTime = 0;
        }
    }
    public boolean isInterfaceStyleChosen() { return interfaceStyleChosen; }
    public void setInterfaceStyleChosen(boolean chosen) {
        synchronized (SAVE_LOCK) {
            interfaceStyleChosen = chosen;
            lastSaveTime = 0;
        }
    }

    public void reset() {
        ModConfig defaults = new ModConfig();
        copyFrom(defaults);
        save();
    }

    public void save() {
        synchronized (SAVE_LOCK) {
            long wait = lastSaveTime + SAVE_DEBOUNCE_MS - System.currentTimeMillis();
            if (wait <= 0) {
                writeNow();
            } else if (!savePending) {
                savePending = true;
                SAVER.schedule(() -> {
                    synchronized (SAVE_LOCK) {
                        savePending = false;
                        writeNow();
                    }
                }, wait, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
    }

    private void writeNow() {
        lastSaveTime = System.currentTimeMillis();
        try {
            Files.createDirectories(CONFIG_DIR);
            backupCurrentFile();
            saveToFile();
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("{} Save failed", LOG_TAG, e);
        }
    }

    public static ModConfig load() {
        Exception failure = null;
        for (Path path : java.util.List.of(CONFIG_PATH, CONFIG_BACKUP)) {
            try {
                ModConfig config = loadFromFile(path);
                config.validate();
                return config;
            } catch (java.nio.file.NoSuchFileException ignored) {
            } catch (IOException | RuntimeException e) {
                if (failure == null) failure = e;
            }
        }
        if (failure != null) RandomRunMod.LOGGER.error("{} Config load failed", LOG_TAG, failure);
        return createDefault();
    }

    private static ModConfig loadFromFile(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            ModConfig config = GSON.fromJson(reader, ModConfig.class);
            if (config == null) throw new IOException("Empty configuration");
            return config;
        }
    }

    private static ModConfig createDefault() {
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    private void saveToFile() throws IOException {
        int maxRetries = 3;
        int retryDelayMs = 100;
        IOException lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                Files.createDirectories(CONFIG_DIR);

                String jsonContent = GSON.toJson(this);
                Files.writeString(CONFIG_TEMP, jsonContent, StandardCharsets.UTF_8);

                Files.move(CONFIG_TEMP, CONFIG_PATH,
                          StandardCopyOption.ATOMIC_MOVE,
                          StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep(retryDelayMs * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while waiting to retry", ie);
                    }
                }
            }
        }

        throw lastException;
    }

    private void backupCurrentFile() {
        if (!Files.exists(CONFIG_PATH)) return;
        try {
            loadFromFile(CONFIG_PATH);
        } catch (IOException | RuntimeException invalid) {
            return;
        }
        try {
            Files.copy(CONFIG_PATH, CONFIG_BACKUP, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            RandomRunMod.LOGGER.warn("{} Backup creation failed", LOG_TAG, e);
        }
    }

    private void validate() {
        soundVolume = clamp(soundVolume, MIN_VOLUME, MAX_VOLUME);

        if (hudPosition == null) hudPosition = HudPosition.getDefault();
        if (hudStyle == null) hudStyle = HudStyle.getDefault();
        if (hudSize == null) hudSize = HudSize.STANDARD;
        if (victoryScreenMode == null) victoryScreenMode = VictoryScreenMode.getDefault();

        if (uiStyle == null) uiStyle = UIStyle.getDefault();
        if (language == null || language.isBlank()) language = "ru_ru";
        if (customSeed == null) customSeed = "";
        if (theme == null || theme.isBlank()) theme = "amethyst";
        interfaceStyle = getInterfaceStyle();
    }

    private void reloadPlayerProfile() {
        try {
            if (net.minecraft.client.Minecraft.getInstance().getUser() != null) {
                String playerName = com.randomrun.network.BackendSession.playerName();
                com.randomrun.core.data.PlayerProfile.load(playerName).thenRun(() -> {
                    if (RandomRunMod.getInstance().getRunDataManager() != null) {
                        RandomRunMod.getInstance().getRunDataManager().reloadResults();
                    }
                });
            }
        } catch (Exception e) {
        }
    }

    private void copyFrom(ModConfig other) {
        this.configVersion = other.configVersion;
        this.hudPosition = other.hudPosition;
        this.customHudX = other.customHudX;
        this.customHudY = other.customHudY;
        this.soundEffectsEnabled = other.soundEffectsEnabled;
        this.soundVolume = other.soundVolume;
        this.language = other.language;
        this.allowUnobtainableItems = other.allowUnobtainableItems;
        this.victoryScreenMode = other.victoryScreenMode;
        this.deleteWorldsAfterSpeedrun = other.deleteWorldsAfterSpeedrun;
        this.askForCustomSeed = other.askForCustomSeed;
        this.customSeed = other.customSeed;

        this.upgraderKey = other.upgraderKey;
        this.onlineMode = other.onlineMode;
        this.privacyChoiceMade = other.privacyChoiceMade;
        this.hardcoreModeEnabled = other.hardcoreModeEnabled;
        this.upgraderEnabled = other.upgraderEnabled;
        this.upgraderHintSeen = other.upgraderHintSeen;
        this.upgraderFastSpin = other.upgraderFastSpin;
        this.uiStyle = other.uiStyle;
        this.hudStyle = other.hudStyle;
        this.hudSize = other.getHudSize();
        this.theme = other.theme;
        this.interfaceStyle = other.getInterfaceStyle();
        this.interfaceStyleChosen = other.interfaceStyleChosen;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
