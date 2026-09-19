# Development Rules / Правила разработки

## RU

- Основной бренд и префикс — **Skazka**. Предыдущее имя не используется для новых сущностей; compatibility identifiers из старых версий допускаются только там, где их нельзя безопасно изменить без отдельной миграции.
- Русский — основной язык, английский — обязательный второй. Это относится к README, документации, release notes и комментариям в исходном коде.
- Комментарии в коде: сначала RU, затем EN. Комментарий объясняет причину решения, ограничения, fallback, совместимость, безопасность или другое неочевидное поведение. Очевидный код не комментируем ради количества.
- Сам код должен выглядеть как работа опытного человека-разработчика: без шаблонной машинной генерации, бессмысленных helper/wrapper/factory-слоёв и абстракций ради абстракций.
- Простое решение предпочтительнее сложного, если оно выполняет ту же задачу и остаётся понятным.
- Публичный код не должен содержать секреты, credentials, signing keys, private endpoints, внутренние адреса или production-конфигурацию.
- Переиспользуемые безопасные компоненты по умолчанию публикуются открыто; инфраструктура и чувствительная административная логика остаются закрытыми.

## EN

- The canonical brand and prefix are **Skazka**. The previous name is not used for new entities; compatibility identifiers from older versions remain only where changing them safely requires a dedicated migration.
- Russian is the primary language; English is the required secondary language. This applies to README files, documentation, release notes, and source-code comments.
- Code comments use RU first, then EN. A comment should explain the reason for a decision, constraints, fallback behavior, compatibility, security, or another non-obvious detail. Do not comment obvious code just to increase comment density.
- The source code itself should read like work written by an experienced human developer: no templated machine-style output, pointless helper/wrapper/factory layers, or abstractions for their own sake.
- Prefer the simpler solution when it solves the same problem and remains clear.
- Public code must not contain secrets, credentials, signing keys, private endpoints, internal addresses, or production configuration.
- Safe reusable components are public by default; infrastructure and sensitive administrative logic remain private.
