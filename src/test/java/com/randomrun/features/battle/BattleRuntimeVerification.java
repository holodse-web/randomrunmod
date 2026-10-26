package com.randomrun.features.battle;

import com.google.gson.JsonParser;
import com.randomrun.features.battle.manager.BattleCleanupTask;
import com.randomrun.features.battle.manager.BattleEventState;
import com.randomrun.network.FirebaseClient;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class BattleRuntimeVerification {
    private static int checks;
    private BattleRuntimeVerification() {}

    public static int verify() {
        checks = 0;
        for (String path : new String[] {"rooms", "12345", "12345/events", "00000/events"}) {
            check(FirebaseClient.isValidSsePath(path), "known SSE surface accepted: " + path);
            new FirebaseClient.SseClient(path, (event, data) -> {}, error -> {}, () -> {});
            check(true, "real SSE constructor accepts the event-stream path without connecting");
        }
        for (String path : new String[] {"", "/rooms", "1234", "123456", "rooms/12345", "12345/anything",
                "12345/events/child", "12345/events?auth=x", "12345/../events", "abcde/events", "12345/Events"}) {
            check(!FirebaseClient.isValidSsePath(path), "unrelated SSE surface rejected");
            rejected(() -> new FirebaseClient.SseClient(path, (event, data) -> {}, error -> {}, () -> {}));
        }
        check(!FirebaseClient.isValidSsePath(null), "null SSE path rejected");

        BattleEventState events = new BattleEventState();
        var first = events.apply("put", "/event1", JsonParser.parseString("{\"type\":\"DEATH\",\"player\":\"Guest\"}"));
        check(first.size() == 1 && first.getFirst().get("player").getAsString().equals("Guest"), "individual event PUT is emitted");
        first.getFirst().addProperty("player", "Mutated outside");
        check(events.apply("put", "/event1", JsonParser.parseString("{\"type\":\"DEATH\",\"player\":\"Guest\"}")).isEmpty(), "event replay cannot duplicate notifications");
        check(events.apply("put", "/event2", JsonParser.parseString("{\"type\":\"ACHIEVEMENT\",\"player\":\"Guest\"}")).isEmpty(), "incomplete achievement waits for its ID");
        var complete = events.apply("put", "/event2/aid", JsonParser.parseString("\"story/mine_diamond\""));
        check(complete.size() == 1 && complete.getFirst().has("aid"), "nested field completes and emits an achievement");
        check(events.apply("patch", "/", JsonParser.parseString("{\"event3\":{\"type\":\"ELIMINATION\",\"player\":\"Host\"}}")).size() == 1, "root PATCH emits only new events");
        check(events.apply("put", "/", JsonParser.parseString("null")).isEmpty(), "deleting event collection emits no phantom event");
        check(events.apply("put", "/event1", JsonParser.parseString("{\"type\":\"DEATH\",\"player\":\"Guest\"}")).isEmpty(), "an old event restored after reconnect stays delivered once");

        ArrayDeque<Runnable> queue = new ArrayDeque<>();
        AtomicInteger resets = new AtomicInteger();
        AtomicBoolean sameRoom = new AtomicBoolean(true);
        CompletableFuture<Boolean> pending = new CompletableFuture<>();
        var finished = BattleCleanupTask.finish(pending, 60_000, queue::add, sameRoom::get, resets::incrementAndGet);
        check(resets.get() == 0 && queue.isEmpty(), "network wait cannot reset client state from another thread");
        pending.complete(true);
        check(resets.get() == 0 && queue.size() == 1, "network completion schedules one client-thread cleanup");
        queue.remove().run();
        check(finished.join() && resets.get() == 1 && queue.isEmpty(), "departure resets the captured room once");
        pending.completeExceptionally(new IllegalStateException("late callback"));
        check(resets.get() == 1 && queue.isEmpty(), "late callback does not schedule another cleanup");
        var stale = BattleCleanupTask.finish(CompletableFuture.completedFuture(true), 60_000, queue::add,
            sameRoom::get, resets::incrementAndGet);
        sameRoom.set(false);
        queue.remove().run();
        check(stale.join() && resets.get() == 1, "a queued old-room callback never resets a replacement room");
        var failed = BattleCleanupTask.finish(CompletableFuture.failedFuture(new IllegalStateException("offline")),
            60_000, Runnable::run, () -> true, resets::incrementAndGet);
        check(!failed.join() && resets.get() == 2, "network failure still performs one local cleanup");
        CompletableFuture<Boolean> late = new CompletableFuture<>();
        var timedOut = BattleCleanupTask.finish(late, 0, Runnable::run, () -> true, resets::incrementAndGet);
        try { check(!timedOut.get(2, TimeUnit.SECONDS), "bounded notification completes without a competing watchdog"); }
        catch (Exception error) { throw new AssertionError("disconnect timeout did not complete", error); }
        check(resets.get() == 3 && !late.isDone(), "local timeout neither waits for nor cancels the actual network request");
        late.complete(true);
        check(resets.get() == 3, "completion after timeout cannot reset or delete a second room");
        return checks;
    }

    private static void rejected(Runnable action) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { check(true, "real SSE constructor rejects an invalid path"); return; }
        throw new AssertionError("invalid SSE path accepted");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
