package com.kroxaboom.skazka.data.sync;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * RU: Планировщик не делает сетевых запросов: он только выбирает записи,
 * которым уже пора выполняться. Dirty-записи идут раньше обычных проверок.
 *
 * EN: The planner performs no network I/O; it only selects records that are due.
 * Dirty records are prioritized ahead of ordinary refresh checks.
 */
public final class SyncPlan {
    public static final long DEFAULT_FRESH_MS = 10L * 60L * 1000L;

    private SyncPlan() {}

    public static List<SyncRecord> due(List<SyncRecord> records, long now) {
        return due(records, now, DEFAULT_FRESH_MS);
    }

    public static List<SyncRecord> due(List<SyncRecord> records, long now, long freshMillis) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        if (freshMillis < 0) {
            throw new IllegalArgumentException("Freshness window must not be negative");
        }

        List<SyncRecord> due = new ArrayList<>();
        for (SyncRecord record : records) {
            if (record == null || record.conflict() || record.retryAt() > now) {
                continue;
            }

            boolean unchecked = record.checkedAt() == 0;
            boolean stale = !unchecked && now - record.checkedAt() >= freshMillis;
            if (record.dirty() || !record.error().isEmpty() || unchecked || stale) {
                due.add(record);
            }
        }

        due.sort(
                Comparator.comparing(SyncRecord::dirty)
                        .reversed()
                        .thenComparingLong(SyncRecord::checkedAt)
        );
        return List.copyOf(due);
    }
}
