# S01 / S02 — исполнение и проверки

Пользователь разрешил взять ровно две новые карточки. В работе S01 (CRUD групп) и S02 (одиночный refresh), после завершения — пауза. Начальный HEAD `4202f31`; прежний статус 7/39. Foundation-срезы ниже ещё не закрывают карточки: нужны общие CRUD/commit ports и host/UI entry points.

Root координирует; research/tests/code — gpt-6-luna max. Gradle запускает только root, последовательно, с установленными JDK/SDK и `--offline`. Постороннее изменение native submodule не затрагивается. Локальные tasks/evidence не добавляются в Git.

## Проверенные первые срезы

- `803704d`: полная application-модель подписки, Android/Desktop mapping, редактирование на месте, отдельный сохранённый profileTitle с legacy fallback. Перед production: исполняемый order RED `[3,8]` / `[8,3]`; mapping API RED; затем два исполняемых RED на distinct/explicit-empty profileTitle.
- `9cbc9cc`: общие Age/timeout/redirect policies и JVM Kage; Desktop direct HTTP через URLConnection с connect/read timeout, initial IDN и Android Basic auth. Before transfer: Android baseline 5/5 GREEN; missing common/Desktop APIs RED; отдельные behavior RED direct timeout path и empty userinfo. Generic automatic-resource guard/cap сохраняет исходный отдельный путь; S02 root/provider/embedded использует direct port.
- `9cca971`: concrete shared load/identity/latest-state reducer, latency/ID/reference/override/metadata preservation, stale target silent no-op, embedded handoff. Missing API RED; дополнительный interval RED `12` / `6`, затем response-only interval merge GREEN. Настоящая Job cancellation после provider fallback не выдаёт успешную загрузку.

Ошибки тестовой спецификации исправлялись по существующему Android-источнику: parser — suspend function typealias, а inline фраза `inline profile content` декодируется прежним flexible Base64 decoder в бинарный текст. Для inline-config теста взят валидный encoded document `e30=` → `{}`; алгоритм decoder не изменялся. Gradle 9.7 custom source sets настроены через create/getByName, nullable public metadata property читается безопасно между модулями.

## Foundation GREEN

`s02-common-refresh-first-green.log`: полный shared/app Desktop suite.
`s01-s02-foundation-host-second-check.log`: оба host adapter/fetcher suites и общие Android suites.
`s01-s02-common-second-check.log`: shared/core Desktop suite GREEN; общий app в этом промежуточном запуске остановился на ошибках синтаксиса тестов, исправленных до последующего GREEN.

Счётчики этих последних выполненных suites сохранены в `foundation-counts.json`:

| Suite | Executions | Failures / errors / skips |
|---|---:|---|
| core Desktop | 231 | 0 / 0 / 0 |
| core Android host | 230 | 0 / 0 / 0 |
| application Desktop | 144 | 0 / 0 / 0 |
| application Android host | 144 | 0 / 0 / 0 |
| Desktop selected repository/fetcher | 34 | 0 / 0 / 0 |
| Android selected mapping/fetcher | 15 | 0 / 0 / 0 |

Это 798 executions соответствующих suites, не полный host regression checkpoint. Real Kage encryption/decryption test прошёл; FFM/Core smoke и manual Android/Desktop UI в этом срезе пока не запускались. APK сборка для S01/S02 ещё впереди.

## Остаётся в этих двух карточках

Следующий срез 8 октября: `s01-s02-operations-api-red.log` подтвердил отсутствие общего каталога/CRUD/store action и commit ports. После реализации `s01-s02-operations-first-check.log`: 35 тестов, 34 GREEN и один реальный RED — уже отменённый Job вызывал первую запись серверов. `s02-cancelled-first-write-red.xml` сохранён до исправления. Проверка активности перед первой записью исправила это; `s01-catalog-host-api-red.log` содержит common commit port 10/10 GREEN и Desktop host API RED, `s01-android-catalog-api-red.log` — отдельный Android host API RED. Host production mapping/counter разрешён только после этих запусков. Общий CRUD и store прошли в первом behavior запуске; XML последнего commit-port GREEN сохранён отдельно. Карточки по-прежнему открыты, host callbacks ещё не подключены.

- S01: общие create/edit/toggle/move/remove с persisted ID counter, atomic adapters и канонической очисткой составных ссылок; store/Android/Desktop editor/Home callbacks.
- S02: общая последовательность commit и проверенный partial-failure result; подключение Android и Desktop к shared load/reducer/commit ports; Desktop latest raw library/opaque rows и реальная cancellation на host boundary.
- Полные необходимые suites, Android APK, независимое review, checklist и итоговый отчёт с паузой. S03 и profile lifecycle P02/P03 не начинаются.


## Каталог, редактор и host commit — 8 октября

Общий каталог/CRUD/store и controller редактора реализованы после API RED. `s01-editor-controller-host-check.log` подтверждает полный shared/app Desktop GREEN: controller 5 тестов и composed commit 11. Host concurrency/counter проверки сначала воспроизвели потерю параллельного создания `[1,2]` вместо `[1,2,3]`, сброс legacy high-water counter `null` вместо `5` и ненужную запись после межхранилищного изменения identity; XML сохранены до исправления. Desktop repository теперь обновляет под latest-state lock и не сохраняет no-op. Отдельный built-in delete RED устранён: `s01-catalog-guard-s02-android-batch-baseline.log` — Desktop repository 15/15 GREEN.

Перед изменением single Android apply зафиксирован GREEN прежнего batch сценария: разные группы, серверы других групп, Strategy/Chain references, selection и два embedded profiles. Первоначальная fixture ошибочно предполагала смену ID при VLESS→VLESS; ожидание сверено с неизменённым core matcher, первая старая запись сделана HTTP до production baseline. Сценарий теперь действительно проверяет выделение ID 102, сохранение ID 8 и итоговый counter 103. Multi-group Android branch остаётся прежним: S03 не берётся.

`s02-opaque-profile-s01-delete-red.log` (последовательный --continue до новых исправлений):

- common profile port API RED: Unit не выражает ignored handoff; новый тест требует Boolean и отсутствие PROFILE applied scope при false;
- Desktop raw removal behavior RED: в составных моделях остаются удалённые IDs `[2,3,4,5]` вместо `[3,5]`;
- Desktop refresh behavior RED: unrelated opaque selected ID 99 превращается в 7, ссылки на opaque также должны сохраняться;
- Desktop malformed embedded behavior RED: прежний direct import сообщает PROFILE failure `Only one FINAL rule is allowed`, Android источник игнорирует ошибку conversion/import;
- target opaque removal test GREEN (должен остаться удалённым и не попасть в retained IDs);
- Android repository removal 2/2, single apply/cancellation + multi baseline 3/3 и load/apply parity 1/1 GREEN.

XML этих before-production результатов сохранены с префиксом `opaque-profile-red-`. Разрешены точные исправления: common retained IDs для host opaque rows; Boolean profile handoff; malformed parse best-effort отдельно от реального save failure; canonical raw deletion. UI callbacks и финальная интеграция пока впереди, статус карточек не повышен.


## Сохранённые application срезы и UI проекции

- `58027bc`: shared каталог/CRUD/controller, host counters/atomic transforms, удаление связанных raw/opaque/составных серверов. `s01-delete-s02-opaque-profile-green.log`: общий application Desktop 170/170 и Android selected 6/6 GREEN; Desktop в этом запуске остановился на механическом nullable selected ID mismatch. После исправления `s01-delete-s02-desktop-green.log`: Desktop repository 15/15, raw library 5/5, refresh adapter 14/14 GREEN.
- `9f40d42`: common composed refresh/commit receipts + Android single apply и Desktop commit adapter. Profile port возвращает false для ignored malformed handoff и не сообщает PROFILE applied; реальный save failure остаётся PROFILE failure. Retained opaque IDs сохраняют unrelated selection/composite references и резервируют ID, target opaque не сохраняется. `s02-android-load-mapping-refactor-green.log`: после удаления повторного Android load mapping прежний load/provider/embedded/apply parity test GREEN.
- `s01-editor-projection-api-red.log`: missing common record/UI APIs; один missing builtIn fixture argument исправлен без смены expectations. `s01-editor-projection-green.log`: 4/4 common UI mapping tests GREEN до host wrapper/callback переноса. Существующий DefaultSubscriptionGroupId переносится физически в shared/core тем же пакетом/значением; обычный Desktop ID 1 не резервируется.

Независимое review не нашло metadata/format/counter blocker, но выявило межоперационную гонку двух save одного ID: A-subscription, B-subscription, B-proxy, A-proxy. `s01-concurrent-editor-save-red.log` + XML — 6 тестов, один реальный RED mismatch final linked Custom override. Разрешена serialization save одним store-owned контроллером через Mutex, без обещания глобальной storage транзакции. Дополнительный cancellation guard агент предварительно включил до отдельного тестового запуска; root не принял этот дополнительный guard, убрал его из предлагаемого среза и требует отдельно зафиксировать поведение отменённого вызова до принятия guard. Concurrency test expectations остаются исходными.


## Редактор: concurrency/cancellation и host wiring

`s01-editor-cancel-red-android-ui-baseline.log`: concurrency уже GREEN под Mutex; отдельный cancelled-call RED показывает 1 subscription write вместо 0 до guard. Android baseline в этом запуске не исполнился: физический перенос DefaultSubscriptionGroupId в файл того же Kotlin JVM facade basename затенял symbol в unit compilation; новый тест также использовал недоступный kotlin.test.Test annotation. Исправлены только filename (`SubscriptionGroupDefaults.kt`) и annotation (`org.junit.Test`), без смены assertions.

`s01-editor-save-green-android-ui-baseline.log`: shared editor 7/7 GREEN (concurrency + already-cancelled) и Android прежний state/UI adapter 2/2 GREEN перед wrapper переносом. Baseline/RED/GREEN XML сохранены отдельно. Store теперь владеет одной instance controller для UI обоих хостов.

`s01-desktop-effect-s02-parser-api-red.log`: до Desktop UI/parser production подтверждены missing onSetSubscriptionEnabled callback и subscriptionPayloadParser API. Существующий pipeline Desktop адаптируется к common context без новых parse policies. `s01-desktop-removal-adapter-api-red.log`: отдельно отсутствующий raw-removal host adapter для сохранения→publication→stop→subscription save. Тесты заранее требуют отсутствие stop/subscription write при failed server save и отсутствие stop для unrelated selected server.

Review дополнительно сверил embedded-profile semantics с Android: прежние Desktop Locked/Conflict guards намеренно снимаются. Android существующий subscription handoff импортирует поверх matching latest profile без pre-fetch baseline/lock rejection; existing active ID сохраняется, activation относится только к new profile. Полный lifecycle P02/P03 не реализуется в этом срезе.


## Финальные host сценарии: source correction и RED

Android Home и отдельный список групп имеют различие в запуске refresh после save: Home обновляет также включённую manual-группу, впервые получившую URL; GroupList обновляет только новую. Это подтверждено frozen Android `4202f31`. `s01-home-manual-refresh-api-red.log` зафиксировал отсутствие общего explicit флага до реализации; `s01-s02-final-host-api-red.log` — общий editor 8/8 GREEN, Android host 17/17 GREEN (Home input 8, groups compatibility 1, editor projection 2, repository 2, apply 3, load/apply parity 1). XML сохранены с префиксом `final-home-host-green-`.

Первоначальная спецификация Desktop removal helper ошибочно ставила stop после server save. Повторная проверка Android Home показала физический stop ДО удаления, а failed stop сохраняет группу и серверы. Ранее написанные assertions исправлены по Android-источнику до нового production; это исправление тестовой спецификации, прежний GREEN не доказывал Home parity. Новый тест требует awaited stop, сохранение изменений latest library во время ожидания, отсутствие всех destructive writes при stop failure, честный частичный runtime результат при server-save failure после успешного stop.

Независимое review выявило ещё две host регрессии: applied scope не означает фактическое изменение серверов (лишний reconnect), пустой subscription UA теряет настроенный global Desktop fallback. До исправлений добавлены tests для metadata-only/no-op/high-water-only и failed persistence change flags, а также transport binding configured fallback/proxy/timeouts/device/Age. Combined RED подтвердил missing APIs flags, transport helper и нового removal constructor; отдельный конфликт имени тестового crypto fake исправляется механически без смены expectations перед повторным API RED.


## Полный shared/Android regression checkpoint

`s01-s02-common-android-full-green.log`: BUILD SUCCESSFUL; shared/core 231 Desktop + 230 Android host, shared/app 173 + 173, shared/ui 63 + 63, Android application 411. Всего 1344 executions; failures/errors/skips 0. XML сохранены в `final-full-xml`, counts в `final-common-android-suite-counts.json`. Debug APK обновлён, universal 167058741 bytes. Native hevtun build/sync исключены, использованы имеющиеся artifacts, foreign submodule не затрагивался.

Повторный adb inventory после sandbox socket ограничения с разрешённым local access видит единственное устройство `recovery`; Android UI QA недоступна, recovery/reboot/install/пользовательские данные не изменялись. Shared-editor/UI independent review сопоставлено с frozen `4202f31`: actionable findings отсутствуют. Screen-owned post-save Desktop refresh не объявляется дефектом, потому что исходный Android Home также запускал refresh в rememberCoroutineScope; accepted storage action отдельно переживает observer cancellation.


## Финальный Desktop GREEN и реальный Core

Clean `s01-s02-desktop-final-api-red.log` подтвердил отсутствие flags/transport helper/new awaited-stop constructor после механического устранения crypto-fake name collision. После реализации compiler потребовал nullable generic type ожидаемого списка Proxy в fixture; уточнён только тип `listOf<java.net.Proxy?>`, ожидаемые значения не менялись. `s01-s02-desktop-final-focused-green.log`: 41/41 GREEN (commit adapter 18, parser 1, transport binding 1, Home effects 17, removal adapter 4). XML сохранены с `final-desktop-focused-green-`.

`s01-s02-desktop-full-green.log`: Desktop full 281/281 GREEN, compilation GREEN. Совместная актуальная точка shared/Android/Desktop — **1625 executions, 0 failures/errors/skips**; counts `final-suite-counts.json`, XML `final-full-xml`. Обычный optional FFM test без env early-return не считается real-native доказательством.

Отдельный `s01-s02-real-core-ffm-green.log` с `SKIPI_CORE_INTEGRATION_DIR` — 1/1 GREEN. XML `final-real-core-ffm-green.xml` содержит реальные Core 26.9.9 start/stop session 1 и session 2, тест также проверяет traffic stats и geosite rule. Использована существующая generated библиотека, native source не пересобирался. Ручная Desktop visual QA не проводилась.


## Завершение S01/S02 и пауза

Independent final review Desktop Main/transport/change flags/stop-reconnect wiring без Required findings. Native stop awaited before removal; failure сохраняет данные; successful stop инвалидирует queued reconnect. Source comparison conducted against frozen Android, no new profile conflict policy. Root отдельно проверил removal helper и final diff, git diff --check clean.

Финальные локальные commits: `7a1e2aa` shared UI projections + Android editor/controller wiring, `c63e5de` Desktop fallback/options + persistence change flags + physical-stop removal adapter, `1edad1e` Desktop Home full editor/ID dispatch + composed refresh и удаление старого Desktop conflict-guard commit path. Вместе с шестью предыдущими S01/S02 commits — девять commits после `4202f31`. Working tree после commits: только foreign native submodule modification и intentional local untracked `tasks/`; код среза сохранён.

S01 и S02 закрыты в checklist с явными QA ограничениями. Всего 9/39 карточек (23,1% по количеству, не оценка объёма). Новые задачи не взяты; S03, H01–H03, P02/P03 и остальные карточки остаются открытыми. Работа остановлена после этих двух карточек по пользовательскому ограничению.
