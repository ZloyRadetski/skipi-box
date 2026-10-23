# R01 — исследование Desktop RoutingRepository

Статус на 23 октября 2026: repository-only foundation подключён и прошёл focused verification. Shared store на Desktop теперь получает persisted global routing state и может передать его обновления adapter-у. R01 остаётся открытой: global Routing UI/editor, полный CRUD/import-export flow и runtime planner пока не подключены. Этот срез сам по себе не закрывает R01.

## Канонический контракт и уже общие части

План R01 требует свести routing CRUD/editor/import-export к общим сценариям, сохранить связи на server/strategy/resource при изменениях каталога и подключить существующий переносимый routing UI в Desktop (`tasks/plan.md`, R01). Android `RoutingPage` сейчас читает `AppState` и выполняет callbacks списка, default outbound, domain strategy, удаления и импорта (`app/src/main/kotlin/features/routing/RoutingPage.kt`). `RouteRuleEditorPage` также читает и меняет `AppState`; новый ID берётся из `nextRouteRuleId`, а edit сохраняет прежний ID и порядок через `upsertRouteRule` (`app/src/main/kotlin/features/routing/RouteRuleComponents.kt`).

Общие pure operations `upsert/remove/setEnabled/toggle/move` уже находятся в `shared/app/src/commonMain/kotlin/app/skipi/app/routing/RouteRuleCollectionOperations.kt`; clipboard codec/import semantics — в `shared/core/src/commonMain/kotlin/features/routing/usecase/RouteRuleClipboardCodec.kt`. Список и форма уже есть в `shared/ui/src/commonMain/kotlin/app/skipi/ui/routing/SkipiRoutingRulesList.kt` и `SkipiRouteRuleEditorForm.kt`. Наличие этих частей не означает, что Android RoutingPage или глобальная Desktop навигация уже используют их end-to-end.

Стабильный общий интерфейс уже задан `RoutingRepository.state: StateFlow<RoutingConfigRecord>` и `update(transform)` (`shared/app/src/commonMain/kotlin/app/skipi/app/repository/RepositoryContracts.kt`). `AppRepositories.routing` пока nullable для staged adoption. `SharedApplicationStore` агрегирует подключённый routing state, а `UpdateRouting` требует настроенный repository и вызывает его `update`. Desktop реализует существующий `RoutingRepository`, без изменений common API или Android adapter.

Android adapter задаёт точную counter semantics: `nextRouteRuleId = maxOf(current.nextRouteRuleId, (updated.rules.maxOfOrNull { it.id } ?: 0) + 1)` в `AndroidRoutingRepository.update` (`app/src/main/kotlin/data/repository/AndroidAppRepositories.kt`). Общий `RoutingConfigRecord` не содержит counter, поэтому Desktop сохраняет его в backing `DesktopAppSettings`.

Это repository-контракт, а не полная семантика существующей глобальной Android страницы. `RoutingPage` обходит repository и вызывает app-state callbacks напрямую; clipboard Replace назначает импортированным правилам ID с 1 и записывает counter, рассчитанный только по импортированным правилам. Текущий Desktop adapter намеренно повторяет `AndroidRoutingRepository.update`; будущий global UI/controller срез должен отдельно сохранить page-level Replace поведение и не полагаться только на high-water update или общий record без counter. См. [source contract audit](../../outputs/drafts/r01-global-routing-source-contract.md). Это не требует изменения repository slice.

## Готовая Desktop repository boundary

`DesktopAppSettings` уже хранит `routeDomainStrategy`, `defaultRouteOutboundTag`, `routeRules` и `nextRouteRuleId`. `DesktopSettingsLibraries` уже читает/сохраняет эти поля в `settings.json`, а `DesktopSettingsLibraryTest` проверяет round-trip и defaults старого файла. `DesktopRoutingRepository` теперь отображает три routing-поля в `RoutingConfigRecord`, не дублируя JSON persistence.

Каждый `update` читает актуальный `DesktopAppSettings` непосредственно перед transform, преобразует routing-поля в `RoutingConfigRecord`, применяет transform и копирует в свежий settings snapshot только domain strategy, default outbound, rules и сохранённый high-water counter. Затем порядок остаётся `saveSettings(...).getOrThrow()` → `publishSettings(...)` → собственный flow. Ошибка сохранения выходит к caller без publish или изменения repository flow. Adapter использует локальный lock как остальные Desktop repositories; он не добавляет retry или CAS, не нормализует route values и не вводит новые defaults.

`refresh()` читает текущее значение `readSettings()` и обновляет только routing `StateFlow`; оно не сохраняет и не публикует settings. `Main` вызывает его в существующем `LaunchedEffect`, который реагирует на `desktopSettings` и обновляет другие Desktop repository snapshots. Так shared store получает внешние изменения настроек, сохраняя Android-подобную наблюдаемую state boundary.

`createDesktopSharedApplication` теперь требует `routing: DesktopRoutingRepository`, передаёт тот же instance в `AppRepositories.routing` и выставляет его как `DesktopSharedApplication.routing`. `Main` создаёт adapter через существующие `desktopSettings`, `DesktopSettingsLibraries.saveDefault` и publish callback. В тестовом fixture `DesktopProxyDeletionActionTest` передан отдельный adapter на собственном локальном settings value. Общий `DesktopSettingsRepository.toPersistedSettings` по-прежнему не поглощает routing fields: ими владеет отдельный routing adapter.

## Test-first gate и результат

Четыре test cases проверяют API и поведение через временный реальный `settings.json`, Desktop factory и shared store:

1. **Latest settings, preservation, counter.** После внешнего изменения файла `update` даёт transform актуальный routing record, сохраняет новые routing-поля и другие последние Desktop settings, удерживает большой counter или поднимает его выше добавленного rule ID.
2. **Read-only refresh.** После внешнего изменения routing fields `refresh()` меняет routing flow, но оставляет JSON, published settings и counts save/publish callbacks без изменений.
3. **Failed save.** Ошибка `saveSettings` выходит к caller; JSON, опубликованные settings и routing flow остаются прежними.
4. **Factory/shared-store path.** Обновление через `SharedApplicationAction.UpdateRouting` проходит через настроенный Desktop repository, попадает в `store.state.routing` и persisted JSON; factory и bundle содержат тот же repository instance.

До production ожиданий missing API RED завершился за 21 секунду: четыре запланированных case не исполнились, поскольку compilation остановилась на отсутствующих adapter/factory `routing`/`refresh` API и связанных unresolved references. Тестовый источник имел SHA-256 `fb60f9c746cbd1da3dbff828453a23ecabab4f7561adb0651a2e16708dca94dc`; этот же SHA записан в GREEN counts. Артефакты: [RED result](../../outputs/r01-desktop-routing-repository-api-red-reports-20261023/result.json) и [compiler diagnostics](../../outputs/r01-desktop-routing-repository-api-red-reports-20261023/compiler-errors.txt).

После подключения production adapter четыре routing cases, 12 settings-library cases и четыре proxy-deletion regressions прошли **20/20** за 12 секунд; `:desktopApp:compileKotlin` также прошёл. [Verification report](r01-desktop-routing-repository-verification.md) содержит точные команды и scope. Это focused Desktop run, не новая полная regression inventory.

## Остаётся вне этого среза

Desktop profile Routing/Rules editor — сценарий внутри Shadowrocket-профиля. Его успешные P04 проверки не подключают глобальный Android `RoutingPage`. Следующий global UI/controller срез должен учитывать отдельную clipboard Replace семантику Android страницы, описанную выше; repo-only high-water формулы недостаточно для этого сценария. Также `DesktopSelectedServerXrayConfigFactory` пока передаёт planner `routeRules = emptyList()` и `defaultRouteOutboundTag = PROXY`; runtime connection остаётся отдельной задачей. Поэтому global Routing UI/editor/navigation, импорт/экспорт, runtime planning/enforcement и полные R01 критерии остаются pending.

Этот repository slice не меняет Android или common code, не запускает native Core/Android/full-suite/APK gate и не заявляет полную R01 acceptance.
