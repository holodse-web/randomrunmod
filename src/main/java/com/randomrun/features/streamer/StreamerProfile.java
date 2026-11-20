package com.randomrun.features.streamer;

import com.randomrun.infrastructure.util.CompactLogger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;





public final class StreamerProfile {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("randomrun-streamer.json");
    private static StreamerProfile instance;

    private boolean enabled;
    private StreamerCharacter character = StreamerCharacter.ZLOY;

    public static synchronized StreamerProfile get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static StreamerProfile load() {
        try {
            if (Files.exists(FILE)) {
                StreamerProfile loaded = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), StreamerProfile.class);
                if (loaded != null) {
                    if (loaded.character == null) loaded.character = StreamerCharacter.ZLOY;
                    return loaded;
                }
            }
        } catch (Exception e) {
            CompactLogger.warn("[Streamer] Config load failed: " + e.getMessage());
        }
        return new StreamerProfile();
    }

    private void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            CompactLogger.warn("[Streamer] Config save failed: " + e.getMessage());
        }
    }

    private void changed() {
        save();
        StreamerNet.sendOwnProfile();
        StreamerNet.syncRoom();
    }

    public boolean isEnabled() { return enabled; }
    public StreamerCharacter character() { return character; }

    public void setEnabled(boolean value) {
        if (com.randomrun.features.battle.manager.BattleManager.getInstance().getCurrentRoom() != null) return;
        enabled = value;
        changed();
    }


    public void choose(StreamerCharacter value) {
        if (com.randomrun.features.battle.manager.BattleManager.getInstance().getCurrentRoom() != null) return;
        character = value;
        enabled = true;
        changed();
    }




    public String encode() {
        return "3;" + character.name();
    }


    public static StreamerProfile decode(String data) {
        if (data == null || data.length() > 100) return null;
        String[] p = data.split(";", -1);
        if (p.length < 2 || !("3".equals(p[0]) || "2".equals(p[0]))) return null;
        StreamerCharacter character = StreamerCharacter.byName(p[1]);
        if (character == null) return null;
        StreamerProfile out = new StreamerProfile();
        out.enabled = true;
        out.character = character;
        return out;
    }
}
