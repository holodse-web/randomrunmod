package com.randomrun.features.battle.manager;

import com.google.gson.annotations.SerializedName;
import com.randomrun.infrastructure.util.PasswordHasher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BattleRoom {
    public enum RoomStatus {
        @SerializedName("WAITING")
        WAITING,

        @SerializedName("LOADING")
        LOADING,

        @SerializedName("FROZEN")
        FROZEN,

        @SerializedName("STARTED")
        STARTED,

        @SerializedName("FINISHED")
        FINISHED,

        @SerializedName("DRAW")
        DRAW
    }

    public transient long lastLocalUpdateTime = 0;

    @SerializedName("upd")
    private long lastUpdateTimestamp = 0;

    public void markUpdated() {
        this.lastLocalUpdateTime = System.currentTimeMillis();
        this.lastUpdateTimestamp = System.currentTimeMillis();
    }

    @SerializedName("h")
    private Map<String, GuestData> host = new HashMap<>();

    @SerializedName("s")
    private String seed;

    @SerializedName("t")
    private String targetItem;

    @SerializedName("st")
    private RoomStatus status;

    @SerializedName("w")
    private String winner;

    private transient String roomCode;

    @SerializedName("ip")
    private String serverAddress;

    @SerializedName("pr")
    private Boolean isPrivate;

    @SerializedName("pwd")
    private String password;

    @SerializedName("cm")
    private String creationMode = "sw";

    @SerializedName("dd")
    private Map<String, Boolean> disconnectedPlayers = new HashMap<>();

    @SerializedName("ca")
    private long createdAt;

    @SerializedName("dr")
    private String defeatReason;

    @SerializedName("start")
    private Long startTime = 0L;

    @SerializedName("srv_start")
    private Long serverStartTime = 0L;

    @SerializedName("swt")
    private Long sharedWorldStartTime = 0L;

    @SerializedName("mp")
    private Integer maxPlayers = 2;

    @SerializedName("gm")
    private String gameMode = "default";

    @SerializedName("ug")
    private boolean upgrader;

    @SerializedName("g")
    private Map<String, GuestData> guests = new HashMap<>();

    public static class GuestData {
        @SerializedName("r")
        public boolean ready = false;

        @SerializedName("l")
        public boolean loaded = false;

        @SerializedName("t")
        public long time = 0;

        public GuestData() {}
        public GuestData(boolean ready, boolean loaded) {
            this.ready = ready;
            this.loaded = loaded;
        }
    }

    @SerializedName("ep")
    private Map<String, Boolean> eliminatedPlayers = new HashMap<>();

    public BattleRoom() {
        this.status = RoomStatus.WAITING;
        this.createdAt = System.currentTimeMillis();
    }

    public BattleRoom(String hostName, String seed, String targetItem, String roomCode, boolean isPrivate, String password, boolean isSharedWorld, int maxPlayers, String creationMode, boolean randomDrop) {
        this(hostName, seed, targetItem, roomCode, isPrivate, password, isSharedWorld, maxPlayers, creationMode, randomDrop, false);
    }

    public BattleRoom(String hostName, String seed, String targetItem, String roomCode, boolean isPrivate, String password, boolean isSharedWorld, int maxPlayers, String creationMode, boolean randomDrop, boolean upgrader) {
        if (maxPlayers < 2 || maxPlayers > 10) {
            throw new IllegalArgumentException("maxPlayers must be between 2 and 10, got: " + maxPlayers);
        }
        this.host = new HashMap<>();
        if (hostName != null && !hostName.isEmpty()) {
            this.host.put(hostName, new GuestData());
        }
        this.seed = seed;
        this.targetItem = targetItem;
        this.roomCode = roomCode;
        this.isPrivate = isPrivate;
        if (password != null && !password.isEmpty()) {
            this.password = PasswordHasher.hash(password);
        } else {
            this.password = null;
        }
        this.maxPlayers = maxPlayers;
        this.creationMode = creationMode != null ? creationMode : (isSharedWorld ? "radmin" : "sw");
        this.gameMode = GameMode.fromBoolean(randomDrop).getId();
        this.upgrader = upgrader;
        this.status = RoomStatus.WAITING;
        this.createdAt = System.currentTimeMillis();
    }

    public BattleRoom(String hostName, String seed, String targetItem, String roomCode, boolean isPrivate, String password, boolean isSharedWorld, int maxPlayers, String creationMode) {
        this(hostName, seed, targetItem, roomCode, isPrivate, password, isSharedWorld, maxPlayers, creationMode, false);
    }

    public BattleRoom(String hostName, String seed, String targetItem, String roomCode, boolean isPrivate, String password, boolean isSharedWorld, int maxPlayers) {
        this(hostName, seed, targetItem, roomCode, isPrivate, password, isSharedWorld, maxPlayers, isSharedWorld ? "radmin" : "sw");
    }

    public BattleRoom(String hostName, String seed, String targetItem, String roomCode, boolean isPrivate, boolean isSharedWorld, int maxPlayers) {
        this(hostName, seed, targetItem, roomCode, isPrivate, null, isSharedWorld, maxPlayers, isSharedWorld ? "radmin" : "sw");
    }

    public String getHost() {
        if (host != null && !host.isEmpty()) {
            return host.keySet().iterator().next();
        }
        return null;
    }

    public void setHost(String hostName) {
        this.host = new HashMap<>();
        if (hostName != null && !hostName.isEmpty()) {
            this.host.put(hostName, new GuestData());
        }
    }

    public Map<String, GuestData> getHostData() {
        if (host == null) host = new HashMap<>();
        return host;
    }

    public String getGuest() {
        if (guests != null && !guests.isEmpty()) {
            return guests.keySet().iterator().next();
        }
        return null;
    }

    public String getSeed() { return seed; }
    public void setSeed(String seed) { this.seed = seed; }

    public String getTargetItem() { return targetItem; }
    public void setTargetItem(String targetItem) { this.targetItem = targetItem; }

    public boolean hasItemSelected() { return targetItem != null && !targetItem.isEmpty(); }

    public RoomStatus getStatus() { return status; }
    public void setStatus(RoomStatus status) { this.status = status; }

    public boolean isPlayerReady(String playerName) {
        if (getHostData().containsKey(playerName)) {
            return getHostData().get(playerName).ready;
        }
        if (getGuests().containsKey(playerName)) {
            return getGuests().get(playerName).ready;
        }
        return false;
    }

    public boolean isPlayerLoaded(String playerName) {
        if (getHostData().containsKey(playerName)) {
            return getHostData().get(playerName).loaded;
        }
        if (getGuests().containsKey(playerName)) {
            return getGuests().get(playerName).loaded;
        }
        return false;
    }

    public String getWinner() { return winner; }
    public void setWinner(String winner) { this.winner = winner; }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public String getServerAddress() { return serverAddress; }
    public void setServerAddress(String serverAddress) { this.serverAddress = serverAddress; }

    public boolean isPrivate() { return Boolean.TRUE.equals(isPrivate); }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isSharedWorld() {
        if (creationMode != null) {
            return !"sw".equals(creationMode);
        }
        return false;
    }

    public void setSharedWorld(boolean sharedWorld) {
        if (this.creationMode == null || "sw".equals(this.creationMode)) {
            this.creationMode = sharedWorld ? "radmin" : "sw";
        }
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getDefeatReason() { return defeatReason; }
    public void setDefeatReason(String defeatReason) { this.defeatReason = defeatReason; }

    public long getStartTime() { return startTime != null ? startTime : 0L; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getServerStartTime() { return serverStartTime != null ? serverStartTime : 0L; }
    public void setServerStartTime(long serverStartTime) { this.serverStartTime = serverStartTime == 0 ? null : serverStartTime; }

    public long getSharedWorldStartTime() { return sharedWorldStartTime != null ? sharedWorldStartTime : 0L; }
    public void setSharedWorldStartTime(long sharedWorldStartTime) { this.sharedWorldStartTime = sharedWorldStartTime == 0 ? null : sharedWorldStartTime; }

    public String getCreationMode() { return creationMode; }
    public void setCreationMode(String creationMode) { this.creationMode = creationMode; }

    public int getMaxPlayers() { return maxPlayers != null ? maxPlayers : 2; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public GameMode getGameMode() {
        return GameMode.fromId(gameMode);
    }

    public void setGameMode(GameMode mode) {
        this.gameMode = mode != null ? mode.getId() : GameMode.DEFAULT.getId();
    }

    public boolean isRandomDrop() {
        return getGameMode() == GameMode.RANDOM_DROP;
    }

    public boolean isUpgrader() { return upgrader; }
    public void setUpgrader(boolean upgrader) { this.upgrader = upgrader; }

    public Map<String, GuestData> getGuests() {
        if (guests == null) guests = new HashMap<>();
        return guests;
    }

    public void setGuests(Map<String, GuestData> guests) { this.guests = guests; }

    public List<String> getPlayers() {
        List<String> result = new ArrayList<>();
        if (host != null && !host.isEmpty()) {
            result.addAll(host.keySet());
        }
        if (guests != null) {
            result.addAll(guests.keySet());
        }
        return result;
    }

    public void addPlayer(String playerName) {
        if (host == null || host.isEmpty()) {
            host = new HashMap<>();
            host.put(playerName, new GuestData());
            return;
        }

        String hostName = getHost();
        if (hostName != null && !hostName.equals(playerName)) {
            if (!getGuests().containsKey(playerName)) {
                getGuests().put(playerName, new GuestData());
            }
        }
    }

    public void removePlayer(String playerName) {
        if (guests != null) {
            guests.remove(playerName);
        }
    }

    public boolean hasPlayer(String playerName) {
        return (host != null && host.containsKey(playerName)) || (guests != null && guests.containsKey(playerName));
    }

    public boolean isHost(String playerName) {
        return host != null && host.containsKey(playerName);
    }

    public boolean isGuest(String playerName) {
        return !isHost(playerName) && hasPlayer(playerName);
    }

    public List<String> getReadyPlayers() {
        List<String> result = new ArrayList<>();
        if (host != null) {
            for (Map.Entry<String, GuestData> entry : host.entrySet()) {
                if (entry.getValue().ready) {
                    result.add(entry.getKey());
                }
            }
        }
        if (guests != null) {
            for (Map.Entry<String, GuestData> entry : guests.entrySet()) {
                if (entry.getValue().ready) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    public void setPlayerReady(String playerName, boolean ready) {
        if (getHostData().containsKey(playerName)) {
            getHostData().get(playerName).ready = ready;
        } else if (getGuests().containsKey(playerName)) {
            getGuests().get(playerName).ready = ready;
        }
    }

    public List<String> getLoadedPlayers() {
        List<String> result = new ArrayList<>();
        if (host != null) {
            for (Map.Entry<String, GuestData> entry : host.entrySet()) {
                if (entry.getValue().loaded) {
                    result.add(entry.getKey());
                }
            }
        }
        if (guests != null) {
            for (Map.Entry<String, GuestData> entry : guests.entrySet()) {
                if (entry.getValue().loaded) {
                    result.add(entry.getKey());
                }
            }
        }
        return result;
    }

    public void setPlayerLoaded(String playerName, boolean loaded) {
        if (getHostData().containsKey(playerName)) {
            getHostData().get(playerName).loaded = loaded;
        } else if (getGuests().containsKey(playerName)) {
            getGuests().get(playerName).loaded = loaded;
        }
    }

    public Map<String, Boolean> getDisconnectedPlayers() {
        return disconnectedPlayers != null ? disconnectedPlayers : new HashMap<>();
    }

    public void setDisconnectedPlayers(Map<String, Boolean> disconnectedPlayers) {
        this.disconnectedPlayers = disconnectedPlayers;
    }

    public boolean isPlayerDisconnected(String playerName) {
        return getDisconnectedPlayers().getOrDefault(playerName, false);
    }

    public void setPlayerDisconnected(String playerName, boolean disconnected) {
        if (this.disconnectedPlayers == null) this.disconnectedPlayers = new HashMap<>();
        if (disconnected) {
            this.disconnectedPlayers.put(playerName, true);
        } else {
            this.disconnectedPlayers.remove(playerName);
        }
    }

    public List<String> getEliminatedPlayers() {
        if (eliminatedPlayers == null) eliminatedPlayers = new HashMap<>();
        return new ArrayList<>(eliminatedPlayers.keySet());
    }

    public Map<String, Boolean> getEliminationMap() {
        if (eliminatedPlayers == null) eliminatedPlayers = new HashMap<>();
        return eliminatedPlayers;
    }

    public void addEliminatedPlayer(String playerName) {
        if (eliminatedPlayers == null) eliminatedPlayers = new HashMap<>();
        eliminatedPlayers.put(playerName, true);
    }

    public boolean isPlayerEliminated(String playerName) {
        if (eliminatedPlayers == null) return false;
        return Boolean.TRUE.equals(eliminatedPlayers.get(playerName));
    }

    public Map<String, Long> getPlayerTimes() {
        Map<String, Long> times = new HashMap<>();
        if (host != null) {
            for (Map.Entry<String, GuestData> entry : host.entrySet()) {
                if (entry.getValue().time > 0) {
                    times.put(entry.getKey(), entry.getValue().time);
                }
            }
        }
        if (guests != null) {
            for (Map.Entry<String, GuestData> entry : guests.entrySet()) {
                if (entry.getValue().time > 0) {
                    times.put(entry.getKey(), entry.getValue().time);
                }
            }
        }
        return times;
    }

    public void setPlayerTime(String playerName, long time) {
        if (getHostData().containsKey(playerName)) {
            getHostData().get(playerName).time = time;
        } else if (getGuests().containsKey(playerName)) {
            getGuests().get(playerName).time = time;
        }
    }

    public long getPlayerTime(String playerName) {
        if (host != null && host.containsKey(playerName)) {
            return host.get(playerName).time;
        }
        if (guests != null && guests.containsKey(playerName)) {
            return guests.get(playerName).time;
        }
        return 0L;
    }
}
