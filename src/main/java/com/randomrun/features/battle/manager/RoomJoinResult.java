package com.randomrun.features.battle.manager;

public enum RoomJoinResult {
    JOINED(null),
    INVALID_PASSWORD("password"),
    ROOM_FULL("full"),
    ROOM_NOT_FOUND("missing"),
    ACCESS_DENIED("access"),
    CONNECTION_FAILED("connection"),
    CANCELLED("cancelled");

    private final String messageKey;

    RoomJoinResult(String suffix) {
        messageKey = suffix == null ? null : "randomrun.battle.join_error." + suffix;
    }

    public boolean joined() {
        return this == JOINED;
    }

    public String messageKey() {
        return messageKey;
    }
}
