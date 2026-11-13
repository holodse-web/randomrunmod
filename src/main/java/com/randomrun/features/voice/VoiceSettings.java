package com.randomrun.features.voice;

import com.randomrun.infrastructure.util.CompactLogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

/** Voice run settings, kept in their own file so the main config format stays untouched. */
public final class VoiceSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("randomrun-voice.json");
    private static VoiceSettings instance;

    private String language;
    private String microphone;
    private boolean voiceRunEnabled;
    private boolean setupDone;
    /** Limit recognition to command words. Off by default: any speech is recognized. */
    private boolean commandsOnly;

    public static synchronized VoiceSettings get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static VoiceSettings load() {
        try {
            if (Files.exists(FILE)) {
                VoiceSettings loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), VoiceSettings.class);
                if (loaded != null) return loaded;
            }
        } catch (Exception e) {
            CompactLogger.warn("[Voice] Settings unreadable, using defaults: " + e.getMessage());
        }
        return new VoiceSettings();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            CompactLogger.warn("[Voice] Settings save failed: " + e.getMessage());
        }
    }

    /** Explicit choice if the player made one, otherwise the mod's interface language. */
    public VoiceLanguage language() {
        VoiceLanguage chosen = VoiceLanguage.fromId(language);
        if (chosen != null) return chosen;
        var mod = com.randomrun.infrastructure.RandomRunMod.getInstance();
        return VoiceLanguage.fromUiLanguage(mod != null && mod.getConfig() != null ? mod.getConfig().getLanguage() : null);
    }

    public void setLanguage(VoiceLanguage lang) { this.language = lang.id; save(); }
    public String microphone() { return microphone; }
    public void setMicrophone(String name) { this.microphone = name; save(); }
    public boolean isVoiceRunEnabled() { return voiceRunEnabled; }
    public void setVoiceRunEnabled(boolean enabled) { this.voiceRunEnabled = enabled; save(); }
    public boolean isSetupDone() { return setupDone; }
    public boolean isFreeSpeech() { return !commandsOnly; }
    public void setFreeSpeech(boolean free) { this.commandsOnly = !free; save(); }

    /** Grammar for the recognizer, or null for free speech. */
    public String grammar() {
        if (!commandsOnly) return null;
        VoiceLanguage lang = language();
        return com.randomrun.features.voice.command.VoiceLexicon.forLanguage(lang.id).grammarJson(lang.id);
    }
    public void setSetupDone(boolean done) { this.setupDone = done; save(); }

    /** Ready to play: setup finished and the chosen language is on disk. */
    public boolean isReady() {
        return setupDone && VoicePack.isInstalled(language());
    }

    /**
     * Offline recognition languages. Models are the official small Vosk models,
     * pinned by size and SHA-256 so a tampered or truncated download is rejected.
     */
    public enum VoiceLanguage {
        RU("ru", "Русский", "vosk-model-small-ru-0.22", 46236750L,
            "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4"),
        UK("uk", "Українська", "vosk-model-small-uk-v3-small", 143914407L,
            "ee05b6a8c796c53c61cd1c821d38e897e5bd67b4d691d8797ec8f9e5100d4247"),
        EN("en", "English", "vosk-model-small-en-us-0.15", 41205931L,
            "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498");

        public final String id;
        public final String displayName;
        public final String modelName;
        public final long zipSize;
        public final String sha256;

        VoiceLanguage(String id, String displayName, String modelName, long zipSize, String sha256) {
            this.id = id;
            this.displayName = displayName;
            this.modelName = modelName;
            this.zipSize = zipSize;
            this.sha256 = sha256;
        }

        public String url() {
            return "https://alphacephei.com/vosk/models/" + modelName + ".zip";
        }

        public int sizeMb() {
            return (int) Math.round(zipSize / 1_048_576.0);
        }

        public VoiceLanguage next() {
            VoiceLanguage[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static VoiceLanguage fromId(String id) {
            if (id != null) {
                for (VoiceLanguage lang : values()) if (lang.id.equals(id)) return lang;
            }
            return null;
        }

        /** Language that matches the mod's interface language (ru_ru / uk_ua / en_us). */
        public static VoiceLanguage fromUiLanguage(String code) {
            String c = code == null ? "" : code.toLowerCase(Locale.ROOT);
            if (c.startsWith("uk")) return UK;
            if (c.startsWith("en")) return EN;
            return RU;
        }
    }
}
