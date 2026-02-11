/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.battle;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.main.util.IdCompressor;
import com.randomrun.ui.screen.endgame.DefeatScreen;
import com.randomrun.battle.screen.MatchReadyScreen;
import com.randomrun.challenges.classic.world.WorldCreator;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.*;

public class BattleManager {
    
    public static final String OWNERSHIP = "PROTECTED CODE: (c) 2026 Stanislav Kholod. Unauthorized copying is prohibited.";

    private static BattleManager instance;
    private final FirebaseClient firebaseClient;
    private final Gson gson = new Gson();
    private ScheduledExecutorService scheduler;

    // Состояние комнаты
    private volatile BattleRoom currentRoom;
    private String currentRoomId;
    private boolean isHost, isFrozen, isInBattle, disconnectConfirmed;

    // Задачи планировщика
    private ScheduledFuture<?> roomListenerTask;

    // Флаги состояния
    private boolean battleEndHandled, loadingTriggered, waitingForWorldLoad, connectionStarted;
    private boolean isCreatingRoom = false; // Lock for room creation
    private String lastConnectionAddress;
    private long lastEventTime = 0;
    
    // E4MC домен
    private boolean awaitingE4mcDomain = false;
    private String lastReceivedDomain = null;
    private long domainReceivedTime = 0;
    private String manualServerAddress = null;

    private BattleManager() {
        this.firebaseClient = FirebaseClient.getInstance();
    }
    
    public static BattleManager getInstance() {
        if (instance == null) instance = new BattleManager();
        return instance;
    }
    
    // ============= ГЕТТЕРЫ/СЕТТЕРЫ =============
    
    public void setAwaitingE4mcDomain(boolean awaiting) {
        this.awaitingE4mcDomain = awaiting;
        log("Ожидание e4mc домена: " + awaiting);
    }
    
    public boolean isAwaitingE4mcDomain() { return awaitingE4mcDomain; }
    public String getLastReceivedDomain() { return lastReceivedDomain; }
    public long getDomainReceivedTime() { return domainReceivedTime; }
    public BattleRoom getCurrentRoom() { return currentRoom; }
    public boolean isHost() { return isHost; }
    public boolean isDisconnectConfirmed() { return disconnectConfirmed; }
    public void setDisconnectConfirmed(boolean confirmed) { this.disconnectConfirmed = confirmed; }
    public boolean isFrozen() { return isFrozen; }
    public boolean isSharedWorld() { return currentRoom != null && currentRoom.isSharedWorld(); }
    public boolean isInBattle() { return isInBattle; }
    public String getCurrentRoomId() { return currentRoomId; }
    public void setManualServerAddress(String addr) { this.manualServerAddress = addr; log("Установлен ручной IP: " + addr); }
    
    // ============= СОЗДАНИЕ/ВСТУПЛЕНИЕ В КОМНАТУ =============
    
    // Секретный токен для управления комнатой (удаление)
    private String roomAdminToken;

    public CompletableFuture<String> createRoom(String playerName, Item targetItem, boolean isSharedWorld, 
                                                 boolean isPrivate, String password, int maxPlayers, String creationMode) {
        if (isCreatingRoom) {
            logWarn("Попытка создать комнату, когда создание уже в процессе!");
            return CompletableFuture.completedFuture(null);
        }
        isCreatingRoom = true;
        resetForNewGame();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                cleanupStaleRoomsSync();
                
                String roomCode = generateRoomCode();
                
                // Collision check (max 3 retries)
                for (int i = 0; i < 3; i++) {
                    if (getRoomData(roomCode) != null) {
                        logWarn("Collision detected for room " + roomCode + ", regenerating...");
                        roomCode = generateRoomCode();
                    } else {
                        break;
                    }
                }
                
                String seed = String.valueOf(new Random().nextLong());
                String itemId = targetItem != null ? IdCompressor.compress(Registries.ITEM.getId(targetItem).toString()) : "minecraft:dirt";
                
                BattleRoom newRoom = new BattleRoom(playerName, seed, itemId, roomCode, isPrivate, password, isSharedWorld, maxPlayers, creationMode);
                
                // Explicit debug log for creation mode
                log("Создание комнаты: " + roomCode + ", Mode: " + creationMode + " (Raw: " + newRoom.getCreationMode() + ")");
                
                // Генерируем секретный токен для этой комнаты
                this.roomAdminToken = UUID.randomUUID().toString();
                
                String path = "/rooms/" + roomCode;
                if (firebaseClient.put(path, newRoom).join()) {
                     setupRoom(newRoom, roomCode, true);
                     com.randomrun.main.data.GlobalStatsManager.incrementRoomCreated();
                     registerPlayer(playerName);
                     startRoomListener(roomCode);
                     log("Комната создана (PUT): " + roomCode);
                     showRoomCode();
                     isCreatingRoom = false;
                     return roomCode;
                }
                
                sendPlayerMessage("§cОшибка создания комнаты (Firebase)!");
                isCreatingRoom = false;
                return null;
            } catch (Exception e) {
                logError("Ошибка создания комнаты", e);
                sendPlayerMessage("§cОшибка: " + e.getMessage());
                isCreatingRoom = false;
                return null;
            }
        });
    }
    
    public CompletableFuture<Boolean> joinRoom(String playerName, String roomCode, String password) {
        resetForNewGame();
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Попытка присоединения с защитой от гонки условий (Race Condition)
                for (int attempt = 0; attempt < 5; attempt++) {
                    JsonObject roomData = getRoomData(roomCode);
                    if (roomData == null) return false;
                    
                    BattleRoom room = gson.fromJson(roomData, BattleRoom.class);
                    
                    // Проверка пароля (только при первой попытке, чтобы не спамить логи)
                    if (attempt == 0 && room.isPrivate() && !validatePassword(room.getPassword(), password)) {
                        logWarn("Неверный пароль для комнаты: " + roomCode);
                        return false;
                    }
                    
                    // Проверка уже присоединенного игрока
                    List<String> currentPlayers = new ArrayList<>(room.getPlayers());
                    if (currentPlayers.contains(playerName)) {
                        log("Игрок уже в комнате: " + roomCode);
                        // Продолжаем как успех, но нужно обновить локальное состояние
                        setupRoom(room, roomCode, false);
                        startRoomListener(roomCode);
                        return true;
                    }

                    // Проверка вместимости
                    if (currentPlayers.size() >= room.getMaxPlayers()) {
                        logWarn("Комната заполнена: " + roomCode);
                        return false;
                    }
                    
                    // Добавляем игрока
                    room.addPlayer(playerName);
                    
                    String path = "/rooms/" + roomCode;
                    
                    // Пытаемся обновить
                    if (firebaseClient.put(path, room).join()) { // PUT заменяет весь объект, что нам и нужно для обновления списка игроков
                        // ВЕРИФИКАЦИЯ (Optimistic Locking check)
                        // Сразу читаем обратно, чтобы убедиться, что мы не перезаписали чужой вход
                        JsonObject verifyData = getRoomData(roomCode);
                        if (verifyData != null) {
                            BattleRoom verifyRoom = gson.fromJson(verifyData, BattleRoom.class);
                            List<String> serverPlayers = verifyRoom.getPlayers();
                            
                            if (serverPlayers.contains(playerName)) {
                                // Мы успешно добавились.
                                // Но нужно проверить, не стерли ли мы кого-то, кто добавился одновременно
                                
                                boolean integrityCompromised = false;
                                List<String> fixedPlayers = new ArrayList<>(serverPlayers);
                                
                                // Проверяем, что все, кто БЫЛ до нас, остались
                                List<String> playersBeforeUs = gson.fromJson(roomData, BattleRoom.class).getPlayers();
                                for (String p : playersBeforeUs) {
                                    if (!serverPlayers.contains(p)) {
                                        logWarn("ОБНАРУЖЕНА ГОНКА УСЛОВИЙ! Игрок " + p + " был перезаписан. Восстанавливаем...");
                                        fixedPlayers.add(p);
                                        integrityCompromised = true;
                                    }
                                }
                                
                                if (integrityCompromised) {
                                    // Восстанавливаем потерянных игроков
                                    verifyRoom.setPlayers(fixedPlayers);
                                    firebaseClient.put(path, verifyRoom).join();
                                    // Повторяем цикл верификации (следующая итерация цикла for)
                                    log("Повторная попытка после восстановления целостности...");
                                    Thread.sleep(200 + new Random().nextInt(300)); // Случайная задержка
                                    continue;
                                }
                                
                                // Все хорошо
                                setupRoom(verifyRoom, roomCode, false);
                                addGuestToMap(path, playerName);
                                registerPlayer(playerName);
                                startRoomListener(roomCode);
                                log("Присоединился к комнате: " + roomCode);
                                return true;
                            } else {
                                // Нас нет в списке (кто-то перезаписал нас). Повторяем.
                                logWarn("Не удалось найти себя в комнате после записи. Повтор...");
                                Thread.sleep(200 + new Random().nextInt(300));
                                continue;
                            }
                        }
                    }
                    
                    Thread.sleep(200); // Задержка перед повтором
                }
                
                logError("Не удалось присоединиться к комнате после 5 попыток (Race Condition)");
                return false;
                
            } catch (Exception e) {
                logError("Ошибка вступления в комнату: " + roomCode, e);
            }
            return false;
        });
    }
    
    public CompletableFuture<List<BattleRoom>> getAllRooms() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Do not use cache for room list to ensure we see new rooms immediately
                JsonElement roomsElement = firebaseClient.get("/rooms", false).join();
                List<BattleRoom> rooms = new ArrayList<>();
                
                if (roomsElement != null && roomsElement.isJsonObject()) {
                    JsonObject roomsData = roomsElement.getAsJsonObject();
                    for (String key : roomsData.keySet()) {
                        if (key.startsWith(".")) continue;
                        JsonObject roomObj = roomsData.getAsJsonObject(key);
                        BattleRoom room = gson.fromJson(roomObj, BattleRoom.class);
                        if (room.getStatus() != BattleRoom.RoomStatus.FINISHED) {
                            rooms.add(room);
                        }
                    }
                }
                
                rooms.sort((r1, r2) -> Long.compare(r2.getCreatedAt(), r1.getCreatedAt()));
                return rooms;
            } catch (Exception e) {
                logError("Ошибка получения списка комнат", e);
                return new ArrayList<>();
            }
        });
    }
    // ============= ПУБЛИЧНАЯ ОЧЕРЕДЬ =============
    
    public CompletableFuture<Boolean> findAndJoinPublicRoom(String playerName, Item targetItem, boolean isSharedWorld) {
        cleanupStaleRooms();
        resetForNewGame();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Do not use cache for public search
                JsonElement allRoomsElement = firebaseClient.get("/rooms", false).join();
                String foundRoomId = null;
                BattleRoom foundRoom = null;
                
                // Поиск подходящей комнаты
                if (allRoomsElement != null && allRoomsElement.isJsonObject()) {
                    JsonObject allRooms = allRoomsElement.getAsJsonObject();
                    for (String roomId : allRooms.keySet()) {
                        try {
                            BattleRoom room = gson.fromJson(allRooms.getAsJsonObject(roomId), BattleRoom.class);
                            if (!room.isPrivate() && isRoomSuitable(room, isSharedWorld)) {
                                foundRoomId = roomId;
                                foundRoom = room;
                                break;
                            }
                        } catch (Exception e) {
                            logWarn("Ошибка парсинга комнаты: " + roomId + " - " + e.getMessage());
                        }
                    }
                }
                
                // Присоединение или создание
                if (foundRoomId != null) {
                    return joinExistingRoom(foundRoomId, foundRoom, playerName);
                } else {
                    return createNewPublicRoom(playerName, targetItem, isSharedWorld);
                }
                
            } catch (Exception e) {
                logError("Ошибка в findAndJoinPublicRoom", e);
                return false;
            }
        });
    }
    
    // ============= ЗАГРУЗКА МИРА =============
    
    public void onWorldLoaded() {
        if (isHost && waitingForWorldLoad && currentRoom != null) {
            showRoomCode();
            if (currentRoom.isSharedWorld()) {
                log("Мир загружен, запуск LAN хостинга...");
                scheduler.schedule(this::openWorldToLAN, 4, TimeUnit.SECONDS);
            }
            waitingForWorldLoad = false;
        }
    }

    private void openWorldToLAN() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.world == null) {
                logError("Мир не загружен!");
                return;
            }
            
            logBox("ОТКРЫТИЕ МИРА ДЛЯ LAN");
            setAwaitingE4mcDomain(manualServerAddress == null || manualServerAddress.isEmpty());
            
            var server = client.getServer();
            if (server == null) {
                logError("Сервер null!");
                return;
            }
            
            if (server.openToLan(net.minecraft.world.GameMode.SURVIVAL, false, 25565)) {
                server.setOnlineMode(false);
                log("✓ LAN сервер запущен на порту " + server.getServerPort());
                log("✓ Принудительный OFFLINE режим для TLauncher");

                if (manualServerAddress != null && !manualServerAddress.isEmpty()) {
                    sendPlayerMessage("§a[RandomRun] §7Сервер запущен. Использование IP Radmin: §b§n" + manualServerAddress);
                    updateServerAddress(manualServerAddress);
                    manualServerAddress = null;
                } else {
                    sendPlayerMessage("§a[RandomRun] §7Сервер запущен. Ожидание e4mc адреса...");
                    scheduleIPCheck("E4MC");
                }
            } else {
                logError("✗ Не удалось открыть LAN сервер");
            }
        });
    }
    
    private void scheduleIPCheck(String providerName) {
        new Thread(() -> {
            try {
                Thread.sleep(30000);
                MinecraftClient.getInstance().execute(() -> {
                    if (isAwaitingE4mcDomain()) {
                        logWarn("⚠ E4MC домен не получен за 30 секунд!");
                        sendPlayerMessage("§e[RandomRun] §7E4MC адрес еще не получен. Проверьте чат.");
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "E4MC-Check").start();
    }

    public void updateServerAddress(String domain) {
        if (domain == null || domain.isEmpty()) {
            logWarn("Попытка обновить пустой адрес");
            return;
        }
        
        this.awaitingE4mcDomain = false;
        this.lastReceivedDomain = domain;
        this.domainReceivedTime = System.currentTimeMillis();
        
        logBox("ПОЛУЧЕН ВНЕШНИЙ IP\n  Адрес: " + domain);
        
        try {
            String roomCode = getCurrentRoomCode();
            if (roomCode == null || roomCode.isEmpty()) {
                logError("Нет активной комнаты для обновления IP!");
                return;
            }
            
            if (currentRoom != null) currentRoom.setServerAddress(domain);
            
            String path = "/rooms/" + roomCode;
            
            Map<String, Object> updates = new HashMap<>();
            updates.put("ip", domain);
            
            log("Отправка в Firebase: " + path + " -> " + domain);
            
            firebaseClient.patch(path, updates).thenAccept(success -> {
                if (success) {
                    logBox("✓ АДРЕС СЕРВЕРА ОБНОВЛЕН\n  Комната: " + roomCode + "\n  Адрес: " + domain);
                    sendPlayerMessage("§a§l[✓] §7IP успешно отправлен в базу данных!");
                    sendPlayerMessage("§7Адрес: §b§n" + domain);
                } else {
                    logError("Firebase ошибка при отправке IP");
                    sendPlayerMessage("§c§l[✗] §7Ошибка Firebase при отправке IP");
                }
            }).exceptionally(e -> {
                logError("Критическая ошибка при обновлении адреса", (Exception) e);
                return null;
            });
            
        } catch (Exception e) {
            logError("Критическая ошибка в updateServerAddress", e);
        }
    }
    
    // ============= ПОДКЛЮЧЕНИЕ К ХОСТУ =============
    
    private void connectToHost(String addressStr, boolean force) {
        connectToHost(addressStr, force, 0);
    }
    
    private void connectToHost(String addressStr, boolean force, int retryCount) {
        if (!force && connectionStarted) return;
        connectionStarted = true;
        
        MinecraftClient.getInstance().execute(() -> {
            try {
                log("Подключение к хосту: " + addressStr + " (Попытка " + (retryCount + 1) + ")");
                
                ServerInfo info = new ServerInfo("Battle Host", addressStr, ServerInfo.ServerType.OTHER);
                ServerAddress address = ServerAddress.parse(addressStr);
                log("Парсинг ServerAddress: " + address.getAddress() + ":" + address.getPort());
                
                ConnectScreen.connect(MinecraftClient.getInstance().currentScreen, 
                                     MinecraftClient.getInstance(), address, info, false, null);
                
                monitorConnectionAttempt(addressStr, retryCount, System.currentTimeMillis());
                
            } catch (Exception e) {
                logError("Ошибка подключения к хосту", e);
                connectionStarted = false;
                
                if (retryCount < 5 && scheduler != null && !scheduler.isShutdown()) {
                    log("Повтор через 3с...");
                    scheduler.schedule(() -> connectToHost(addressStr, true, retryCount + 1), 
                                      3, TimeUnit.SECONDS);
                }
            }
        });
    }
    
    private void monitorConnectionAttempt(String addressStr, int retryCount, long startTime) {
        if (scheduler == null || scheduler.isShutdown()) return;
        
        scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    log("✓ Подключение успешно!");
                    return;
                }
                
                if (MinecraftClient.getInstance().currentScreen != null) {
                    String screenName = MinecraftClient.getInstance().currentScreen.getClass().getSimpleName();
                    if (screenName.contains("Disconnected")) {
                        handleConnectionFailure(addressStr, retryCount, "Обнаружен DisconnectedScreen");
                        return;
                    }
                }
                
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > 30000) {
                    handleConnectionFailure(addressStr, retryCount, "Таймаут подключения (>30с)");
                    return;
                }
                
                monitorConnectionAttempt(addressStr, retryCount, startTime);
            });
        }, 1, TimeUnit.SECONDS);
    }
    
    private void handleConnectionFailure(String addressStr, int retryCount, String reason) {
        logWarn("Подключение не удалось: " + reason);
        
        if (retryCount < 5) {
            log("Повтор подключения через 3с... (Попытка " + (retryCount + 2) + "/6)");
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.schedule(() -> connectToHost(addressStr, true, retryCount + 1), 
                                  3, TimeUnit.SECONDS);
            }
        } else {
            logError("Достигнут лимит повторов. Отмена.");
        }
    }
    
    // ============= ГОТОВНОСТЬ И СТАРТ =============
    
    public void setStatusLoading() {
        if (currentRoomId == null || currentRoom == null) {
            logWarn("Невозможно установить статус LOADING - нет активной комнаты");
            return;
        }
        
        if (loadingTriggered) {
            log("LOADING уже запущен, пропуск");
            return;
        }
        loadingTriggered = true;
        
        logBox("🔄 УСТАНОВКА СТАТУСА LOADING\n  Room ID: " + currentRoomId + 
               "\n  Is Private: " + currentRoom.isPrivate());
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + currentRoomId;
                
                JsonObject update = new JsonObject();
                update.addProperty("st", "LOADING");
                update.addProperty("rc", 0);
                update.addProperty("hl", false);
                
                boolean success = firebaseClient.patch(path, update).join();
                log("Статус установлен в LOADING, readyCount сброшен. Успех: " + success);
                
                if (currentRoom.isSharedWorld() && isHost) {
                    MinecraftClient.getInstance().execute(() -> {
                        log("ХОСТ: Создание мира для Shared World режима...");
                        this.waitingForWorldLoad = true;
                        
                        Item targetItem = Registries.ITEM.get(Identifier.of(IdCompressor.decompress(currentRoom.getTargetItem())));
                        WorldCreator.createSpeedrunWorld(targetItem, currentRoom.getSeed());
                    });
                }
                
            } catch (Exception e) {
                logError("Ошибка установки статуса LOADING", e);
            }
        });
    }
    
    public void setPlayerLoaded() {
        if (currentRoomId == null || currentRoom == null) {
            logWarn("Невозможно установить готовность игрока - нет активной комнаты");
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + currentRoomId;
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                boolean isHostPlayer = currentRoom.isHost(playerName);
                
                if (isHostPlayer) {
                    JsonObject update = new JsonObject();
                    update.addProperty("hl", true);
                    if (firebaseClient.patch(path, update).join()) {
                        currentRoom.setHostLoaded(true);
                    }
                } else {
                    updateGuestMap(path, playerName, "l", true);
                }
                
                logBox("✓ Игрок загружен в мир: " + playerName);
            } catch (Exception e) {
                logError("Ошибка установки готовности игрока", e);
            }
        });
    }

    public void sendLobbyReady() {
        if (currentRoom == null || currentRoomId == null) {
            logWarn("Невозможно отправить готовность в лобби - нет активной комнаты");
            return;
        }
        
        if (currentRoom.getStatus() != BattleRoom.RoomStatus.WAITING) {
            logWarn("Невозможно отправить готовность - не в статусе WAITING: " + currentRoom.getStatus());
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + currentRoomId;
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                boolean isHostPlayer = currentRoom.isHost(playerName);
                
                logBox("⚡ ОТПРАВКА ГОТОВНОСТИ В ЛОББИ\n  Room ID: " + currentRoomId + 
                       "\n  Player: " + playerName + " (isHost: " + isHostPlayer + ")");
                
                // Исправленная логика обновления: Обновляем только флаги хоста или карту гостей
                // Список readyPlayers (rp) больше не используется и вычисляется динамически
                if (isHostPlayer) {
                    JsonObject update = new JsonObject();
                    update.addProperty("hr", true);
                    firebaseClient.patch(path, update).join();
                    currentRoom.setHostReady(true);
                } else {
                    updateGuestMap(path, playerName, "r", true);
                }
                
                log("  - Готовность отправлена");
                
                JsonElement latestRoomElement = firebaseClient.get(path).join();
                if (latestRoomElement != null && latestRoomElement.isJsonObject()) {
                    BattleRoom latestRoom = gson.fromJson(latestRoomElement, BattleRoom.class);
                    currentRoom = latestRoom;
                    
                    if (latestRoom.isHostReady() && latestRoom.isGuestReady() && 
                        latestRoom.getStatus() == BattleRoom.RoomStatus.WAITING) {
                        
                        if (isHostPlayer) {
                            log("✓ Оба игрока готовы в лобби - ХОСТ переходит в LOADING");
                            MinecraftClient.getInstance().execute(this::setStatusLoading);
                        } else {
                            log("✓ Оба игрока готовы в лобби - ожидание ХОСТА");
                        }
                    }
                }
                
            } catch (Exception e) {
                logError("Ошибка отправки готовности в лобби", e);
            }
        });
    }

    public void sendReady() {
        if (currentRoom == null) {
            log("sendReady вызван вне битвы - разрешено для соло спидрана");
            return;
        }
        
        if (currentRoom.getStatus() != BattleRoom.RoomStatus.FROZEN) {
            if (currentRoom.getStatus() == BattleRoom.RoomStatus.LOADING) {
                sendPlayerMessage(Text.translatable("randomrun.battle.opponent_loading"));
                return;
            }
            logWarn("Невозможно отправить готовность - не в статусе FROZEN: " + currentRoom.getStatus());
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + currentRoomId;
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                boolean isHostPlayer = currentRoom.isHost(playerName);
                
                logBox("⚡ ОТПРАВКА ГОТОВНОСТИ (/go)\n  Room ID: " + currentRoomId + 
                       "\n  Player: " + playerName + " (isHost: " + isHostPlayer + ")");
                
                // Optimized update: Just update specific fields (hostReady or guest map)
                
                if (isHostPlayer) {
                    JsonObject update = new JsonObject();
                    update.addProperty("hr", true);
                    firebaseClient.patch(path, update).join();
                } else {
                    // Update Guest: Ready = true, Disconnected = false
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("r", true);
                    updates.put("dd", false); // Initialize dd field
                    updateGuestMap(path, playerName, updates);
                }
                
                // Note: Ready players list is now computed dynamically on client side from hr and g maps.
                // No need to patch "rp" field anymore.
                
                JsonElement latestRoomElement = firebaseClient.get(path).join();
                if (latestRoomElement != null && latestRoomElement.isJsonObject()) {
                    BattleRoom latestRoom = gson.fromJson(latestRoomElement, BattleRoom.class);
                    latestRoom.setPlayerReady(playerName, true);
                    if (isHostPlayer) latestRoom.setHostReady(true);
                    else latestRoom.setGuestReady(true);
                    
                    currentRoom = latestRoom;
                    
                    if (areAllPlayersReady(latestRoom) && 
                        latestRoom.getStatus() == BattleRoom.RoomStatus.FROZEN) {
                        
                        JsonObject statusUpdate = new JsonObject();
                        statusUpdate.addProperty("st", "STARTED");
                        // Start time logic: now + 3000ms
                        long startDelay = 3000;
                        long serverStartTime = System.currentTimeMillis() + startDelay;
                        statusUpdate.addProperty("srv_start", serverStartTime);
                        
                        // FIX: Для Shared World используем игровое время для синхронизации
                        if (latestRoom.isSharedWorld()) {
                             MinecraftClient client = MinecraftClient.getInstance();
                             if (client.world != null) {
                                 // +60 тиков = 3 секунды
                                 long targetWorldTime = client.world.getTime() + 60;
                                 statusUpdate.addProperty("swt", targetWorldTime);
                                 latestRoom.setSharedWorldStartTime(targetWorldTime); // Update local object
                                 log("Установлено время старта мира (SWT): " + targetWorldTime + " (текущее: " + client.world.getTime() + ")");
                             }
                        }
                        
                        latestRoom.setServerStartTime(serverStartTime); // Update local object
                        latestRoom.setStatus(BattleRoom.RoomStatus.STARTED); // Update local status to prevent double start from listener
                        currentRoom = latestRoom; // Ensure currentRoom is up to date locally
                        
                        firebaseClient.patch(path, statusUpdate).join();
                        
                        log("✓ Все игроки готовы - битва начинается (Синхронизированный старт: " + serverStartTime + ")");
                        
                        // Local host start
                        scheduleStart(startDelay);
                    } else {
                        log("⏳ Ожидание готовности противников...");
                        sendPlayerMessage("§eОжидание готовности противника...");
                    }
                }
            } catch (Exception e) {
                logError("Ошибка отправки готовности", e);
            }
        });
    }
    
    // ============= ОТЧЁТЫ О ПОБЕДЕ/ПОРАЖЕНИИ =============
    
    private boolean hasWinner(JsonObject data) {
        return data != null && data.has("w") && !data.get("w").isJsonNull() && !data.get("w").getAsString().isEmpty();
    }

    public void reportVictory(long time) {
        log("reportVictory вызван со временем: " + time);
        if (currentRoom == null) {
            logError("reportVictory ошибка: currentRoom is null");
            return;
        }
        
        final String roomId = currentRoomId;
        final String playerName = MinecraftClient.getInstance().getSession().getUsername();
        final String timeFormatted = formatDuration(time);
        final String hostName = currentRoom.getHost();
        
        log("Запуск асинхронной отправки победы для: " + playerName + " в комнате: " + roomId);
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + roomId;
                
                if (time <= 0) {
                    logWarn("Невозможно сообщить о победе - неверное время: " + time);
                    return;
                }
                
                // Шаг 1: Записать время
                Map<String, Long> timeUpdate = new HashMap<>();
                timeUpdate.put(playerName, time);
                boolean timeSuccess = firebaseClient.patch(path + "/pt", timeUpdate).join();
                
                if (timeSuccess) {
                    log("Записано время финиша: " + playerName + " = " + time + "мс");
                } else {
                    logError("Ошибка записи времени финиша!");
                }
                
                Thread.sleep(200);
                
                // Шаг 2: Проверить существующего победителя
                JsonElement currentDataElement = firebaseClient.get(path).join();
                if (currentDataElement == null || !currentDataElement.isJsonObject()) {
                    logError("Не удалось получить данные комнаты для проверки победы");
                    return;
                }
                JsonObject currentData = currentDataElement.getAsJsonObject();
                
                if (hasWinner(currentData)) {
                    log("В матче уже есть победитель: " + currentData.get("w").getAsString());
                    return;
                }
                
                // Шаг 3: Объявить победу
                log("Победитель не найден, объявляем победу для: " + playerName);
                
                boolean winnerSuccess = firebaseClient.put(path + "/w", playerName).join();
                if (winnerSuccess) {
                    log("Успешно записан победитель!");
                } else {
                    logError("ОШИБКА записи победителя!");
                }

                firebaseClient.put(path + "/st", "FINISHED");

                boolean isPlayerHost = playerName.equals(hostName);
                if (isPlayerHost) {
                    firebaseClient.put(path + "/ht", time);
                    com.randomrun.main.data.GlobalStatsManager.incrementRun();
                } else {
                    updateGuestMapTime(path, playerName, time, timeFormatted);
                }
                
                // Шаг 4: Проверить гонку условий
                Thread.sleep(200);
                JsonElement verifyDataElement = firebaseClient.get(path).join();
                if (verifyDataElement != null && verifyDataElement.isJsonObject()) {
                    JsonObject verifyData = verifyDataElement.getAsJsonObject();
                    if (verifyData.has("w")) {
                        String actualWinner = verifyData.get("w").getAsString();
                        if (!playerName.equals(actualWinner)) {
                            logWarn("Обнаружена гонка условий! Я записал " + playerName + 
                                   ", но победитель теперь " + actualWinner);
                        } else {
                            log("Успешно подтверждена победа: " + playerName);
                        }
                    }
                }
            } catch (Exception e) {
                logError("Ошибка при отправке победы", e);
            }
        });
    }

    private void reportSharedWorldVictory(long time) {
        if (currentRoom == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + currentRoomId;
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                String timeFormatted = formatDuration(time);
                
                JsonElement currentDataElement = firebaseClient.get(path).join();
                if (currentDataElement != null && currentDataElement.isJsonObject() && hasWinner(currentDataElement.getAsJsonObject())) {
                    log("Shared World матч уже имеет победителя: " + currentDataElement.getAsJsonObject().get("w").getAsString());
                    return;
                }
                
                Map<String, Long> timeUpdate = new HashMap<>();
                timeUpdate.put(playerName, time);
                firebaseClient.patch(path + "/pt", timeUpdate).join();
                
                Thread.sleep(300);
                JsonElement recheckDataElement = firebaseClient.get(path).join();
                if (recheckDataElement != null && recheckDataElement.isJsonObject() && hasWinner(recheckDataElement.getAsJsonObject())) {
                    log("Shared World: Победитель был установлен другим игроком: " + 
                        recheckDataElement.getAsJsonObject().get("w").getAsString());
                    return;
                }
                
                JsonObject update = new JsonObject();
                update.addProperty("w", playerName);
                update.addProperty("st", "FINISHED");
                
                boolean isHostPlayer = playerName.equals(currentRoom.getHost());
                if (isHostPlayer) {
                    update.addProperty("ht", time);
                    com.randomrun.main.data.GlobalStatsManager.incrementRun();
                } else {
                    updateGuestMapTime(path, playerName, time, timeFormatted);
                }
                
                firebaseClient.patch(path, update).join();
                log("Shared World победа: " + playerName + " (последний выживший) со временем " + time + "мс");
            } catch (Exception e) {
                logError("Ошибка отчёта о Shared World победе", e);
            }
        });
    }

    private void reportSharedWorldDraw(long time) {
        if (currentRoom == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + currentRoomId;
                String playerName = MinecraftClient.getInstance().getSession().getUsername();
                String timeFormatted = formatDuration(time);
                
                // Проверка, есть ли уже победитель
                JsonElement currentDataElement = firebaseClient.get(path).join();
                if (currentDataElement != null && currentDataElement.isJsonObject() && hasWinner(currentDataElement.getAsJsonObject())) {
                    log("Матч уже завершен кем-то другим.");
                    return;
                }
                
                Map<String, Long> timeUpdate = new HashMap<>();
                timeUpdate.put(playerName, time);
                firebaseClient.patch(path + "/pt", timeUpdate).join();
                
                // Объявляем НИЧЬЮ
                JsonObject update = new JsonObject();
                update.addProperty("st", "DRAW");
                update.addProperty("dr", "survived_alone");
                
                boolean isHostPlayer = playerName.equals(currentRoom.getHost());
                if (isHostPlayer) {
                    update.addProperty("ht", time);
                } else {
                    updateGuestMapTime(path, playerName, time, timeFormatted);
                }
                
                firebaseClient.patch(path, update).join();
                log("Shared World: Объявлена НИЧЬЯ (остался один) со временем " + time + "мс");
                
                // Удаление комнаты с задержкой
                scheduler.schedule(() -> {
                    if (currentRoomId != null && currentRoomId.equals(currentRoomId)) {
                        deleteRoom();
                    }
                }, 10, TimeUnit.SECONDS);
                
            } catch (Exception e) {
                logError("Ошибка отчёта о Shared World ничьей", e);
            }
        });
    }

    public void reportElimination() {
        if (currentRoom == null || !isInBattle) return;
        
        final String roomId = currentRoomId;
        if (roomId == null) return;

        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + roomId;
                String myName = MinecraftClient.getInstance().getSession().getUsername();

                Map<String, Long> update = new HashMap<>();
                update.put(myName, System.currentTimeMillis());
                
                firebaseClient.patch(path + "/ep", update).join();
                log("Отчёт об элиминации (PATCH) для: " + myName);
                
                if (currentRoom != null) currentRoom.addEliminatedPlayer(myName);

            } catch (Exception e) {
                logError("Ошибка отчёта об элиминации", e);
            }
        });
    }

    public void reportDefeat(String reason) {
        if (currentRoom == null || !isInBattle) return;
        
        final String roomId = currentRoomId;
        if (roomId == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + roomId;
                String myName = MinecraftClient.getInstance().getSession().getUsername();
                
                reportDeathEvent(myName);
                
                Map<String, Long> elimUpdate = new HashMap<>();
                long myDeathTime = System.currentTimeMillis();
                elimUpdate.put(myName, myDeathTime);
                firebaseClient.patch(path + "/ep", elimUpdate).join();
                
                JsonElement roomDataElement = firebaseClient.get(path).join();
                if (roomDataElement != null && roomDataElement.isJsonObject()) {
                    BattleRoom room = gson.fromJson(roomDataElement, BattleRoom.class);
                    
                    List<String> allPlayers = room.getPlayers();
                    Map<String, Long> eliminatedMap = room.getEliminationMap();
                    
                    if (!eliminatedMap.containsKey(myName)) {
                        eliminatedMap.put(myName, myDeathTime);
                    }
                    
                    int activeCount = (int) allPlayers.stream()
                        .filter(p -> !eliminatedMap.containsKey(p)).count();
                    
                    if (allPlayers.size() > 1) {
                        String winnerName = determineWinner(allPlayers, eliminatedMap, activeCount);
                        
                        // Если причиной поражения был КРАШ/ДИСКОННЕКТ ("disconnect"), то объявляем НИЧЬЮ
                        if (reason != null && (reason.toLowerCase().contains("disconnect") || reason.toLowerCase().contains("crash"))) {
                            log("Обнаружен вылет игрока (" + reason + "). Объявляем НИЧЬЮ.");
                            
                            JsonObject update = new JsonObject();
                            update.addProperty("st", "DRAW");
                            update.addProperty("dr", "disconnect"); // Маркер для клиентов
                            
                            firebaseClient.patch(path, update).join();
                            
                            // Удаление комнаты с задержкой
                            scheduler.schedule(() -> {
                                if (currentRoomId != null && currentRoomId.equals(roomId)) {
                                    deleteRoom();
                                }
                            }, 10, TimeUnit.SECONDS);
                            
                        } else if (winnerName != null && room.getStatus() != BattleRoom.RoomStatus.FINISHED) {
                            
                            // ИЗМЕНЕНИЕ ПО ЗАПРОСУ: 
                            // Если кто-то умер, но остался один выживший, мы НЕ заканчиваем игру сразу.
                            // Мы даем выжившему шанс продолжить играть и найти предмет (или умереть).
                            // Игра заканчивается только если все вышли или кто-то нашел предмет.
                            // Поэтому здесь мы НИЧЕГО не делаем, если статус еще не FINISHED.
                            
                            log("Игрок " + myName + " выбыл. Остался последний выживший: " + winnerName + ". Игра продолжается!");
                            
                        } else if (activeCount > 0) {
                            log("Игрок " + myName + " выбыл. Активных игроков: " + activeCount);
                        }
                    }
                }
                
            } catch (Exception e) {
                logError("Ошибка отчёта о поражении", e);
            }
        });
    }
    
    public void reportDeathEvent(String playerName) {
        if (currentRoom == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + currentRoomId + "/events";
                JsonObject event = new JsonObject();
                event.addProperty("type", "DEATH");
                event.addProperty("player", playerName);
                event.addProperty("timestamp", System.currentTimeMillis());
                
                firebaseClient.post(path, event).join();
            } catch (Exception e) {
                logError("Ошибка отчёта о смерти", e);
            }
        });
    }

    public void reportAchievement(String achievementId, String title, String iconItem) {
        if (currentRoom == null) return;
        
        CompletableFuture.runAsync(() -> {
            try {
                String path = "/rooms/" + currentRoomId + "/events";
                JsonObject event = new JsonObject();
                event.addProperty("type", "ACHIEVEMENT");
                event.addProperty("player", MinecraftClient.getInstance().getSession().getUsername());
                event.addProperty("achievementId", achievementId);
                event.addProperty("title", title);
                event.addProperty("icon", iconItem);
                event.addProperty("timestamp", System.currentTimeMillis());
                
                firebaseClient.post(path, event).join();
            } catch (Exception e) {
                logError("Ошибка отчёта о достижении", e);
            }
        });
    }

    @Deprecated
    public void reportDraw(long time) {
        // Отключено по запросу пользователя
    }
    
    // Счетчик ошибок для startRoomListener
    private int consecutiveErrors = 0;
    
    // Adaptive Polling Variables
    private long lastPollTime = 0;
    private long pollInterval = 1000; // Default 1s
    private ScheduledFuture<?> pollingTask; // Renamed from roomListenerTask for clarity

    // ============= СЛУШАТЕЛИ =============
    private void startRoomListener(String roomId) {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(false);
        }
        
        consecutiveErrors = 0;
        
        // Use scheduleWithFixedDelay instead of fixedRate to allow dynamic intervals
        // However, standard ScheduledExecutorService doesn't support changing delay easily.
        // We will reschedule ourselves recursively or use a fixed fast tick (e.g. 500ms) and check elapsed time.
        // Recursive rescheduling is cleaner for dynamic intervals.
        
        scheduleNextPoll(roomId);
    }
    
    private void scheduleNextPoll(String roomId) {
        if (pollingTask != null && !pollingTask.isCancelled()) {
             pollingTask.cancel(false);
        }
        
        pollingTask = scheduler.schedule(() -> {
            try {
                pollRoom(roomId);
            } catch (Exception e) {
                logError("Critical polling error", e);
            } finally {
                // Schedule next poll regardless of success/failure (unless stopped)
                if (currentRoomId != null && currentRoomId.equals(roomId)) {
                    scheduleNextPoll(roomId);
                }
            }
        }, pollInterval, TimeUnit.MILLISECONDS);
    }

    private void pollRoom(String roomId) {
        try {
            String path = "/rooms/" + roomId;
            JsonElement roomDataElement = firebaseClient.get(path, true).join();
            
            // Check for ETag "No Change" marker
            if (roomDataElement != null && roomDataElement.isJsonObject() && roomDataElement.getAsJsonObject().has("_etag_no_change")) {
                // Data hasn't changed, but we might want to adjust polling rate if needed
                // For now, just keep existing interval or relax it if we are idle
                // log("ETag: No changes.");
                return;
            }
            
            if (roomDataElement == null || !roomDataElement.isJsonObject()) {
                consecutiveErrors++;
                logWarn("Не удалось получить данные комнаты (Попытка " + consecutiveErrors + "/5)");
                
                if (consecutiveErrors >= 5) {
                    logError("Слишком много ошибок связи. Отключение.");
                    stopBattle();
                }
                return;
            }
            
            JsonObject roomData = roomDataElement.getAsJsonObject();
            
            // Сброс счетчика при успехе
            consecutiveErrors = 0;
            
            BattleRoom updatedRoom = gson.fromJson(roomData, BattleRoom.class);
            
            // Adaptive Polling Logic
            updatePollingInterval(updatedRoom.getStatus());
            
            BattleRoom.RoomStatus oldStatus = currentRoom.getStatus();
            BattleRoom.RoomStatus newStatus = updatedRoom.getStatus();
            
            processRoomStatusChange(oldStatus, newStatus, updatedRoom, roomId, path);
            
            if ((updatedRoom.getWinner() != null || 
                 updatedRoom.getStatus() == BattleRoom.RoomStatus.DRAW || 
                 updatedRoom.getStatus() == BattleRoom.RoomStatus.FINISHED) 
                && !battleEndHandled) {
                
                battleEndHandled = true;
                MinecraftClient.getInstance().execute(() -> handleBattleEnd(updatedRoom));
            }
            
            if (newStatus == BattleRoom.RoomStatus.STARTED) {
                checkForEvents(path + "/events");
                checkSharedWorldVictory(updatedRoom);
            }
            
            currentRoom = updatedRoom;
            
        } catch (Exception e) {
            logError("Ошибка слушателя комнаты", e);
        }
    }
    
    private void updatePollingInterval(BattleRoom.RoomStatus status) {
        switch (status) {
            case WAITING:
                this.pollInterval = 1000; // Faster in lobby (1s)
                break;
            case STARTED:
            case LOADING:
            case FROZEN:
                this.pollInterval = 200; // Fast sync in game (0.2s)
                break;
            case FINISHED:
            case DRAW:
                this.pollInterval = 5000; // Very slow after finish (5s)
                break;
            default:
                this.pollInterval = 1000;
        }
    }
    
    private void checkForEvents(String eventsPath) {
        try {
            JsonElement eventsElement = firebaseClient.get(eventsPath).join();
            if (eventsElement == null || !eventsElement.isJsonObject()) return;
            
            JsonObject events = eventsElement.getAsJsonObject();
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            
            for (String key : events.keySet()) {
                JsonObject event = events.getAsJsonObject(key);
                long timestamp = event.get("timestamp").getAsLong();
                
                if (timestamp > lastEventTime) {
                    lastEventTime = timestamp;
                    
                    String eventPlayer = event.get("player").getAsString();
                    if (!eventPlayer.equals(playerName)) {
                        String type = event.get("type").getAsString();
                        
                        if ("ACHIEVEMENT".equals(type)) {
                            String title = event.get("title").getAsString();
                            String iconId = event.get("icon").getAsString();
                            
                            MinecraftClient.getInstance().execute(() -> {
                                com.randomrun.challenges.advancement.hud.OpponentAchievementHud.show(
                                    eventPlayer, title, iconId);
                            });
                        } else if ("DEATH".equals(type)) {
                            MinecraftClient.getInstance().execute(() -> {
                                com.randomrun.challenges.advancement.hud.OpponentAchievementHud.show(
                                    eventPlayer, 
                                    Text.translatable("randomrun.defeat.death").getString(), 
                                    "minecraft:skeleton_skull"
                                );
                            });
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Игнорировать ошибки
        }
    }
    
    // ============= УПРАВЛЕНИЕ СОСТОЯНИЕМ =============
    
    public void stopBattle() {
        logBox("🛑 ОСТАНОВКА БИТВЫ - ПОЛНЫЙ СБРОС");
        
        cancelAllTasks();
        resetAllFlags();
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        
        log("✓ Состояние битвы полностью сброшено");
    }
    
    public void resetForNewGame() {
        log("🔄 Сброс BattleManager для новой игры");
        
        this.lastEventTime = System.currentTimeMillis();
        cancelAllTasks();
        resetAllFlags();
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = Executors.newScheduledThreadPool(4);
        
        unfreezePlayer();
        
        if (RandomRunMod.getInstance().getRunDataManager() != null) {
            RandomRunMod.getInstance().getRunDataManager().cancelRun();
        }
        
        log("✓ Сброс BattleManager завершён");
    }
    
    public void freezePlayer() { 
        this.isFrozen = true; 
        log("Игрок ЗАМОРОЖЕН (BattleManager)");
    }
    public void unfreezePlayer() { 
        this.isFrozen = false; 
        log("Игрок РАЗМОРОЖЕН (BattleManager)");
    }
    
    // ============= ОЧИСТКА =============
    
    public void deleteRoom() {
        if (currentRoomId == null) return;
        
        final String roomId = currentRoomId;
        
        CompletableFuture.runAsync(() -> {
            try {
                // Обычное удаление (Firebase Rules должны разрешать удаление)
                firebaseClient.delete("/rooms/" + roomId).join();
                log("Удалена комната: " + roomId);
            } catch (Exception e) {
                logError("Ошибка удаления комнаты: " + roomId, e);
            }
        });
        
        this.roomAdminToken = null;
    }

    public void cleanupStaleRooms() {
        CompletableFuture.runAsync(this::cleanupStaleRoomsSync);
    }

    // ============= DISCONNECT HANDLING =============
    
    public void handlePlayerDisconnect(String playerName) {
        if (currentRoom == null || !isHost) return;
        
        log("Обработка отключения игрока: " + playerName);
        
        // 1. Mark as disconnected in local object
        currentRoom.setPlayerDisconnected(playerName, true);
        
        // 2. Update Firebase: Add 'dd' field to GUEST object specifically
        // Path: /rooms/{roomId}/g/{playerName}/dd
        // Always try to patch, even if local map doesn't show it (async sync issues)
        String guestPath = "/rooms/" + currentRoomId + "/g/" + playerName;
        Map<String, Object> update = new HashMap<>();
        update.put("dd", true);
        firebaseClient.patch(guestPath, update); // Don't join(), let it run async to avoid blocking main thread
        
        // Also update root 'dd' map for backward compatibility/easier lookup if needed
        String rootPath = "/rooms/" + currentRoomId + "/dd/" + playerName;
        firebaseClient.put(rootPath, true).thenRun(() -> {
             // 3. Check game state
             checkGameStateAfterDisconnect(playerName);
        });
    }
    
    // NEW: Handle Self Disconnect (Called when I leave the server/world)
    public void handleSelfDisconnect() {
        if (!isInBattle || currentRoom == null) return;
        
        String myName = MinecraftClient.getInstance().getSession().getUsername();
        log("Игрок " + myName + " покидает битву (Self Disconnect).");
        
        // If I am guest, I must update my 'dd' status before leaving context
        if (!isHost) {
             String guestPath = "/rooms/" + currentRoomId + "/g/" + myName;
             Map<String, Object> update = new HashMap<>();
             update.put("dd", true);
             // Use join() to ensure it sends before we kill the connection/thread
             try {
                 firebaseClient.patch(guestPath, update).join();
                 log("✓ Статус 'dd: true' успешно отправлен для " + myName);
             } catch (Exception e) {
                 logError("Ошибка отправки dd статуса при выходе", e);
             }
        }
        
        // Cleanup local state
        cleanupOnShutdown();
    }
    
    private void checkGameStateAfterDisconnect(String disconnectedPlayer) {
        if (currentRoom == null) return;
        
        List<String> players = currentRoom.getPlayers();
        int total = players.size();
        int active = 0;
        String lastActive = null;
        
        for (String p : players) {
            if (!currentRoom.isPlayerDisconnected(p) && !currentRoom.getEliminatedPlayers().contains(p)) {
                active++;
                lastActive = p;
            }
        }
        
        log("Проверка состояния после выхода: Всего=" + total + ", Активно=" + active);
        
        if (active == 1 && total > 1 && currentRoom.getStatus() == BattleRoom.RoomStatus.STARTED) {
            // Only one player left -> WINNER or DRAW?
            // User requested: "disconnect -> draw" logic in previous prompt, 
            // BUT here user said: "показывает что гость отключился... чтобы не было проблем... либо экран ничьи"
            // AND "чтобы когда он опять заходит, он уже получал режим наблюдателя"
            
            // If the remaining player finishes, they win.
            // If they are just playing alone, we should probably let them finish OR declare draw?
            // User said: "чтобы не было проблем, с тем что ктото один остался на сервер и игшрает дальше а не получает экран ничьи"
            // Meaning: They SHOULD get a Draw screen if the other disconnects?
            // "не получает экран ничьи" implies they currently play alone and nothing happens.
            // So user WANTS Draw screen OR notification.
            
            // Let's declare DRAW for safety if it's 1v1 and one leaves.
            // Or better: Let the host decide? 
            // The prompt says: "гостям dd... показывает что гость отключился... чтобы не было проблем... а не получает экран ничьи"
            // I interpret this as: "When guest disconnects, the game should end with Draw (or Win), instead of leaving the host alone."
            
            // However, typically in competitive games, if opponent disconnects, you WIN.
            // But user asked for "draw screen" specifically in previous context for "shared world disconnect".
            // Let's go with DRAW for now as per previous logic, but update the reason.
            
            final String winner = lastActive;
            
            // Опция 1: Объявить ничью
            reportSharedWorldDraw(System.currentTimeMillis() - currentRoom.getStartTime());
            
            // Опция 2 (Альтернатива): Авто-победа
            // reportVictory(winner);
        }
    }
    
    // Check for rejoin in Spectator Mode
    public void handlePlayerRejoin(String playerName) {
        if (currentRoom == null) return;
        
        if (currentRoom.isPlayerDisconnected(playerName)) {
            // Player was disconnected, now back.
            // Set Spectator Mode
            log("Игрок " + playerName + " вернулся после дисконнекта. Перевод в режим наблюдателя.");
            
            MinecraftClient.getInstance().execute(() -> {
                // If we are the rejoining player (Guest side logic?)
                // Wait, handlePlayerRejoin is usually called on server/host?
                // If we are host, we set their gamemode.
                if (isHost) {
                     net.minecraft.server.MinecraftServer server = MinecraftClient.getInstance().getServer();
                     if (server != null) {
                         net.minecraft.server.network.ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerName);
                         if (player != null) {
                             player.changeGameMode(net.minecraft.world.GameMode.SPECTATOR);
                             player.sendMessage(Text.literal("§cВы были отключены и переведены в режим наблюдателя."), false);
                         }
                     }
                }
            });
        }
    }

    private void cleanupStaleRoomsSync() {
        try {
            String playerName = MinecraftClient.getInstance().getSession().getUsername();
            long now = System.currentTimeMillis();
            
            cleanupRoomCategory("/rooms", playerName, now, 7200000); // 2 часа
            
        } catch (Exception e) {
            logError("Ошибка очистки устаревших комнат", e);
        }
    }

    public void cleanupOnShutdown() {
        if (currentRoomId == null) return;
        
        try {
            String path = "/rooms/" + currentRoomId;
            
            if (currentRoom != null && currentRoom.isPrivate() && 
               (currentRoom.getStatus() == BattleRoom.RoomStatus.STARTED || 
                currentRoom.getStatus() == BattleRoom.RoomStatus.FROZEN)) {
                
                String opponentName = isHost ? currentRoom.getGuest() : currentRoom.getHost();
                
                if (opponentName != null) {
                    JsonObject update = new JsonObject();
                    update.addProperty("w", opponentName);
                    update.addProperty("st", "FINISHED");
                    
                    firebaseClient.patch(path, update).join();
                    log("Сдался при выключении. Победитель: " + opponentName);
                    
                    // Не удаляем сразу, чтобы победитель увидел результат
                    // Удаление произойдет тайм-аутом или когда победитель выйдет
                    return;
                }
            }
            
            // Если мы хост - удаляем комнату всегда при выходе
            if (isHost) {
                firebaseClient.delete(path).join();
                log("Удалена комната при выключении: " + currentRoomId);
            }
        } catch (Exception e) {
            logError("Ошибка удаления комнаты при выключении", e);
        }
    }
    
    public void handleDisconnect() {
        if (currentRoom == null || currentRoomId == null) return;

        String myName = MinecraftClient.getInstance().getSession().getUsername();
        
        if (currentRoom != null && currentRoom.isPrivate() &&  
               (currentRoom.getStatus() == BattleRoom.RoomStatus.STARTED || 
                currentRoom.getStatus() == BattleRoom.RoomStatus.FROZEN)) {
                
                String opponentName = isHost ? currentRoom.getGuest() : currentRoom.getHost();
            
            if (opponentName != null) {
                log("Отключение от активной приватной битвы - Отправка сигнала о ДИСКОННЕКТЕ");
                
                CompletableFuture.runAsync(() -> {
                    try {
                        String path = "/rooms/" + currentRoomId;
                        
                        // Шаг 1: Записываем себя как выбывшего (eliminated)
                        Map<String, Long> elimUpdate = new HashMap<>();
                        elimUpdate.put(myName, System.currentTimeMillis());
                        firebaseClient.patch(path + "/ep", elimUpdate).join();
                        
                        // Шаг 2: Проверяем, остался ли один выживший (Последний герой)
                        JsonElement roomDataElement = firebaseClient.get(path).join();
                        if (roomDataElement != null && roomDataElement.isJsonObject()) {
                            JsonObject roomData = roomDataElement.getAsJsonObject();
                            BattleRoom room = gson.fromJson(roomData, BattleRoom.class);
                            List<String> allPlayers = room.getPlayers();
                            Map<String, Long> eliminatedMap = room.getEliminationMap();
                            
                            // Добавляем себя в локальную карту выбывших для расчета
                            eliminatedMap.put(myName, System.currentTimeMillis());
                            
                            int activeCount = (int) allPlayers.stream()
                                .filter(p -> !eliminatedMap.containsKey(p)).count();
                                
                            if (activeCount <= 1) {
                                // Все вышли или остался один игрок.
                                // В случае дисконнекта оппонентов (все гости вышли), объявляем НИЧЬЮ.
                                JsonObject drawUpdate = new JsonObject();
                                drawUpdate.addProperty("st", "DRAW");
                                drawUpdate.addProperty("dr", "disconnect");
                                firebaseClient.patch(path, drawUpdate).join();
                                log("Игроки отключились (Active=" + activeCount + "). Объявляем НИЧЬЮ.");
                            } else {
                                // Кто-то остался (>1) - ИГРА ПРОДОЛЖАЕТСЯ
                                log("Игрок отключился. Осталось активных: " + activeCount + ". Игра продолжается.");
                            }
                        } else {
                            // Fallback
                            JsonObject update = new JsonObject();
                            update.addProperty("st", "DRAW");
                            update.addProperty("dr", "disconnect");
                            firebaseClient.patch(path, update).join();
                        }
                        
                    } catch (Exception e) {
                        logError("Ошибка отправки сигнала о дисконнекте", e);
                    }
                });
                
                stopBattle();
                return;
            }
        }
        
        if (isHost) {
            deleteRoom();
        } else {
            log("Гость отключился - комната остаётся для Хоста");
        }
        
        stopBattle();
    }
    
    private boolean areAllPlayersLoaded(BattleRoom room) {
        if (room == null || room.getPlayers() == null || room.getPlayers().isEmpty()) return false;
        
        for (String player : room.getPlayers()) {
            if (!room.isPlayerLoaded(player)) {
                return false;
            }
        }
        return true;
    }
    
    private void processRoomStatusChange(BattleRoom.RoomStatus oldStatus, BattleRoom.RoomStatus newStatus, 
                                         BattleRoom updatedRoom, String roomId, String path) {
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        
        // Публичная комната - гость получает экран выбора
        if (!currentRoom.isPrivate() && !isHost && 
            newStatus == BattleRoom.RoomStatus.WAITING && 
            oldStatus != BattleRoom.RoomStatus.WAITING) {
            
            currentRoom = updatedRoom;
            MinecraftClient.getInstance().execute(() -> {
                MinecraftClient.getInstance().setScreen(new MatchReadyScreen(null, roomId));
            });
            return;
        }
        
        // Обработка готовности в лобби
        if (newStatus == BattleRoom.RoomStatus.WAITING) {
            handleLobbyReadiness(updatedRoom, playerName);
        }
        
        // Переход в FROZEN когда ВСЕ загружены
        boolean allLoadedNow = areAllPlayersLoaded(updatedRoom);
        boolean allLoadedBefore = areAllPlayersLoaded(currentRoom);
        
        if ((newStatus == BattleRoom.RoomStatus.LOADING || newStatus == BattleRoom.RoomStatus.WAITING) && 
            allLoadedNow && !allLoadedBefore) {
            
            transitionToFrozen(path, roomId);
        }
        
        // Логика подключения гостя (Shared World)
        if (updatedRoom.isSharedWorld() && !isHost && updatedRoom.getServerAddress() != null) {
            handleGuestConnection(updatedRoom);
        }

        // Создание мира (Separate Worlds)
        if (!updatedRoom.isSharedWorld() && 
            (newStatus == BattleRoom.RoomStatus.LOADING || 
             newStatus == BattleRoom.RoomStatus.FROZEN || 
             newStatus == BattleRoom.RoomStatus.STARTED) && 
            oldStatus == BattleRoom.RoomStatus.WAITING) {
            
            handleSeparateWorldCreation(updatedRoom);
            return;
        }
        
        // Обработка FROZEN статуса
        if (newStatus == BattleRoom.RoomStatus.FROZEN) {
            handleFrozenStatus(updatedRoom, playerName);
        }
        
        // Старт битвы
        if (newStatus == BattleRoom.RoomStatus.STARTED && oldStatus != BattleRoom.RoomStatus.STARTED) {
            handleBattleStart(updatedRoom);
        }
    }
    
    private void handleLobbyReadiness(BattleRoom updatedRoom, String playerName) {
        boolean isHostPlayer = currentRoom.isHost(playerName);
        
        // Fix: Correctly determine readiness of opponent
        // For host, opponent is guest. For guest, opponent is host.
        // Also check readyPlayers list for robustness
        
        boolean wasOpponentReady = isHostPlayer ? 
            (currentRoom != null && (currentRoom.isGuestReady() || areAnyGuestsReady(currentRoom))) : 
            (currentRoom != null && currentRoom.isHostReady());
            
        boolean isOpponentReady = isHostPlayer ? 
            (updatedRoom.isGuestReady() || areAnyGuestsReady(updatedRoom)) : 
            updatedRoom.isHostReady();
        
        if (!wasOpponentReady && isOpponentReady) {
            log("✓ Противник готов в лобби: " + (isHostPlayer ? "гость" : "хост"));
            // Force refresh room to ensure UI updates
            currentRoom = updatedRoom;
        }
        
        // Also check if I became ready (remote confirmation)
        boolean wasIReady = isHostPlayer ? 
            (currentRoom != null && currentRoom.isHostReady()) : 
            (currentRoom != null && currentRoom.isGuestReady());
            
        boolean amIReady = isHostPlayer ? updatedRoom.isHostReady() : updatedRoom.isGuestReady();
        
        if (!wasIReady && amIReady) {
            log("✓ Моя готовность подтверждена сервером");
            currentRoom = updatedRoom;
        }
        
        // Universal check: if everyone is ready
        if (areAllPlayersReady(updatedRoom) && !loadingTriggered) {
            log("✓ Все игроки готовы в лобби - запуск LOADING");
            MinecraftClient.getInstance().execute(this::setStatusLoading);
        }
    }
    
    private boolean areAnyGuestsReady(BattleRoom room) {
        if (room.getGuests() != null) {
            for (BattleRoom.GuestData data : room.getGuests().values()) {
                if (data.ready) return true;
            }
        }
        // Fallback to readyPlayers list
        if (room.getReadyPlayers() != null) {
            for (String p : room.getReadyPlayers()) {
                if (!room.isHost(p)) return true;
            }
        }
        return false;
    }
    
    private void transitionToFrozen(String path, String roomId) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject statusUpdate = new JsonObject();
                statusUpdate.addProperty("st", "FROZEN");
                statusUpdate.addProperty("hr", false);
                
                firebaseClient.patch(path, statusUpdate).join();
                log("✓ Оба игрока загружены - статус установлен в FROZEN (флаг готовности хоста сброшен)");
                
                if (currentRoom != null) {
                    currentRoom.setHostReady(false);
                    currentRoom.setStatus(BattleRoom.RoomStatus.FROZEN);
                }
            } catch (Exception e) {
                logError("Ошибка установки FROZEN статуса", e);
            }
        });
    }
    
    private void handleGuestConnection(BattleRoom updatedRoom) {
        String serverAddr = updatedRoom.getServerAddress();
        boolean isValidAddress = serverAddr.contains(".e4mc.link") || 
                                serverAddr.matches(".*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*");
        
        if (isValidAddress) {
            boolean isNewAddress = lastConnectionAddress == null || !lastConnectionAddress.equals(serverAddr);
            
            if (!connectionStarted || isNewAddress) {
                if (isNewAddress && connectionStarted) {
                    log("Найден новый адрес, переключение подключения...");
                }
                
                // Check disconnect status
                if (updatedRoom.isPlayerDisconnected(MinecraftClient.getInstance().getSession().getUsername())) {
                     log("⚠ Мы помечены как отключенные. Вход в режиме наблюдателя.");
                }
                
                connectionStarted = true;
                lastConnectionAddress = serverAddr;
                
                log("Адрес сервера готов: " + serverAddr + ", подключение через 5 секунд...");
                scheduler.schedule(() -> connectToHost(serverAddr, true), 5, TimeUnit.SECONDS);
            } else {
                updateSharedWorldItem(updatedRoom);
            }
        }
    }
    
    private void updateSharedWorldItem(BattleRoom updatedRoom) {
        MinecraftClient.getInstance().execute(() -> {
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            String roomItemId = IdCompressor.decompress(updatedRoom.getTargetItem());
            
            if (roomItemId != null) {
                Item roomItem = Registries.ITEM.get(Identifier.of(roomItemId));
                Item currentRunItem = runManager.getTargetItem();
                
                if (roomItem != null && (currentRunItem != roomItem || 
                    runManager.getStatus() == RunDataManager.RunStatus.INACTIVE)) {
                    
                    log("🔄 Shared World: Обновление целевого предмета: " + roomItemId);
                    runManager.cancelRun();
                    runManager.startNewRun(roomItem, 0, updatedRoom.getServerAddress());
                    
                    if (updatedRoom.getStatus() == BattleRoom.RoomStatus.LOADING || 
                        updatedRoom.getStatus() == BattleRoom.RoomStatus.FROZEN) {
                        freezePlayer();
                    }
                }
            }
        });
    }
    
    private void handleSeparateWorldCreation(BattleRoom updatedRoom) {
        currentRoom = updatedRoom;
        
        MinecraftClient.getInstance().execute(() -> {
            MinecraftClient.getInstance().setScreen(null);
            
            Item targetItem = Registries.ITEM.get(Identifier.of(IdCompressor.decompress(updatedRoom.getTargetItem())));
            String seed = updatedRoom.getSeed();
            
            if (seed == null || seed.isEmpty() || seed.equals("null")) {
                logError("Получен неверный сид! Невозможно создать мир.");
                sendPlayerMessage("§cОшибка: неверный сид!");
                return;
            }
            
            logBox("🌍 ЗАГРУЗКА МИРА\n  - Сид: " + seed + "\n  - Предмет: " + updatedRoom.getTargetItem());
            
            RandomRunMod.getInstance().getRunDataManager().setTargetItem(targetItem);
            WorldCreator.createSpeedrunWorld(targetItem, seed);
        });
    }
    
    private void handleFrozenStatus(BattleRoom updatedRoom, String playerName) {
        List<String> oldReady = currentRoom != null ? currentRoom.getReadyPlayers() : new ArrayList<>();
        List<String> newReady = updatedRoom.getReadyPlayers();
        
        if (newReady != null) {
            for (String readyPlayer : newReady) {
                if (!oldReady.contains(readyPlayer) && !readyPlayer.equals(playerName)) {
                    sendPlayerMessage("§a✓ Игрок " + readyPlayer + " готов!");
                }
            }
        }
        
        boolean amIReady = updatedRoom.isPlayerReady(playerName);
        int totalPlayers = updatedRoom.getPlayers().size();
        int readyCount = newReady != null ? newReady.size() : 0;
        
        if (!amIReady && readyCount == totalPlayers - 1) {
            sendPlayerMessage("§eВсе готовы! Напишите /go для старта", true);
        }
    }
    
    private void handleBattleStart(BattleRoom room) {
        if (battleEndHandled) return;
        
        long serverStartTime = room.getServerStartTime();
        long now = System.currentTimeMillis();
        long delayToStart = 3000; // Default safe delay
        boolean usedSharedTime = false;
        
        // 1. Пытаемся использовать игровое время (Shared World) - Самый точный метод для LAN/VPN
        if (room.isSharedWorld() && room.getSharedWorldStartTime() > 0) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                long diffTicks = room.getSharedWorldStartTime() - client.world.getTime();
                // Валидация: если разница слишком огромная (более 30 сек), что-то не так с миром
                if (Math.abs(diffTicks) < 600) { 
                    delayToStart = diffTicks * 50; // ticks -> ms
                    usedSharedTime = true;
                    log("Shared World Sync: DiffTicks=" + diffTicks + ", Delay=" + delayToStart + "ms");
                } else {
                    logWarn("Shared World Sync: Слишком большая разница тиков (" + diffTicks + "). Игнорируем.");
                }
            }
        } 
        
        // 2. Если не вышло с Shared World, используем эвристику для Separate Worlds
        if (!usedSharedTime) {
            long calculatedDelay = serverStartTime - now;
            
            // Анализ рассинхрона часов (Clock Skew)
            // Нормальная задержка должна быть около 3000мс (плюс-минус пинг/поллинг, скажем 200-5000мс)
            // Если задержка > 5000 (значит часы отправителя спешат) или < -2000 (часы отправителя отстают)
            // То мы игнорируем серверное время и стартуем через 3 секунды от момента получения сигнала.
            
            if (calculatedDelay > 6000 || calculatedDelay < -2000) {
                logWarn("Обнаружен сильный рассинхрон часов! CalcDelay=" + calculatedDelay + "ms. Используем локальный таймер 3000ms.");
                delayToStart = 3000;
            } else {
                // Если в пределах разумного - используем рассчитанную, чтобы быть синхроннее
                // Но если calculatedDelay < 0 (мы уже опоздали), ставим 0
                delayToStart = calculatedDelay;
                log("Server Time Sync: Delay=" + delayToStart + "ms");
            }
        }
        
        // 3. Запуск визуального отсчета
        // Передаем calculatedDelay или 3000, чтобы отсчет соответствовал реальному старту
        long finalCountdownTime = delayToStart;
        MinecraftClient.getInstance().execute(() -> startCountdown(finalCountdownTime));
        
        // 4. Планирование реального старта
        if (delayToStart < 0) delayToStart = 0;
        log("Планирование старта через " + delayToStart + "мс");

        scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(() -> {
                if (!isInBattle || currentRoom == null) return;
                
                unfreezePlayer();
                
                sendPlayerMessage("§a§lGO!", true);
                playSound(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                
                RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
                if (runManager != null) {
                    runManager.unfreezeRun();
                }
                
                log("Битва началась!");
            });
        }, delayToStart, TimeUnit.MILLISECONDS);
    }
    
    private void checkSharedWorldVictory(BattleRoom updatedRoom) {
        if (!updatedRoom.isSharedWorld() || updatedRoom.getWinner() != null) return;
        
        List<String> allPlayers = updatedRoom.getPlayers();
        List<String> eliminated = updatedRoom.getEliminatedPlayers();
        Map<String, Long> playerTimes = updatedRoom.getPlayerTimes();
        
        String myName = MinecraftClient.getInstance().getSession().getUsername();
        
        List<String> stillPlaying = new ArrayList<>();
        for (String p : allPlayers) {
            if (!eliminated.contains(p) && !playerTimes.containsKey(p)) {
                stillPlaying.add(p);
            }
        }
        
        // Логика ничьей при "Last Man Standing" (если пользователь запросил)
        // Если остался 1 игрок, остальные выбыли, и НИКТО не нашел предмет -> НИЧЬЯ (вместо победы выживанием)
        if (stillPlaying.size() == 1 && stillPlaying.get(0).equals(myName) && 
            eliminated.size() > 0 && allPlayers.size() > 1) {
            
            RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
            long time = runManager.getCurrentTime();
            if (time <= 0) time = System.currentTimeMillis() - updatedRoom.getStartTime();
            
            // Если игрок выжил, но предмета нет -> Ничья
            // (Ранее это считалось победой, но пользователь попросил "экран ничьи")
            reportSharedWorldDraw(time);
        }
    }
    
    private boolean areAllPlayersReady(BattleRoom room) {
        List<String> players = room.getPlayers();
        if (players == null || players.isEmpty()) {
            return room.isHostReady() && room.isGuestReady();
        }
        
        for (String player : players) {
            if (!room.isPlayerReady(player)) return false;
        }
        return true;
    }
    
    private String determineWinner(List<String> allPlayers, Map<String, Long> eliminatedMap, int activeCount) {
        if (activeCount == 1) {
            for (String p : allPlayers) {
                if (!eliminatedMap.containsKey(p)) return p;
            }
        } else if (activeCount == 0) {
            long maxTime = -1;
            String winner = null;
            for (Map.Entry<String, Long> entry : eliminatedMap.entrySet()) {
                if (allPlayers.contains(entry.getKey()) && entry.getValue() > maxTime) {
                    maxTime = entry.getValue();
                    winner = entry.getKey();
                }
            }
            if (winner != null) log("Все выбыли. Последний выживший (победитель): " + winner);
            return winner;
        }
        return null;
    }
    
    private void cleanupRoomCategory(String path, String playerName, long now, long maxAge) {
        try {
            JsonElement roomsElement = firebaseClient.get(path).join();
            if (roomsElement == null || !roomsElement.isJsonObject()) return;
            JsonObject rooms = roomsElement.getAsJsonObject();
            
            for (String key : rooms.keySet()) {
                if (key.length() > 6) {
                    firebaseClient.delete(path + "/" + key).join();
                    log("Очищен неправильный ключ комнаты: " + key);
                    continue;
                }

                JsonObject room = rooms.getAsJsonObject(key);
                
                String host = room.has("h") ? room.get("h").getAsString() : (room.has("host") ? room.get("host").getAsString() : "");
                
                if (host.isEmpty()) {
                    firebaseClient.delete(path + "/" + key).join();
                    log("Очищена повреждённая комната (нет хоста): " + key);
                    continue;
                }

                boolean isMyRoom = host.equals(playerName);
                long createdAt = room.has("ca") ? room.get("ca").getAsLong() : (room.has("createdAt") ? room.get("createdAt").getAsLong() : 0);
                boolean isPrivate = room.has("pr") ? room.get("pr").getAsBoolean() : (room.has("isPrivate") && room.get("isPrivate").getAsBoolean());
                long dynamicMaxAge = isPrivate ? 86400000 : 7200000;
                boolean isDynamicOld = (now - createdAt) > dynamicMaxAge;

                if (isMyRoom || isDynamicOld) {
                    firebaseClient.delete(path + "/" + key).join();
                    log("Очищена устаревшая комната: " + key);
                }
            }
        } catch (Exception e) {
            logError("Ошибка очистки категории комнат: " + path, e);
        }
    }
    
    private void updateGuestMapTime(String roomPath, String guestName, long time, String formatted) {
        Map<String, Object> update = new HashMap<>();
        update.put("t", time);
        
        firebaseClient.patch(roomPath + "/g/" + guestName, update).join();
    }
    
    private void handleBattleEnd(BattleRoom room) {
        String playerName = MinecraftClient.getInstance().getSession().getUsername();
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();

        // Fix: If we already won locally (COMPLETED), ignore conflicting statuses (like DRAW from race condition).
        // BUT if the server confirms our VICTORY, we MUST proceed to show the Victory Screen!
        boolean serverSaysIWon = room.getWinner() != null && room.getWinner().equals(playerName);
        
        if (runManager != null && runManager.getStatus() == RunDataManager.RunStatus.COMPLETED) {
            if (serverSaysIWon) {
                 log("Локальная победа подтверждена сервером. Продолжаем обработку для показа экрана.");
                 // Не прерываем выполнение, идем к handleVictory
            } else {
                log("Победа уже зафиксирована локально (RunStatus.COMPLETED). Игнорирование конфликтующего статуса сервера: " + room.getStatus());
                
                // Ensure we clean up even if we return early
                scheduler.schedule(() -> {
                    if (isHost) {
                        com.randomrun.main.data.GlobalStatsManager.incrementRun();
                        deleteRoom();
                    }
                    stopBattle();
                }, 5, TimeUnit.SECONDS);
                return;
            }
        }
        
        if (room.getStatus() == BattleRoom.RoomStatus.DRAW) {
            String reason = room.getDefeatReason();
            if (reason == null) reason = "Ничья (Игрок отключился)";
            if ("disconnect".equals(reason)) reason = "Ничья! Игрок отключился.";
            
            final String finalReason = reason;
            MinecraftClient.getInstance().execute(() -> {
                Item targetItem = Registries.ITEM.get(Identifier.of(room.getTargetItem()));
                MinecraftClient.getInstance().setScreen(new com.randomrun.ui.screen.endgame.DrawScreen(targetItem, 0, finalReason));
            });
            return;
        }

        boolean won = room.getWinner() != null && room.getWinner().equals(playerName);
        
        log("Обработка окончания битвы. Победитель: " + room.getWinner() + ", Я: " + playerName + ", Победил: " + won);

        if (won) {
            handleVictory(room, playerName);
        } else {
            handleDefeat(room, playerName);
        }
        
        final BattleRoom finalRoom = room;
        scheduler.schedule(() -> {
            if (isHost) {
                com.randomrun.main.data.GlobalStatsManager.incrementRun();
                deleteRoom();
            }
            stopBattle();
        }, 5, TimeUnit.SECONDS);
    }
    
    private void handleVictory(BattleRoom room, String playerName) {
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        long duration = runManager.getCurrentTime();
        if (duration <= 0 && room.getPlayerTime(playerName) > 0) {
             duration = room.getPlayerTime(playerName);
        }
        String itemId = IdCompressor.decompress(room.getTargetItem());
        com.randomrun.main.data.PlayerProfile.get().addRun(duration, true, itemId, duration, com.randomrun.challenges.classic.world.WorldCreator.getLastCreatedSeed(), true, RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled());
        
        boolean isSurvivalWin = false;
        
        // Survival Win if I won but have no time (meaning I didn't find the item, but opponent died)
        // Use local duration which incorporates both local and remote time knowledge
        if (duration <= 0) {
            isSurvivalWin = true;
        }
        
        if (isSurvivalWin) {
            showDrawScreen("randomrun.draw.description", 0);
        } else {
            showNormalVictory();
        }
    }
    
    private void showDrawScreen(String reasonKey, long time) {
        MinecraftClient.getInstance().execute(() -> {
            Item targetItem = RandomRunMod.getInstance().getRunDataManager().getTargetItem();
            if (targetItem == null && currentRoom != null) {
                try {
                    targetItem = Registries.ITEM.get(Identifier.of(IdCompressor.decompress(currentRoom.getTargetItem())));
                } catch(Exception e) {}
            }
            
            final Item finalItem = targetItem;
            MinecraftClient.getInstance().setScreen(new com.randomrun.ui.screen.endgame.DrawScreen(
                finalItem,
                time,
                Text.translatable(reasonKey).getString()
            ));
        });
    }
    
    private void showNormalVictory() {
        playSound(net.minecraft.sound.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING) {
            runManager.completeRun();
        }
    }
    
    private void handleDefeat(BattleRoom room, String playerName) {
        log("handleDefeat вызван для игрока: " + playerName);
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        long duration = runManager.getCurrentTime();
        if (duration <= 0 && room.getPlayerTime(playerName) > 0) {
             duration = room.getPlayerTime(playerName);
        }
        
        String itemId = IdCompressor.decompress(room.getTargetItem());
        com.randomrun.main.data.PlayerProfile.get().addRun(duration, false, itemId, 0, com.randomrun.challenges.classic.world.WorldCreator.getLastCreatedSeed(), true, RandomRunMod.getInstance().getConfig().isHardcoreModeEnabled());
        
        if (runManager.getStatus() == RunDataManager.RunStatus.RUNNING) {
            runManager.failRun();
        }
        
        if (MinecraftClient.getInstance().currentScreen instanceof com.randomrun.ui.screen.endgame.VictoryScreen) {
            MinecraftClient.getInstance().setScreen(null);
        }
        
        String winnerName = room.getWinner();
        
        playSound(net.minecraft.sound.SoundEvents.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
        
        long opponentTime = room.getPlayerTime(winnerName);
        if (opponentTime <= 0) {
            if (winnerName.equals(room.getHost())) opponentTime = room.getHostTime();
            else if (winnerName.equals(room.getGuest())) opponentTime = room.getGuestTime();
        }
        log("Время победителя (" + winnerName + "): " + opponentTime);

        String reason = Text.translatable("randomrun.defeat.reason.opponent_won", winnerName).getString();
        if (opponentTime > 0) {
            reason += " (Время: " + formatDuration(opponentTime) + ")";
        }
        
        Item targetItem = runManager.getTargetItem();
        if (targetItem == null && room.getTargetItem() != null) {
            try {
                targetItem = Registries.ITEM.get(Identifier.of(IdCompressor.decompress(room.getTargetItem())));
            } catch (Exception e) {
                logError("Ошибка получения предмета комнаты: " + room.getTargetItem(), e);
            }
        }
        
        long elapsedTime = runManager.getCurrentTime();
        
        final Item finalTargetItem = targetItem;
        final String finalReason = reason;
        
        log("Показ DefeatScreen. Победитель: " + winnerName + ", Причина: " + finalReason);
        MinecraftClient.getInstance().setScreen(new DefeatScreen(finalTargetItem, elapsedTime, finalReason));
    }
    
    private void scheduleStart(long delayMs) {
        log("Планирование старта через " + delayMs + "мс");
        
        // Запуск визуального отсчета
        startCountdown(delayMs);
        
        // Запуск реальной разморозки и начала игры
        scheduler.schedule(() -> {
            MinecraftClient.getInstance().execute(() -> {
                if (!isInBattle || currentRoom == null) return; // Check valid state

                unfreezePlayer();
                
                // FIX: Heal ALL players on start (HOST only)
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.getServer() != null) {
                    try {
                        var playerManager = client.getServer().getPlayerManager();
                        for (var serverPlayer : playerManager.getPlayerList()) {
                            serverPlayer.setHealth(serverPlayer.getMaxHealth());
                            serverPlayer.getHungerManager().setFoodLevel(20);
                            serverPlayer.getHungerManager().setSaturationLevel(5.0f);
                            serverPlayer.clearStatusEffects();
                        }
                        log("Все игроки исцелены перед стартом");
                    } catch (Exception e) {
                        logError("Ошибка исцеления игроков", e);
                    }
                }
                
                sendPlayerMessage("§a§lGO!", true);
                playSound(net.minecraft.sound.SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                
                RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
                if (runManager != null) {
                    runManager.unfreezeRun();
                }
                
                log("Битва началась!");
            });
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    private void startCountdown(long remaining) {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(4);
        }
        
        // Schedule ticks relative to remaining time
        // 3... (at remaining - 3000, if > 0)
        // 2... (at remaining - 2000)
        // 1... (at remaining - 1000)
        // We just fire them sequentially with 1s gap, but if remaining is short, we skip
        
        // Simplified: Just run 3-2-1 if we have time, otherwise skip
        // Ideally we should sync visuals to serverStartTime
        
        // 3...
        if (remaining > 2000) {
            scheduler.schedule(() -> showCountdown("§e§l3...", 0.8f), remaining - 3000, TimeUnit.MILLISECONDS);
        }
        // 2...
        if (remaining > 1000) {
            scheduler.schedule(() -> showCountdown("§e§l2...", 1.0f), remaining - 2000, TimeUnit.MILLISECONDS);
        }
        // 1...
        if (remaining > 0) {
            scheduler.schedule(() -> showCountdown("§e§l1...", 1.2f), remaining - 1000, TimeUnit.MILLISECONDS);
        } else {
             // Instant start
             showCountdown("§e§l1...", 1.2f);
        }
        
        // Note: The actual "GO" is handled by scheduleStart(delay)
    }
    
    private void showCountdown(String text, float pitch) {
        MinecraftClient.getInstance().execute(() -> {
            sendPlayerMessage(text, true);
            playSound(net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, pitch);
        });
    }
    
    private String generateRoomCode() {
        return String.format("%05d", new Random().nextInt(100000));
    }
    
    private String formatDuration(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        long ms = millis % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
    
    private String getCurrentRoomCode() {
        if (this.currentRoom != null) return this.currentRoom.getRoomCode();
        return this.currentRoomId;
    }
    
    private void showRoomCode() {
        MinecraftClient.getInstance().execute(() -> {
            sendPlayerMessage("§a[RandomRun] §fКомната создана! Код: §e§l" + currentRoomId);
        });
    }
    
    private void sendPlayerMessage(String message) {
        sendPlayerMessage(message, false);
    }
    
    private void sendPlayerMessage(String message, boolean actionBar) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(Text.literal(message), actionBar);
            }
        });
    }
    
    private void sendPlayerMessage(Text message) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(message, false);
            }
        });
    }
    
    private void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        MinecraftClient.getInstance().execute(() -> {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.playSound(sound, volume, pitch);
            }
        });
    }
    
    // ============= ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ =============

    private void registerPlayer(String playerName) {
        com.randomrun.main.data.PlayerProfile.load(playerName).thenRun(() -> {
             com.randomrun.main.data.PlayerProfile.save();
        });
    }
    
    private void setupRoom(BattleRoom room, String roomCode, boolean asHost) {
        this.currentRoom = room;
        this.currentRoomId = roomCode;
        this.isHost = asHost;
        this.isInBattle = true;
        ensureScheduler();
    }
    
    private void ensureScheduler() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newScheduledThreadPool(4);
        }
    }
    
    private void cancelAllTasks() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(true);
            pollingTask = null;
        }
    }
    
    private void resetAllFlags() {
        this.currentRoom = null;
        this.currentRoomId = null;
        this.isHost = false;
        this.isFrozen = false;
        this.isInBattle = false;
        this.battleEndHandled = false;
        this.loadingTriggered = false;
        this.connectionStarted = false;
        this.lastConnectionAddress = null;
        this.isCreatingRoom = false; // Reset lock on reset
    }
    
    private JsonObject getRoomData(String roomCode) {
        try {
            JsonElement element = firebaseClient.get("/rooms/" + roomCode, false).join();
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (Exception e) {
            logError("Error getting room data", e);
        }
        return null;
    }
    
    private boolean validatePassword(String roomPassword, String inputPassword) {
        return roomPassword == null || roomPassword.isEmpty() || roomPassword.equals(inputPassword);
    }
    
    private void addGuestToMap(String path, String playerName) {
        try {
            String guestsPath = path + "/g/" + playerName;
            BattleRoom.GuestData guestData = new BattleRoom.GuestData(false, false);
            firebaseClient.put(guestsPath, guestData).join();
        } catch (Exception e) {
            logError("Ошибка добавления в карту гостей", e);
        }
    }
    
    private void updateGuestMap(String path, String playerName, String field, boolean value) {
        try {
            String guestsPath = path + "/g/" + playerName;
            JsonObject guestUpdate = new JsonObject();
            guestUpdate.addProperty(field, value);
            firebaseClient.patch(guestsPath, guestUpdate).join();
            
            if (currentRoom.getGuests().containsKey(playerName)) {
                if ("l".equals(field)) currentRoom.getGuests().get(playerName).loaded = value;
                if ("r".equals(field)) currentRoom.getGuests().get(playerName).ready = value;
            }
        } catch (Exception e) {
            logError("Ошибка обновления карты гостей (" + field + ")", e);
        }
    }

    private void updateGuestMap(String path, String playerName, Map<String, Object> updates) {
        try {
            String guestsPath = path + "/g/" + playerName;
            firebaseClient.patch(guestsPath, updates).join();
        } catch (Exception e) {
            logError("Ошибка массового обновления карты гостей", e);
        }
    }
    
    private boolean isRoomSuitable(BattleRoom room, boolean isSharedWorld) {
        return room.getStatus() == BattleRoom.RoomStatus.WAITING && 
               room.getPlayers().size() < room.getMaxPlayers() &&
               room.isSharedWorld() == isSharedWorld;
    }
    
    private boolean joinExistingRoom(String roomId, BattleRoom room, String playerName) {
        log("Найдена публичная комната: " + roomId + ". Присоединение...");
        
        room.addPlayer(playerName);
        
        if (firebaseClient.put("/rooms/" + roomId, room).join()) {
            setupRoom(room, roomId, false);
            addGuestToMap("/rooms/" + roomId, playerName);
            startRoomListener(roomId);
            return true;
        } else {
            logWarn("Ошибка присоединения (гонка условий?), повтор создания...");
            return false;
        }
    }
    
    private boolean createNewPublicRoom(String playerName, Item targetItem, boolean isSharedWorld) {
        log("Подходящая комната не найдена. Создание новой...");
        
        String roomCode = generateRoomCode();
        // Collision check
        for (int i = 0; i < 3; i++) {
            if (getRoomData(roomCode) != null) {
                roomCode = generateRoomCode();
            } else {
                break;
            }
        }
        
        String seed = String.valueOf(new Random().nextLong());
        String itemId = targetItem != null ? Registries.ITEM.getId(targetItem).toString() : "minecraft:dirt";
        
        BattleRoom newRoom = new BattleRoom(playerName, seed, itemId, roomCode, false, "", isSharedWorld, 2);
        
        if (firebaseClient.put("/rooms/" + roomCode, newRoom).join()) {
            setupRoom(newRoom, roomCode, true);
            startRoomListener(roomCode);
            return true;
        }
        
        return false;
    }

    // ============= ЛОГИРОВАНИЕ =============
    
    private void log(String message) {
        RandomRunMod.LOGGER.info("[BattleManager] " + message);
    }
    
    private void logWarn(String message) {
        RandomRunMod.LOGGER.warn("[BattleManager] " + message);
    }
    
    private void logError(String message) {
        RandomRunMod.LOGGER.error("[BattleManager] " + message);
    }
    
    private void logError(String message, Exception e) {
        RandomRunMod.LOGGER.error("[BattleManager] " + message, e);
    }
    
    private void logBox(String message) {
        RandomRunMod.LOGGER.info("════════════════════════════════════════");
        for (String line : message.split("\n")) {
            RandomRunMod.LOGGER.info("  " + line);
        }
        RandomRunMod.LOGGER.info("════════════════════════════════════════");
    }
}
