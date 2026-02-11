package com.randomrun.mixin;

import com.randomrun.battle.BattleManager;
import com.randomrun.challenges.classic.world.WorldCreator;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
// import net.minecraft.client.gui.screen.world.WorldCreator; // REMOVED: Conflict with com.randomrun...WorldCreator
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Объединяет все механики управления миром.
 * Содержит миксины для создания мира (авто-настройка) и входа в мир (логика спавна).
 */
public class WorldManagementMixin {

    @Mixin(ClientPlayNetworkHandler.class)
    public static class JoinWorld {
        
        private void configureSpeedrunWorld(MinecraftClient client, boolean isSharedWorld) {
            // Настройка на стороне сервера (Хост/Одиночная игра)
            if (client.getServer() != null) {
                MinecraftServer server = client.getServer();
                ServerWorld world = server.getOverworld();
                
                if (world != null) {
                    // 1. Граница мира (Практически бесконечный для спидрана - 1,000,000 блоков)
                    // Центр границы всегда в 0,0, спавн тоже ищется рядом с 0,0
                    world.getWorldBorder().setCenter(0.0, 0.0);
                    world.getWorldBorder().setSize(1000000.0);
                    
                    // ОПТИМИЗАЦИЯ: Отключаем прогрузку спавн-чанков (1.20.5+)
                    // Значение 0 отключает постоянную загрузку чанков вокруг спавна,
                    // что значительно ускоряет загрузку мира и уменьшает потребление памяти.
                    world.getGameRules().get(net.minecraft.world.GameRules.SPAWN_CHUNK_RADIUS).set(0, server);
                    // Устанавливаем радиус спавна игроков в 0, чтобы все появлялись в одной точке
                    world.getGameRules().get(net.minecraft.world.GameRules.SPAWN_RADIUS).set(0, server);
                    
                    // 2. Поиск безопасного спавна на суше (игнорируем 0,0 если это вода)
                    net.minecraft.util.math.BlockPos spawnPos = findSafeLandSpawn(world, 0, 0);
                    int safeY = spawnPos.getY();
                    
                    // Удалено создание стеклянной платформы по запросу пользователя
                    // Мы полагаемся на findSafeLandSpawn, который должен найти твердую землю.
                    // Если земля не найдена (океан), игрок появится на поверхности воды, но без стекла.
                    
                    world.setSpawnPos(spawnPos, 0.0f);
                    RandomRunMod.LOGGER.info("✓ Set World Spawn to: " + spawnPos.toShortString());
                    
                    // 3. View/Sim Distance (Увеличиваем, но не форсируем клиентские настройки жестко)
                    // Серверная дальность обзора (влияет на отправку чанков)
                    server.getPlayerManager().setViewDistance(20); 
                    server.getPlayerManager().setSimulationDistance(10);
                    
                    // 4. Pre-generation (Small area)
                    RandomRunMod.LOGGER.info("⚙ Предварительная генерация чанков вокруг спавна...");
                    int radius = 1; // Оставляем 1 для скорости загрузки
                    for (int x = -radius; x <= radius; x++) {
                        for (int z = -radius; z <= radius; z++) {
                            world.getChunk(x, z);
                        }
                    }
                    RandomRunMod.LOGGER.info("✓ Предварительная генерация завершена.");
                }
            }
            
            // Клиентские настройки (Применяем рекомендуемые, если текущие ниже)
            // Мы не будем принудительно ставить, если у игрока уже настроено
            // Но можем предложить дефолт
            if (client.options.getViewDistance().getValue() < 12) {
                 client.options.getViewDistance().setValue(12);
            }
            if (client.options.getSimulationDistance().getValue() < 8) {
                 client.options.getSimulationDistance().setValue(8);
            }
        }

        private int findSafeY(net.minecraft.world.World world, int x, int z) {
        // 1. Try Heightmap
        int y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        
        // 2. Validate Heightmap (Scan down if needed)
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x, y - 1, z);
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        
        // If air or fluid, scan down
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            for (int i = y; i > world.getBottomY(); i--) {
                pos = new net.minecraft.util.math.BlockPos(x, i - 1, z);
                state = world.getBlockState(pos);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    return i;
                }
            }
            // If nothing found (void?), return default
            return world.getSeaLevel() + 1;
        }
        
        return y;
    }
    
    private net.minecraft.util.math.BlockPos findSafeLandSpawn(ServerWorld world, int centerX, int centerZ) {
        int radius = 0;
        int maxRadius = 1000; // Search up to 1000 blocks
        int step = 16; // Search chunk by chunk
        
        // Spiral search
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        int t = Math.max(radius, 1);
        int maxI = t * t;
        
        for (int i = 0; i < maxI; i++) {
            if ((-radius/2 <= x) && (x <= radius/2) && (-radius/2 <= z) && (z <= radius/2)) {
                // Check this chunk
                int checkX = centerX + x * step;
                int checkZ = centerZ + z * step;
                
                int y = findSafeY(world, checkX, checkZ);
                net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(checkX, y - 1, checkZ);
                net.minecraft.block.BlockState state = world.getBlockState(pos);
                
                // Found land! (Not water, not air, not leaves/logs if possible but land usually is solid)
                if (state.getFluidState().isEmpty() && !state.isAir()) {
                    // Double check if it's not some weird block (like ice in ocean)
                    // But ice is solid, so it's okay-ish. We want to avoid WATER.
                    RandomRunMod.LOGGER.info("Found safe land spawn at: " + checkX + "," + y + "," + checkZ);
                    return new net.minecraft.util.math.BlockPos(checkX, y, checkZ);
                }
            }
            
            if ((x == z) || ((x < 0) && (x == -z)) || ((x > 0) && (x == 1-z))) {
                t = dx;
                dx = -dz;
                dz = t;
            }
            x += dx;
            z += dz;
            
            // Expand radius if loop finished (manual expansion logic since this loop is weird for infinite spiral)
            // Simplified spiral:
        }
        
        // Better simple spiral loop
        for (int r = 0; r < maxRadius; r += step) {
            for (int lx = -r; lx <= r; lx += step) {
                if (checkSpot(world, centerX + lx, centerZ - r)) return new net.minecraft.util.math.BlockPos(centerX + lx, findSafeY(world, centerX + lx, centerZ - r), centerZ - r);
                if (checkSpot(world, centerX + lx, centerZ + r)) return new net.minecraft.util.math.BlockPos(centerX + lx, findSafeY(world, centerX + lx, centerZ + r), centerZ + r);
            }
            for (int lz = -r + step; lz < r; lz += step) {
                if (checkSpot(world, centerX - r, centerZ + lz)) return new net.minecraft.util.math.BlockPos(centerX - r, findSafeY(world, centerX - r, centerZ + lz), centerZ + lz);
                if (checkSpot(world, centerX + r, centerZ + lz)) return new net.minecraft.util.math.BlockPos(centerX + r, findSafeY(world, centerX + r, centerZ + lz), centerZ + lz);
            }
        }
        
        return new net.minecraft.util.math.BlockPos(centerX, world.getSeaLevel() + 1, centerZ);
    }
    
    private boolean checkSpot(ServerWorld world, int x, int z) {
        int y = findSafeY(world, x, z);
        net.minecraft.util.math.BlockPos pos = new net.minecraft.util.math.BlockPos(x, y - 1, z);
        net.minecraft.block.BlockState state = world.getBlockState(pos);
        return state.getFluidState().isEmpty() && !state.isAir();
    }

    private void teleportToSafeSpawn(MinecraftClient client, double targetX, double targetZ) {
        // Only host/singleplayer should force teleport logic. 
        // Guests should rely on server-synced spawn position.
        if (client.getServer() == null) {
            return; 
        }

        int targetY;
        
        // Server Side (Singleplayer / Host)
        ServerWorld serverWorld = client.getServer().getOverworld();
        
        // Force load chunk to be sure
        serverWorld.getChunk((int)targetX >> 4, (int)targetZ >> 4);
        
        targetY = findSafeY(serverWorld, (int)targetX, (int)targetZ);
        
        // Verify safety one last time
        net.minecraft.util.math.BlockPos footPos = new net.minecraft.util.math.BlockPos((int)targetX, targetY - 1, (int)targetZ);
        net.minecraft.block.BlockState footState = serverWorld.getBlockState(footPos);
        
        // If still unsafe (e.g. water), build platform
        if (footState.getFluidState().isEmpty() == false || footState.isAir()) {
             RandomRunMod.LOGGER.warn("⚠ Spawn unsafe (Water/Air). No platform built (Removed by request).");
             // serverWorld.setBlockState(footPos, net.minecraft.block.Blocks.GLASS.getDefaultState());
             // Clear space for head
             // serverWorld.setBlockState(footPos.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
             // serverWorld.setBlockState(footPos.up(2), net.minecraft.block.Blocks.AIR.getDefaultState());
        }
        
        int worldTopY = client.world.getBottomY() + client.world.getHeight();
        if (targetY < client.world.getBottomY() || targetY > worldTopY) targetY = 80;
        
        client.player.refreshPositionAndAngles(targetX, targetY, targetZ, 0, 0);
        client.player.setVelocity(0, 0, 0);
        RandomRunMod.LOGGER.info("✓ Teleported to: " + targetX + ", " + targetY + ", " + targetZ);
    }

    @Inject(method = "onGameJoin", at = @At("TAIL"))
    private void onJoinWorld(GameJoinS2CPacket packet, CallbackInfo ci) {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            BattleManager battleManager = BattleManager.getInstance();
            MinecraftClient mc = MinecraftClient.getInstance();
            
            // Получение уникального идентификатора мира для валидации забега
            String worldName = "unknown";
            if (mc.getServer() != null) {
                worldName = mc.getServer().getSaveProperties().getLevelName();
            } else if (mc.getCurrentServerEntry() != null) {
                worldName = mc.getCurrentServerEntry().address;
            }
            
            if (battleManager.isInBattle()) {
                // Получение целевого предмета из комнаты битвы, если не установлен
                Item targetItem = runManager.getTargetItem();
                com.randomrun.battle.BattleRoom room = battleManager.getCurrentRoom();
                
                if (targetItem == null && room != null && room.getTargetItem() != null) {
                    targetItem = Registries.ITEM.get(Identifier.of(room.getTargetItem()));
                    runManager.setTargetItem(targetItem);
                    RandomRunMod.LOGGER.info("✓ Установлен целевой предмет из комнаты битвы: " + room.getTargetItem());
                }
                
                if (targetItem == null) {
                    RandomRunMod.LOGGER.warn("⚠ Нет целевого предмета для битвы!");
                    return;
                }
                
                // Инициализация замороженного забега
                RunDataManager.RunStatus currentStatus = runManager.getStatus();
                if (currentStatus != RunDataManager.RunStatus.FROZEN) {
                    if (currentStatus != RunDataManager.RunStatus.INACTIVE) {
                        runManager.cancelRun();
                    }
                    runManager.startNewRun(targetItem, 0, worldName);
                    RandomRunMod.LOGGER.info("✓ Инициализирован замороженный забег для битвы");
                }
                
                battleManager.freezePlayer();
                battleManager.setPlayerLoaded();
                battleManager.onWorldLoaded();
                
                // Настройка мира (Граница, пре-генерация и т.д.)
                boolean isSharedWorld = room != null && room.isSharedWorld();
                configureSpeedrunWorld(mc, isSharedWorld);
                
                WorldCreator.clearPendingData();
                
                // Логика телепортации
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && client.world != null) {
                        // Фиксированный спавн на 0,0 (теперь берем из мира)
                        // Гости должны использовать точку спавна мира, установленную сервером
                        net.minecraft.util.math.BlockPos worldSpawn = client.world.getSpawnPos();
                        double targetX = worldSpawn.getX() + 0.5;
                        double targetZ = worldSpawn.getZ() + 0.5;
                        double targetY = worldSpawn.getY(); // Используем Y спавна, а не расчет клиента
                        
                        // Удалена логика разброса (scatter) по запросу пользователя
                        // Все игроки спавнятся в одной точке (stacking) или очень близко
                        
                        // Если мы Хост, мы можем перепроверить безопасность (на всякий случай)
                        if (client.getServer() != null) {
                            ServerWorld serverWorld = client.getServer().getOverworld();
                            targetY = serverWorld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int)targetX, (int)targetZ);
                        } else {
                            // Гости: доверяем worldSpawn.getY(), но проверяем загрузку чанка
                             if (targetY < client.world.getBottomY()) {
                                 targetY = 100; // Fallback если spawnPos еще не синхронизирован
                             }
                        }
                        
                        client.player.refreshPositionAndAngles(targetX, targetY, targetZ, 0, 0);
                        client.player.setVelocity(0, 0, 0);
                        
                        RandomRunMod.LOGGER.info("✓ Телепортирован на спавн: " + targetX + ", " + targetY + ", " + targetZ);
                        client.player.sendMessage(Text.literal("§e§lВы заморожены! Напишите §f/go §e§lкогда будете готовы"), false);
                    }
                });
            } else if (runManager.getStatus() == RunDataManager.RunStatus.FROZEN) {
                if ("pending".equals(runManager.getRunWorldName())) {
                    runManager.setRunWorldName(worldName);
                }
            } else if (WorldCreator.hasPendingRun()) {
                // SAFETY CHECK: Ensure we are in a RandomRun world
                // Если имя мира не начинается с "RandomRun", значит игрок создал обычный мир,
                // а флаг hasPendingRun остался от предыдущей сессии. Сбрасываем его.
                if (!worldName.startsWith("RandomRun")) {
                     RandomRunMod.LOGGER.warn("⚠ Pending run detected but world '" + worldName + "' is not a RandomRun world. Clearing pending data.");
                     WorldCreator.clearPendingData();
                     RandomRunMod.getInstance().getRunDataManager().cancelRun(); // SAFETY: Ensure run manager is reset
                     return; // Выходим, не применяя логику мода
                }

                // Инициализация соло режима
                Item targetItem = WorldCreator.getPendingTargetItem();
                Identifier advancementId = WorldCreator.getPendingAdvancementId();
                long timeLimit = WorldCreator.getPendingTimeLimit();
                
                configureSpeedrunWorld(mc, false);
                
                if (targetItem != null) {
                    runManager.startNewRun(targetItem, timeLimit, worldName);
                    RandomRunMod.LOGGER.info("✓ Начал новый соло забег для предмета: " + targetItem);
                } else if (advancementId != null) {
                    runManager.startNewRun(advancementId, timeLimit, worldName);
                    RandomRunMod.LOGGER.info("✓ Начал новый соло забег для достижения: " + advancementId);
                }
                
                // Телепортация соло игрока на фиксированный спавн (0,0)
                MinecraftClient.getInstance().execute(() -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null && client.world != null) {
                        double targetX = 0.5;
                        double targetZ = 0.5;
                        double targetY = 100;
                        
                        int topY = 64;
                        if (client.world != null) topY = client.world.getSeaLevel() + 1;

                        if (client.getServer() != null) {
                            ServerWorld serverWorld = client.getServer().getOverworld();
                            topY = serverWorld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int)targetX, (int)targetZ);
                            
                            // Safe Spawn Check (Server Side)
                            net.minecraft.util.math.BlockPos testPos = new net.minecraft.util.math.BlockPos((int)targetX, topY - 1, (int)targetZ);
                            net.minecraft.block.BlockState state = serverWorld.getBlockState(testPos);
                            
                            // Check for fluids (Water/Lava) or Air
                            if (!state.getFluidState().isEmpty() || state.isAir()) {
                                RandomRunMod.LOGGER.warn("⚠ Unsafe spawn detected (Water/Air). Searching for safe spot...");
                                boolean foundSafe = false;
                                
                                // Spiral search for safe spot
                                int radius = 1;
                                int maxRadius = 32; // Search up to 32 blocks away
                                
                                searchLoop:
                                for (int r = radius; r <= maxRadius; r += 2) {
                                    for (int x = -r; x <= r; x += 4) {
                                        for (int z = -r; z <= r; z += 4) {
                                            int checkX = (int)targetX + x;
                                            int checkZ = (int)targetZ + z;
                                            int checkY = serverWorld.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, checkX, checkZ);
                                            
                                            net.minecraft.util.math.BlockPos safePos = new net.minecraft.util.math.BlockPos(checkX, checkY - 1, checkZ);
                                            net.minecraft.block.BlockState safeState = serverWorld.getBlockState(safePos);
                                            
                                            if (safeState.getFluidState().isEmpty() && !safeState.isAir()) {
                                                targetX = checkX + 0.5;
                                                targetZ = checkZ + 0.5;
                                                topY = checkY;
                                                foundSafe = true;
                                                RandomRunMod.LOGGER.info("✓ Safe spawn found at: " + targetX + ", " + topY + ", " + targetZ);
                                                break searchLoop;
                                            }
                                        }
                                    }
                                }
                                
                                if (!foundSafe) {
                                     // Fallback: Create platform (REMOVED)
                                     RandomRunMod.LOGGER.warn("⚠ No safe spawn found. Glass platform disabled.");
                                     // serverWorld.setBlockState(testPos, net.minecraft.block.Blocks.GLASS.getDefaultState());
                                     // topY = testPos.getY() + 1;
                                }
                            }
                            
                            RandomRunMod.LOGGER.info("📍 [Соло] Y спавна рассчитан через ServerWorld: " + topY);
                        } else {
                            int chunkX = (int)targetX >> 4;
                            int chunkZ = (int)targetZ >> 4;
                            if (client.world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                                int heightmapY = client.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, (int)targetX, (int)targetZ);
                                
                                // Safe Spawn Check (Client Side)
                                net.minecraft.util.math.BlockPos testPos = new net.minecraft.util.math.BlockPos((int)targetX, heightmapY - 1, (int)targetZ);
                                net.minecraft.block.BlockState state = client.world.getBlockState(testPos);
                                
                                if (!state.getFluidState().isEmpty() || state.isAir()) {
                                    RandomRunMod.LOGGER.warn("⚠ [Client] Unsafe spawn detected. Searching...");
                                    // Client side search is limited by loaded chunks, but we try
                                    boolean foundSafe = false;
                                    for (int x = -16; x <= 16; x += 4) {
                                        for (int z = -16; z <= 16; z += 4) {
                                            int checkX = (int)targetX + x;
                                            int checkZ = (int)targetZ + z;
                                            int checkY = client.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, checkX, checkZ);
                                            
                                            net.minecraft.util.math.BlockPos safePos = new net.minecraft.util.math.BlockPos(checkX, checkY - 1, checkZ);
                                            net.minecraft.block.BlockState safeState = client.world.getBlockState(safePos);
                                            
                                            if (safeState.getFluidState().isEmpty() && !safeState.isAir()) {
                                                targetX = checkX + 0.5;
                                                targetZ = checkZ + 0.5;
                                                
                                                // Deterministic offset based on player UUID to prevent stacking but keep players near
                                                // Range: -2.0 to +2.0
                                                int hash = client.player.getUuid().hashCode();
                                                targetX += ((hash % 5) - 2) * 1.0; 
                                                targetZ += (((hash / 5) % 5) - 2) * 1.0;
                                                
                                                topY = checkY;
                                                foundSafe = true;
                                                break;
                                            }
                                        }
                                        if (foundSafe) break;
                                    }
                                    if (!foundSafe) topY = client.world.getSeaLevel() + 1;
                                } else {
                                    topY = heightmapY;
                                 }
                            } else {
                                topY = client.world.getSeaLevel() + 1;
                            }
                        }

                        if (topY > client.world.getBottomY() && topY < (client.world.getBottomY() + client.world.getHeight())) {
                            targetY = topY;
                        } else {
                            targetY = 80; // Absolute fallback
                        }
                        
                        client.player.refreshPositionAndAngles(targetX, targetY, targetZ, 0, 0);
                        client.player.setVelocity(0, 0, 0);
                        RandomRunMod.LOGGER.info("✓ Соло игрок телепортирован на фиксированный спавн: " + targetX + ", " + targetY + ", " + targetZ);
                    }
                });
                
                WorldCreator.clearPendingData();
            }
        }
    }

    @Mixin(CreateWorldScreen.class)
    public static abstract class AutoCreate {
        @Inject(method = "init", at = @At("TAIL"))
        private void onInit(CallbackInfo ci) {
            if (WorldCreator.hasPendingRun()) {
                if (WorldCreator.isCreationTriggered()) {
                    RandomRunMod.LOGGER.warn("⚠ Попытка повторного создания мира для текущего спидрана. Пропуск.");
                    return;
                }
                
                CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
                ScreenAccessor screenAccessor = (ScreenAccessor) screen;
                
                try {
                    net.minecraft.client.gui.screen.world.WorldCreator worldCreator = screenAccessor.getWorldCreator();
                    if (worldCreator != null) {
                        String worldName;
                        if (WorldCreator.getPendingTargetItem() != null) {
                            worldName = WorldCreator.generateWorldName(WorldCreator.getPendingTargetItem());
                        } else if (WorldCreator.getPendingAdvancementId() != null) {
                            worldName = "RandomRun " + WorldCreator.getPendingAdvancementId().getPath().replace('/', '_');
                        } else {
                            worldName = "RandomRun Speedrun";
                        }
                        
                        RandomRunMod.LOGGER.info("Настройка мира для спидрана: " + worldName);
                        worldCreator.setWorldName(worldName);
                        
                        net.minecraft.client.gui.screen.world.WorldCreator.Mode targetMode = net.minecraft.client.gui.screen.world.WorldCreator.Mode.HARDCORE;
                        
                        if (com.randomrun.battle.BattleManager.getInstance().isInBattle()) {
                            // В мультиплеере всегда SURVIVAL, чтобы можно было возрождаться (наблюдателем)
                            targetMode = net.minecraft.client.gui.screen.world.WorldCreator.Mode.SURVIVAL;
                        } else {
                             // В одиночном режиме проверяем конфиг
                             boolean isHardcore = RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled();
                             targetMode = isHardcore 
                                 ? net.minecraft.client.gui.screen.world.WorldCreator.Mode.HARDCORE 
                                 : net.minecraft.client.gui.screen.world.WorldCreator.Mode.SURVIVAL;
                        }
                        
                        worldCreator.setGameMode(targetMode); 
                        RandomRunMod.LOGGER.info("Режим игры применен: " + targetMode.name());
                        
                        String pendingSeed = WorldCreator.getPendingSeed();
                        if (pendingSeed != null && !pendingSeed.isEmpty()) {
                            pendingSeed = pendingSeed.trim();
                            worldCreator.setSeed(pendingSeed);
                            
                            // Если мы в битве, сид предоставлен системой (BattleManager), а не пользователем вручную.
                            // Поэтому считаем его "не ручным" (false), чтобы разрешить лидерборды.
                            boolean isBattle = com.randomrun.battle.BattleManager.getInstance().isInBattle();
                            WorldCreator.setLastCreatedSeed(pendingSeed, !isBattle);
                            
                            RandomRunMod.LOGGER.info("✓ Сид установлен: '" + pendingSeed + "' (IsBattle: " + isBattle + ")");
                        } else {
                            String generatedSeed = String.valueOf(new java.util.Random().nextLong());
                            worldCreator.setSeed(generatedSeed);
                            WorldCreator.setLastCreatedSeed(generatedSeed, false);
                            RandomRunMod.LOGGER.info("✓ Случайный сид " + generatedSeed);
                        }
                    }
                } catch (Exception e) {
                    RandomRunMod.LOGGER.error("Не удалось настроить мир для спидрана", e);
                }
                
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        if (WorldCreator.isCreationTriggered()) return; // Double check inside executor
                        WorldCreator.setCreationTriggered(true);
                        
                        screenAccessor.invokeCreateLevel();
                        RandomRunMod.LOGGER.info("Создание мира началось");
                    } catch (Exception e) {
                        RandomRunMod.LOGGER.error("Не удалось начать создание мира", e);
                        WorldCreator.setCreationTriggered(false); // Reset on error
                    }
                });
            }
        }
    }

    // Accessor Interfaces (Inner)
    
    @Mixin(CreateWorldScreen.class)
    public interface ScreenAccessor {
        @Invoker("createLevel")
        void invokeCreateLevel();
        
        @Accessor("worldCreator")
        net.minecraft.client.gui.screen.world.WorldCreator getWorldCreator();
    }
}
