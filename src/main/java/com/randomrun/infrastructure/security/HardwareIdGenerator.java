package com.randomrun.infrastructure.security;

import com.randomrun.infrastructure.util.CompactLogger;

import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class HardwareIdGenerator {
    private static final int HWID_LENGTH = 32;
    private static final int HEX_STRING_CAPACITY = 64;
    private static final int MEMORY_MB_DIVISOR = 1024 * 1024;
    private static final String FALLBACK_HWID = "unknown_hwid";

    private static String cachedHWID = null;

    public static String generateHWID() {
        if (cachedHWID != null) {
            return cachedHWID;
        }

        try {
            List<String> components = new ArrayList<>();

            components.add(System.getProperty("os.name", "unknown"));
            components.add(System.getProperty("os.arch", "unknown"));
            components.add(System.getProperty("os.version", "unknown"));
            components.add(System.getProperty("java.version", "unknown"));
            components.add(System.getProperty("java.vendor", "unknown"));
            components.add(System.getProperty("user.name", "unknown"));
            components.add(System.getProperty("user.home", "unknown"));
            components.add(String.valueOf(Runtime.getRuntime().availableProcessors()));
            components.add(String.valueOf(Runtime.getRuntime().maxMemory() / MEMORY_MB_DIVISOR));
            components.add(System.getProperty("user.timezone", "unknown"));

            String macAddress = getMacAddress();
            if (macAddress != null) {
                components.add(macAddress);
            }

            String combined = String.join("|", components);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes());

            StringBuilder hexString = new StringBuilder(HEX_STRING_CAPACITY);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            cachedHWID = hexString.toString().substring(0, HWID_LENGTH);

            return cachedHWID;
        } catch (Exception e) {
            CompactLogger.error("[HWID] Generation failed: {}", e.getMessage());
            cachedHWID = generateFallbackHWID();
            return cachedHWID;
        }
    }

    private static String getMacAddress() {
        try {
            Enumeration<NetworkInterface> networks = NetworkInterface.getNetworkInterfaces();
            while (networks.hasMoreElements()) {
                NetworkInterface network = networks.nextElement();
                byte[] mac = network.getHardwareAddress();

                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }
                    return sb.toString();
                }
            }
        } catch (Exception e) {
            CompactLogger.warn("[HWID] MAC address unavailable");
        }
        return null;
    }

    private static String generateFallbackHWID() {
        try {
            String simple = System.getProperty("user.name", "unknown") +
                          System.getProperty("os.name", "unknown") +
                          Runtime.getRuntime().availableProcessors();

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(simple.getBytes());

            StringBuilder hexString = new StringBuilder(HWID_LENGTH);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (Exception e) {
            return FALLBACK_HWID;
        }
    }

    public static boolean matches(String hwid1, String hwid2) {
        return hwid1 != null && hwid1.equals(hwid2);
    }
}
