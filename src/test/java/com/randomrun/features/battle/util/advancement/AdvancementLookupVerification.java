package com.randomrun.features.battle.util.advancement;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

public final class AdvancementLookupVerification {
    private AdvancementLookupVerification() {}

    public static int verify() {
        int checks = 0;
        Identifier id = Identifier.withDefaultNamespace("story/mine_stone");
        try (var stream = AdvancementLookupVerification.class.getResourceAsStream("/data/minecraft/advancement/story/mine_stone.json")) {
            require(stream != null, "native 26.3 singular advancement resource is available"); checks++;
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var info = AdvancementLoader.parseInfo(id, JsonParser.parseReader(reader).getAsJsonObject());
                require(info != null && info.id.equals(id) && info.icon.is(Items.WOODEN_PICKAXE), "26.3 icon id schema is retained"); checks++;
            }
            var legacy = AdvancementLoader.parseInfo(id, JsonParser.parseString(
                "{\"display\":{\"title\":{\"text\":\"Title\"},\"description\":{\"text\":\"Description\"},\"icon\":{\"item\":\"minecraft:stone\"}}}").getAsJsonObject());
            require(legacy.icon.is(Items.STONE) && legacy.title.getString().equals("Title"), "legacy icon item schema still works"); checks++;
            require(AdvancementLoader.parseInfo(id, JsonParser.parseString("{}").getAsJsonObject()) == null,
                "non-display advancement has no notification"); checks++;

            AdvancementLoader.clearCache();
            var missing = AdvancementLoader.getInfoAsync("minecraft:randomrun_missing_startup_check");
            require(missing == AdvancementLoader.getInfoAsync("minecraft:randomrun_missing_startup_check"), "concurrent misses share one lookup"); checks++;
            require(missing.get(5, TimeUnit.SECONDS) == null, "missing lookup completes without blocking the render thread"); checks++;
            require(missing == AdvancementLoader.getInfoAsync("minecraft:randomrun_missing_startup_check"), "empty result is cached"); checks++;
            AdvancementLoader.clearCache();
            require(missing != AdvancementLoader.getInfoAsync("minecraft:randomrun_missing_startup_check"), "cache clear replaces lookup epoch"); checks++;
            for (String invalid : new String[]{"not valid", "minecraft:../outside", "other:story/root", "minecraft:recipes/test"}) {
                require(AdvancementLoader.getInfoAsync(invalid).getNow(null) == null, "invalid/non-display lookup is immediate"); checks++;
            }
        } catch (Exception error) { throw new AssertionError(error); }
        finally { AdvancementLoader.clearCache(); }
        return checks;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
