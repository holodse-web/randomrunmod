package com.randomrun.features.voice.control;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** World lookups for voice commands: what "дерево" or "кирка" means in game terms. */
final class VoiceWorld {
    static final String[] TOOL_TIERS = {"netherite", "diamond", "iron", "copper", "stone", "golden", "wooden"};
    static final String[] ARMOR_TIERS = {"netherite", "diamond", "iron", "copper", "chainmail", "golden", "leather"};

    /** A block or a living thing we can turn to. */
    record Found(BlockPos pos, Entity entity, Vec3 point, double distance) {
        boolean isEntity() { return entity != null; }
    }

    private VoiceWorld() {}

    static boolean isEntityTarget(String key) {
        return switch (key) {
            case "cow", "pig", "sheep", "chicken", "zombie", "skeleton", "creeper", "spider", "enderman",
                 "villager", "hostile", "animal", "item", "player" -> true;
            default -> false;
        };
    }

    static Predicate<BlockState> blockMatcher(String key) {
        return switch (key) {
            case "tree" -> s -> isTag(s, BlockTags.LOGS);
            case "leaves" -> s -> isTag(s, BlockTags.LEAVES);
            case "stone" -> s -> isTag(s, BlockTags.BASE_STONE_OVERWORLD) || idPath(s).contains("cobblestone");
            case "dirt" -> s -> isTag(s, BlockTags.DIRT);
            case "sand" -> s -> isTag(s, BlockTags.SAND);
            case "gravel" -> s -> idPath(s).equals("gravel");
            case "coal" -> s -> idPath(s).endsWith("coal_ore");
            case "iron_ore" -> s -> idPath(s).endsWith("iron_ore");
            case "diamond_ore" -> s -> idPath(s).endsWith("diamond_ore");
            case "gold_ore" -> s -> idPath(s).endsWith("gold_ore");
            case "water" -> s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource();
            case "crafting_table" -> s -> idPath(s).equals("crafting_table");
            case "furnace" -> s -> idPath(s).equals("furnace") || idPath(s).equals("blast_furnace") || idPath(s).equals("smoker");
            case "chest" -> s -> idPath(s).equals("chest") || idPath(s).equals("trapped_chest") || idPath(s).equals("barrel");
            case "bed" -> s -> isTag(s, BlockTags.BEDS);
            case "door" -> s -> isTag(s, BlockTags.DOORS);
            case "trapdoor" -> s -> isTag(s, BlockTags.TRAPDOORS);
            default -> s -> false;
        };
    }

    static Predicate<Entity> entityMatcher(String key) {
        return switch (key) {
            case "hostile" -> e -> e instanceof Enemy;
            case "animal" -> e -> e instanceof Animal;
            case "zombie" -> e -> entityPath(e).contains("zombie") || entityPath(e).equals("husk") || entityPath(e).equals("drowned");
            case "skeleton" -> e -> entityPath(e).contains("skeleton") || entityPath(e).equals("stray") || entityPath(e).equals("bogged");
            case "spider" -> e -> entityPath(e).endsWith("spider");
            case "item" -> e -> e instanceof net.minecraft.world.entity.item.ItemEntity;
            default -> e -> entityPath(e).equals(key);
        };
    }

    private static boolean isTag(BlockState state, TagKey<Block> tag) {
        return state.is(tag, s -> true);
    }

    static String idPath(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    private static String entityPath(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    /**
     * Nearest visible match. Prefers anything in reach; otherwise the nearest
     * one we can see within {@code range}, so the player knows where to walk.
     */
    static Found find(LocalPlayer player, String key, double range) {
        return isEntityTarget(key) ? findEntity(player, key, range) : findBlock(player, key, range);
    }

    static Found findEntity(LocalPlayer player, String key, double range) {
        List<Found> all = findAllEntities(player, key, range);
        return all.isEmpty() ? null : all.get(0);
    }

    static Found findBlock(LocalPlayer player, String key, double range) {
        List<Found> all = findAllBlocks(player, key, range, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    /** Every visible match within range, nearest first. */
    static List<Found> findAll(LocalPlayer player, String key, double range) {
        return isEntityTarget(key) ? findAllEntities(player, key, range) : findAllBlocks(player, key, range, 48);
    }

    static List<Found> findAllEntities(LocalPlayer player, String key, double range) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Predicate<Entity> matcher = entityMatcher(key);
        AABB box = player.getBoundingBox().inflate(range);
        List<Found> out = new ArrayList<>();
        for (Entity entity : level.getEntities(player, box, e -> e.isAlive() && (e instanceof LivingEntity || e instanceof net.minecraft.world.entity.item.ItemEntity) && matcher.test(e))) {
            Vec3 point = entity.getBoundingBox().getCenter();
            double distance = eye.distanceTo(point);
            if (distance > range || !canSee(level, player, eye, point, null)) continue;
            out.add(new Found(entity.blockPosition(), entity, point, distance));
        }
        out.sort(Comparator.comparingDouble(Found::distance));
        return out;
    }

    static List<Found> findAllBlocks(LocalPlayer player, String key, double range, int limit) {
        Level level = player.level();
        Predicate<BlockState> matcher = blockMatcher(key);
        Vec3 eye = player.getEyePosition();
        BlockPos origin = BlockPos.containing(eye);
        int r = (int) Math.ceil(range);
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (matcher.test(level.getBlockState(cursor))) candidates.add(cursor.immutable());
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(eye)));
        boolean fluid = key.equals("water");
        List<Found> out = new ArrayList<>();
        int checked = 0;
        for (BlockPos pos : candidates) {
            if (++checked > 400 || out.size() >= limit) break;
            Vec3 point = aimPoint(level, eye, pos);
            if (point == null) continue;
            if (!fluid && !canSee(level, player, eye, point, pos)) continue;
            out.add(new Found(pos, null, point, eye.distanceTo(point)));
        }
        return out;
    }

    /** Center of the face that points at the player, so the crosshair lands on the block itself. */
    private static Vec3 aimPoint(Level level, Vec3 eye, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        Vec3 toEye = eye.subtract(center);
        double ax = Math.abs(toEye.x), ay = Math.abs(toEye.y), az = Math.abs(toEye.z);
        double nudge = 0.42;
        if (ax >= ay && ax >= az) return center.add(Math.signum(toEye.x) * nudge, 0, 0);
        if (ay >= ax && ay >= az) return center.add(0, Math.signum(toEye.y) * nudge, 0);
        return center.add(0, 0, Math.signum(toEye.z) * nudge);
    }

    private static boolean canSee(Level level, Entity viewer, Vec3 from, Vec3 to, BlockPos expected) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, viewer));
        if (hit.getType() == HitResult.Type.MISS) return true;
        if (expected != null && hit.getBlockPos().equals(expected)) return true;
        return hit.getLocation().distanceToSqr(from) >= to.distanceToSqr(from) - 0.05;
    }

    /** Yaw/pitch that point the camera from {@code eye} at {@code point}. */
    static float[] anglesTo(Vec3 eye, Vec3 point) {
        double dx = point.x - eye.x, dy = point.y - eye.y, dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90f;
        float pitch = (float) -(Mth.atan2(dy, horizontal) * (180 / Math.PI));
        return new float[]{yaw, Mth.clamp(pitch, -90f, 90f)};
    }

    // ---------------------------------------------------------------- items

    /** Candidate items for a spoken name, best first. Unknown ids are skipped. */
    static List<Item> items(String key, String tier) {
        List<String> ids = new ArrayList<>();
        switch (key) {
            case "pickaxe", "axe", "shovel", "sword", "hoe" -> {
                for (String t : TOOL_TIERS) if (tier == null || tier.equals(t)) ids.add(t + "_" + key);
            }
            case "helmet", "chestplate", "leggings", "boots" -> {
                for (String t : ARMOR_TIERS) if (tier == null || tier.equals(t)) ids.add(t + "_" + key);
            }
            case "planks" -> collect(ids, path -> path.endsWith("_planks"));
            case "boat" -> collect(ids, path -> path.endsWith("_boat") && !path.contains("chest"));
            case "bed" -> collect(ids, path -> path.endsWith("_bed"));
            case "food" -> {
                List<Item> food = new ArrayList<>();
                for (Item item : BuiltInRegistries.ITEM) if (new ItemStack(item).has(DataComponents.FOOD)) food.add(item);
                return food;
            }
            default -> ids.add(key);
        }
        List<Item> out = new ArrayList<>();
        for (String id : ids) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id));
            item.ifPresent(out::add);
        }
        return out;
    }

    private static void collect(List<String> ids, Predicate<String> filter) {
        for (Item item : BuiltInRegistries.ITEM) {
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (filter.test(path)) ids.add(path);
        }
    }

    /** Items that a spoken block or mob stands for in item context: "дерево" -> logs, "корова" -> beef. */
    static List<Item> targetItems(String key) {
        List<String> ids = new ArrayList<>();
        switch (key) {
            case "tree" -> collect(ids, path -> path.endsWith("_log") || path.endsWith("_stem"));
            case "leaves" -> collect(ids, path -> path.endsWith("_leaves"));
            case "stone" -> ids.addAll(List.of("cobblestone", "stone", "cobbled_deepslate"));
            case "dirt" -> ids.addAll(List.of("dirt", "grass_block"));
            case "sand" -> ids.addAll(List.of("sand", "red_sand"));
            case "gravel" -> ids.addAll(List.of("gravel", "flint"));
            case "coal" -> ids.addAll(List.of("coal", "charcoal"));
            case "iron_ore" -> ids.addAll(List.of("iron_ingot", "raw_iron", "iron_ore"));
            case "diamond_ore" -> ids.addAll(List.of("diamond", "diamond_ore"));
            case "gold_ore" -> ids.addAll(List.of("gold_ingot", "raw_gold", "gold_ore"));
            case "water" -> ids.add("water_bucket");
            case "cow" -> ids.addAll(List.of("beef", "leather"));
            case "pig" -> ids.add("porkchop");
            case "sheep" -> ids.addAll(List.of("mutton", "white_wool"));
            case "chicken" -> ids.addAll(List.of("chicken", "feather", "egg"));
            case "zombie" -> ids.add("rotten_flesh");
            case "skeleton" -> ids.addAll(List.of("bone", "arrow"));
            case "creeper" -> ids.add("gunpowder");
            case "spider" -> ids.addAll(List.of("string", "spider_eye"));
            case "enderman" -> ids.add("ender_pearl");
            case "bed" -> collect(ids, path -> path.endsWith("_bed"));
            default -> ids.add(key);
        }
        List<Item> out = new ArrayList<>();
        for (String id : ids) BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(id)).ifPresent(out::add);
        return out;
    }

    /**
     * Items whose shown name matches spoken words that the lexicon does not
     * know ("тотем", "незерит"), so any item can be named, in the game's language.
     */
    static List<Item> itemsByWords(String words) {
        List<Item> out = new ArrayList<>();
        if (words == null || words.isBlank()) return out;
        List<String> stems = new ArrayList<>();
        for (String word : com.randomrun.features.voice.command.VoiceLexicon.normalize(words).split("\\s+")) {
            if (word.length() < 4) continue;
            stems.add(word.substring(0, Math.max(4, word.length() - 2)));
        }
        if (stems.isEmpty()) return out;
        int bestScore = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            String name = com.randomrun.features.voice.command.VoiceLexicon.normalize(new ItemStack(item).getHoverName().getString());
            int score = 0;
            for (String stem : stems) {
                for (String part : name.split("\\s+")) if (part.startsWith(stem)) { score++; break; }
            }
            if (score == 0 || score < bestScore) continue;
            if (score > bestScore) { bestScore = score; out.clear(); }
            out.add(item);
        }
        out.sort(Comparator.comparingInt(item -> new ItemStack(item).getHoverName().getString().length()));
        return out;
    }

    /** Everything the words could mean as items: lexicon item, then block/mob, then shown names. */
    static List<Item> resolveItems(String item, String tier, String target, String words) {
        java.util.LinkedHashSet<Item> out = new java.util.LinkedHashSet<>();
        if (item != null) out.addAll(items(item, tier));
        if (target != null) out.addAll(targetItems(target));
        if (out.isEmpty()) out.addAll(itemsByWords(words));
        return new ArrayList<>(out);
    }

    /** Inventory slot (0..35) holding the best candidate, or -1. */
    static int findSlot(Inventory inventory, List<Item> candidates) {
        for (Item wanted : candidates) {
            int hotbarHit = -1, mainHit = -1;
            for (int slot = 0; slot < 36; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty() || stack.getItem() != wanted) continue;
                if (slot < 9) { hotbarHit = slot; break; }
                if (mainHit < 0) mainHit = slot;
            }
            if (hotbarHit >= 0) return hotbarHit;
            if (mainHit >= 0) return mainHit;
        }
        return -1;
    }

    static int findFoodSlot(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        int best = -1;
        float bestValue = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.getItem(slot);
            var food = stack.get(DataComponents.FOOD);
            if (food == null) continue;
            float value = food.nutrition() + food.saturation() + (slot < 9 ? 0.5f : 0f);
            if (value > bestValue) {
                bestValue = value;
                best = slot;
            }
        }
        return best;
    }

    static Minecraft mc() {
        return Minecraft.getInstance();
    }
}
