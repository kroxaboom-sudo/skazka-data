package com.kroxaboom.skazka.data.sync;

/**
 * RU: Минимальное состояние, необходимое планировщику синхронизации.
 * EN: Minimal state required by the sync scheduler.
 */
public record SyncRecord(
        String id,
        boolean dirty,
        boolean conflict,
        String error,
        long retryAt,
        long checkedAt
) {
    public SyncRecord {
        id = id == null ? "" : id;
        error = error == null ? "" : error;
        retryAt = Math.max(0, retryAt);
        checkedAt = Math.max(0, checkedAt);
    }
}
