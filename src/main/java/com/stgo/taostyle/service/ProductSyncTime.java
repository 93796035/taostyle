package com.stgo.taostyle.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;

/** An upload must carry an unambiguous instant; never invent a modification time. */
public final class ProductSyncTime {
    private ProductSyncTime() {}

    public static Timestamp requireTimestamp(String value) {
        if (value == null || value.trim().isEmpty())
            throw new IllegalArgumentException("Product last_modified is required");
        final Instant instant;
        try {
            instant = OffsetDateTime.parse(value.trim()).toInstant();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Product last_modified must include Z or an explicit UTC offset", invalid);
        }
        return Timestamp.from(instant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }
}
