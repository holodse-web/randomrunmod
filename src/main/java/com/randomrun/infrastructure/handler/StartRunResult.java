package com.randomrun.infrastructure.handler;

public enum StartRunResult {
    SUCCESS("Забег начат"),
    NOT_FROZEN("Забег не заморожен"),
    NO_PLAYER("Игрок не найден"),
    COOLDOWN("Подождите перед повторной попыткой"),
    INVALID_STATE("Неверное состояние мира"),
    NO_PERMISSION("Нет прав для старта");
    
    private final String message;
    
    StartRunResult(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return message;
    }
    
    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
