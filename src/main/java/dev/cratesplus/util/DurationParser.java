package dev.cratesplus.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([a-zA-Z]+)");

    private DurationParser() {
    }

    public static long millis(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("none") || normalized.equals("off") || normalized.equals("disabled")) {
            return 0L;
        }
        if (normalized.equals("daily")) {
            return 86_400_000L;
        }
        if (normalized.equals("weekly")) {
            return 604_800_000L;
        }
        if (normalized.equals("monthly")) {
            return 2_592_000_000L;
        }

        Matcher matcher = TOKEN.matcher(normalized);
        long total = 0L;
        int matches = 0;
        while (matcher.find()) {
            matches++;
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                return fallback;
            }
            long unitMillis = unitMillis(matcher.group(2));
            if (unitMillis <= 0L || amount > Long.MAX_VALUE / unitMillis) {
                return fallback;
            }
            long add = amount * unitMillis;
            if (total > Long.MAX_VALUE - add) {
                return fallback;
            }
            total += add;
        }
        if (matches > 0) {
            return total;
        }
        try {
            return Math.max(0L, Long.parseLong(normalized) * 1000L);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long unitMillis(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "ms", "milli", "millis", "millisecond", "milliseconds" -> 1L;
            case "s", "sec", "secs", "second", "seconds" -> 1000L;
            case "m", "min", "mins", "minute", "minutes" -> 60_000L;
            case "h", "hr", "hrs", "hour", "hours" -> 3_600_000L;
            case "d", "day", "days" -> 86_400_000L;
            case "w", "week", "weeks" -> 604_800_000L;
            case "mo", "month", "months" -> 2_592_000_000L;
            case "y", "yr", "year", "years" -> 31_536_000_000L;
            default -> 0L;
        };
    }
}
