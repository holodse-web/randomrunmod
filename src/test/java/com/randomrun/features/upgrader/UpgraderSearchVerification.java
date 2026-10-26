package com.randomrun.features.upgrader;

import com.randomrun.features.upgrader.client.UpgraderSearch;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;

public final class UpgraderSearchVerification {
    private static int checks;
    private record Entry(String id, String name, long value) {}
    private UpgraderSearchVerification() {}

    public static int verify() {
        checks = 0;
        List<Entry> entries = List.of(new Entry("iron", "Железо", 580),
            new Entry("above", "Above", 581), new Entry("below", "Below", 579),
            new Entry("zero", "Empty", 0), new Entry("far", "Far", 900),
            new Entry("second_580", "Another exact", 580));
        check(search(entries, "580", 0).stream().map(Entry::value).toList()
            .equals(List.of(580L, 580L, 579L, 581L, 900L)), "numeric search ranks exact and nearest prices");
        check(search(entries, "580", 580).stream().map(Entry::value).toList()
            .equals(List.of(580L, 580L, 579L, 581L, 900L)), "nearest search includes equal and cheaper targets");
        check(search(entries, "ЖЕЛЕЗ", 0).getFirst().id.equals("iron"), "localized names are case insensitive");
        check(search(entries, "SECOND_", 0).getFirst().id.equals("second_580"), "registry ID text search preserved");
        check(search(entries, "missing", 0).isEmpty(), "unmatched text remains empty");
        check(search(entries, "580", 1000).stream().map(Entry::value).toList()
            .equals(List.of(580L, 580L, 579L, 581L, 900L)), "large stake does not hide cheaper targets");
        check(search(entries, "ЖЕЛЕЗ", 1000).getFirst().id.equals("iron"), "name search finds cheaper targets");
        check(search(entries, "", Long.MAX_VALUE).size() == 5, "all positive targets remain available for any stake");
        check(search(entries, "Empty", 0).isEmpty(), "zero-value targets stay excluded");
        check(search(List.of(new Entry("negative", "Negative", -10)), "", 1000).isEmpty(),
            "negative-value targets stay excluded");
        check(search(entries, "", 0).stream().map(Entry::value).toList()
            .equals(List.of(579L, 580L, 580L, 581L, 900L)), "empty query restores ordinary price order");
        check(UpgraderSearch.numericValue("5 800").orElseThrow() == 5800, "space-separated number");
        check(UpgraderSearch.numericValue("5\u202f800").orElseThrow() == 5800, "narrow space-separated number");
        check(UpgraderSearch.numericValue("5_800").orElseThrow() == 5800, "underscore-separated number");
        check(UpgraderSearch.numericValue("0").orElseThrow() == 0, "zero value query");
        check(UpgraderSearch.numericValue("000580").orElseThrow() == 580, "leading zeros");
        check(UpgraderSearch.numericValue(" ").isEmpty(), "empty query is text mode");
        check(UpgraderSearch.numericValue("iron_580").isEmpty(), "alphanumeric query is text mode");
        check(UpgraderSearch.numericValue("-580").isEmpty(), "negative value is not a price");
        check(UpgraderSearch.numericValue("9".repeat(80)).orElseThrow() == Long.MAX_VALUE, "huge number cannot overflow");
        check(search(entries, "9".repeat(80), 0).getFirst().value == 900, "huge number ranks largest legal prices first");
        check(entries.getFirst().id.equals("iron"), "search never mutates original catalog order");
        var actual = UpgraderSearch.apply(UpgraderValues.targets(), "580", 0, UpgraderValues::unitValue,
            item -> BuiltInRegistries.ITEM.getKey(item).toString(), item -> BuiltInRegistries.ITEM.getKey(item).toString());
        long minimum = UpgraderValues.targets().stream().mapToLong(item -> Math.abs(UpgraderValues.unitValue(item) - 580)).min().orElseThrow();
        check(!actual.isEmpty() && Math.abs(UpgraderValues.unitValue(actual.getFirst()) - 580) == minimum,
            "actual Minecraft catalog query 580 starts with closest real price");
        var withLargeStake = UpgraderSearch.apply(UpgraderValues.targets(), "580", Long.MAX_VALUE,
            UpgraderValues::unitValue, item -> BuiltInRegistries.ITEM.getKey(item).toString(),
            item -> BuiltInRegistries.ITEM.getKey(item).toString());
        check(actual.equals(withLargeStake), "actual Minecraft numeric catalog results never depend on stake size");
        return checks;
    }

    private static List<Entry> search(List<Entry> items, String query, long stake) {
        return UpgraderSearch.apply(items, query, stake, Entry::value, Entry::name, Entry::id);
    }

    private static void check(boolean success, String description) {
        if (!success) throw new AssertionError(description);
        checks++;
    }
}
