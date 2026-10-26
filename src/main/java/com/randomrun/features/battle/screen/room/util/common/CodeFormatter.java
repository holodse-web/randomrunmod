package com.randomrun.features.battle.screen.room.util.common;

public class CodeFormatter {
    public static String format(String input) {
        if (input == null || input.isEmpty()) return "";
        
        String digits = input.replaceAll("\\D", "");
        
        if (digits.length() > 5) {
            digits = digits.substring(0, 5);
        }
        
        if (digits.length() <= 1) {
            return digits;
        }
        
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0) formatted.append("-");
            formatted.append(digits.charAt(i));
        }
        
        return formatted.toString();
    }
    
    public static String unformat(String formatted) {
        if (formatted == null) return "";
        return formatted.replaceAll("-", "");
    }
}
