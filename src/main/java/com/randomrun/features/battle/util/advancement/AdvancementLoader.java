package com.randomrun.features.battle.util.advancement;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.util.PreviewItems;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class AdvancementLoader {
    public static class AdvancementInfo {
        public final Identifier id;
        public final Component title;
        public final Component description;
        public final ItemStack icon;

        public AdvancementInfo(Identifier id, Component title, Component description, ItemStack icon) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.icon = icon;
        }
    }

    private static final Executor IO = Executors.newVirtualThreadPerTaskExecutor();
    private static final String[] DIRECTORIES = {"advancement", "advancements"};
    private static volatile Cache cache = new Cache();

    private static final class Cache {
        final Map<Identifier, CompletableFuture<AdvancementInfo>> entries = new ConcurrentHashMap<>();
        List<AdvancementInfo> all;
    }

    public static CompletableFuture<AdvancementInfo> getInfoAsync(String achievementId) {
        Identifier id = achievementId == null ? null : Identifier.tryParse(achievementId);
        if (id == null || !id.getNamespace().equals("minecraft") || id.getPath().startsWith("recipes/")
                || id.getPath().startsWith("/") || id.getPath().contains("..")) {
            return CompletableFuture.completedFuture(null);
        }
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.isSameThread() && client.getConnection() != null) {
            var entry = client.getConnection().getAdvancements().get(id);
            if (entry != null && entry.value().display().isPresent()) {
                var display = entry.value().display().get();
                return CompletableFuture.completedFuture(new AdvancementInfo(id, display.title(), display.description(), display.icon().create()));
            }
        }
        Cache current = cache;
        return current.entries.computeIfAbsent(id, key -> CompletableFuture.supplyAsync(() -> loadInfo(key), IO));
    }

    public static AdvancementInfo getInfo(String achievementId) {
        return getInfoAsync(achievementId).join();
    }

    public static void clearCache() { cache = new Cache(); }

    private static AdvancementInfo loadInfo(Identifier id) {
        try {
            for (String directory : DIRECTORIES) {
                String resource = "/data/minecraft/" + directory + "/" + id.getPath() + ".json";
                try (var stream = AdvancementLoader.class.getResourceAsStream(resource)) {
                    if (stream != null) {
                        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                            return parseInfo(id, JsonParser.parseReader(reader).getAsJsonObject());
                        }
                    }
                }
            }
            var minecraft = FabricLoader.getInstance().getModContainer("minecraft");
            if (minecraft.isPresent()) {
                for (Path root : minecraft.get().getRootPaths()) {
                    for (String directory : DIRECTORIES) {
                        Path base = root.resolve("data/minecraft/" + directory).normalize();
                        Path file = base.resolve(id.getPath() + ".json").normalize();
                        if (!file.startsWith(base) || !Files.isRegularFile(file)) continue;
                        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                            return parseInfo(id, JsonParser.parseReader(reader).getAsJsonObject());
                        }
                    }
                }
            }
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                for (String directory : DIRECTORIES) {
                    var resource = client.getResourceManager().getResource(
                        Identifier.fromNamespaceAndPath("minecraft", directory + "/" + id.getPath() + ".json"));
                    if (resource.isEmpty()) continue;
                    try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                        return parseInfo(id, JsonParser.parseReader(reader).getAsJsonObject());
                    }
                }
            }
        } catch (Exception error) {
            RandomRunMod.LOGGER.debug("[AdvancementLoader] Could not load {}", id, error);
        }
        return null;
    }

    public static List<AdvancementInfo> getAdvancements() {
        Cache current = cache;
        synchronized (current) {
            if (current.all != null) return current.all;
            List<AdvancementInfo> loaded = new ArrayList<>();
            try {
                var minecraft = FabricLoader.getInstance().getModContainer("minecraft");
                if (minecraft.isPresent()) {
                    for (Path root : minecraft.get().getRootPaths()) {
                        for (String directory : DIRECTORIES) {
                            Path base = root.resolve("data/minecraft/" + directory);
                            if (!Files.isDirectory(base)) continue;
                            try (var paths = Files.walk(base)) {
                                paths.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
                                    String relative = base.relativize(path).toString().replace('\\', '/');
                                    if (relative.startsWith("recipes/")) return;
                                    Identifier id = Identifier.fromNamespaceAndPath("minecraft", relative.substring(0, relative.length() - 5));
                                    AdvancementInfo info = current.entries.computeIfAbsent(id,
                                        key -> CompletableFuture.supplyAsync(() -> loadInfo(key), IO)).join();
                                    if (info != null) loaded.add(info);
                                });
                            }
                            if (!loaded.isEmpty()) break;
                        }
                        if (!loaded.isEmpty()) break;
                    }
                }
                if (loaded.isEmpty() && Minecraft.getInstance() != null) {
                    for (String directory : DIRECTORIES) {
                        Minecraft.getInstance().getResourceManager().listResources(directory, id -> id.getPath().endsWith(".json"))
                            .forEach((resource, value) -> {
                                if (!resource.getNamespace().equals("minecraft")) return;
                                String path = resource.getPath().substring(directory.length() + 1);
                                Identifier id = Identifier.fromNamespaceAndPath("minecraft", path.substring(0, path.length() - 5));
                                if (id.getPath().startsWith("recipes/")) return;
                                AdvancementInfo info = current.entries.computeIfAbsent(id,
                                    key -> CompletableFuture.supplyAsync(() -> loadInfo(key), IO)).join();
                                if (info != null) loaded.add(info);
                            });
                        if (!loaded.isEmpty()) break;
                    }
                }
            } catch (Exception error) {
                RandomRunMod.LOGGER.debug("[AdvancementLoader] Could not enumerate advancements", error);
            }
            current.all = List.copyOf(loaded);
            return current.all;
        }
    }

    static AdvancementInfo parseInfo(Identifier id, JsonObject json) {
        if (!json.has("display")) return null;
        JsonObject display = json.getAsJsonObject("display");
        JsonObject icon = display.getAsJsonObject("icon");
        JsonElement item = icon.has("id") ? icon.get("id") : icon.get("item");
        ItemStack stack = PreviewItems.stack(BuiltInRegistries.ITEM.getValue(Identifier.parse(item.getAsString())));
        return new AdvancementInfo(id, parseText(display.get("title")), parseText(display.get("description")), stack);
    }

    private static Component parseText(JsonElement element) {
        if (element == null) return Component.empty();
        if (element.isJsonPrimitive()) return Component.literal(element.getAsString());
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("translate")) return Component.translatable(object.get("translate").getAsString());
            if (object.has("text")) return Component.literal(object.get("text").getAsString());
        }
        return Component.literal(element.toString());
    }
}
