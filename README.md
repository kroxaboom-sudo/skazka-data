# Skazka Data

**RU:** Общие local-first компоненты хранения, синхронизации и резервного копирования.

**EN:** Shared local-first storage, sync and backup components.

## Что здесь будет / What belongs here

- storage contracts;
- локальное состояние;
- Sync Core;
- Backup Core;
- backup/restore metadata;
- проверка целостности;
- hooks для расписания и фонового запуска.

## Граница / Boundary

Публичный репозиторий содержит движки и contracts, но не пользовательские резервные копии, cloud credentials или production-конфигурацию хранилища.

## Основной принцип / Main principle

Приложение остаётся полезным офлайн. Сеть и сервер расширяют возможности, но не становятся единственной точкой хранения пользовательского состояния.
