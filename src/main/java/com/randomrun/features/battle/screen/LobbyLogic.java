package com.randomrun.features.battle.screen;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.BattleRoomSerializer;
import com.randomrun.features.battle.screen.LobbyRender.UIColors;
import com.randomrun.network.HttpClient;
import com.randomrun.network.RealtimeClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;

public final class LobbyLogic {
    private LobbyLogic() {
    }

    public static class LobbyPollingManager {
        private static final String LOG_TAG = "[LobbyPoller]";
        private static final long MIN_POLL_INTERVAL_MS = 2000;
        private static final long MAX_POLL_INTERVAL_MS = 10000;
        private static final int UNCHANGED_THRESHOLD = 5;
        private static final long INTERVAL_INCREMENT_MS = 1000;

        private final ScheduledExecutorService scheduler;
        private ScheduledFuture<?> pollingTask;
        private int lastDataHash = 0;
        private long currentInterval = MIN_POLL_INTERVAL_MS;
        private int unchangedCount = 0;
        private boolean firstSuccessfulPoll = false;
        private volatile boolean isPaused = false;
        private boolean active;
        private long generation;

        private final Consumer<List<BattleRoom>> onRoomsUpdate;
        private final Consumer<ConnectionStatus> onStatusChange;
        private volatile ConnectionStatus status = ConnectionStatus.DISCONNECTED;

        public enum ConnectionStatus {
            CONNECTED,
            CONNECTING,
            DISCONNECTED,
            ERROR
        }

        public LobbyPollingManager(Consumer<List<BattleRoom>> onRoomsUpdate,
                                   Consumer<ConnectionStatus> onStatusChange) {
            this.onRoomsUpdate = onRoomsUpdate;
            this.onStatusChange = onStatusChange;
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LobbyPoller");
                t.setDaemon(true);
                return t;
            });
        }

        public synchronized void connect() {
            if (active || scheduler.isShutdown()) return;

            if (!com.randomrun.network.BackendSession.isAuthenticated()) {
                com.randomrun.infrastructure.RandomRunMod.LOGGER.error("{} AuthManager не инициализирован!", LOG_TAG);
                updateStatus(ConnectionStatus.ERROR);
                return;
            }

            updateStatus(ConnectionStatus.CONNECTING);
            active = true;
            generation++;
            lastDataHash = 0;
            firstSuccessfulPoll = false;
            currentInterval = MIN_POLL_INTERVAL_MS;
            unchangedCount = 0;

            scheduleNextPoll(0, generation);
        }

        private synchronized void scheduleNextPoll(long delay, long expectedGeneration) {
            if (!active || generation != expectedGeneration || scheduler.isShutdown()) return;
            pollingTask = scheduler.schedule(() -> {
                synchronized (this) {
                    if (!active || generation != expectedGeneration) return;
                }
                if (isPaused && firstSuccessfulPoll) {
                    scheduleNextPoll(currentInterval, expectedGeneration);
                    return;
                }

                HttpClient.getInstance().getNoCache("/rooms")
                    .thenAccept(data -> {
                        synchronized (this) {
                        if (!active || generation != expectedGeneration) return;
                        if (data != null) {
                            int currentHash = data.toString().hashCode();

                            if (currentHash != lastDataHash) {
                                lastDataHash = currentHash;
                                unchangedCount = 0;
                                currentInterval = MIN_POLL_INTERVAL_MS;
                                handleData(data);
                            } else {
                                unchangedCount++;
                                if (unchangedCount >= UNCHANGED_THRESHOLD) {
                                    currentInterval = Math.min(MAX_POLL_INTERVAL_MS,
                                        currentInterval + INTERVAL_INCREMENT_MS);
                                }
                            }

                            if (!firstSuccessfulPoll) {
                                firstSuccessfulPoll = true;
                                updateStatus(ConnectionStatus.CONNECTED);
                            }
                        } else {
                            onRoomsUpdate.accept(new ArrayList<>());
                        }
                        updateStatus(ConnectionStatus.CONNECTED);

                        scheduleNextPoll(currentInterval, expectedGeneration);
                        }
                    })
                    .exceptionally(e -> {
                        synchronized (this) {
                        if (!active || generation != expectedGeneration) return null;
                        if (status != ConnectionStatus.ERROR) com.randomrun.infrastructure.RandomRunMod.LOGGER.warn("{} Ошибка запроса: {}", LOG_TAG, e.getMessage());
                        updateStatus(ConnectionStatus.ERROR);

                        scheduleNextPoll(currentInterval, expectedGeneration);
                        }
                        return null;
                    });
            }, delay, TimeUnit.MILLISECONDS);
        }

        private void handleData(JsonElement element) {
            try {
                List<BattleRoom> rooms = new ArrayList<>();
                Gson gson = new GsonBuilder()
                    .registerTypeAdapter(BattleRoom.class, new BattleRoomSerializer())
                    .create();

                if (element.isJsonObject()) {
                    JsonObject obj = element.getAsJsonObject();

                    for (var entry : obj.entrySet()) {
                        JsonElement value = entry.getValue();
                        if (value != null && !value.isJsonNull() && value.isJsonObject()) {
                            BattleRoom room = gson.fromJson(value, BattleRoom.class);
                            room.setRoomCode(entry.getKey());
                            rooms.add(room);
                        }
                    }
                } else if (element.isJsonArray()) {
                    for (JsonElement el : element.getAsJsonArray()) {
                        if (el != null && !el.isJsonNull() && el.isJsonObject()) {
                            rooms.add(gson.fromJson(el, BattleRoom.class));
                        }
                    }
                }

                onRoomsUpdate.accept(rooms);
            } catch (Exception e) {
                com.randomrun.infrastructure.RandomRunMod.LOGGER.error("{} Ошибка парсинга: {}",
                    LOG_TAG, e.getMessage());
                onRoomsUpdate.accept(new ArrayList<>());
            }
        }

        private void updateStatus(ConnectionStatus newStatus) {
            if (this.status != newStatus) {
                this.status = newStatus;
                onStatusChange.accept(newStatus);
            }
        }

        public synchronized void disconnect() {
            active = false;
            generation++;
            if (pollingTask != null && !pollingTask.isCancelled()) {
                pollingTask.cancel(false);
                pollingTask = null;
            }
            lastDataHash = 0;
            unchangedCount = 0;
            currentInterval = MIN_POLL_INTERVAL_MS;
            firstSuccessfulPoll = false;
            updateStatus(ConnectionStatus.DISCONNECTED);
        }

        public ConnectionStatus getStatus() {
            return status;
        }

        public synchronized boolean isConnected() {
            return active;
        }

        public void pause() {
            if (!isPaused) {
                isPaused = true;
            }
        }

        public void resume() {
            if (isPaused) {
                isPaused = false;
            }
        }

        public void shutdown() {
            disconnect();
            scheduler.shutdownNow();
        }
    }

    public final static class LobbyUpdates {
        public enum ConnectionStatus { CONNECTED, CONNECTING, DISCONNECTED, ERROR }
        private final Consumer<List<BattleRoom>> listener;
        private final Consumer<ConnectionStatus> status;
        private RealtimeClient.Subscription subscription;
        private volatile long generation;
        public LobbyUpdates(Consumer<List<BattleRoom>> listener, Consumer<ConnectionStatus> status, Runnable failed, ScheduledExecutorService scheduler) { this.listener = listener; this.status = status; }
        public synchronized void start() {
            if (subscription != null) return;
            long expected = generation;
            status.accept(ConnectionStatus.CONNECTING);
            subscription = RealtimeClient.getInstance().subscribeRooms(snapshot -> {
                if (generation != expected) return;
                List<BattleRoom> rooms = new ArrayList<>();
                for (var entry : snapshot.entrySet()) {
                    BattleRoom room = new Gson().fromJson(entry.getValue(), BattleRoom.class);
                    room.setRoomCode(entry.getKey()); rooms.add(room);
                }
                listener.accept(rooms); status.accept(ConnectionStatus.CONNECTED);
            });
        }
        public synchronized void stop() { generation++; if (subscription != null) subscription.close(); subscription = null; status.accept(ConnectionStatus.DISCONNECTED); }
        public boolean isConnected() { return subscription != null && RealtimeClient.getInstance().isConnected(); }
        public boolean isHealthy() { return isConnected(); }
        public ConnectionStatus getStatus() { return subscription == null ? ConnectionStatus.DISCONNECTED : isConnected() ? ConnectionStatus.CONNECTED : ConnectionStatus.CONNECTING; }
    }

    public static class PingMonitor {
        private static final long PING_INTERVAL = 30000;

        private volatile int currentPing = 0;
        private long lastPingTime = 0;
        private volatile boolean isUpdating = false;

        public void update() {
            if (System.currentTimeMillis() - lastPingTime > PING_INTERVAL) {
                measurePing();
                lastPingTime = System.currentTimeMillis();
            }
        }

        public void forceUpdate() {
            if (!isUpdating) {
                measurePing();
                lastPingTime = System.currentTimeMillis();
            }
        }

        private synchronized void measurePing() {
            if (isUpdating) return;
            isUpdating = true;
            long start = System.nanoTime();
            com.randomrun.network.HttpClient.getInstance().request("GET", "/health", null, false)
                .whenComplete((response, error) -> {
                    currentPing = error == null ? (int) ((System.nanoTime() - start) / 1_000_000) : -1;
                    isUpdating = false;
                });
        }

        public int getCurrentPing() {
            return currentPing;
        }

        public int getPingColor() {
            if (currentPing == -1) {
                return UIColors.PING_ERROR;
            } else if (currentPing == 0) {
                return UIColors.PING_LOADING;
            } else if (currentPing <= 300) {
                return 0xFF55FF55;
            } else if (currentPing <= 600) {
                return 0xFFFFFF55;
            } else {
                return 0xFFFF5555;
            }
        }

        public String getPingText() {
            if (currentPing == -1) {
                return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.ping_error").getString();
            } else if (currentPing == 0) {
                return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.ping_loading").getString();
            } else {
                return currentPing + "ms";
            }
        }

        public boolean isUpdating() {
            return isUpdating;
        }
    }

    public static class RoomClickHandler {
        private final Minecraft client;
        private final Screen parentScreen;
        private final Screen lobbyScreen;
        private boolean joining;

        public RoomClickHandler(Minecraft client, Screen parentScreen, Screen lobbyScreen) {
            this.client = client;
            this.parentScreen = parentScreen;
            this.lobbyScreen = lobbyScreen;
        }

        public boolean handleClick(BattleRoom room) {
            if (joining || client.gui.screen() != lobbyScreen) return false;
            if (room.getStatus() != BattleRoom.RoomStatus.WAITING) {
                playErrorSound();
                return false;
            }

            int currentPlayers = room.getPlayers() != null ? room.getPlayers().size() : 0;
            if (currentPlayers >= room.getMaxPlayers()) {
                playErrorSound();
                return false;
            }

            // A voice room needs the speech model before joining.
            if (room.isVoice() && !com.randomrun.features.voice.VoiceSettings.get().isReady()) {
                client.gui.setScreen(new com.randomrun.features.voice.ui.VoiceSetupScreen(lobbyScreen, ok -> {
                    client.gui.setScreen(lobbyScreen);
                    if (ok) handleClick(room);
                }, false));
                return true;
            }

            if (room.isPrivate()) {
                client.gui.setScreen(new PasswordInputScreen(lobbyScreen, room));
                return true;
            }

            joinRoom(room);
            return true;
        }

        private void joinRoom(BattleRoom room) {
            joining = true;
            String username = com.randomrun.network.BackendSession.playerName();

            BattleManager.getInstance()
                .joinRoom(username, room.getRoomCode(), "")
                .whenComplete((success, throwable) -> client.execute(() -> {
                    joining = false;
                    if (client.gui.screen() != lobbyScreen) return;
                    if (throwable != null) {
                        com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[RoomClick] Ошибка подключения", throwable);
                        playErrorSound();
                    } else if (Boolean.TRUE.equals(success)) {
                            client.gui.setScreen(new RoomWaitingScreen(
                                parentScreen,
                                room.getRoomCode(),
                                false
                            ));
                    } else {
                        playErrorSound();
                    }
                }));
        }

        public boolean isJoining() { return joining; }

        private void playErrorSound() {
            client.getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f
                )
            );
        }
    }

    public static class RoomSearchFilter {
        public static List<BattleRoom> filter(List<BattleRoom> rooms, String query) {
            if (query == null || query.isEmpty()) {
                return rooms;
            }

            String lowerQuery = query.toLowerCase(Locale.ROOT);
            return rooms.stream()
                .filter(room -> matches(room, lowerQuery))
                .collect(Collectors.toList());
        }

        private static boolean matches(BattleRoom room, String query) {
            if (room.isUpgrader() && ("upgrader".contains(query) || "апгрейдер".contains(query))) return true;
            if (room.isVoice() && ("voice".contains(query) || "голос".contains(query) || "голосом".contains(query))) return true;
            if (room.isRandomDrop() && ("random drop".contains(query) || "rd".equals(query))) return true;
            if (room.getRoomCode() != null && room.getRoomCode().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }

            if (room.getHost() != null && room.getHost().toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }

            if (room.getTargetItem() != null) {
                try {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(room.getTargetItem()));
                    String itemName = net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString().toLowerCase(Locale.ROOT);
                    if (itemName.contains(query)) {
                        return true;
                    }
                } catch (Exception e) {
                }
            }

            return false;
        }
    }

    public static class RoomSorter {
        public enum SortType {
            PLAYERS_DESC("randomrun.lobby.sort.players_desc"),
            PLAYERS_ASC("randomrun.lobby.sort.players_asc"),
            MODE("randomrun.lobby.sort.mode"),
            STATUS("randomrun.lobby.sort.status");

            private final String displayName;

            SortType(String displayName) {
                this.displayName = displayName;
            }

            public String getDisplayName() {
                return com.randomrun.infrastructure.util.LanguageManager.translate(displayName);
            }
        }

        public static List<BattleRoom> sort(List<BattleRoom> rooms, SortType sortType) {
            if (rooms == null || rooms.isEmpty()) {
                return rooms;
            }

            Comparator<BattleRoom> comparator = getComparator(sortType);
            return rooms.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
        }

        private static Comparator<BattleRoom> getComparator(SortType sortType) {
            switch (sortType) {
                case PLAYERS_DESC:
                    return Comparator.comparingInt((BattleRoom r) ->
                        r.getPlayers() != null ? r.getPlayers().size() : 0).reversed();

                case PLAYERS_ASC:
                    return Comparator.comparingInt(r ->
                        r.getPlayers() != null ? r.getPlayers().size() : 0);

                case MODE:
                    return Comparator.comparing(r ->
                        r.getCreationMode() != null ? r.getCreationMode() : "");

                case STATUS:
                    return Comparator.comparing(BattleRoom::getStatus);

                default:
                    return Comparator.comparingInt((BattleRoom r) ->
                        r.getPlayers() != null ? r.getPlayers().size() : 0).reversed();
            }
        }
    }

    public static class SearchDebouncer {
        private static final long DEBOUNCE_DELAY = 300;

        private Timer timer;
        private final Runnable action;
        private long generation;

        public SearchDebouncer(Runnable action) {
            this.action = action;
        }

        public synchronized void trigger() {
            if (timer != null) {
                timer.cancel();
            }

            long requestGeneration = ++generation;
            timer = new Timer("RandomRun-LobbySearch", true);
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (SearchDebouncer.this) {
                        if (generation == requestGeneration) action.run();
                    }
                }
            }, DEBOUNCE_DELAY);
        }

        public synchronized void cancel() {
            generation++;
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
        }
    }
}
