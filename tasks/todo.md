# Чек-лист завершения Android → shared

Дата исходного плана: 7 октября 2026 года. **12 октября пользователь подтвердил продолжение всего плана («Делаем!», «Продолжай»). Прежняя пауза после S01/S02 отменена; работа продолжается малыми срезами, сначала тесты.**

Полное описание, зависимости, критерии и проверки: [tasks/plan.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md).
Для каждой карточки сначала пишутся и запускаются поведенческие тесты, затем production-код. Карточка отмечается выполненной после GREEN, подключения нужных host entry points, интеграционной проверки и удаления заменённого пути. Крупные карточки разбиваются на малые срезы при исполнении. Исторические исследования не считаются выполнением этих задач.

## 0. Исходное поведение и проверка границ

- [ ] [B01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#b01) — Зафиксировать исходное поведение и карту потребителей.
- [ ] [B02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#b02) — Закрепить правила границ и сравнительной проверки.

## 1. Каталог серверов и импорт

- [x] [C01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#c01) — Разделить создание нового сервера и редактирование существующего.
- [x] [C02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#c02) — Подключить создание и сохранение в Android и закрыть коллизию ID; код/автопроверки выполнены, manual QA без устройства не выполнена.
- [x] [C03](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#c03) — Подключить editor add/edit каталога Desktop к тем же операциям; visual QA не выполнена, прочие writers относятся к I/S/H.
- [x] [C04](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#c04) — Перенести фабрику черновиков и подключить все 13 форм; native AWG/OlcRtc bridges и selected StrategyGroup/ChainProxy runtime ещё не перенесены, точные механизмы зафиксированы для X02–X04; visual QA не выполнена.
- [x] [I01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#i01) — Общий разбор payload/Mihomo и live callbacks подключены к Android и Desktop (ручной импорт и подписки); parser/source/provider tests GREEN. Полная HTTP policy/Age parity остаётся в S02/S03; manual UI QA не проведена.
- [x] [I02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#i02) — Ручной batch commit в обоих хостах через общий store; порядок/ID/выбор/empty/error сохранены в проверенных сценариях. Desktop refresh/read-latest сериализован тем же repository lock; удалены detached server commit и повторный selection save. UI QA не проведена; subscription/profile lifecycle остаются S/P-карточками.

## 2. Подписки и Home

- [x] [S01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#s01) — Перенести CRUD подписок и ручных групп; общий controller и полный редактор подключены к обоим хостам, persistence/counter/opaque removal проверены; manual UI QA не выполнена.
- [x] [S02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#s02) — Перенести обновление одной подписки и сверку результата; shared load/rebase/commit подключён, identity/cancellation/partial failure/Age/provider/embedded проверены; batch/install/schedules остаются S03.
- [ ] [S03](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#s03) — Перенести массовое обновление, install и правила расписаний.
- [ ] [H01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#h01) — Свести группировку, сортировку и Home-проекцию.
- [ ] [H02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#h02) — Вынести прикладные Home-операции и runtime-решения.
- [ ] [H03](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#h03) — Подключить Home и переносимые инструменты в обоих хостах.

## 3. Профили Shadowrocket/SKIPI

- [x] [P01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#p01) — Закрепить общую семантику профиля и адаптер Desktop JSON; ordinary editor save/legacy schema/UA проверены, остальные lifecycle/refresh относятся к P02/P03.
- [ ] [P02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#p02) — Перенести полный жизненный цикл профиля.
- [ ] [P03](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#p03) — Сохранить обновление профиля, расписание и связанные группы.
- [ ] [P04](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#p04) — Подключить общий список и визуальный редактор профиля.

## 4. Планирование Xray, runtime JSON и экспорт

- [ ] [X01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#x01) — Свести преобразование Shadowrocket rules к одному алгоритму.
- [ ] [X02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#x02) — Перенести чистое outbound-планирование Android.
- [ ] [X03](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#x03) — Перенести общую сборку runtime Xray JSON.
- [ ] [X04](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#x04) — Унифицировать полный JSON и экспорт профилей.

## 5. Настройки

- [ ] [T01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#t01) — Перенести настройки оформления, Home и общие preferences.
- [ ] [T02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#t02) — Перенести local proxy и subscription preferences.
- [ ] [T03](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#t03) — Перенести DNS, Mux и Fragment preferences.
- [ ] [T04](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#t04) — Перенести VPN/TUN, service-control и системно зависимые settings.

## 6. Маршрутизация, ресурсы и выбор приложений

- [ ] [R01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#r01) — Перенести routing CRUD и редакторы.
- [ ] [R02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#r02) — Перенести каталог ресурсов и правила обновления.
- [ ] [R03](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#r03) — Перенести app selection и платформенную привязку per-app.

## 7. Остальные существующие мобильные сценарии

- [ ] [A01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#a01) — Перенести Network Automation и profile activation.
- [ ] [A02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#a02) — Перенести onboarding и переносимый app flow.
- [ ] [D01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#d01) — Перенести модели и координацию диагностических инструментов.
- [ ] [D02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#d02) — Подключить диагностику к реальным adapters двух хостов.
- [ ] [L01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#l01) — Перенести общую логику журналов и настройки интеграций.
- [ ] [U01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#u01) — Перенести чистую логику обновлений приложения.
- [ ] [K01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#k01) — Перенести backup/restore/reset над общими данными.

## 8. Оболочки, очистка и итоговая интеграция

- [ ] [N01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#n01) — Сократить composition roots и дублирующий UI.
- [ ] [Z01](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#z01) — Убрать заменённые пути и провести независимое ревью.
- [ ] [Z02](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md#z02) — Провести итоговую интеграцию и зафиксировать ограничения.

## Контрольные точки

- [ ] CP1 — каталог/импорт: уникальные ID, порядок, совместимость и ошибки.
- [ ] CP2 — подписки/Home: общие операции, cancellation, refresh и host эффекты.
- [ ] CP3 — профили/Xray: .conf, visual/raw editor, groups, runtime/export.
- [ ] CP4 — настройки/остальные сценарии: state/actions, storage и реальные OS adapters.
- [ ] CP5 — финал: границы, suites, сборки, доступная UI/native QA и карта ограничений.

## Журнал исполнения

7 октября 2026: пользователь подтвердил реализацию. HEAD исходной точки ff5f5d8; постороннее изменение native submodule сохранено. Начаты research B01 и подготовка тестов B02/C01. Production первого среза ещё не изменён; Gradle-прогоны координирует оркестратор.


C01 foundation выполнен: create/edit общие, tests-first RED отсутствующего API → GREEN shared/app:allTests (102 Android + 102 Desktop, 0 failures/errors/skips). Коммит 86cd255. B01/B02 зафиксированы для первого каталожного среза; для следующих областей fixtures уточняются до кода. C02/C03 tests готовы; исходные прогоны идут, host integration ещё не изменена.

C02/C03 выполнены в b525f77/0549c5d/8be56c5: общий результат редактора и оба хоста, retained opaque selection, store-owned queued commit после ухода страницы. Перед fix — behavioral RED opaque selection и lifecycle; перед новыми API — compile RED. Полный checkpoint: 1346 tests без failures/errors/skips + Desktop compile + debug APK. После lifecycle fix shared/app 111×2 GREEN. Подробные ограничения и chronology: [catalog-verification.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261007/catalog-verification.md).

C04 выполняется: Desktop typed Add behavioral RED (открывает URL dialog), новые factory/callback API compile RED. Общая factory для всех 13 типов и Android wrapper реализованы; factory cases и независимость mutable drafts GREEN. Desktop form/save/preview wiring ещё не завершены.

I01 первый срез: парные YAML fixtures показывают Android baseline 3 GREEN и Desktop behavioral RED (expected 2 nodes / actual 1). Сохраняется actual scalar trim contract Android. После compile RED нового API общий `shared:yaml-jvm` loader реализован: 5 tests GREEN, прежние SnakeYAML defaults/alias limits/tags. Android/Desktop parser calls подключены; host suites и удаление остальных дублирующих provider/format algorithms ещё проверяются.

P01 первый срез: Android/common characterization подтверждает untouched raw read и canonical SKIPI rewrite при сохранении. Перед новым `ConfigProfile.withTrafficConfigBasics` — clean compile RED на обоих targets; после helper shared/app: 118 Android + 118 Desktop tests GREEN. Seed только для отсутствующих typed значений, envelope ID/time/raw не заменяются seed. Desktop editor save adapter tests/wiring готовятся отдельно. Карточки C04/I01/P01 пока открыты.

Возобновление 7 октября: P01 acceptance закрыта после Desktop editor-save/UA wiring (`31c9505`, `21f3d72`, metadata `1efe3b0`), host suites/build GREEN и независимого Luna source review без замечаний. По последнему указанию пользователя fallback Desktop — `Skipi/<VERSION_NAME>/Desktop`, explicit blank/custom сохраняются. Актуальная контрольная точка 1434 tests (shared/ui 118 из предыдущего неизменённого suite), 0 failures/errors/skips. Реальный Q4 Desktop Core 26.9.9: два запуска/остановки, traffic stats/geosite GREEN. Persistent логи и chronology: [factory-yaml-profile-verification.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261007/factory-yaml-profile-verification.md).

C04 acceptance закрыта в `e55ad00`/`0b683bc`: все 13 shared draft kinds и общие формы в Desktop; общий NEW group/return constructor в обоих хостах; save/validation/warning/discard и ephemeral preview/opaque/order/counter/Saved-only group feedback GREEN. Независимый review без дефектов. Native/protocol ограничения записаны конкретно по Desktop FFM dispatch и Android bridge/outbound планам в том же verification документе; эти работы остаются X02–X04. Закрыто 5/39 карточек (12,8%), незавершённые части I01 не включаются в процент.

Пауза по просьбе пользователя: уже взятые I01 parser/adapter и I02 batch-foundation срезы доведены до GREEN и commits `868769a`/`55affe3`/`930eee6`/`7bbd1c4`. Desktop 219, shared/app 131×2, сборки GREEN; совокупная актуальная контрольная точка 1453 tests без ошибок (отдельные последние suites, не повторный единый прогон). I01 source entry-point/real fetch orchestration и I02 serialized host commits ещё не подключены; обе карточки остаются открытыми. P02 read-only research записана, implementation/tests не начаты. Новые задачи до следующего указания пользователя не берутся.

Следующее указание пользователя — продолжать работу. Пауза снята; возобновлены I01 source entry points/live provider ports и подготовка I02 serialized commit contracts. Исходная точка `930eee6`, 5/39 карточек закрыто. Luna max агенты готовят Android-based tests до production, Gradle/git координирует root.

Контрольная точка после продолжения: **7/39 карточек = 17,9%** (по количеству карточек, не объёму кода). Завершены I01/I02, новые S/H/P-карточки не начаты. HEAD `4202f31`, шесть новых commits после `930eee6`. Итоговые актуальные suites: Desktop 246, shared/app 134×2, Android 398 = 912 исполнений, failures/errors/skipped 0. APK assembleDebug успешен с существующими native artifacts, hevtun submodule не пересобирался/не изменялся этой работой. Реальная UI QA без устройства не выполнена, новый FFM smoke не запускался (предыдущий отдельный smoke сохранён).

Evidence/RED→GREEN chronology: [import-resume-verification.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261007/import-resume-verification.md), [final counts](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261007/logs/i01-i02-final-suite-counts.json). Пауза после текущих задач по прежнему указанию пользователя; автоматических продолжений и новых задач не запускать.

Новое указание пользователя: «можешь взять ещё 2». Взяты **S01 + S02**, исходный HEAD `4202f31`, статус 7/39 закрыто. Пауза снята для этих двух карточек; другие карточки не начинать. S01 prerequisites C03 done; S02 follows S01+I02. Root orchestrates Luna max research/tests/code; tests first, producer proposal → test-only → RED → production → GREEN → host wiring. Research/evidence directory tasks/shared-migration-20261007/s01-s02/.

8 октября 2026: S01/S02 foundation-срезы `803704d`, `9cbc9cc`, `9cca971` проверены (798 executions selected/full common suites, 0 failures/errors/skips). Полная модель/legacy mapping, JVM Age и concrete shared load/reducer готовы. Общие CRUD, commit ports и host/UI entry points ещё в работе; **S01/S02 пока не отмечены завершёнными**. Chronology/RED/GREEN: [verification.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261007/s01-s02/verification.md).


S01/S02 acceptance завершена 8 октября 2026: HEAD `1edad1e`, девять локальных commits после `4202f31`. Полная модель/CRUD/record-UI projections/controller, persisted counters/atomic latest transforms, cleanup raw/opaque/composite refs и общий single-refresh load/identity/reconcile/commit подключены к Android и Desktop. Android Home/GroupList сохраняют разные условия auto-refresh; Desktop полный общий editor, real-ID refresh, global-UA fallback и awaited physical stop до удаления. Reconnect зависит от успешно сохранённых фактических изменений серверов или активного профиля.

Актуальный regression checkpoint: shared/core 231+230, shared/app 173+173, shared/ui 63+63, Android 411, Desktop 281 = **1625 executions, failures/errors/skips 0**. Отдельный реальный Core 26.9.9 FFM test GREEN: два start/stop, traffic stats и geosite. Android debug APK и Desktop compilation GREEN. Tests-first API/behavior RED, frozen Android baseline, source correction удаления и GREEN chronology сохранены в [verification.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261007/s01-s02/verification.md); counts [final-suite-counts.json](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261007/s01-s02/final-suite-counts.json). Independent S01 и Desktop wiring review без actionable findings.

Ограничения проверки: ручная Android UI QA недоступна (единственный adb device в recovery), Desktop visual QA не проведена. Native hevtun source не пересобирался, использованы существующие artifacts; foreign submodule сохранён. Embedded-profile handoff в single-refresh проверен, полный профильный lifecycle остаётся P02/P03.

**Закрыто 9/39 карточек = 23,1% по числу карточек (не по трудозатратам). Пауза после S01/S02 по указанию пользователя. Новые карточки не взяты; S03/H/P и другие открытые работы не начинать без следующего указания.**

12 октября 2026: пользователь одобрил весь проверенный план («Делаем!»); пауза снята. Исходная точка — HEAD `d8b3e0a96c48c3f4eebd8415293377c4a36381e0`, закрыто 9/39 карточек. Начаты первые H01 и S03 срезы в режиме tests-first: сейчас уточняются Android-контракты и спецификации тестов; production-работа S03 ждёт отдельного согласования root после первого прогона тестов. S03-агент ведёт INSTALL + SCHEDULE; batch refresh и grouping code/tests в его scope не входят. Исторические записи и checkbox-статусы карточек сохранены.

12 октября 2026, S03 INSTALL + SCHEDULE: Android install-2 и expiry-scheduler-1 baseline GREEN зафиксирован в `tasks/shared-migration-20261012/install-expiry-android-green/`; initial common API compile RED зафиксирован root до production. Root открыл production gate для pure install preparation, expiry trigger policy и Android adapters. Добавлены shared common tests, Android install/expiry tests, Desktop URI-to-shared preparation helper/tests; production реализация начата. Desktop `Main` wiring остаётся у batch owner до отдельного указания. Batch refresh и grouping остаются вне S03 scope; карточки не отмечены завершёнными, общий acceptance и итоговые проверки ещё впереди.

12 октября 2026, промежуточная проверка S03/H01: цель всего плана остаётся одобрена, пауза снята, исходный HEAD `d8b3e0a96c48c3f4eebd8415293377c4a36381e0`, статусы карточек без изменений — 9/39. Снимки исходного focused baseline GREEN42 сохранены. Install/expiry Android baseline 2+1 GREEN; worker API прошёл compile RED, затем shared/app allTests 197 Desktop + 197 Android, Android worker 5 и assembleDebug GREEN. Cancellation parity дала behavioral RED на старом shared пути и подтверждённое продолжение старых Android writes после observer cancellation; root reports исправленный common путь GREEN. Первые H01 RED заменены последующими GREEN для Desktop all-groups/projection, sort persistence и install adapter; эти срезы не закрывают H01 до Q2 checkpoint. Актуальные evidence и контракты: [verification.md](/home/vqsego/TorvaldsVPN/skipi-box/tasks/shared-migration-20261012/verification.md). Android H02 bridge tests перенесены в `app/src/test`, но baseline ждёт Android runner source prerequisite. Common bulk coordinator tests и Desktop callback tests остаются draft вне source sets; single/group ordering описан отдельно по исходнику HEAD. S03 host UI runner wiring и полный integrated checkpoint ещё не завершены; карточки не закрыты.

16 октября 2026, P04 focused test-first checkpoint (in progress): scope is profile basics + General/DNS save/navigation coordination and raw Back, without claiming full P04. `TrafficConfigEditorControllerTest` covers latest same-ID transforms, typed basics/general/DNS, Missing, commit-before-navigation and raw save/rejected-back behavior; `TrafficConfigEditorAndroidOracleTest` applies the same transforms through Android's current `AppState` updater and raw `[SKIPI]` read/write chain. Root confirmed the four Android oracle cases GREEN before production. `DesktopTrafficConfigEditorProfileStoreTest` covers the repository adapter contract, the required injected `onTransformConfigLibrary` callback path, latest library/selection/counter/sibling preservation, per-profile UA semantics, and raw save synchronization of typed state with the Desktop `ConfigProfile` envelope. A Desktop RED was captured before raw-adapter normalization; after correcting the test to assert semantic empty UA rather than trailing whitespace, that gate must be rerun before recording its current result. P04 remains open: routing, proxy groups, resources, remaining policy/routes, host UI review, and final integrated suites/visual QA are outstanding.

16 октября 2026, P04 raw-normalization gate: root reran `DesktopTrafficConfigEditorProfileStoreTest` after the UA assertion correction. Four tests ran; callback invocation/order, latest library/selection/counter/sibling behavior, Missing, and explicit-empty-UA semantics passed. Exactly one intended RED remains: `controllerRawSaveUsesAndroidSkipiNormalizationAndUpdatesLatestDesktopEnvelope` observes the persisted Desktop profile name still as `Envelope before` after valid raw `[SKIPI] profile-name` changed to `Raw editor name`; the Desktop adapter needs Android's collection updater read-known-SKIPI → write-known-SKIPI chain and envelope remapping. Log: `outputs/shared-migration-20261016-p04-envelope-behavior-red.log`. Android oracle 4/4 GREEN was confirmed before production. This focused gate does not close P04; shared visual host wiring and final suites/QA remain under review.

16 октября 2026, P04 independent source review and checkpoint: common Basics/General/DNS screens are connected on Android and Desktop; raw Back uses the current editor text and the approved save/rejected/failure navigation rules. The Desktop store now uses the injected Main library-transform callback and Android's selected-profile `[SKIPI]` normalization path. Static review found no actionable P04 source mismatch. Root-confirmed GREEN: Android oracle 4/4 before production; common controller 13 cases; shared/app 277 tests per target; shared/ui 82 tests per target; Android 458 tests and `assembleDebug`. Latest UI/APK log: `outputs/shared-migration-20261016-p04-final-ui-apk.log`. Desktop main compilation is GREEN; final Desktop adapter tests remain unverified because test compilation is blocked by unrelated unresolved `DesktopLocalProxyRuntime` references in `DesktopLocalProxyRuntimeLifecycleTest` (`outputs/shared-migration-20261016-editor-green-t02-api-red.log`). No device or rendered Desktop visual QA was performed. P04 remains open for common list composition, routing/rules, proxy groups, resources, remaining platform-specific routes, full raw/list parity, the Desktop focused suite and visual QA. Details and Android source map: `tasks/shared-migration-20261016/p04-verification.md`.

16 октября 2026, уточнение после Desktop focused gate: предыдущее сообщение отражает состояние до нового прогона и superseded в части Desktop test-compile blocker. `outputs/shared-migration-20261016-t02-invalid-port-behavior-red.log` запустил 20 Desktop tests; root подтвердил `DesktopTrafficConfigEditorProfileStoreTest` 5/5 GREEN. Единственный failure — независимый T02 `DesktopLocalProxyRuntimeLifecycleTest > invalidRawStaticPortUsesAndroidDefaultRatherThanAStaleParsedDesktopPort`; полный Desktop suite ждёт исправления canonical default. Это не меняет P04 scope/status: focused Desktop editor/store gate теперь GREEN, но список, routing/rules, proxy groups, resources, оставшиеся platform-specific routes и визуальная QA остаются открытыми.

16 октября 2026, финальная контрольная точка текущего блока: canonical port-default fix выполнен Luna после behavioral RED. Полный Desktop suite **399/399 GREEN**; последние shared/core **244+243**, shared/app **277+277**, shared/ui **82+82**, Android **458** — всего **2062 исполнения**, failures/errors/skips 0 в актуальных соответствующих XML. Финальные shared UI и APK assemble GREEN. Отдельно выполнены **3/3 реальных FFM/Core tests** с заданным `SKIPI_CORE_INTEGRATION_DIR`: lifecycle/traffic/geosite, SOCKS authentication и dynamic runtime endpoint. Это отдельная проверка, не досрочный возврат optional native tests в обычном suite. Evidence/границы/следующие работы: [checkpoint.md](shared-migration-20261016/checkpoint.md). Полный перенос и широкие P04/T02/X-карточки не объявляются завершёнными; UI/device QA и Android bind-collision retry parity остаются открытыми.

Пользователь попросил продолжать. Следующий P04 срез Tunnel/Network ещё не реализован: все три Luna-агента вернули `Your workspace is out of credits`. Root сохранил сделанные агентами изменения и завершил доступные проверки; дальнейшее написание production/tests в требуемом Luna-only режиме заблокировано доступностью кредитов агентов. Это не пауза по просьбе пользователя. Сам root код и тесты не писал.

16 октября 2026, продолжение после восстановления Luna: предыдущий credit blocker снят. Luna max перенесли stateful Tunnel/Network UI и save/navigation в shared с подключением Android/Desktop; чистый Android outbound planner теперь в shared/app, Android остался адаптером; общий bind-retry policy подключён в Android и Desktop, Desktop пересобирает конфигурацию из единого снимка и исключает неудачный динамический порт. До кода зафиксированы Android baselines и missing-API RED; отдельный RED/GREEN сохранил Android first-ID rebind для временных duplicate IDs при restore. Проверено root: shared/app **293+293**, shared/ui **82+82**, Android **474**, Desktop **406**, APK assemble GREEN. Реальный Core **4/4 GREEN**, включая занятый порт → повторный запуск → authenticated SOCKS endpoint. Native не пересобирался, UI/device QA не выполнена. P04 Rules oracle **6/6 GREEN** до production, новый common/Desktop API RED зафиксирован; следующий срез выполняется. Общие P04/T02/X02/X03 карточки остаются открыты: полный UI, Desktop planner/runtime wiring и остальные критерии ещё не закрыты. Подробности: [continuation-verification.md](shared-migration-20261016/continuation-verification.md).
