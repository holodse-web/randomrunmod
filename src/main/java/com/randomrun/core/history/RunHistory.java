package com.randomrun.core.history;

import com.randomrun.infrastructure.util.CompactLogger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RunHistory {
    private static final String LOG_TAG = "[History]";
    private static final int MAX_RECORDS = 10000;

    private final List<Run> records = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Run> bestRecords = new ConcurrentHashMap<>();

    private int totalVictories = 0;

    private boolean loaded = false;
    private boolean stale = false;

    private RunHistory() {}

    private static class Holder {
        private static final RunHistory INSTANCE = new RunHistory();
    }

    public static RunHistory getInstance() {
        return Holder.INSTANCE;
    }

    public void addRecord(Run record) {
        if (record == null) {
            CompactLogger.warn("{} Null record ignored", LOG_TAG);
            return;
        }

        String targetId = record.getTargetId();
        if (targetId == null || targetId.isEmpty()) {
            CompactLogger.warn("{} Record without targetId ignored", LOG_TAG);
            return;
        }

        addRecords(Collections.singletonList(record));
    }

    public void addRecords(List<Run> newRecords) {
        if (newRecords == null || newRecords.isEmpty()) return;

        for (Run record : newRecords) {
            if (record != null && record.getTargetId() != null && !record.getTargetId().isEmpty()) {
                if (records.size() >= MAX_RECORDS) {
                    removeOldestRecord();
                }

                records.add(record);

                if (record.isVictory()) {
                    totalVictories++;
                    updateBestRecord(record);
                }
            }
        }
    }

    private void removeOldestRecord() {
        if (records.isEmpty()) return;

        Run oldest = records.remove(0);

        if (oldest.isVictory()) {
            totalVictories--;

            String targetId = oldest.getTargetId();
            Run currentBest = bestRecords.get(targetId);
            if (!oldest.isUpgrader() && currentBest != null && currentBest.getTimestamp() == oldest.getTimestamp()) {
                recalculateBestRecord(targetId);
            }
        }
    }

    private void updateBestRecord(Run record) {
        if (!record.isVictory() || record.isUpgrader()) return;

        String targetId = record.getTargetId();
        Run currentBest = bestRecords.get(targetId);

        if (currentBest == null || record.getDuration() < currentBest.getDuration()) {
            bestRecords.put(targetId, record);
        }
    }

    private void recalculateBestRecord(String targetId) {
        Run newBest = records.stream()
            .filter(r -> r.getTargetId().equals(targetId) && r.isVictory() && !r.isUpgrader())
            .min(Comparator.comparingLong(Run::getDuration))
            .orElse(null);

        if (newBest != null) {
            bestRecords.put(targetId, newBest);
        } else {
            bestRecords.remove(targetId);
        }
    }

    public List<Run> getAllRecords() {
        return new ArrayList<>(records);
    }

    public List<Run> getRecordsForTarget(String targetId) {
        return records.stream()
            .filter(r -> r.getTargetId().equals(targetId))
            .collect(Collectors.toList());
    }

    public List<Run> getRecentRecords(int limit) {
        synchronized (records) {
            int size = records.size();
            int fromIndex = Math.max(0, size - limit);
            List<Run> recent = new ArrayList<>(records.subList(fromIndex, size));
            Collections.reverse(recent);
            return recent;
        }
    }

    public Optional<Run> getBestRecord(String targetId) {
        return Optional.ofNullable(bestRecords.get(targetId));
    }

    public Optional<Long> getBestTime(String targetId) {
        return getBestRecord(targetId).map(Run::getDuration);
    }

    public Map<String, Long> getBestTimes() {
        Map<String, Long> times = new HashMap<>();
        bestRecords.forEach((targetId, record) -> {
            times.put(targetId, record.getDuration());
        });
        return times;
    }

    public int getAttemptCount(String targetId) {
        return getAttemptCount(targetId, false);
    }

    public int getAttemptCount(String targetId, boolean upgrader) {
        return (int) records.stream()
            .filter(r -> r.getTargetId().equals(targetId) && r.isUpgrader() == upgrader)
            .count();
    }

    public int getVictoryCount(String targetId) {
        return (int) records.stream()
            .filter(r -> r.getTargetId().equals(targetId) && r.isVictory())
            .count();
    }

    public List<Run> getVictories() {
        return records.stream()
            .filter(Run::isVictory)
            .collect(Collectors.toList());
    }

    public void clear() {
        records.clear();
        bestRecords.clear();
        totalVictories = 0;
        loaded = false;
    }

    public void setRecords(List<Run> newRecords) {
        clear();
        addRecords(newRecords);
        loaded = true;
        stale = false;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean isStale() {
        return stale;
    }

    public void markAsStale() {
        stale = true;
    }

    public int getTotalRecords() {
        return records.size();
    }

    public int getTotalVictories() {
        return totalVictories;
    }

    public int getTotalDefeats() {
        return records.size() - totalVictories;
    }
}
