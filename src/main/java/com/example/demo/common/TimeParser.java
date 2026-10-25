package com.example.demo.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 兼容多种时间格式的解析工具。
 */
public final class TimeParser {

    private static final DateTimeFormatter SPACE_MILLIS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter SPACE_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long EPOCH_MILLIS_THRESHOLD = 1_000_000_000_000L;

    private TimeParser() {
    }

    public static Instant parseOrNull(String text) {
        return parse(text, null);
    }

    /**
     * 支持 ISO-8601、yyyy-MM-dd HH:mm:ss[.SSS]、Unix 秒、Unix 毫秒。
     */
    public static Instant parse(String text, Instant defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        String value = text.trim();
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, SPACE_MILLIS)
                    .atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, SPACE_SECONDS)
                    .atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value)
                    .atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            long numeric = Long.parseLong(value);
            return numeric < EPOCH_MILLIS_THRESHOLD
                    ? Instant.ofEpochSecond(numeric)
                    : Instant.ofEpochMilli(numeric);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法时间格式: " + text);
        }
    }
}
