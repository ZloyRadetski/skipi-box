# План переноса оставшегося Android-кода в shared (30 октября 2026)

Оценка по импортам и размеру файлов `app/src/main/kotlin` (≈ 32 тыс. строк против ≈ 97 тыс. в `shared`).
Правила проекта: сначала тесты, затем код; семантика Android переносится точно; Android и Desktop
подключаются к одному коду, дубликат удаляется. Отчёт каждого среза различает выполненные и непроведённые проверки.

## Остаётся в Android (платформа, переносить нечего)
- VPN-сервис, HevTun, уведомления, перехват сетевых переходов: `SkipiVpnService`, `SkipiCoreRuntime`, `AndroidTunnelController`, `AndroidProxyEngine`, `ProxyTrafficStatsService`.
- Виджеты, плитка быстрых настроек, `MainActivity`, `SkipiApplication`, `Route`, `AppContent`, `App`.
- Room и SharedPreferences: `AndroidAppStateStore`, `ListEntities`, `AppSettingsPreferences`.
- Установка APK и загрузчик обновлений, WorkManager-шлюзы, `AndroidResourceFile*`, `AndroidSubscriptionFetcher`, `AndroidDiagnostics`, `AppBackground`, `AppHapticFeedback`, редакторы кода на Android-view, `Theme`.

## Этап 1. Чистая логика без Android-импортов (низкий риск)
| Файл | Строк | Действие |
|---|---|---|
| `ProxyServerUseCases` | 595 | Операции с каталогом серверов в `shared/app`; Android оставляет вызовы |
| `SubscriptionUpdateUseCase` | 336 | Оставить только вызов fetcher |
| `ProxyServerRuntimeUseCases` | 225 | Свести с общим `ProxyHomeLatencyRunner` |
| `AppBackupMapper` | 495 | В `shared/core` рядом с `features.backup`, один маппер для Android и Desktop |
| `AppStateJson`, `AppStateSlices` | 171, 177 | Сериализация и срезы состояния |
| `TrafficConfigState` | 214 | Только адаптер |

## Этап 2. Сборка конфига Xray (X01–X04), рискованный
`XrayConfig`, `VpnXrayConfig`, `XrayRoutingConfig`, `XrayDnsConfig`, `XrayOutboundPlanner`, `XrayExportConfig` и два Desktop-аналога.
1. Таблица различий Android/Desktop (инбаунды, DNS, sniffing, TUN, strict full-tunnel).
2. Общий каркас в `shared/core`, различия параметрами хоста.
3. Проверка на реальном ядре: Android до/после, Desktop с `SKIPI_CORE_INTEGRATION_DIR`.

## Этап 3. Хост-логика главной (H02–H03)
`ProxyServerListPage` (1378), `ProxyServerListActions` (650), `ProxyHomeAndroidInput` (258), `ProxyServerListGroups`, `ProxyServerEditResultHandler`.
- Общий обработчик `ProxyHomeEffect` в `shared/app` с портами: буфер обмена, уведомления, навигация, запуск сервиса.
- Android реализует порты; страница сокращается примерно до 400 строк; Desktop использует тот же обработчик.

## Этап 4. Настройки и редакторы
`SettingsBottomSheetsHost`, `SettingsDrafts`, `SettingsSheetState`; хост-обёртки `TrafficConfigPage`, `TrafficConfigEditorPages`, `TrafficConfigRuleEditor`, `ProxyServerPage`, `StrategyGroupMemberSelectorPage` — проверить остатки логики.

## Этап 5. Автоматизация сети и обновления
`NetworkAutomationEvaluator`/`Monitor`: общий цикл наблюдение → решение → действие (как у Desktop), в Android остаётся чтение сети. Обновления: общий слой уже есть; скачивание и установка остаются в Android.

## Этап 6. Очистка (N01, Z01, Z02)
Удаление заменённых путей после каждого этапа; сокращение `Main.kt` Desktop и `AppContent`; итоговое ревью и список ограничений.

## Порядок
Этап 1 → Этап 3 и Этап 2 (после 1); этапы 4 и 5 независимы; этап 6 после каждого этапа и в конце.

## Проверки на срез
Unit-тесты всех модулей (`shared:core`, `shared:app`, `shared:ui`, `desktopApp`, `app`), компиляция Android и Desktop, `assembleDebug`; для этапа 2 реальный запуск ядра и сверка JSON до/после.

## Статус
- [ ] Этап 1
- [ ] Этап 2
- [ ] Этап 3
- [ ] Этап 4
- [ ] Этап 5
- [ ] Этап 6
