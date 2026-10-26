package com.randomrun.features.upgrader.client;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public final class UpgraderSearch {
    private UpgraderSearch() {}

    public static OptionalLong numericValue(String query) {
        if (query == null) return OptionalLong.empty();
        long number = 0;
        boolean digitFound = false;
        boolean overflow = false;
        for (int index = 0; index < query.length(); index++) {
            char symbol = query.charAt(index);
            if (Character.isWhitespace(symbol) || Character.isSpaceChar(symbol) || symbol == '_') continue;
            if (symbol < '0' || symbol > '9') return OptionalLong.empty();
            digitFound = true;
            int digit = symbol - '0';
            if (number > (Long.MAX_VALUE - digit) / 10) overflow = true;
            if (!overflow) number = number * 10 + digit;
        }
        return digitFound ? OptionalLong.of(overflow ? Long.MAX_VALUE : number) : OptionalLong.empty();
    }

    public static <T> List<T> apply(List<T> items, String query, long stake,
                                    ToLongFunction<T> value, Function<T, String> label,
                                    Function<T, String> id) {
        String needle = normalize(query).strip();
        OptionalLong requested = numericValue(query);
        Comparator<T> order = Comparator.comparingLong(value).thenComparing(id);
        if (requested.isPresent()) {
            long desired = requested.getAsLong();
            order = Comparator.<T>comparingLong(item -> Math.abs(value.applyAsLong(item) - desired))
                .thenComparingLong(value).thenComparing(id);
        }
        return items.stream()
            .filter(item -> value.applyAsLong(item) > 0)
            .filter(item -> requested.isPresent() || needle.isEmpty()
                || normalize(label.apply(item)).contains(needle) || normalize(id.apply(item)).contains(needle))
            .sorted(order)
            .toList();
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}
