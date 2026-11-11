package com.randomrun.network;

import com.google.gson.JsonObject;
import com.randomrun.infrastructure.RandomRunMod;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

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
}
