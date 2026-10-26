package com.randomrun.features.battle.screen.room.util.validation;

public class InputValidator {
    public static boolean isValidIp(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        
        String[] parts = ip.split(":", 2);
        String host = parts[0].trim();
        String portStr = parts.length > 1 ? parts[1].trim() : null;
        
        String[] octets = host.split("\\.");
        if (octets.length != 4) return false;
        
        for (String octet : octets) {
            try {
                int val = Integer.parseInt(octet);
                if (val < 0 || val > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        if (portStr != null && !portStr.isEmpty()) {
            try {
                int port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        
        return true;
    }
    
    public static ValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.valid();
        }
        
        if (password.length() < 4) {
            return ValidationResult.invalid("randomrun.error.password_too_short");
        }
        
        if (password.length() > 32) {
            return ValidationResult.invalid("randomrun.error.password_too_long");
        }
        
        if (password.contains(" ")) {
            return ValidationResult.invalid("randomrun.error.password_no_spaces");
        }
        
        return ValidationResult.valid();
    }
    
    public static boolean isValidRoomCode(String code) {
        return code != null && code.length() == 5 && code.matches("\\d+");
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorKey;
        
        private ValidationResult(boolean valid, String errorKey) {
            this.valid = valid;
            this.errorKey = errorKey;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String errorKey) {
            return new ValidationResult(false, errorKey);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorKey() {
            return errorKey;
        }
    }
}
