package com.randomrun.battle;

import com.google.gson.annotations.SerializedName;
import com.randomrun.main.RandomRunMod;

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
        FINISHED
    }
    
    @SerializedName("host")
    private String host;
    
    @SerializedName("guest")
    private String guest;
    
    @SerializedName("seed")
    private String seed;
    
    @SerializedName("targetItem")
    private String targetItem;
    
    @SerializedName("status")
    private RoomStatus status;
    
    @SerializedName("readyCount")
    private int readyCount;
    
    @SerializedName("hostReady")
    private boolean hostReady = false;
    
    @SerializedName("guestReady")
    private boolean guestReady = false;
    
    @SerializedName("hostLoaded")
    private boolean hostLoaded = false;
    
    @SerializedName("guestLoaded")
    private boolean guestLoaded = false;
    
    @SerializedName("winner")
    private String winner;
    
    @SerializedName("hostTime")
    private long hostTime;
    
    @SerializedName("guestTime")
    private long guestTime;
    
    @SerializedName("roomCode")
    private String roomCode;
    
    @SerializedName("isPrivate")
    private boolean isPrivate;
    
    @SerializedName("createdAt")
    private long createdAt;
    
    public BattleRoom() {
        this.status = RoomStatus.WAITING;
        this.readyCount = 0;
        this.createdAt = System.currentTimeMillis();
    }
    
    public BattleRoom(String host, String seed, String targetItem, String roomCode, boolean isPrivate) {
        this.host = host;
        this.seed = seed;
        this.targetItem = targetItem;
        this.roomCode = roomCode;
        this.isPrivate = isPrivate;
        this.status = RoomStatus.WAITING;
        this.readyCount = 0;
        this.createdAt = System.currentTimeMillis();
    }
    
    public String getHost() {
        return host;
    }
    
    public void setHost(String host) {
        this.host = host;
    }
    
    public String getGuest() {
        return guest;
    }
    
    public void setGuest(String guest) {
        this.guest = guest;
    }
    
    public String getSeed() {
        return seed;
    }
    
    public void setSeed(String seed) {
        this.seed = seed;
    }
    
    public String getTargetItem() {
        return targetItem;
    }
    
    public void setTargetItem(String targetItem) {
        this.targetItem = targetItem;
    }
    
    public RoomStatus getStatus() {
        return status;
    }
    
    public void setStatus(RoomStatus status) {
        this.status = status;
    }
    
    public int getReadyCount() {
        return readyCount;
    }
    
    public void setReadyCount(int readyCount) {
        this.readyCount = readyCount;
    }
    
    public boolean isHostReady() {
        return hostReady;
    }
    
    public void setHostReady(boolean hostReady) {
        this.hostReady = hostReady;
    }
    
    public boolean isGuestReady() {
        return guestReady;
    }
    
    public void setGuestReady(boolean guestReady) {
        this.guestReady = guestReady;
    }
    
    public boolean isHostLoaded() {
        return hostLoaded;
    }
    
    public void setHostLoaded(boolean hostLoaded) {
        this.hostLoaded = hostLoaded;
    }
    
    public boolean isGuestLoaded() {
        return guestLoaded;
    }
    
    public void setGuestLoaded(boolean guestLoaded) {
        this.guestLoaded = guestLoaded;
    }
    
    public String getWinner() {
        return winner;
    }
    
    public void setWinner(String winner) {
        this.winner = winner;
    }
    
    public long getHostTime() {
        return hostTime;
    }
    
    public void setHostTime(long hostTime) {
        this.hostTime = hostTime;
    }
    
    public long getGuestTime() {
        return guestTime;
    }
    
    public void setGuestTime(long guestTime) {
        this.guestTime = guestTime;
    }
    
    public String getRoomCode() {
        return roomCode;
    }
    
    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }
    
    public boolean isPrivate() {
        return isPrivate;
    }
    
    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public boolean isHost(String playerName) {
        return host != null && host.equals(playerName);
    }
    
    public boolean isGuest(String playerName) {
        return guest != null && guest.equals(playerName);
    }
    
    public boolean hasPlayer(String playerName) {
        return isHost(playerName) || isGuest(playerName);
    }
}
