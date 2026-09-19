package com.kroxaboom.skazka.data.storage;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RU: Небольшое транзакционное key/value-хранилище поверх SQLite.
 * Оно хранит строки как есть и не знает о JSON, библиотеке, главах или синхронизации.
 *
 * EN: Small transactional key/value store backed by SQLite.
 * It stores opaque strings and has no knowledge of JSON, libraries, chapters, or sync.
 */
public final class SqliteKeyValueStore extends SQLiteOpenHelper {
    private static final int SCHEMA_VERSION = 1;

    public SqliteKeyValueStore(Context context, String databaseName) {
        super(applicationContext(context), checkedName(databaseName), null, SCHEMA_VERSION);
        setWriteAheadLoggingEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE records(" +
                        "k TEXT PRIMARY KEY NOT NULL," +
                        "v TEXT NOT NULL," +
                        "updated INTEGER NOT NULL" +
                        ")"
        );
        database.execSQL(
                "CREATE TABLE meta(" +
                        "k TEXT PRIMARY KEY NOT NULL," +
                        "v TEXT NOT NULL" +
                        ")"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        throw new IllegalStateException(
                "Unsupported key/value schema " + oldVersion + " -> " + newVersion
        );
    }

    public synchronized String get(String key) {
        requireKey(key);
        try (Cursor cursor = getReadableDatabase().query(
                "records",
                new String[]{"v"},
                "k=?",
                new String[]{key},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    public synchronized boolean contains(String key) {
        requireKey(key);
        try (Cursor cursor = getReadableDatabase().query(
                "records",
                new String[]{"k"},
                "k=?",
                new String[]{key},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst();
        }
    }

    public synchronized void put(String key, String value) {
        requireKey(key);
        requireValue(value);

        ContentValues row = record(key, value, System.currentTimeMillis());
        if (getWritableDatabase().insertWithOnConflict(
                "records",
                null,
                row,
                SQLiteDatabase.CONFLICT_REPLACE
        ) < 0) {
            throw new IllegalStateException("Failed to store value");
        }
    }

    public synchronized void putAll(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                requireKey(entry.getKey());
                requireValue(entry.getValue());

                if (database.insertWithOnConflict(
                        "records",
                        null,
                        record(entry.getKey(), entry.getValue(), now),
                        SQLiteDatabase.CONFLICT_REPLACE
                ) < 0) {
                    throw new IllegalStateException("Failed to store batch value");
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public synchronized int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM records",
                null
        )) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public synchronized Map<String, String> scan(String... prefixes) {
        Map<String, String> output = new LinkedHashMap<>();

        try (Cursor cursor = getReadableDatabase().query(
                "records",
                new String[]{"k", "v"},
                null,
                null,
                null,
                null,
                "k"
        )) {
            while (cursor.moveToNext()) {
                String key = cursor.getString(0);
                if (matchesPrefix(key, prefixes)) {
                    output.put(key, cursor.getString(1));
                }
            }
        }

        return output;
    }

    public synchronized void clear() {
        getWritableDatabase().delete("records", null, null);
    }

    public synchronized void remove(String key) {
        requireKey(key);
        getWritableDatabase().delete("records", "k=?", new String[]{key});
    }

    public synchronized void removeAll(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }

        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            for (String key : keys) {
                requireKey(key);
                database.delete("records", "k=?", new String[]{key});
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public synchronized void importMissing(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                requireKey(entry.getKey());
                requireValue(entry.getValue());

                database.insertWithOnConflict(
                        "records",
                        null,
                        record(entry.getKey(), entry.getValue(), now),
                        SQLiteDatabase.CONFLICT_IGNORE
                );
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    public synchronized String meta(String key) {
        requireKey(key);
        try (Cursor cursor = getReadableDatabase().query(
                "meta",
                new String[]{"v"},
                "k=?",
                new String[]{key},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    public synchronized void meta(String key, String value) {
        requireKey(key);
        requireValue(value);

        ContentValues row = new ContentValues();
        row.put("k", key);
        row.put("v", value);

        if (getWritableDatabase().insertWithOnConflict(
                "meta",
                null,
                row,
                SQLiteDatabase.CONFLICT_REPLACE
        ) < 0) {
            throw new IllegalStateException("Failed to store metadata");
        }
    }

    private static ContentValues record(String key, String value, long updatedAt) {
        ContentValues row = new ContentValues();
        row.put("k", key);
        row.put("v", value);
        row.put("updated", updatedAt);
        return row;
    }

    private static boolean matchesPrefix(String key, String[] prefixes) {
        if (prefixes == null || prefixes.length == 0) {
            return true;
        }

        for (String prefix : prefixes) {
            if (prefix != null && key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static Context applicationContext(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null");
        }
        return context.getApplicationContext();
    }

    private static String checkedName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Database name must be a simple file name");
        }
        return name;
    }

    private static void requireKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Key must not be empty");
        }
    }

    private static void requireValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
    }
}
