# R01 — исследование Desktop RoutingRepository

Статус на 23 октября 2026: R01 остаётся незавершённой реализацией, а не только пробелом приёмки. Текущий малый срез — подключить уже сохраняемые Desktop routing settings к существующему `RoutingRepository` и `SharedApplicationStore`. Этот срез не подключает routing UI и не закрывает R01.

## Канонический контракт и уже общие части

План R01 требует единую реализацию create/edit/remove/reorder/enable/import/export, сохранение ссылок на server/strategy/resource при изменениях каталога и подключение существующего переносимого routing UI в Desktop (`tasks/plan.md`, строки 585–599). Android `RoutingPage` сейчас читает `AppState` и там же выполняет callbacks для списка, default outbound, domain strategy, удаления и импорта (`app/src/main/kotlin/features/routing/RoutingPage.kt`, строки 102–293). `RouteRuleEditorPage` также читает и меняет `AppState`; новый ID берётся из `nextRouteRuleId`, edit сохраняет прежний ID и порядок через `upsertRouteRule` (`app/src/main/kotlin/features/routing/RouteRuleComponents.kt`, строки 44–95).

Уже общими являются чистые `upsert/remove/setEnabled/toggle/move` операции (`shared/app/src/commonMain/kotlin/app/skipi/app/routing/RouteRuleCollectionOperations.kt`) и clipboard codec/import semantics (`shared/core/src/commonMain/kotlin/features/routing/usecase/RouteRuleClipboardCodec.kt`). Список и форма лежат в shared UI (`shared/ui/src/commonMain/kotlin/app/skipi/ui/routing/SkipiRoutingRulesList.kt`, `SkipiRouteRuleEditorForm.kt`). Отдельных tests, проходящих через Android `RoutingPage`/editor entry point, в текущем дереве не найдено; существующие тесты проверяют общие операции и codec.

Стабильная граница уже задана `RoutingRepository.state: StateFlow<RoutingConfigRecord>` и `update(transform)` (`shared/app/src/commonMain/kotlin/app/skipi/app/repository/RepositoryContracts.kt`, строки 81–85). `SharedApplicationStore` агрегирует `repositories.routing?.state`, а action `UpdateRouting` вызывает обязательный repository и завершится ошибкой, если он не подключён (`shared/app/src/commonMain/kotlin/app/skipi/app/store/SharedApplicationStore.kt`, строки 136 и 253–255). Поэтому production-изменение этого среза не требует нового common API.

Android adapter показывает точную запись: перенести `domainStrategy`, `defaultOutboundTag`, `rules` и удержать счётчик `nextRouteRuleId` не ниже текущего и `max(rule.id) + 1` (`app/src/main/kotlin/data/repository/AndroidAppRepositories.kt`, строки 229–247). `RoutingConfigRecord` счётчик не содержит, поэтому Desktop adapter обязан сохранить backing `DesktopAppSettings.nextRouteRuleId` при обновлении.

## Текущий Desktop пробел

`DesktopAppSettings` уже имеет routing-поля и счётчик, но комментарий помечает их как неактивные legacy values (`desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsLibrary.kt`, строки 104–108). JSON-тест уже доказывает round-trip этих значений и defaults старого файла (`DesktopSettingsLibraryTest.kt`, строки 257–289); его не надо дублировать.

`DesktopSettingsRepository.toPersistedSettings` намеренно не отображает эти поля в общий settings record (`desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt`, строки 466–508). `createDesktopSharedApplication` сейчас создаёт `AppRepositories` без `routing`, а `Main` вызывает factory без routing-параметра (`DesktopApplicationRepositories.kt`, строки 391–415; `desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt`, строки 297–304). Значит shared store получает пустой routing snapshot, хотя JSON поля существуют.

Важно отличать это от P04: Desktop profile Routing/Rules editor — сценарий внутри Shadowrocket-профиля. Его успешные проверки в `tasks/shared-migration-20261020/p04-rules-verification.md` не подключают глобальный Android `RoutingPage`; отчёт явно оставляет R01 global routing открытой. Вне репозитория останется также отдельная проверка генерации Desktop runtime routing: `DesktopSelectedServerXrayConfigFactory` пока передаёт planner `routeRules = emptyList()` и `defaultRouteOutboundTag = PROXY` (`desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSelectedServerXrayConfigFactory.kt`, строки 49–61). Это не входит в repository-only срез и не позволяет считать R01 закрытой после него.

## Предлагаемый API и узкая граница

Добавить host adapter `DesktopRoutingRepository`, по аналогии с Desktop repository constructor pattern: `initialSettings: DesktopAppSettings`, `readSettings: () -> DesktopAppSettings`, `saveSettings: (DesktopAppSettings) -> Result<Unit>`, `publishSettings: (DesktopAppSettings) -> Unit`. Его публичный контракт — существующий `RoutingRepository`; initial `StateFlow` маппит только `routeDomainStrategy`, `defaultRouteOutboundTag`, `routeRules` в `RoutingConfigRecord`.

`update` должен прочитать settings через `readSettings()` непосредственно перед применением transform, скопировать в эту свежую запись только три routing-поля, сохранить счётчик через `maxOf(current.nextRouteRuleId, (updated.rules.maxOfOrNull { it.id } ?: 0) + 1)`, вызвать `saveSettings`, а затем `publishSettings` и обновить собственный flow. Ошибка `saveSettings` выбрасывается как есть; до успешного сохранения публикация и flow не меняются. Это следует Android adapter и Desktop `save → publish → refresh` pattern, не добавляя новых retry/CAS policies.

`createDesktopSharedApplication` получает обязательный `routing: DesktopRoutingRepository`, прокидывает тот же instance в `AppRepositories.routing` и предоставляет его в `DesktopSharedApplication.routing` по аналогии с остальными repository properties. Composition root `Main` создаёт adapter из тех же `desktopSettings`, `DesktopSettingsLibraries.saveDefault` и publish callback, которые уже используются `DesktopSettingsRepository`.

## TESTS ONLY gate

Draft находится в `outputs/drafts/DesktopRoutingRepositoryTest.kt.draft`; короткая спецификация — `outputs/drafts/DesktopRoutingRepositoryTest.spec.md`. Он проверяет только repository contract, persisted settings callbacks и factory → shared-store route. Сначала тесты следует отревьюить и внести в `src` только после отдельного разрешения root; затем выполнить ожидаемый API RED. Production пока не начинается.

Случаи используют настоящий временный `settings.json` через `DesktopSettingsLibraries.save/load`:

1. После создания adapter внешнее изменение файла должно быть прочитано при `update`. Transform получает актуальный `RoutingConfigRecord`; новое сохранение меняет routing поля, сохраняет актуальные остальные Desktop preferences, удерживает высокий `nextRouteRuleId`, а при большем новом rule ID увеличивает счётчик до `id + 1`.
2. При отказе `saveSettings` исключение выходит к caller, исходный JSON остаётся прежним, `RoutingRepository.state` и published Desktop settings остаются неизменными.
3. Desktop factory подключает тот же repository в `AppRepositories`/shared store; `SharedApplicationAction.UpdateRouting` сохраняется через adapter, отражается в `store.state.routing` и JSON.

Не включены UI/navigation/editor, system clipboard, installed-app enumeration, runtime enforcement, generated Xray routing и profile-specific Rules. Внешняя concurrency policy не вводится.

Это исследование и drafts-only шаг: файлы `src` не изменялись, Gradle/wrapper/build/test/git-команды не запускались.
