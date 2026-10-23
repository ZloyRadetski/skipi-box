# P04: per-app editor screen source specification

Статус: общий переносимый редактор подключён к Android и Desktop; полная регрессия и финальный raster QA Per-app screen прошли. P04 остаётся открытой, пока тесты DNS validation и Resources route остаются draft; package inventory/runtime work относится к отдельному R03 scope. Этот документ фиксирует поведение Android и фактическую границу host/shared; новых функций он не добавляет.

## Поведение Android, которое переносим

Канонический экран — [`ProxyAppListPage.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPage.kt), открываемый из Android route с `trafficConfigId` в `app/src/main/kotlin/app/AppContent.kt`. Он читает целевой профиль по ID и применяет каждое изменение к актуальной записи этого профиля.

- **Режим меняется сразу.** `onModeChanged` обновляет `proxyAppListMode` у целевого профиля. Список выбранных приложений сохраняется. Не добавлять Save, Back-discard или draft.
- **Выбор меняется сразу.** Android передаёт `item.selectionKeys`; `updateProxyAppListSelection` добавляет или удаляет эти ключи и сохраняет неизвестные/legacy значения согласно общему helper. Для shared UID один элемент может передать несколько ключей. В `shared/app` уже есть контроллерные операции `setProxyAppListMode` и `setProxyAppListSelection`, которые обновляют последний профиль через `TrafficConfigEditorProfileStore`.
- **Clipboard остаётся тем же форматом и алгоритмом.** `ProxyAppListPage` экспортирует `encodeProxyAppListForClipboard` с текущим режимом и выбранными ключами. Импорт читает plain text, вызывает `decodeProxyAppListFromClipboard` с `currentUserId = selectedUserId ?: 0` и `selfPackageName` Android-приложения, а затем предлагает существующие `Replace` и `Merge`. `0` — уже имеющийся fallback при отсутствии выбранного Android user; Desktop должен передать отсутствие user, а общий экран сохраняет этот fallback. Имя собственного пакета остаётся host input.
- **Импорт применяет данные к последнему состоянию профиля.** `applyProxyAppListClipboardImport` использует импортированный mode, если он присутствует, иначе оставляет текущий. `Replace` заменяет selection импортированным списком; `Merge` объединяет списки с существующим distinct/trim поведением. Android показывает число импортированных элементов после `updateAppState`: для Replace это размер результата, для Merge — `max(result.size - current.size, 0)`. Если целевой профиль отсутствует, Android оставляет state как есть, закрывает диалог и показывает `Импортировано приложений: 0`; общий UI сохраняет это поведение для `Missing`. Если update бросает исключение без handler, confirm прерывается до закрытия и notice; когда store/controller перехватывает исключение и возвращает `Failed`, общий UI не показывает success и не закрывает диалог. Не добавлять отдельную Missing-защиту.
- **Ошибки разбора и clipboard** используют существующие Android-сообщения для пустого буфера, неизвестного формата, отсутствия валидных записей, некорректной записи/user ID и неподдерживаемого mode.

## Минимальная shared UI граница

Общий экран `SkipiTrafficConfigProxyAppListEditorScreen` в `shared/ui` владеет presentation и поведением, уже общими по источнику: заголовком, меню mode, меню Import/Export, codec clipboard, ожиданием подтверждения импорта, существующим `ImportModeDialog`, вызовом immediate-операций контроллера и отображением существующих localized notice/error текстов. Для импорта он вызывает controller с ID целевого профиля и не хранит редактируемый draft.

Текущий API компонента:

```kotlin
@Composable
fun SkipiTrafficConfigProxyAppListEditorScreen(
    profile: TrafficConfigState,
    controller: TrafficConfigEditorController,
    actionScope: CoroutineScope,
    padding: PaddingValues,
    isWideScreen: Boolean,
    readClipboardText: suspend () -> String?,
    writeClipboardText: suspend (String) -> Unit,
    currentUserId: Int?,
    selfPackageName: String,
    onNotice: suspend (String) -> Unit,
    onBack: (() -> Unit)? = null,
    showSystemApps: Boolean = false,
    onToggleSystemApps: (() -> Unit)? = null,
    headerContent: @Composable () -> Unit = {},
    appContent: @Composable (
        contentPadding: PaddingValues,
        listPadding: PaddingValues,
        scrollBehavior: ScrollBehavior,
        selectedKeys: Set<String>,
        modeIndex: Int,
        onSelectionChange: (List<String>, Boolean) -> Unit,
    ) -> Unit,
)
```

Это фактический API общего экрана. Он передаёт `profile.id` контроллеру; mode/selection используют immediate controller operations. Для импорта `Saved` показывает Android count и закрывает диалог; `Missing` закрывает диалог и показывает count `0`, как Android; `Failed` не показывает success и оставляет диалог открытым. Если handler отсутствует, исключение не проглатывается: confirm обрывается до success notice и сброса pending import. Экран получает обязательный `actionScope` от host и использует его для clipboard import/export и notice; Android создаёт его до profile guard, Desktop передаёт свой существующий root scope. Это сохраняет исходный lifecycle действий. Проверка `Missing` прошла; перенос scope является source-lifecycle alignment, а не исправлением воспроизведённого UI-дефекта. Не добавлять retries, CAS, нормализацию или новую конфликтную политику.

`headerContent` и `appContent` — host slots. Android оставляет в них текущие SearchBar, user tabs, package pager/list, загрузку, pull-to-refresh, platform labels/icons и selection projection. `onToggleSystemApps` может быть передан Android; на Desktop этот пункт меню отсутствует. Общий экран не принимает Android package/user модели и не требует перечисления пакетов.

Переиспользовать `SkipiTrafficConfigFullScreenScaffold`, расширив его nullable `onBack`, `subtitle: String = ""` и необязательным `topBarBottomContent` slot, чтобы Android мог сохранить текущие search/user tabs в top bar. Per-app передаёт в subtitle локализованную подпись активного mode, как Android `ProxyAppListTopBar`. На Android не добавлять видимую кнопку Back: `onBack` отсутствует, существующий `ProxyAppListTopBar` её не показывает; Android system-back и navigation остаются host responsibility. На Desktop передать `onBack`, возвращающий пользователя из Per-app в Basics. Для Android layout screen должен вычислять content padding штатно и использовать `pageListPadding(contentPadding)`; не использовать form-specific list padding scaffold (`+12.dp` по краям и `+8.dp` top inset), чтобы сохранить текущую Android геометрию списка.

## Desktop host и clipboard

`DesktopConfigsScreen` уже принимает `copyTextToClipboard: (String) -> Unit` с настоящим AWT default и уже создаёт `TrafficConfigEditorController`, подключённый к `DesktopTrafficConfigEditorProfileStore`. Поэтому экспорт использует существующий callback, а общий controller может сохранить selection/mode/import в существующий профильный store. Отдельный Desktop Per-app store не нужен.

`DesktopConfigsScreen` получил optional host callback `readPerAppClipboardText: suspend () -> String?` с default, читающий plain text через AWT system clipboard (`Toolkit` / `DataFlavor.stringFlavor`). Он используется только общим Per-app editor. Существующий toolbar import и его `copyTextToClipboard` wiring не менялись.

Desktop route/section добавлен в существующую visual editor navigation и подключён к общему screen. Desktop передаёт `currentUserId = null` и `selfPackageName = ""`: у Desktop нет Android package identity, и выдумывать package name для фильтра self-package нельзя. Общий codec сохраняет текущий Android fallback `null -> 0`, но Desktop не пытается определить или имитировать Android user и не фильтрует реальные Android IDs по host identity. В host body отображается локализованный `configs_per_app_desktop_notice`: inventory Android-приложений и применение VPN по приложениям недоступны на Desktop, профиль при этом можно редактировать.

На Desktop нет fake apps, списка Android users, свободного text editor для package IDs, новой выдачи/summary уже выбранных IDs или runtime enforcement. Пользователь может менять mode и использовать clipboard import/export для существующих сохранённых данных. Локализованный `configs_per_app_desktop_notice` добавлен в основной и русский resource-файлы рядом с английским default и русским переводом.

## Existing shared UI и ресурсы

Повторно использовать существующие common resources в `shared/ui/src/commonMain/composeResources/values/strings.xml` и `values-ru/strings.xml`: `proxy_app_list_title`, mode labels, `proxy_app_list_more_actions`, общие `common_import_from_clipboard` / `common_export_to_clipboard`, `common_copied`, `common_clipboard_empty`, `common_clipboard_unsupported_format`, `common_merge_import`, `common_replace_existing`, а также import/error/count messages. Не плодить вторые ресурсы с тем же смыслом.

`shared/ui/src/commonMain/kotlin/app/skipi/ui/components/ImportModeDialog.kt` отображает `ClipboardImportMode.Merge` и `.Replace`; общий экран передаёт существующие локализованные title/message/button strings. `app/src/main/kotlin/features/proxy/app/ProxyAppListComponents.kt` фиксирует source composition: `ProxyAppListUserSpaceTabs`, package row, icon loading и Android search/list projection остаются host-owned. `AdaptiveTopAppBar` в `shared/ui/.../layout/PageLayout.kt` принимает `subtitle`, `actions` и `bottomContent`; общий `SkipiTrafficConfigFullScreenScaffold` теперь принимает `subtitle` и необязательный `topBarBottomContent`, сохраняя subtitle текущего режима и Android header slot. Per-app screen вычисляет Android list padding как `pageListPadding(contentPadding)`, игнорируя form-specific list padding scaffold (`+12.dp` и `+8.dp`).

## Источники и уже существующая общая логика

- Android presentation, latest target profile, clipboard callbacks, Android `selectedUserId ?: 0`, immediate mode/selection и import success count: [`ProxyAppListPage.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPage.kt).
- Android menu, mode selector, user tabs, search, package card/icon: [`ProxyAppListComponents.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListComponents.kt).
- Android package/user state and enumeration are host data: [`ProxyAppListPageState.kt`](../../app/src/main/kotlin/features/proxy/app/ProxyAppListPageState.kt) and [`ProxyAppListData.kt`](../../app/src/main/kotlin/features/proxy/app/model/ProxyAppListData.kt).
- Clipboard codec and Replace/Merge behavior: [`ProxyAppListClipboardCodec.kt`](../../shared/core/src/commonMain/kotlin/features/proxy/app/usecase/ProxyAppListClipboardCodec.kt).
- Shared-UID and unknown selection-key operations: [`ProxyAppListSelection.kt`](../../shared/core/src/commonMain/kotlin/features/proxy/app/ProxyAppListSelection.kt).
- Latest-profile editor/store seam and immediate Per-app controller operations: [`TrafficConfigEditorController.kt`](../../shared/app/src/commonMain/kotlin/app/skipi/app/config/TrafficConfigEditorController.kt).
- Desktop latest-profile store adapter: [`DesktopTrafficConfigEditorProfileStore.kt`](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTrafficConfigEditorProfileStore.kt).
- Desktop route, `copyTextToClipboard` callback and visual-editor section routing: [`DesktopConfigsScreen.kt`](../../desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt).
- Existing import dialog, resources and topbar slots: [`ImportModeDialog.kt`](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/components/ImportModeDialog.kt), `shared/ui/src/commonMain/composeResources/values/strings.xml`, `values-ru/strings.xml`, [`PageLayout.kt`](../../shared/ui/src/commonMain/kotlin/app/skipi/ui/layout/PageLayout.kt).

## Текущий порядок и статус проверок

Восемь Desktop actual-screen сценариев описали отсутствующий Per-app route и поведение до UI production-кода. После callback/resource bootstrap чистый behavior RED завершился 8/8 ожидаемыми отказами на отсутствующем route; SHA-256 исходника теста был `98fdc89fbe349749922e0c7c0942488fdddeb20d01bfb4339689b96e4d98df07`. Детальная последовательность ранних harness/compile попыток и их отчёты сохранены в [verification report](p04-per-app-screen-verification.md).

Первый scrolled behavior run завершился за 33 секунды: 7/8 тестов прошли, включая `Missing` с notice `0`, позднее Merge, обе ветки Failed, export и шесть viewport captures. В восьмом тесте mode уже сохранялся сразу, но проверка Back не нашла карточку ниже viewport. Диагностический запуск за 13 секунд подтвердил, что Basics отображается правильно, а карточка Per-app находится ниже viewport. Helper теперь прокручивает Basics после Back и дополнительно проверяет `targetName`. Итоговый pre-build SHA-256 исходника теста — `225d3b982267fe8643d3bd0e7a58f546cb48cb35013ddae1f3d5bb902da47756`; отличие от RED hash связано с исправлением harness и дополнительной проверкой навигации, а не с ослаблением product expectations.

Свежая полная регрессия завершилась за 117 секунд: **2,822/2,822 GREEN**, без failures, errors и skips по восьми targets. `:desktopApp:compileKotlin` и `:app:assembleDebug` прошли; с заданным `SKIPI_CORE_INTEGRATION_DIR` выполнились все семь native Core tests. Per-app test прошёл 8/8. Финальные шесть PNG byte-identical к scrolled gate, который root просмотрел; hashes зафиксированы в [counts.json](../../outputs/p04-per-app-screen-full-green-reports-20261023/counts.json). Pre-build и post-build test source SHA совпадают; provenance записан в [test-source-provenance.json](../../outputs/p04-per-app-screen-full-green-reports-20261023/test-source-provenance.json). Подробная команда и counts — в [verification report](p04-per-app-screen-verification.md). P04 остаётся открытой для DNS validation и Resources route test work; package enumeration, search/filter projection и OS integration остаются отдельным R03 scope.
