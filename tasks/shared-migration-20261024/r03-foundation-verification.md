# R03 — проекция списка и VPN-policy: первый срез

Статус на 24 октября: первый срез R03 проверен, карточка в работе. Исходная точка `77f217c`; S03 закрыта. Этот отчёт относится к чистой проекции данных, сохранению per-app полей и synthetic Android package/Builder smoke. Общий list UI остаётся следующим срезом; R03 пока не принимается.

## Исходное поведение

Android открывает список приложений из конкретного профиля. Общие codec/selection/controller и экран режима/clipboard уже подключены к Android и Desktop в P04. Нового глобального редактора нет. Android получает catalog/user facts, иконки и применяет VpnService policy; Desktop сохраняет и редактирует переносимые ID без придуманного OS enforcement.

Проекция списка переносит fallback имени, поиск по label/package, user-space partition/fallback tabs, shared-UID selection keys/counts, fixed-user remap и selected-first refresh order. Policy переносит нормализацию режима, strict/global override и фильтрацию user-prefixed/legacy ID с исходным порядком parse → trim → distinct. Active-profile overlay и OS user ID остаются Android wiring.

Source-аудиты до добавления тестов: [список](../../outputs/drafts/r03-android-selection-source-audit.md), [policy](../../outputs/drafts/r03-native-policy-test-audit.md), [Desktop/storage](../../outputs/drafts/r03-desktop-state-source-audit.md). Актуальные контракты следующего среза: [projection](../../outputs/drafts/r03-projection-spec.md), [policy](../../outputs/drafts/r03-vpn-policy-spec.md), [preservation](../../outputs/drafts/r03-per-app-preservation-spec.md). Ранние аудиты описывают отсутствие новых тестов на момент исследования.

## Тесты до production

1. Написаны пять Android source-oracle тестов проекции и шесть policy/runtime-overlay тестов. На неизменённом Android production все **11/11 GREEN**.
2. Новый Desktop test реального editor/store на временном `configs.json`: **1/1 GREEN**. Он проверяет non-active profile, opaque/legacy/other-user IDs, selected sibling и library envelope. Реальные данные не используются.
3. Написаны matching common projection/policy tests, а также profile-document и backup round-trip. Первый common compile обнаружил отсутствующие API и отдельную ошибку тестового fixture: `withSkipiSettingsInRawConfig()` возвращает `TrafficConfigState`; тесту нужен `.rawConfig`. Исправлен только receiver/возвращаемое значение, все ожидания сохранены.
4. После исправления fixture повторно получен чистый **API compile RED, 1 s**, только в двух тестовых классах отсутствующих projection/policy API. Поведенческие common tests ещё не исполнялись; это не behavioral RED.

Baseline Android + Desktop занял 29 s. Логи, XML, initial/final SHA и correction record: [outputs/r03-foundation-reports-20261024](../../outputs/r03-foundation-reports-20261024). Все final-RED test hashes заморожены до production.

## Разрешённый перенос

Plain records и алгоритмы идут в shared/core. Android оставляет загрузку/record conversion, `Dispatchers.Default` вокруг prepare/tabs, Coil icon request и реальные providers. Нынешний `AndroidUserSpace(id,name)` — простой record; source alias на такой же portable record сохраняет конструктор и потребителей без второй проекции данных. У common Kotlin/JVM файла отдельное имя `ProxyAppListProjection.kt`, чтобы не столкнуться с native facade `ProxyAppListData.kt`.

Android policy adapter передаёт mode/keys/current user/strict в общий projector. `VpnService.Builder`, package admission и обработка `NameNotFoundException` остаются Android. Никакие новые defaults, whitelist semantics или Desktop application routing не вводятся.

## GREEN после переноса

Один focused прогон после source review: **92/92 GREEN, 33 s**, failures/errors/skips 0. shared/core Desktop23 + Android23, Android app35, Desktop11; Desktop compile GREEN. В нём выполнены projection/policy/selection/clipboard, profile-document/backup, StrictFullTunnel, temporary Desktop store и существующие восемь actual P04 Per-App UI cases.

Все десять новых тестовых файлов сохранили final-RED/pre-GREEN SHA. Зафиксированы [counts](../../outputs/r03-foundation-reports-20261024/green-counts.json), XML по четырём targets, log, before/after hashes. Android baseline11 и Desktop baseline1 выше — отдельный исходный прогон, не прибавленный к 92.

До этого GREEN добавлены и заморожены восемь synthetic native smoke cases: PackageRepository2, Builder5, non-default no-profile fallback1. PackageManager получает только Robolectric fixture records. Test-only shadow записывает реальные вызовы private application-policy метода; reflection не потребовала production seam. Проверены self exclusion, trim/dedup, allow/deny, stale `NameNotFoundException` и propagation `SecurityException`. В whitelist учитывается именно число успешных Builder adds: all-stale → 0 → installed deny fallback. Исходный `SkipiVpnService`/repository не изменены; `establish()` не вызывается. [Native smoke boundary](../../outputs/drafts/r03-native-adapter-smoke.md).

Перенесённые source paths: два common core файла; Android `ProxyAppListData.kt`, `VpnApplicationPolicy.kt` и plain user-space alias. OS I/O, dispatcher boundaries и native icon request сохранены; дублирующиеся чистые реализации удалены. Root сравнил source diff с Android исходником. Общий presentation/UI — следующий tests-first срез. Последний полный межмодульный baseline остаётся S03: 2906 GREEN и real Core9/9; scoped gate его не заменяет. APK/full suites после этого среза отдельно не запускались.

Независимое review также подтвердило отсутствие semantic drift и Android imports в common. Plain core records больше не несут Compose `@Immutable`: Compose dependency в core не добавлена, возможное отличие стабильности/оптимизации рекомпозиций не заявляется измеренным. `AndroidUserSpace` alias сохраняет Kotlin source API; Java/binary compatibility старого JVM класса не обещается (в репозитории найдены только Kotlin потребители).

После GREEN92 найдено уточнение test isolation: Builder smoke теперь явно использует `android.app.Application`, чтобы manifest `SkipiApplication` не запускал runtime initializers. Все Builder assertions сохранены; новая SHA и причина записаны в `native-final-fixtures.json`. Дополнительно написаны два synthetic теста реального `AndroidPackageProvider → loadProxyAppListPackages`: оба currentUserOnly пути, show-system scope, self exclusion, UID/user/label mapping. Алгоритм native loader не менялся. Исправленный Builder5 + loader2 прошли отдельным **7/7 GREEN26s**, final hashes неизменны. Это отдельный дополнительный gate, не «99 уникальных тестов»; [counts](../../outputs/r03-foundation-reports-20261024/native-final-counts.json).

Production сохранён отдельными коммитами `d3b285b` (policy), `0a06c65` (projection). Следующая работа — shared list UI, сначала сцены/expectations и API RED.
