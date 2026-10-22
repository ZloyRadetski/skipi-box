# P03 — ручное обновление профиля Desktop

22 октября 2026. Малый срез GREEN; P03 остаётся открытой до завершения известных batch failure/cancellation corrections и итогового gate.

## Исходное поведение и изменение

Android `TrafficConfigPage.updateConfigFromUrl` захватывает профиль и trimmed URL, использует профильный UA и текущие `toSubscriptionFetchOptions()`; default `useRunningProxy=false`. Это прямой manual request даже при работающем Core. Автоматический worker явно включает running proxy и остаётся отдельным сценарием.

Desktop manual screen ранее делал inline fetch с UA и timeout, без opt-in device headers. Теперь `DesktopConfigsScreen` получает обязательный `fetchProfileDocument(TrafficConfigState, String)` только для profile refresh. Main передаёт отдельный `DesktopTrafficConfigProfileFetcher(activeProxy={null})`, читающий свежие settings. Automatic instance сохраняет реальный runtime proxy. URL create/clipboard import используют прежний IO fetch с global UA/timeout через локальный `fetchImportedDocument`; общий launch/loading/error/finally helper принимает явный fetch callback.

Manual lock confirmation, validation, shared `TrafficConfigProfileRefreshOperations.apply(Manual)`, latest same-ID transform, captured URL и существующая асинхронная reconciliation в Main сохранены. Новый revision/CAS fence не добавлен. При отсутствии UA используется профильный `Skipi/<version>/Desktop`; явно заданный UA и низкоуровневый transport fallback для пустого значения не менялись.

## Тесты до production

Добавлены три actual Compose menu tests и один transport binding test:

- captured profile/trimmed fetch URL, поздняя same-ID правка, имя из latest row при отсутствии имени в ответе, manual response lock/scheduling/UA, активный sibling и high-water ID;
- locked warning не начинает fetch до подтверждения; explicit confirmation отключает lock даже при `true` в ответе;
- actual menu → suspended fetch → real repository late edit → public profile select 7→8 → response → asynchronous shared reconciliation → reload настоящих временных `configs.json`/`servers.json`: стабильный group ID 30, состав Node B/C, выбранный Node B, sibling isolation, opaque selected server 99 и оба ID counters;
- отдельные manual/automatic binding instances: automatic использует runtime SOCKS, manual direct, актуальные timeout/profile UA/opt-in device identity headers, без Age negotiation.

Первые три теста проверяют экран и его host callback harness. Последний проверяет настоящий transport adapter через recording connection, без физического сетевого запроса. Main composition непосредственно не запускалась; её подключение проверено root и независимым Luna review. Тестовый asynchronous host callback повторяет существующую repository/reconciliation границу Main, а не подменяет shared group алгоритм.

Initial API RED: BUILD FAILED **20 s**, `No parameter with name 'fetchProfileDocument' found`. После добавления третьего acceptance scenario окончательный test set повторно проверен до production: тот же единственный API error, **2 s**, тесты не исполнялись. [API evidence и pre-code hashes](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-manual-profile-refresh-api-red-reports-20261022/final-test-set.json). Compile error не выдаётся за воспроизведение HTTP header failure. Восемь остальных screen test callers получили только fail-fast callback arguments.

## Проверка и исправление test harness

Первый post-code gate: **57 executions, 56 PASS / 1 FAIL**, BUILD FAILED **25 s**. Новые captured/locked UI tests и binding прошли. Сквозной scenario ожидал selection commit после неоднозначного text click: sourced group subtitle тоже содержит имя профиля и находится раньше его карточки. По исходнику `projectTrafficConfigSourcedProxyGroups`/`SkipiTrafficConfigProfileCard` подтверждено, что тест нажимал sourced group. Селектор исправлен на profile row с действием «Ещё» и публичный Card OnClick. Product expectations и timeouts не менялись. [Исходные XML/counts](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-manual-profile-refresh-initial-failure-reports-20261022/counts.json).

Root также исправил ошибочный filter `DesktopAutomaticRefreshLoopTest` на существующий `DesktopTrafficAutoUpdateLoopTest`; два loop cases отсутствовали в первом прогоне.

Окончательный gate: **59/59 GREEN**, failures/errors/skips 0, BUILD SUCCESSFUL **16 s**, Desktop compile GREEN:

| Suite | Executions |
| --- | ---: |
| DesktopTrafficConfigCollectionScreenIntegrationTest | 19 (16 existing + 3 new) |
| DesktopTrafficConfigProfileFetcherTest | 6 (5 existing + 1 new) |
| DesktopConfigProfileRefreshActionTest | 3 |
| DesktopTrafficConfigAutoRefreshTest / AutoUpdaterTest | 7 / 10 |
| DesktopTrafficConfigAutoRefreshIntegrationTest | 1 |
| DesktopTrafficAutoUpdateLoopTest | 2 |
| DesktopResourceAutoUpdaterTest / AutoUpdateLoopTest | 10 / 1 |

[GREEN XML/counts/hashes](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-manual-profile-refresh-green-reports-20261022/counts.json), timestamps `2026-10-22T20:40:08Z`–`20:40:18Z`. Logs: `outputs/p03-manual-profile-refresh-{final-api-red,green,green-retry}-20261022.log`.

Команда: offline `:desktopApp:test` с указанными suite filters и `:desktopApp:compileKotlin`, JDK 26/test JDK 21, hevtun native tasks исключены. Gate не является полным project suite/APK/native/device QA. Пользовательское приложение не перезапускалось, данные и foreign native submodule сохранены. Независимое review не нашло correctness defects в manual diff. Известные batch cancellation/reconnect issues идут отдельным tests-first срезом.
