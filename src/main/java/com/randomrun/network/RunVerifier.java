package com.randomrun.network;

import com.randomrun.infrastructure.RandomRunMod;

import com.google.gson.JsonObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.fabricmc.loader.api.FabricLoader;

public final class RunVerifier {
    public record Ticket(String id, String seed, String target) {}
    private record Proof(String id, long rta) {}

    private static volatile Ticket pending;
    private static volatile Ticket active;
    private static final Map<Long, Proof> PROOFS = new ConcurrentHashMap<>();

    private RunVerifier() {}

    public static boolean available() {
        return RandomRunMod.getInstance().getConfig().isOnlineMode() && BackendSession.isAuthenticated();
    }

    public static CompletableFuture<Ticket> begin(String target) {
        if (!available() || target == null) return CompletableFuture.completedFuture(null);
        return HttpClient.getInstance().request("POST", "/runs/begin", Map.of("target", target), true).thenApply(result -> {
            JsonObject value = result.getAsJsonObject();
            return new Ticket(value.get("id").getAsString(), value.get("seed").getAsString(), target);
        }).exceptionally(error -> null);
    }

    public static void prepare(Ticket ticket) {
        pending = ticket;
    }

    public static void activate(String target, String seed) {
        Ticket ticket = pending;
        pending = null;
        active = ticket != null && ticket.target().equals(target) && ticket.seed().equals(seed) ? ticket : null;
    }

    public static void timerStarted(String target) {
        Ticket ticket = active;
        if (ticket == null || !ticket.target().equals(target) || !available()) return;
        HttpClient.getInstance().request("POST", "/runs/start", Map.of("id", ticket.id()), true).exceptionally(error -> null);
    }

    public static void finished(String target, long stamp, long rta) {
        Ticket ticket = active;
        active = null;
        if (ticket != null && ticket.target().equals(target)) PROOFS.put(stamp, new Proof(ticket.id(), rta));
    }

    public static void cancel() {
        pending = null;
        active = null;
    }

    public static void attach(long stamp, Map<String, Object> data) {
        Proof proof = PROOFS.remove(stamp);
        if (proof == null) return;
        data.put("rid", proof.id());
        data.put("rta", proof.rta());
    }

    public static final class ModIntegrity {
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
}
