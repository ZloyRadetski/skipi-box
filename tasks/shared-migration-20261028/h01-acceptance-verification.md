# Приёмка H01: группировка, сортировка и Home-проекция

Дата: 28 октября 2026 года. Проверенный HEAD: `981aaa8d3da5a9d84e3a119d26933b4aebcb9e2c` плюс два test-only исправления из этого отчёта.

## Результат

H01 принята. Общий алгоритм `groupProxyServersForHome` и общая Home-проекция используются Android и Desktop; host adapters только преобразуют свои сохранённые ID/модели и не создают независимую копию каталога. Production-код в ходе этой приёмки не изменялся. H02/H03 отдельно не принимались.

- Порядок AutoBalancer → включённые subscription/manual группы, All, пустые включённые группы, disabled-фильтрация и fallback выбора покрыты общими fixtures и Android compatibility tests.
- Config-owned strategy visibility (`ALWAYS`/`NEVER`/`ACTIVE_CONFIG`), членство All без config-owned strategies и исходный порядок серверов совпадают с Android-контрактом.
- Поиск использует canonical remarks. Name/Latency sorting использует raw remarks, включая flag prefix; testing/success/unavailable/negative/overflow buckets сверены с Android source oracle.
- Перестановка разрешена только в Default sort и применяется к последнему полному Desktop catalog с сохранением metadata, selection и high-water ID. Desktop `servers.json` и `settings.json` подтверждают сохранение пользовательского порядка и Android numeric sort mode при reload.

## Проверки и хронология

Focused baseline на текущем HEAD до любых изменений: общие `ProxyHomeGroupingProjectionTest`, `ProxyHomeSortModePersistenceTest`, `ProxyHomeStaleGroupSelectionContractTest`, Android `ProxyServerListGroupsCompatibilityTest`, `ProxyHomeAndroidInputTest`, `ProxyServerReorderTest` и Desktop `DesktopProxyHomeProjectionTest` — **GREEN**. Это приёмка уже существующего production-кода, поэтому искусственный RED не создавался.

Первый полный запуск выявил два Desktop failures:

1. `DesktopTrafficProfileXrayConfigTest` ещё требовал исключение для неизвестной policy, хотя Android `TrafficConfigRuntimePlanner` нормализует её в outbound tag. Исходный fixture сохранён; assertion синхронизирован с Android source и существующим common oracle. Production planner не менялся.
2. `DesktopTrafficConfigCollectionScreenIntegrationTest` выбирал первый общий `Add` и открывал Desktop profile menu вместо Global Groups editor. Harness теперь выбирает `Add` по строке заголовка секции; продуктовые ожидания draft/save не изменены.

Оба focused повтора после test-only исправлений — **2/2 GREEN**. Затем Desktop suite повторён полностью — **666/666 GREEN**, compile/debug APK GREEN.

Итоговая inventory по текущим JUnit XML: **3023/3023**, failures/errors/skips **0/0/0**:

- shared/core: 404 Desktop + 345 Android;
- shared/app: 369 Desktop + 361 Android;
- shared/ui: 124 Desktop + 124 Android;
- Android app: 630;
- Desktop app: 666.

Inventory объединяет первоначальный свежий запуск shared/Android targets и последующий свежий Desktop rerun после исправления двух тестов; это не заявляется как один физический прогон всех 3023 тестов.

## Ограничения

Real Core/FFM в этом срезе не запускался, native artifacts не пересобирались, физическая Android/Desktop UI QA не выполнялась. Исключённые HevTun build tasks означают использование существующих artifacts. Дальнейшая работа — H02 по одному tests-first сценарию; незакрытые H02/H03 этим отчётом не закрываются.
