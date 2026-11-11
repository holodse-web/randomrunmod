package com.randomrun.network;

import net.fabricmc.loader.api.FabricLoader;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

public final class ModIntegrity {
    private static volatile CompletableFuture<String> fingerprint;

    private ModIntegrity() {}

    public static CompletableFuture<String> fingerprint() {
        CompletableFuture<String> value = fingerprint;
        if (value == null) {
            synchronized (ModIntegrity.class) {
                if (fingerprint == null) fingerprint = CompletableFuture.supplyAsync(ModIntegrity::compute);
                value = fingerprint;
            }
        }
        return value;
    }

    private static String compute() {
        try {
            var container = FabricLoader.getInstance().getModContainer("randomrun").orElse(null);
            if (container == null) return "unknown";
            for (Path path : container.getOrigin().getPaths()) {
                if (Files.isRegularFile(path)) return hash(path);
            }
            String release = System.getenv("RANDOMRUN_INTEGRITY_JAR");
            if (FabricLoader.getInstance().isDevelopmentEnvironment() && release != null && Files.isRegularFile(Path.of(release))) return hash(Path.of(release));
            return "development";
        } catch (Exception error) {
            return "unknown";
        }
    }

    private static String hash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[1 << 16];
            for (int read; (read = input.read(buffer)) > 0; ) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String proof(String fingerprint, String nonce, String name) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprint.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((nonce + ":" + name.toLowerCase(java.util.Locale.ROOT)).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            return "";
        }
    }
}
