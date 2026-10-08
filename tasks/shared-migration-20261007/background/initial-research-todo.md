# Ресёрч миграции Android-кода в shared

Этот список относится к завершённому первичному исследованию. Текущие задачи переноса находятся в [актуальном чек-листе](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-todo.md), подробное описание — в [сводном плане](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-roadmap.md).

- [x] Проверить платформенные цели, Gradle-модули и границы нативного SKIPI Core.
- [x] Инвентаризировать `shared:core` и сопоставить его с оставшейся логикой приложения.
- [x] Инвентаризировать `shared:app`, контракты, репозитории и переходное состояние Android.
- [x] Сопоставить общие Compose UI с Android/Desktop экранами по сценариям.
- [x] Проверить платформенные composition roots, runtime и OS-интеграции.
- [x] Свести результаты в карту миграции и предложить порядок следующих вертикальных срезов.
