package com.randomrun.features.streamer;

import com.randomrun.core.run.RunSessionManager;
import com.randomrun.features.streamer.StreamerCharacter.Event;
import com.randomrun.features.streamer.StreamerCharacter.Phrase;
import com.randomrun.ui.screen.endgame.EndgameScreen.DefeatScreen;
import com.randomrun.ui.screen.endgame.VictoryScreen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.Ocelot;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;





public final class StreamerPhrases {
    private static final long MIN_GAP_MS = 2500;

    private static final long MOB_GAP_MS = 15_000;
    private static final double SCAN_RADIUS = 16;
    private static final Random RANDOM = new Random();


    private static final List<Event> MOB_EVENTS = List.of(Event.DRAGON, Event.CREEPER, Event.SHULKER, Event.WITCH,
        Event.JOCKEY, Event.HORDE, Event.BABIES, Event.IRON_GOLEM, Event.ENDERMAN, Event.SKELETON, Event.ZOMBIE,
        Event.SPIDER, Event.SILVERFISH, Event.ENDERMITE, Event.MOB_WATER, Event.MOB_FIRE, Event.MOB_STUCK, Event.FISH,
        Event.SQUID, Event.PIGLIN, Event.VILLAGER, Event.PLAYER, Event.PANDA, Event.DOLPHIN, Event.ARMADILLO, Event.FOX, Event.PARROT, Event.CAT, Event.BEE, Event.BAT,
        Event.CHICKEN, Event.SHEEP, Event.PIG, Event.NEUTRAL, Event.ANIMALS);

    private static final Set<Holder<MobEffect>> BAD_EFFECTS = Set.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.HUNGER,
        MobEffects.NAUSEA, MobEffects.BLINDNESS, MobEffects.LEVITATION);

    private static final Map<Event, Long> READY_AT = new EnumMap<>(Event.class);

    private static final Map<Event, Deque<Phrase>> DECKS = new EnumMap<>(Event.class);
    private static Set<Event> seen = EnumSet.noneOf(Event.class);
    private static Set<Holder<MobEffect>> badEffects = new HashSet<>();
    private static final Map<Integer, Long> SWELLING = new HashMap<>();
    private static final Map<Integer, double[]> STUCK = new HashMap<>();

    private static boolean wasRunning;
    private static float lastHealth = -1;
    private static long lastSaid;
    private static long lastMobSaid;
    private static long lastSwear;
    private static boolean wasInPortal;
    private static boolean lowSeen;
    private static boolean wasDrowning;

    private static long speakingUntil;
    private static long pickaxeCraftedAt;
    private static boolean minedStone;
    private static long undergroundSince;
    private static boolean caveLongSaid;
    private static long idleAt;
    private static int ticks;
    private static Screen lastScreen;
    private static boolean wasInWater = true;
    private static boolean wasUnderWater;
    private static long outOfWaterSince;
    private static boolean wasSleeping;
    private static boolean wasDark;
    private static boolean wasRaining;
    private static boolean wasThundering;
    private static boolean wasDead;
    private static boolean wasUnderground;
    private static boolean wasDeep;
    private static boolean wasInBoat;
    private static boolean wasFull;
    private static boolean wasHungry;
    private static boolean wasOnGround = true;
    private static int sprintTicks;
    private static int food = -1;
    private static int placed;
    private static int towered;
    private static long toweredAt;
    private static double airFall;
    private static double landedFall;
    private static long landedAt;
    private static boolean wasSelfie;
    private static boolean wasEating;
    private static long lastDamageSwear;
    private static long cameUpAt;
    private static long villageSince;
    private static long villageSeenAt;
    private static long villageLeftAt;
    private static int torches = -1;
    private static BlockPos lastStartPos;

    private static ItemStack brokenItem = ItemStack.EMPTY;
    private static ItemStack eatingItem = ItemStack.EMPTY;
    private static Item smeltedItem;
    private static Item craftedItem;
    private static int diamondsAtDeath;
    private static net.minecraft.resources.ResourceKey<Level> lastDimension;
    private static String lastBiome = "";
    private static final Map<Item, Integer> COUNTS = new HashMap<>();
    private static ItemStack lastMain = ItemStack.EMPTY;
    private static ItemStack lastOff = ItemStack.EMPTY;
    private static int lastSelected = -1;
    private static boolean[] armor = new boolean[4];
    private static Entity attacked;
    private static long attackedAt;

    private StreamerPhrases() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(StreamerPhrases::tick);
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> onBreak(player, pos, state));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide()) onStartBreak(player, pos, level.getBlockState(pos));
            return InteractionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) onPlace(player, player.getItemInHand(hand), level.getBlockState(hit.getBlockPos()), hit);
            return InteractionResult.PASS;
        });
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (level.isClientSide() && stack.has(DataComponents.FOOD) && !player.canEat(false)) maybe(Event.FULL, 1f, 180_000);
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && entity instanceof Animal && !player.getItemInHand(hand).isEmpty()) maybe(Event.FEED, 0.9f, 30_000);
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) {
                attacked = entity;
                attackedAt = System.currentTimeMillis();
            }
            return InteractionResult.PASS;
        });
    }




    public static void onDrop() {
        maybe(Event.DROP, 0.8f, 15_000);
    }


    public static void onContainerClick(AbstractContainerMenu menu, int slotId) {
        if (!enabled() || slotId < 0 || slotId >= menu.slots.size()) return;
        ItemStack result = menu.getSlot(slotId).getItem();
        if (result.isEmpty()) return;
        if ((menu instanceof CraftingMenu || menu instanceof InventoryMenu) && slotId == 0) {
            Item item = result.getItem();
            craftedItem = item;
            if (result.is(net.minecraft.tags.ItemTags.PICKAXES)) pickaxeCraftedAt = System.currentTimeMillis();
            if (item == Items.IRON_PICKAXE) say(Event.IRON_PICK, true);
            else if (isDiamondGear(item)) maybe(Event.DIAMOND_TOOL, 1f, 20_000);
            else if (result.has(DataComponents.EQUIPPABLE) && result.get(DataComponents.EQUIPPABLE).slot().isArmor()) maybe(Event.ARMOR, 1f, 30_000);
            else if (item == Items.CRAFTING_TABLE) craft(Event.CRAFT_TABLE);
            else if (item == Items.STONE_PICKAXE || item == Items.STONE_AXE || item == Items.STONE_SWORD || item == Items.STONE_SHOVEL) craft(Event.CRAFT_STONE);
            else if (result.is(net.minecraft.tags.ItemTags.PICKAXES)) craft(Event.CRAFT_PICKAXE);
            else if (item == Items.STICK) craft(Event.CRAFT_STICKS);
            else if (item == Items.FURNACE) craft(Event.CRAFT_FURNACE);
            else if (item == Items.CHEST) craft(Event.CRAFT_CHEST);
            else if (item == Items.COOKIE || item == Items.BREAD || item == Items.CAKE || item == Items.PUMPKIN_PIE) craft(Event.CRAFT_FOOD);
            else if (result.is(net.minecraft.tags.ItemTags.SWORDS) || result.is(net.minecraft.tags.ItemTags.AXES) || item == Items.SHIELD
                || item == Items.BOW || item == Items.ARROW || item == Items.CROSSBOW) craft(Event.CRAFT_WEAPON);
            else maybe(Event.CRAFT, 0.5f, 20_000);
        } else if (menu instanceof AbstractFurnaceMenu && slotId == 2) {
            Item item = result.getItem();
            smeltedItem = item;
            if (item == Items.COPPER_INGOT) maybe(Event.SMELT_COPPER, 1f, 30_000);
            else if (item == Items.IRON_INGOT) maybe(Event.SMELT_IRON, 1f, 30_000);
            else maybe(Event.SMELT, 0.9f, 20_000);
        }
    }


    private static void craft(Event event) {
        maybe(event, 0.9f, 60_000);
    }

    private static boolean isDiamondGear(Item item) {
        return item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_SWORD || item == Items.DIAMOND_AXE || item == Items.DIAMOND_SHOVEL
            || item == Items.DIAMOND_HELMET || item == Items.DIAMOND_CHESTPLATE || item == Items.DIAMOND_LEGGINGS || item == Items.DIAMOND_BOOTS;
    }


    private static void onBreak(Player player, BlockPos pos, BlockState state) {
        if (!enabled()) return;
        Block block = state.getBlock();
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) swearOr(Event.WOW, 0.3f, Event.DIAMOND, 1f, 8_000);
    }


    private static void onStartBreak(Player player, BlockPos pos, BlockState state) {
        if (!enabled() || state.isAir()) return;

        if (pos.equals(lastStartPos)) return;
        lastStartPos = pos.immutable();
        Block block = state.getBlock();
        if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) return;
        if (state.is(BlockTags.GOLD_ORES)) { ore(Event.ORE_GOLD, 1f); return; }
        if (state.is(BlockTags.IRON_ORES)) { ore(Event.ORE_IRON, 0.9f); return; }
        if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) { ore(Event.ORE_COAL, 0.8f); return; }
        if (state.is(BlockTags.COPPER_ORES)) { ore(Event.ORE_COPPER, 0.8f); return; }
        if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) { ore(Event.ORE_LAPIS, 1f); return; }
        if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) { ore(Event.ORE_REDSTONE, 1f); return; }
        if (state.is(BlockTags.ORES)) { ore(Event.ORE, 0.9f); return; }
        if (block == Blocks.IRON_BLOCK) { maybe(Event.IRON_BLOCK, 1f, 120_000); return; }
        if (state.is(BlockTags.BEDS)) { maybe(Event.BED_BREAK, 1f, 120_000); return; }
        if (state.is(BlockTags.LOGS)) {
            if (count(player, st -> st.is(net.minecraft.tags.ItemTags.LOGS)) == 0) maybe(Event.TREE_FIRST, 1f, 120_000);
            else if (pos.getY() >= player.getBlockY() + 4) maybe(Event.TREE_HIGH, 1f, 300_000);
            else maybe(Event.TREE, 0.7f, 25_000);
            return;
        }
        if (block == Blocks.MOSS_BLOCK) { maybe(Event.MOSS, 1f, 300_000); return; }
        if (block instanceof CropBlock crop && crop.isMaxAge(state)) { maybe(Event.HARVEST, 0.8f, 40_000); return; }
        if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
            long now = System.currentTimeMillis();
            if (!minedStone) { minedStone = true; maybe(Event.FIRST_STONE, 1f, 600_000); return; }
            if (pickaxeCraftedAt > 0 && now - pickaxeCraftedAt < 90_000) { pickaxeCraftedAt = 0; maybe(Event.MINE_NEWPICK, 1f, 120_000); return; }
            if (block != Blocks.STONE && RANDOM.nextFloat() < 0.3f) { maybe(Event.STRANGE_STONE, 1f, 180_000); return; }
            BlockPos feet = player.blockPosition();
            if (pos.getX() == feet.getX() && pos.getZ() == feet.getZ()) {
                if (pos.getY() < feet.getY()) { maybe(Event.DIG_DOWN, 0.7f, 60_000); return; }
                if (pos.getY() > feet.getY() + 1 && wasUnderground) { maybe(Event.DIG_UP, 0.7f, 90_000); return; }
            }
            maybe(Event.MINE, 0.25f, 25_000);
        }
    }


    private static void ore(Event event, float chance) {
        if (RANDOM.nextFloat() < 0.25f) maybe(Event.ORE, chance, 15_000);
        else maybe(event, chance, 15_000);
    }

    private static void onPlace(net.minecraft.world.entity.player.Player player, ItemStack stack, BlockState target,
                                net.minecraft.world.phys.BlockHitResult hit) {
        if (!enabled() || stack.isEmpty()) return;
        Item item = stack.getItem();
        if (stack.is(Items.WHEAT_SEEDS) || stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT_SEEDS)) {
            if (target.is(Blocks.FARMLAND)) maybe(Event.PLANT, 0.8f, 60_000);
            return;
        }
        if (!(item instanceof BlockItem blockItem)) return;
        BlockState placedState = blockItem.getBlock().defaultBlockState();
        if (placedState.is(BlockTags.BEDS)) { maybe(Event.BED_PLACE, 1f, 30_000); return; }
        if (stack.is(Items.TORCH) || stack.is(Items.SOUL_TORCH)) { maybe(Event.TORCH, 0.6f, 40_000); return; }
        if (stack.is(Items.OBSIDIAN)) { maybe(Event.OBSIDIAN, 0.8f, 120_000); return; }

        BlockPos at = hit.getBlockPos().relative(hit.getDirection());
        if (hit.getDirection() == net.minecraft.core.Direction.UP && !player.onGround()
            && at.getX() == player.blockPosition().getX() && at.getZ() == player.blockPosition().getZ()) {
            long now = System.currentTimeMillis();
            towered = now - toweredAt < 3000 ? towered + 1 : 1;
            toweredAt = now;
            if (towered >= 3) { towered = 0; maybe(Event.TOWER, 0.8f, 90_000); }
        }
        if (++placed >= 8) {
            placed = 0;
            maybe(Event.BUILD, 0.8f, 40_000);
        }
    }



    private static boolean enabled() {
        return StreamerProfile.get().isEnabled() && Minecraft.getInstance().player != null;
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!StreamerProfile.get().isEnabled() || player == null) {
            reset();
            lastScreen = mc.gui.screen();
            return;
        }
        long now = System.currentTimeMillis();
        RunSessionManager runs = RunSessionManager.getInstance();
        boolean running = runs.hasActiveRun() && runs.isRunning();
        if (running && !wasRunning) {
            say(Event.START, true);
            idleAt = now + nextIdle();
        }
        wasRunning = running;
        if (idleAt == 0) idleAt = now + nextIdle();

        Screen screen = mc.gui.screen();
        if (screen != lastScreen && screen != null) {
            if (screen instanceof VictoryScreen) say(Event.VICTORY, true);
            else if (screen instanceof DefeatScreen) say(Event.DEFEAT, true);
            else if (screen instanceof DeathScreen) {
                diamondsAtDeath = count(player, st -> st.is(Items.DIAMOND));
                if (!swear(Event.SWEAR, 0.55f)) say(Event.DEATH, true);
            }
            else if (screen instanceof MerchantScreen) maybe(Event.TRADE, 1f, 20_000);
            else if (screen instanceof ContainerScreen) maybe(Event.CHEST, 0.9f, 20_000);
            else if (screen instanceof AbstractFurnaceScreen<?>) maybe(Event.FURNACE_OPEN, 0.8f, 30_000);
            else if (screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen) maybe(Event.CRAFT_OPEN, 0.4f, 120_000);
            else if (screen instanceof net.minecraft.client.gui.screens.inventory.EnchantmentScreen) maybe(Event.ENCHANT, 1f, 120_000);
        }
        lastScreen = screen;

        boolean dead = player.isDeadOrDying();
        if (!dead && wasDead) say(Event.RESPAWN, true);
        wasDead = dead;
        if (dead) return;

        health(player);
        movement(mc, player, now);
        equipment(player);
        kills(now);

        if (++ticks % 20 == 0) {
            world(player);
            inventory(player);
            effects(player);
            mobs(player, now);
            village(player, now);
        }
        if (ticks % 40 == 0 && nearLava(player)) swearOr(Event.WOW, 0.35f, Event.LAVA, 1f, 90_000);

        if (now >= idleAt) {

            if (now - lastSaid > 15_000) {
                Event idle = player.level().dimension() == Level.NETHER && RANDOM.nextFloat() < 0.6f ? Event.NETHER_IDLE
                    : wasUnderground && RANDOM.nextFloat() < 0.7f ? Event.IDLE_CAVE : Event.IDLE;
                say(idle, false);
            }
            idleAt = now + nextIdle();
        }
    }

    private static void health(LocalPlayer player) {
        float health = player.getHealth();
        if (lastHealth >= 0 && health > 0) {
            float lost = lastHealth - health;
            long now = System.currentTimeMillis();
            if (lost >= 1) {
                boolean fell = now - landedAt < 1500 && landedFall > 3.5;
                boolean skeleton = !player.level().getEntitiesOfClass(AbstractSkeleton.class, player.getBoundingBox().inflate(20), Entity::isAlive).isEmpty()
                    && player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, player.getBoundingBox().inflate(3), e -> !(e instanceof AbstractSkeleton)).isEmpty();
                if (health <= 6 && lastHealth > 6 && now >= READY_AT.getOrDefault(Event.LOW_HP, 0L) && say(Event.LOW_HP, true)) {
                    READY_AT.put(Event.LOW_HP, now + 40_000);
                } else if (fell && RANDOM.nextFloat() < 0.4f && now >= READY_AT.getOrDefault(Event.FALL, 0L) && say(Event.FALL, true)) {
                    READY_AT.put(Event.FALL, now + 60_000);
                } else if (now - lastDamageSwear >= 6_000 && RANDOM.nextFloat() < 0.85f) {

                    if (say(RANDOM.nextFloat() < 0.15f ? Event.WOW : Event.SWEAR, true)) lastDamageSwear = now;
                } else if (lost >= 2 && now - lastDamageSwear >= 3_000) {
                    if (skeleton && RANDOM.nextFloat() < 0.5f) maybe(Event.SKELETON_HURT, 1f, 60_000);
                    else maybe(Event.HURT, 0.8f, 12_000);
                }
            }
        }
        lastHealth = health;

        if (health > 0 && health <= 4) lowSeen = true;
        if (lowSeen && health >= 12) {
            lowSeen = false;
            swearOr(Event.WOW, 0.3f, Event.CLOSE_CALL, 1f, 120_000);
        }

        boolean eating = player.isUsingItem() && player.getUseItem().has(DataComponents.FOOD);
        if (eating && !wasEating) {
            eatingItem = player.getUseItem().copy();
            maybe(Event.EAT_START, 0.7f, 40_000);
        }
        wasEating = eating;
        int f = player.getFoodData().getFoodLevel();
        if (food >= 0 && f > food) maybe(Event.EAT, 0.3f, 90_000);
        food = f;
        boolean hungry = f <= 6;
        if (hungry && !wasHungry) maybe(Event.HUNGRY, 1f, 120_000);
        wasHungry = hungry;
    }

    private static void movement(Minecraft mc, LocalPlayer player, long now) {
        boolean onGround = player.onGround();
        if (!onGround) airFall = Math.max(airFall, player.fallDistance);
        else if (!wasOnGround) { landedFall = airFall; landedAt = now; airFall = 0; }
        wasOnGround = onGround;


        boolean selfie = mc.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_FRONT;
        if (selfie && !wasSelfie) maybe(Event.SELFIE, 1f, 45_000);
        wasSelfie = selfie;

        sprintTicks = player.isSprinting() && onGround ? sprintTicks + 1 : 0;
        if (sprintTicks == 20 * 12) maybe(Event.SPRINT, 0.6f, 120_000);

        boolean inWater = player.isInWater();
        if (inWater && !wasInWater && now - outOfWaterSince > 8000 && player.getVehicle() == null) maybe(Event.WATER, 1f, 45_000);
        if (!inWater && wasInWater) outOfWaterSince = now;
        wasInWater = inWater;
        boolean under = player.isUnderWater();
        if (under && !wasUnderWater) maybe(Event.UNDERWATER, 0.9f, 60_000);
        wasUnderWater = under;

        boolean inPortal = player.level().getBlockState(player.blockPosition()).is(Blocks.NETHER_PORTAL);
        if (inPortal && !wasInPortal && player.level().dimension() == Level.OVERWORLD) maybe(Event.PORTAL, 1f, 120_000);
        wasInPortal = inPortal;
        boolean drowning = under && player.getAirSupply() < 90;
        if (drowning && !wasDrowning) maybe(Event.DROWNING, 1f, 60_000);
        wasDrowning = drowning;

        boolean inBoat = player.getVehicle() instanceof AbstractBoat;
        if (inBoat && !wasInBoat) maybe(Event.BOAT, 1f, 40_000);
        if (!inBoat && wasInBoat) maybe(Event.BOAT_EXIT, 0.5f, 150_000);
        wasInBoat = inBoat;

        boolean sleeping = player.isSleeping();
        if (sleeping && !wasSleeping) say(Event.SLEEP, true);
        wasSleeping = sleeping;
    }


    private static void equipment(LocalPlayer player) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            boolean has = !player.getItemBySlot(slots[i]).isEmpty();
            if (has && !armor[i] && ticks > 40) maybe(Event.ARMOR, 0.9f, 45_000);
            armor[i] = has;
        }
        int selected = player.getInventory().getSelectedSlot();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (selected == lastSelected && main.isEmpty() && almostBroken(lastMain)) { brokenItem = lastMain; swearOr(Event.SWEAR, 0.3f, Event.TOOL_BREAK, 1f, 60_000); }
        if (off.isEmpty() && almostBroken(lastOff)) { brokenItem = lastOff; swearOr(Event.SWEAR, 0.3f, Event.TOOL_BREAK, 1f, 60_000); }
        lastSelected = selected;
        lastMain = main.copy();
        lastOff = off.copy();
    }

    private static boolean almostBroken(ItemStack stack) {
        return !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() <= 2;
    }

    private static void kills(long now) {
        if (attacked == null) return;
        if (now - attackedAt > 2500) { attacked = null; return; }
        if (attacked instanceof LivingEntity living && living.isDeadOrDying()) {
            if (attacked instanceof Enemy) swearOr(Event.SWEAR, 0.3f, Event.KILL, 0.6f, 20_000);
            attacked = null;
        }
    }

    private static void world(LocalPlayer player) {
        Level level = player.level();
        var dim = level.dimension();
        if (!dim.equals(lastDimension)) {
            if (lastDimension != null) {
                if (dim == Level.END) say(Event.END, true);
                else if (dim == Level.NETHER) say(Event.NETHER, true);
            }
            lastDimension = dim;
        }
        boolean overworld = dim == Level.OVERWORLD;
        BlockPos pos = player.blockPosition();

        boolean dark = overworld && level.isDarkOutside();
        if (dark && !wasDark && !player.isSleeping()) {
            boolean bed = player.getInventory().getNonEquipmentItems().stream().anyMatch(st -> st.is(net.minecraft.tags.ItemTags.BEDS));
            maybe(!bed && RANDOM.nextBoolean() ? Event.NIGHT_NOBED : Event.NIGHT, 1f, 300_000);
        }
        if (!dark && wasDark && overworld) maybe(Event.DAWN, 1f, 300_000);
        wasDark = dark;

        boolean raining = overworld && level.isRaining();
        boolean thunder = overworld && level.isThundering();
        if (thunder && !wasThundering) maybe(Event.THUNDER, 1f, 300_000);
        else if (raining && !wasRaining) maybe(Event.RAIN, 1f, 300_000);
        wasRaining = raining;
        wasThundering = thunder;



        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        boolean sky = level.canSeeSky(pos.above());
        boolean underground = overworld && (wasUnderground ? !sky && ground - pos.getY() > 3 : !sky && ground - pos.getY() > 8);
        long now = System.currentTimeMillis();
        if (underground && !wasUnderground) {
            maybe(Event.CAVE, 1f, 60_000);
            undergroundSince = now;
            caveLongSaid = false;
        }
        if (!underground && wasUnderground) {

            if (now - undergroundSince > 40_000) {
                cameUpAt = now;
                if (count(player, st -> st.is(Items.DIAMOND)) >= 3) swearOr(Event.WOW, 0.25f, Event.SURFACE_RICH, 1f, 60_000);
                else maybe(Event.SURFACE, 1f, 60_000);
            }
        }
        if (underground && !caveLongSaid && now - undergroundSince > 6 * 60_000) {
            caveLongSaid = true;
            maybe(Event.CAVE_LONG, 1f, 300_000);
        }
        wasUnderground = underground;
        boolean deep = overworld && pos.getY() < 0;
        if (deep && !wasDeep) maybe(Event.DEEP, 1f, 240_000);
        wasDeep = deep;
        if (underground && level.getMaxLocalRawBrightness(pos) <= 3) maybe(Event.DARK, 0.4f, 60_000);

        if (dim == Level.NETHER && level.getBlockState(pos.below()).is(Blocks.NETHER_BRICKS)) maybe(Event.FORTRESS, 1f, 600_000);

        var biome = level.getBiome(pos);
        String name = biome.unwrapKey().map(Object::toString).orElse("");
        if (!name.equals(lastBiome)) {
            if (!lastBiome.isEmpty() && !underground) {
                if (biome.is(BiomeTags.IS_JUNGLE)) maybe(Event.JUNGLE, 1f, 300_000);
                else if (biome.is(BiomeTags.IS_BADLANDS)) maybe(Event.BIOME_MESA, 1f, 300_000);
                else if (name.contains("desert")) maybe(Event.BIOME_DESERT, 1f, 300_000);
                else maybe(Event.BIOME, 0.6f, 90_000);
            }
            lastBiome = name;
        }
    }


    private static void village(LocalPlayer player, long now) {
        boolean villagers = !player.level().getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(40), Entity::isAlive).isEmpty();
        if (villagers) {
            if (villageLeftAt > 0 && now - villageLeftAt > 60_000) maybe(Event.VILLAGE_BACK, 1f, 300_000);
            villageLeftAt = 0;
            if (villageSince == 0) villageSince = now;
            villageSeenAt = now;
        } else if (villageSince > 0 && now - villageSeenAt > 15_000) {
            if (villageSeenAt - villageSince > 30_000) {
                maybe(Event.VILLAGE_LEAVE, 1f, 300_000);
                villageLeftAt = now;
            }
            villageSince = 0;
        }
    }

    private static void inventory(LocalPlayer player) {
        int t = count(player, st -> st.is(Items.TORCH));
        if (torches > 0 && t == 0) maybe(Event.TORCH_OUT, 1f, 120_000);
        torches = t;
        gained(player, Event.FLINT, 60_000, Items.FLINT);
        gained(player, Event.BUCKET, 60_000, Items.WATER_BUCKET);
        gained(player, Event.LAVA_BUCKET, 90_000, Items.LAVA_BUCKET);
        gained(player, Event.ROTTEN, 300_000, Items.ROTTEN_FLESH);
        gained(player, Event.APPLE, 300_000, Items.APPLE);
        gained(player, Event.ENDER_PEARL, 120_000, Items.ENDER_PEARL);
        gained(player, Event.GUNPOWDER, 300_000, Items.GUNPOWDER);
        gained(player, Event.EYE, 300_000, Items.ENDER_EYE);
        boolean full = player.getInventory().getFreeSlot() < 0;
        if (full && !wasFull) maybe(Event.INV_FULL, 1f, 300_000);
        wasFull = full;
    }

    private static void gained(Player player, Event event, long cooldown, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) if (stack.is(item)) n += stack.getCount();
        Integer before = COUNTS.put(item, n);
        if (before != null && n > before) maybe(event, 1f, cooldown);
    }

    private static void effects(LocalPlayer player) {
        Set<Holder<MobEffect>> now = new HashSet<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {

            if (BAD_EFFECTS.contains(effect.getEffect()) && !effect.isInfiniteDuration() && effect.getDuration() < 20 * 90) now.add(effect.getEffect());
        }
        for (Holder<MobEffect> effect : now) {
            if (!badEffects.contains(effect)) {
                maybe(Event.EFFECT, 1f, 60_000);
                break;
            }
        }
        badEffects = now;
    }

    private static boolean nearLava(LocalPlayer player) {
        BlockPos base = player.blockPosition();
        Level level = player.level();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -5; dz <= 5; dz++) {
                    pos.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (level.getFluidState(pos).is(FluidTags.LAVA)) return true;
                }
            }
        }
        return false;
    }


    private static void mobs(LocalPlayer player, long now) {
        Set<Event> present = EnumSet.noneOf(Event.class);
        int enemies = 0;
        Set<Integer> swellingNow = new HashSet<>();
        for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(SCAN_RADIUS), Entity::isAlive)) {
            if (entity instanceof FallingBlockEntity && entity.distanceTo(player) < 6) maybe(Event.GRAVEL, 1f, 180_000);

            if (!noticed(entity, player)) continue;
            if (entity instanceof Chicken && entity.isOnFire()) maybe(Event.CHICKEN_FIRE, 1f, 300_000);
            Event event = classify(entity, player);
            if (event != null) present.add(event);
            if (!(entity instanceof Enemy)) continue;
            enemies++;
            if (entity instanceof LivingEntity living && living.isBaby()) present.add(Event.BABIES);
            if (entity.isVehicle()) present.add(Event.JOCKEY);
            {
                if (entity.isInWater() && !(entity instanceof Guardian) && !(entity instanceof net.minecraft.world.entity.monster.zombie.Drowned)) present.add(Event.MOB_WATER);
                if (entity.isOnFire()) present.add(Event.MOB_FIRE);
            }
            if (entity instanceof Creeper creeper && creeper.getSwellDir() > 0) swellingNow.add(entity.getId());
            if (stuck(entity, player, now)) present.add(Event.MOB_STUCK);
        }

        for (Integer id : SWELLING.keySet()) {
            if (!swellingNow.contains(id) && player.level().getEntity(id) == null) {
                if (!swear(Event.SWEAR, 0.6f)) say(Event.EXPLOSION, true);
                break;
            }
        }
        SWELLING.clear();
        for (Integer id : swellingNow) SWELLING.put(id, now);
        STUCK.keySet().removeIf(id -> player.level().getEntity(id) == null);
        if (enemies >= 3) present.add(Event.HORDE);

        boolean canTalk = now - lastMobSaid >= MOB_GAP_MS && now - lastSaid >= 5000;
        for (Event event : MOB_EVENTS) {
            if (!canTalk || !present.contains(event)) continue;
            boolean fresh = !seen.contains(event);
            long ready = READY_AT.getOrDefault(event, 0L);
            if ((fresh && now >= ready) || now >= ready + 90_000) {
                if (say(event, false)) {
                    READY_AT.put(event, now + 60_000);
                    lastMobSaid = now;
                    break;
                }
            }
        }
        seen = present;
    }


    private static boolean noticed(Entity e, LocalPlayer player) {
        double range = e instanceof EnderDragon ? 48 : e instanceof Enderman || e instanceof IronGolem ? 8 : 5.5;
        if (e.distanceTo(player) > range || !player.hasLineOfSight(e)) return false;
        var look = player.getViewVector(1f);
        var to = e.getEyePosition().subtract(player.getEyePosition()).normalize();
        return look.dot(to) > 0.35 || e.distanceTo(player) < 3;
    }


    private static boolean stuck(Entity e, LocalPlayer player, long now) {
        double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ(), dy = e.getY() - player.getY();
        if (dx * dx + dz * dz > 9 || Math.abs(dy) < 2 || Math.abs(dy) > 6) { STUCK.remove(e.getId()); return false; }
        double[] since = STUCK.get(e.getId());
        if (since == null || Math.abs(since[0] - e.getX()) > 0.5 || Math.abs(since[1] - e.getZ()) > 0.5) {
            STUCK.put(e.getId(), new double[] {e.getX(), e.getZ(), now});
            return false;
        }
        return now - since[2] > 4000;
    }

    private static Event classify(Entity e, LocalPlayer self) {
        if (e instanceof EnderDragon) return Event.DRAGON;
        if (e instanceof Creeper) return Event.CREEPER;
        if (e instanceof Shulker) return Event.SHULKER;
        if (e instanceof Witch) return Event.WITCH;
        if (e instanceof IronGolem) return Event.IRON_GOLEM;
        if (e instanceof Enderman) return Event.ENDERMAN;
        if (e instanceof AbstractSkeleton) return Event.SKELETON;
        if (e instanceof Zombie) return Event.ZOMBIE;
        if (e instanceof Spider) return Event.SPIDER;
        if (e instanceof Silverfish) return Event.SILVERFISH;
        if (e instanceof Endermite) return Event.ENDERMITE;
        if (e instanceof Pufferfish || e instanceof Guardian || e instanceof AbstractFish) return Event.FISH;
        if (e instanceof Squid) return Event.SQUID;
        if (e instanceof AbstractVillager) return Event.VILLAGER;
        if (e instanceof Player other && !other.isSpectator()) return Event.PLAYER;
        if (e instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin) return Event.PIGLIN;
        if (e instanceof net.minecraft.world.entity.animal.panda.Panda) return Event.PANDA;
        if (e instanceof net.minecraft.world.entity.animal.dolphin.Dolphin) return Event.DOLPHIN;
        if (e instanceof net.minecraft.world.entity.animal.armadillo.Armadillo) return Event.ARMADILLO;
        if (e instanceof Fox) return Event.FOX;
        if (e instanceof Parrot) return Event.PARROT;
        if (e instanceof Cat || e instanceof Ocelot) return Event.CAT;
        if (e instanceof Bee) return Event.BEE;
        if (e instanceof Bat) return Event.BAT;
        if (e instanceof Chicken) return Event.CHICKEN;
        if (e instanceof Sheep) return Event.SHEEP;
        if (e instanceof Pig) return Event.PIG;
        if (e instanceof NeutralMob) return Event.NEUTRAL;
        if (e instanceof Cow) return Event.ANIMALS;
        return null;
    }

    private static void reset() {
        wasRunning = false;
        lastHealth = -1;
        idleAt = 0;
        food = -1;
        wasInWater = true;
        wasUnderWater = false;
        wasInBoat = false;
        wasDead = false;
        lastDimension = null;
        lastBiome = "";
        COUNTS.clear();
        SWELLING.clear();
        STUCK.clear();
        seen = EnumSet.noneOf(Event.class);
        badEffects = new HashSet<>();
        attacked = null;
        minedStone = false;
        lowSeen = false;
        pickaxeCraftedAt = 0;
        speakingUntil = 0;
        wasEating = false;
        torches = -1;
        villageSince = villageSeenAt = villageLeftAt = 0;
        lastStartPos = null;
    }

    private static int count(Player player, java.util.function.Predicate<ItemStack> what) {
        int n = 0;
        for (ItemStack st : player.getInventory().getNonEquipmentItems()) if (what.test(st)) n += st.getCount();
        for (ItemStack st : new ItemStack[] {player.getOffhandItem()}) if (what.test(st)) n += st.getCount();
        return n;
    }

    private static boolean has(Player player, Item... items) {
        return count(player, st -> { for (Item i : items) if (st.is(i)) return true; return false; }) > 0;
    }

    private static boolean hasTag(Player player, net.minecraft.tags.TagKey<Item> tag) {
        return count(player, st -> st.is(tag)) > 0;
    }

    private static boolean near(Player player, int radius, java.util.function.Predicate<BlockState> what) {
        BlockPos base = player.blockPosition();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius / 2; dy <= radius / 2; dy++)
                for (int dz = -radius; dz <= radius; dz++)
                    if (what.test(player.level().getBlockState(p.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz)))) return true;
        return false;
    }





    private static final Map<String, java.util.function.Predicate<LocalPlayer>> REQUIRES = new HashMap<>();
    static {
        java.util.function.Predicate<LocalPlayer> surface = p -> !wasUnderground;
        java.util.function.Predicate<LocalPlayer> shieldBroke = p -> brokenItem.is(Items.SHIELD);
        REQUIRES.put("zloy.s_v_preispodnyuyu", p -> has(p, Items.GOLD_INGOT, Items.RAW_GOLD) && has(p, Items.IRON_INGOT, Items.RAW_IRON));
        REQUIRES.put("zloy.p_eda_est", p -> hasTag(p, net.minecraft.tags.ItemTags.BEDS) && has(p, Items.BREAD));
        REQUIRES.put("zloy.r_kayfuem", p -> has(p, Items.DIAMOND));
        REQUIRES.put("zloy.p_nakachany", p -> !p.getItemBySlot(EquipmentSlot.CHEST).isEmpty());
        REQUIRES.put("zloy.s_29_almazov", p -> diamondsAtDeath >= 10);
        REQUIRES.put("zloy.p_mech_est", p -> hasTag(p, net.minecraft.tags.ItemTags.SWORDS) && hasTag(p, net.minecraft.tags.ItemTags.PICKAXES));
        REQUIRES.put("zloy.q_shchit_est", p -> has(p, Items.SHIELD) && hasTag(p, net.minecraft.tags.ItemTags.SHOVELS) && hasTag(p, net.minecraft.tags.ItemTags.PICKAXES));
        REQUIRES.put("zloy.r_chetyre", p -> { int d = count(p, st -> st.is(Items.DIAMOND)); return d >= 2 && d <= 6; });
        REQUIRES.put("zloy.r_22_almaza", p -> count(p, st -> st.is(Items.DIAMOND)) >= 12);
        REQUIRES.put("zloy.r_kirka_lomaetsya", p -> brokenItem.is(net.minecraft.tags.ItemTags.PICKAXES) && has(p, Items.IRON_PICKAXE));
        REQUIRES.put("zloy.q_shchit_polomali", shieldBroke);
        REQUIRES.put("zloy.t_shchit_slomalsya", shieldBroke);
        REQUIRES.put("zloy.u_shchit_noviy", shieldBroke);
        REQUIRES.put("zloy.y_shchit_suki", shieldBroke);
        REQUIRES.put("zloy.t_topor_propal", p -> brokenItem.is(net.minecraft.tags.ItemTags.AXES));
        REQUIRES.put("zloy.s_na_pereplavochku", p -> has(p, Items.RAW_GOLD, Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE));
        REQUIRES.put("zloy.q_nuzhny_ugli", p -> !has(p, Items.COAL, Items.CHARCOAL));
        REQUIRES.put("zloy.s_kartofel", p -> smeltedItem == Items.BAKED_POTATO);
        REQUIRES.put("zloy.x_pozharilas", p -> smeltedItem == Items.COOKED_CHICKEN || smeltedItem == Items.COOKED_BEEF
            || smeltedItem == Items.COOKED_PORKCHOP || smeltedItem == Items.COOKED_MUTTON || smeltedItem == Items.COOKED_RABBIT);
        REQUIRES.put("zloy.p_20pshenic", p -> has(p, Items.WHEAT));
        REQUIRES.put("zloy.p_torguem", p -> has(p, Items.WHEAT));
        for (String k : new String[] {"p_podzemelye", "r_danzhik", "s_nadybaem", "s_igra_klassnaya", "s_puteshestvovat", "p_sredizemye", "q_ulov", "v_derevnyu"})
            REQUIRES.put("zloy." + k, surface);
        REQUIRES.put("zloy.p_pospim", p -> hasTag(p, net.minecraft.tags.ItemTags.BEDS));
        REQUIRES.put("zloy.q_posplyu", p -> hasTag(p, net.minecraft.tags.ItemTags.BEDS));
        REQUIRES.put("zloy.yama", p -> p.level().canSeeSky(p.blockPosition().above()));
        REQUIRES.put("zloy.p_lodku", p -> count(p, st -> st.getItem() instanceof net.minecraft.world.item.BoatItem) == 0);
        REQUIRES.put("zloy.p_vodichka", p -> near(p, 8, st -> st.getFluidState().is(FluidTags.WATER)));
        REQUIRES.put("zloy.p_kapaet", p -> near(p, 8, st -> !st.getFluidState().isEmpty()));
        REQUIRES.put("zloy.p_svetitsya", p -> near(p, 12, st -> st.getFluidState().is(FluidTags.LAVA)));
        REQUIRES.put("zloy.q_svetitsya", p -> near(p, 12, st -> st.getFluidState().is(FluidTags.LAVA)));
        REQUIRES.put("zloy.q_stalaktity", p -> near(p, 10, st -> st.is(Blocks.POINTED_DRIPSTONE)));
        REQUIRES.put("zloy.q_fakelov_net", p -> !has(p, Items.TORCH));
        REQUIRES.put("zloy.p_fakel", p -> !has(p, Items.TORCH));
        REQUIRES.put("zloy.p_shchit", p -> !has(p, Items.SHIELD));
        REQUIRES.put("zloy.y_esche_fakelov", p -> has(p, Items.COAL, Items.CHARCOAL));
        REQUIRES.put("zloy.r_vyhodim_na_volyu", p -> !has(p, Items.DIAMOND));
        REQUIRES.put("zloy.p_tri_bloka", p -> !p.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, p.getBoundingBox().inflate(10), Entity::isAlive).isEmpty());
        REQUIRES.put("zloy.r_pryanik", p -> eatingItem.is(Items.COOKIE) || eatingItem.is(Items.BREAD));
        REQUIRES.put("zloy.s_pirogov", p -> eatingItem.is(Items.PUMPKIN_PIE) || eatingItem.is(Items.BREAD) || eatingItem.is(Items.COOKIE));
        REQUIRES.put("zloy.y_poem_pirozhki", p -> eatingItem.is(Items.PUMPKIN_PIE) || eatingItem.is(Items.BREAD) || eatingItem.is(Items.COOKIE));
        REQUIRES.put("zloy.p_farmit", p -> System.currentTimeMillis() - villageSeenAt < 120_000);
        REQUIRES.put("zloy.x_drugoy_biom", p -> p.isInWater() || p.getVehicle() instanceof AbstractBoat);
        REQUIRES.put("zloy.r_rastut", p -> near(p, 5, st -> st.is(Blocks.DIAMOND_ORE) || st.is(Blocks.DEEPSLATE_DIAMOND_ORE)));
        REQUIRES.put("zloy.q_pod_nogami", p -> near(p, 6, st -> st.is(Blocks.DIAMOND_ORE) || st.is(Blocks.DEEPSLATE_DIAMOND_ORE)));
        REQUIRES.put("zloy.q_na_potolke", p -> lastStartPos != null && lastStartPos.getY() > p.getBlockY() + 1);
        REQUIRES.put("zloy.p_legend_mech", p -> craftedItem != null && new ItemStack(craftedItem).is(net.minecraft.tags.ItemTags.SWORDS));
        REQUIRES.put("zloy.q_topor", p -> craftedItem != null && new ItemStack(craftedItem).is(net.minecraft.tags.ItemTags.AXES));
        REQUIRES.put("zloy.v_strely", p -> craftedItem == Items.BOW || craftedItem == Items.ARROW);
        REQUIRES.put("zloy.miny", p -> !p.level().getEntitiesOfClass(Pufferfish.class, p.getBoundingBox().inflate(8), Entity::isAlive).isEmpty());
        REQUIRES.put("zloy.r_stremno", p -> !hasTag(p, net.minecraft.tags.ItemTags.BEDS));
        REQUIRES.put("zloy.p_tunec", p -> eatingItem.is(Items.BREAD) || eatingItem.is(Items.COOKED_COD) || eatingItem.is(Items.COOKED_SALMON));
        REQUIRES.put("zloy.p_vkusneyshiy", p -> eatingItem.is(Items.BREAD));
    }

    private static boolean fits(Phrase phrase) {
        var test = REQUIRES.get(phrase.sound());
        LocalPlayer player = Minecraft.getInstance().player;
        return test == null || (player != null && test.test(player));
    }

    private static long nextIdle() {
        return 25_000 + RANDOM.nextInt(25_000);
    }





    private static boolean swear(Event pool, float chance) {
        long now = System.currentTimeMillis();
        if (now - lastSwear < 30_000 || now - lastDamageSwear < 3_000 || RANDOM.nextFloat() >= chance) return false;
        if (!say(pool, true)) return false;
        lastSwear = now;
        return true;
    }


    private static void swearOr(Event pool, float swearChance, Event event, float chance, long cooldown) {
        long now = System.currentTimeMillis();
        if (now < READY_AT.getOrDefault(event, 0L)) return;
        if (swear(pool, swearChance)) READY_AT.put(event, now + cooldown);
        else maybe(event, chance, cooldown);
    }


    private static void maybe(Event event, float chance, long cooldown) {
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        if (now < READY_AT.getOrDefault(event, 0L) || RANDOM.nextFloat() >= chance) return;
        if (say(event, false)) READY_AT.put(event, now + cooldown);
    }


    public static boolean say(Event event, boolean important) {
        long now = System.currentTimeMillis();
        if (!important && (now - lastSaid < MIN_GAP_MS || now < speakingUntil)) return false;
        List<Phrase> list = StreamerProfile.get().character().phrases(event);
        if (list.isEmpty()) return false;
        Deque<Phrase> deck = DECKS.computeIfAbsent(event, e -> new ArrayDeque<>());
        if (deck.isEmpty()) {
            List<Phrase> shuffled = new java.util.ArrayList<>(list);
            java.util.Collections.shuffle(shuffled, RANDOM);
            deck.addAll(shuffled);
        }

        Phrase phrase = null;
        for (int i = 0, n = deck.size(); i < n; i++) {
            Phrase next = deck.poll();
            if (fits(next)) { phrase = next; break; }
            deck.addLast(next);
        }
        if (phrase == null) return false;
        lastSaid = now;
        speakingUntil = now + phrase.ms() + 400;
        StreamerNet.sayPhrase(phrase);
        return true;
    }
}
