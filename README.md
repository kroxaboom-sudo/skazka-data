# Skazka Data

> RU — основной язык · EN — required second language

## RU

Общие компоненты хранения, синхронизации и резервного копирования Skazka.

**Статус:** `0.1.0-preview` — три независимых слоя уже вынесены и проверены.

- `backup-core` — совместимый контейнер `.skb`: Recovery Key, PBKDF2-HMAC-SHA256, AES-GCM, AAD-заголовок, SHA-256 и контроль размера.
- `sync-core` — source-neutral трёхсторонняя политика конфликтов и планирование due-записей; сетевой transport конкретного источника остаётся адаптером приложения.
- `storage-android` — универсальное transactional SQLite key/value-хранилище с batch, prefix scan и meta-таблицей.
- `BackupData`, пользовательские settings/assets, remote backup endpoint и source-specific sync payload намеренно не входят в public core.

Проверено на HOSTKEY: backup self-test — PASS; sync self-test — PASS; `backup-core:build` — PASS; `sync-core:build` — PASS; `storage-android:assembleDebug` — PASS; `storage-android:lintDebug` — PASS.

## EN

Shared Skazka storage, synchronization, and backup building blocks.

**Status:** `0.1.0-preview` — three independent layers have been extracted and verified.

- `backup-core` — compatible `.skb` container: Recovery Key, PBKDF2-HMAC-SHA256, AES-GCM, authenticated header, SHA-256, and size checks.
- `sync-core` — source-neutral three-way conflict policy and due-record planning; concrete network transport remains an application adapter.
- `storage-android` — reusable transactional SQLite key/value storage with batch writes, prefix scans, and metadata.
- `BackupData`, user settings/assets, remote backup endpoints, and source-specific sync payloads are intentionally outside the public core.

Verified on HOSTKEY: backup self-test — PASS; sync self-test — PASS; `backup-core:build` — PASS; `sync-core:build` — PASS; `storage-android:assembleDebug` — PASS; `storage-android:lintDebug` — PASS.

## Coordinates / Координаты

- `com.kroxaboom.skazka:backup-core:0.1.0-preview`
- `com.kroxaboom.skazka:sync-core:0.1.0-preview`
- `com.kroxaboom.skazka:storage-android:0.1.0-preview`

See [DEVELOPMENT_RULES.md](DEVELOPMENT_RULES.md).

> A license will be selected before the first stable public release. Until then, publication of the source does not grant reuse rights.
