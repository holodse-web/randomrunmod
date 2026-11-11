package com.randomrun.infrastructure.security;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.infrastructure.util.CompactLogger;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

public class ModScannerService {
    public enum Reason {
        CHEAT_CLIENT("randomrun.modblock.reason.client"),
        XRAY_MOD("randomrun.modblock.reason.xray"),
        CHEAT_MOD("randomrun.modblock.reason.mod"),
        MAP("randomrun.modblock.reason.map"),
        HELPER("randomrun.modblock.reason.helper"),
        XRAY_PACK("randomrun.modblock.reason.pack");

        public final String key;

        Reason(String key) {
            this.key = key;
        }
    }

    private static final Set<String> CHEAT_CLIENTS = Set.of(
        "wurst", "meteor-client", "meteorclient", "liquidbounce", "aristois", "bleachhack",
        "coffeeclient", "thunderhack", "rusherhack", "futureclient", "kamiblue", "kami",
        "salhack", "phobos", "konas", "gamesense", "3arthh4ck", "earthhack", "cornos",
        "wwe", "wweclient", "aoba", "boze", "inertiaclient", "lambda-client", "mioclient",
        "prestigeclient", "nhack", "skyblock-cheats"
    );

    private static final Set<String> CHEAT_TOKENS = Set.of(
        "baritone", "freecam", "killaura", "autoclicker", "triggerbot", "aimbot",
        "seedcracker", "seedcrackerx", "wallhack", "esp", "nofall", "autocrystal", "scaffoldwalk"
    );

    private static final Set<String> MAP_MODS = Set.of(
        "xaerominimap", "xaerominimapfair", "xaeroworldmap", "journeymap", "voxelmap", "voxelmap-updated",
        "ftbchunks", "antiqueatlas", "antique_atlas", "mapfrontiers", "simplemap"
    );

    private static final Set<String> HELPER_MODS = Set.of(
        "jei", "roughlyenoughitems", "emi", "minihud", "tweakeroo", "itemscroller", "inventoryprofilesnext",
        "mousetweaks", "invmove", "autototem", "naturescompass", "explorerscompass", "seedmapper", "worldedit",
        "ok_zoomer", "okzoomer", "zoomify", "logical_zoom", "wi_zoom", "fullbright", "gammautils", "gamma-utils"
    );

    private static final List<String> XRAY_MARKERS = List.of("xray", "x-ray", "orefinder", "ore-finder", "cavefinder", "oredetector");

    private static final List<String> PACK_NAME_MARKERS = List.of(
        "xray", "x-ray", "orefinder", "ore-finder", "orehighlight", "ore-highlight", "oreoutline", "ore-outline",
        "wallhack", "see-through", "seethrough"
    );

    private static final List<String> PROBE_BLOCKS = List.of(
        "stone", "deepslate", "netherrack", "dirt", "granite", "diorite", "andesite", "tuff", "gravel", "end_stone"
    );

    private static final float TRANSPARENT_SHARE = 0.25f;

    private static class Holder {
        private static final ModScannerService INSTANCE = new ModScannerService();
    }

    public static ModScannerService getInstance() {
        return Holder.INSTANCE;
    }

    private final Map<String, Reason> mods = new LinkedHashMap<>();
    private final Map<String, Reason> packs = new LinkedHashMap<>();
    private String packKey = "";
    private boolean scanned;

    public synchronized void scan() {
        mods.clear();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
            Reason reason = classify(id, mod.getMetadata().getName().toLowerCase(Locale.ROOT));
            if (reason == null) continue;
            String label = mod.getMetadata().getName() + " (" + id + ")";
            mods.put(label, reason);
            CompactLogger.warn("Blacklisted mod: {}", label);
        }
        packKey = "";
        refreshPacks();
        scanned = true;
        if (isTrusted()) CompactLogger.security("Trusted mode");
        else CompactLogger.warn("Untrusted mode: {} mods, {} packs", mods.size(), packs.size());
    }

    private static Reason classify(String id, String name) {
        if (CHEAT_CLIENTS.contains(id)) return Reason.CHEAT_CLIENT;
        for (String marker : XRAY_MARKERS) if (id.contains(marker) || name.contains(marker)) return Reason.XRAY_MOD;
        for (String token : id.split("[-_.]")) if (CHEAT_TOKENS.contains(token)) return Reason.CHEAT_MOD;
        for (String token : CHEAT_TOKENS) if (token.length() > 5 && id.contains(token)) return Reason.CHEAT_MOD;
        if (MAP_MODS.contains(id) || id.contains("minimap") || id.contains("worldmap") || name.contains("minimap") || name.contains("world map")) return Reason.MAP;
        if (HELPER_MODS.contains(id) || id.contains("fullbright") || id.contains("zoom")) return Reason.HELPER;
        return null;
    }

    public synchronized void refreshPacks() {
        List<String> selected = selectedPacks();
        File dir = FabricLoader.getInstance().getGameDir().resolve("resourcepacks").toFile();
        StringBuilder key = new StringBuilder();
        List<File> files = new ArrayList<>();
        for (String id : selected) {
            if (!id.startsWith("file/")) continue;
            File file = new File(dir, id.substring(5));
            if (!file.exists()) continue;
            files.add(file);
            key.append(id).append(':').append(file.lastModified()).append(';');
        }
        if (key.toString().equals(packKey)) return;
        packKey = key.toString();
        packs.clear();
        for (File file : files) {
            if (isXrayPack(file)) {
                packs.put(file.getName(), Reason.XRAY_PACK);
                CompactLogger.warn("X-ray resource pack: {}", file.getName());
            }
        }
    }

    private static List<String> selectedPacks() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null) return new ArrayList<>(minecraft.options.resourcePacks);
        List<String> result = new ArrayList<>();
        Path options = FabricLoader.getInstance().getGameDir().resolve("options.txt");
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (!line.startsWith("resourcePacks:")) continue;
                JsonElement value = JsonParser.parseString(line.substring("resourcePacks:".length()));
                if (value.isJsonArray()) for (JsonElement entry : value.getAsJsonArray()) result.add(entry.getAsString());
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private boolean isXrayPack(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
        for (String marker : PACK_NAME_MARKERS) if (name.contains(marker)) return true;
        try {
            PackReader reader = file.isDirectory() ? new FolderReader(file.toPath()) : new ZipReader(new ZipFile(file));
            try (reader) {
                for (String block : PROBE_BLOCKS) {
                    if (transparent(reader.read("assets/minecraft/textures/block/" + block + ".png"))) return true;
                    if (emptyModel(reader.read("assets/minecraft/models/block/" + block + ".json"))) return true;
                    if (hiddenState(reader.read("assets/minecraft/blockstates/" + block + ".json"))) return true;
                }
            }
        } catch (Exception error) {
            CompactLogger.warn("Pack scan error {}: {}", file.getName(), error.getMessage());
        }
        return false;
    }

    private static boolean transparent(byte[] data) {
        if (data == null) return false;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null || !image.getColorModel().hasAlpha()) return false;
            int width = image.getWidth(), height = Math.min(image.getHeight(), image.getWidth());
            int clear = 0;
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) if ((image.getRGB(x, y) >>> 24) < 200) clear++;
            return clear > width * height * TRANSPARENT_SHARE;
        } catch (Exception error) {
            return false;
        }
    }

    private static boolean emptyModel(byte[] data) {
        if (data == null) return false;
        try {
            JsonObject model = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            if (model.has("elements")) {
                JsonArray elements = model.getAsJsonArray("elements");
                return elements.isEmpty();
            }
            return !model.has("parent") && !model.has("loader");
        } catch (Exception error) {
            return false;
        }
    }

    private static boolean hiddenState(byte[] data) {
        if (data == null) return false;
        String text = new String(data, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return text.contains("block/air\"") || text.contains("block/barrier\"") || text.contains("block/glass\"");
    }

    private interface PackReader extends AutoCloseable {
        byte[] read(String path) throws Exception;

        @Override
        void close() throws Exception;
    }

    private record ZipReader(ZipFile zip) implements PackReader {
        @Override
        public byte[] read(String path) throws Exception {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null || entry.getSize() > 4_000_000) return null;
            try (InputStream input = zip.getInputStream(entry)) {
                return input.readNBytes(4_000_000);
            }
        }

        @Override
        public void close() throws Exception {
            zip.close();
        }
    }

    private record FolderReader(Path root) implements PackReader {
        @Override
        public byte[] read(String path) throws Exception {
            Path file = root.resolve(path);
            if (!Files.isRegularFile(file) || Files.size(file) > 4_000_000) return null;
            return Files.readAllBytes(file);
        }

        @Override
        public void close() {
        }
    }

    public void rescan() {
        scan();
    }

    public boolean isScanned() {
        return scanned;
    }

    public synchronized boolean isTrusted() {
        return scanned && mods.isEmpty() && packs.isEmpty();
    }

    public synchronized boolean hasSuspiciousMods() {
        return !mods.isEmpty();
    }

    public synchronized boolean hasSuspiciousResourcePacks() {
        return !packs.isEmpty();
    }

    public synchronized List<String> getSuspiciousMods() {
        return new ArrayList<>(mods.keySet());
    }

    public synchronized List<String> getSuspiciousResourcePacks() {
        return new ArrayList<>(packs.keySet());
    }

    public synchronized Map<String, Reason> findings() {
        Map<String, Reason> result = new LinkedHashMap<>(mods);
        result.putAll(packs);
        return result;
    }
}
