package com.randomrun.features.battle.screen;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.screen.LobbyRender.UIColors;
import com.randomrun.network.HttpClient;
import com.randomrun.network.RealtimeClient;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
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

    public static final class LobbyUpdates {
        public enum ConnectionStatus { CONNECTED, CONNECTING, DISCONNECTED, ERROR }

        private static final Gson GSON = new Gson();
        private static final long POLL_INTERVAL_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        private final Consumer<List<BattleRoom>> listener;
        private volatile RealtimeClient.Subscription subscription;
        private volatile ConnectionStatus fallbackStatus = ConnectionStatus.CONNECTING;
        private long nextPoll;
        private boolean polling;
        private volatile long generation;

        public LobbyUpdates(Consumer<List<BattleRoom>> listener) {
            this.listener = listener;
        }

        public synchronized void start() {
            if (subscription != null) return;
            long expected = generation;
            subscription = RealtimeClient.getInstance().subscribeRooms(snapshot -> {
                synchronized (LobbyUpdates.this) {
                    if (generation == expected) acceptRooms(snapshot);
                }
            });
        }

        public synchronized void stop() {
            generation++;
            if (subscription != null) subscription.close();
            subscription = null;
            polling = false;
            nextPoll = 0;
            fallbackStatus = ConnectionStatus.CONNECTING;
        }

        public synchronized void tick() {
            long now = System.nanoTime();
            if (subscription == null || polling || nextPoll != 0 && now - nextPoll < 0
                    || RealtimeClient.getInstance().isConnected()) return;
            long expected = generation;
            polling = true;
            nextPoll = now + POLL_INTERVAL_NANOS;
            HttpClient.getInstance().getNoCache("/rooms").whenComplete((value, error) -> {
                synchronized (LobbyUpdates.this) {
                    if (generation != expected) return;
                    polling = false;
                    if (RealtimeClient.getInstance().isConnected()) return;
                    if (error != null || value == null || !value.isJsonObject()) {
                        fallbackStatus = ConnectionStatus.ERROR;
                        return;
                    }
                    try {
                        acceptRooms(value.getAsJsonObject());
                        fallbackStatus = ConnectionStatus.CONNECTED;
                    } catch (RuntimeException invalid) {
                        fallbackStatus = ConnectionStatus.ERROR;
                    }
                }
            });
        }

        private void acceptRooms(com.google.gson.JsonObject snapshot) {
            List<BattleRoom> rooms = new ArrayList<>();
            for (var entry : snapshot.entrySet()) {
                BattleRoom room = GSON.fromJson(entry.getValue(), BattleRoom.class);
                room.setRoomCode(entry.getKey());
                rooms.add(room);
            }
            listener.accept(rooms);
        }

        public ConnectionStatus getStatus() {
            if (subscription == null) return ConnectionStatus.DISCONNECTED;
            return RealtimeClient.getInstance().isConnected()
                ? ConnectionStatus.CONNECTED : fallbackStatus;
        }
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
            if (!room.isJoinable()) {
                playErrorSound();
                return false;
            }

            if (room.isVoice() && !com.randomrun.features.voice.VoiceSettings.get().isReady()) {
                client.gui.setScreen(new com.randomrun.features.voice.ui.VoiceSetupScreen(lobbyScreen, ok -> {
                    client.gui.setScreen(lobbyScreen);
                    if (ok) handleClick(room);
                }, false));
                return true;
            }


            com.randomrun.features.streamer.StreamerScreen.askForRoom(lobbyScreen, room, () -> {
                if (room.isPrivate()) client.gui.setScreen(new PasswordInputScreen(lobbyScreen, room));
                else joinRoom(room);
            });
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
                        com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[RoomClick] Join failed", throwable);
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
            return switch (sortType) {
                case PLAYERS_DESC -> Comparator.comparingInt(BattleRoom::getPlayerCount).reversed();
                case PLAYERS_ASC -> Comparator.comparingInt(BattleRoom::getPlayerCount);
                case MODE -> Comparator.comparing(room -> room.getCreationMode() != null ? room.getCreationMode() : "");
                case STATUS -> Comparator.comparing(BattleRoom::getStatus);
            };
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
