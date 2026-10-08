# I01/I02: продолжение текущего среза

Исходный HEAD: `930eee6`; продолжение после прямого указания пользователя. Новые карточки не начаты: работа ограничена уже взятыми I01/I02. Пользовательское изменение native submodule сохранено.

## Общая операция фиксации пакета

Тесты написаны до action: high-water/opaque ID и ручная группа/порядок, два параллельных пакета с разными диапазонами ID, ошибка сохранения без публикации нового состояния. RED в `logs/i01-live-green-source-red-i02-action-red.log`: compile commonTest на обоих targets не находит ImportProxyServerBatch.

После реализации `SharedApplicationAction.ImportProxyServerBatch` вызывает существующий `repository.updateCatalog` с общим `importProxyServerRecordBatch`, под прежним mutex и прежними outcome/cancellation rules. GREEN: `:shared:app:allTests`, 134 теста Desktop + 134 Android host, 0 failures/errors/skipped. Лог `logs/i01-source-i02-action-green.log` содержит успешные shared suites и отдельную ошибку компиляции Desktop planner; весь этот запуск GREEN не объявляется. Коммит `c59c5e2`.

## Провайдеры и источник импорта

Семь тестов live callback написаны до API/production: отдельный ответ каждому повторному sibling URL; remote вместо inline; failure и Android cancellation fallback; literal ancestor cycle; fetch перед проверкой предельной глубины; root/provider порядок и count; Base64 согласно источнику. RED отсутствующего API: `logs/i01-live-provider-api-red.log`. После подключения callback в Desktop adapter все семь GREEN в `logs/i01-live-green-source-red-i02-action-red.log`; полный Desktop тогда имел четыре ожидаемых source/priority failures.

Четыре поведенческих RED Desktop planner: Clipboard Base64, mixed raw/Base64 paste, лишняя per-line повторная обработка URL, WG-first вместо общего JSON → YAML → WG → URL. File включает Base64 как Android; Clipboard/manual text остаются raw. JSON overlap тест baseline GREEN.

При ревью найден отдельный регресс profile routing: поддержанная URL-ссылка внутри .conf могла перехватить весь профиль. До исправления добавлены два теста сохранения полного Shadowrocket документа для File и Text. RED подтверждён в `logs/i01-profile-live-fetch-red.log`: оба profile routing теста падают; остальные source/priority tests уже GREEN. В том же запуске два fetcher-level теста подтверждают старое кеширование sibling URL и пропуск fetch на границе глубины. Всего 236 Desktop тестов, 4 ожидаемых failures. Отдельные XML сохранены с префиксом `i01-red-`. GREEN дополняется после реализации.

## Границы текущей проверки

I01/I02 пока не отмечены завершёнными: callback проверен отдельно, реальное сетевое подключение и host commit ещё в работе. Существующие Desktop HTTP ограничения (8 MiB, redirects, запрет private/local automatic resources; старый traversal cap 12 удалён по Android-эталону) отличаются от Android; это переносимые policy, а не native ограничения. Эта часть требует отдельного согласования с Android-поведением в рамках уже утверждённого плана S02; нынешняя проверка не доказывает полную HTTP parity.

Ручная Android/Desktop UI QA в этом продолжении пока не проведена. Native FFM smoke предыдущей контрольной точки не заменяет проверки нового импорта.

## Следующий RED: ручной live импорт и сериализация refresh

Пять тестов `DesktopProxyImportLiveProviderTest` написаны до suspend API: File Base64 root/provider, Clipboard raw YAML, repeated URL ответы, inline fallback и полный .conf документ. Два repository теста до API: импорт пакета + create + refresh на трёх потоках с latch при первом сохранении; ошибка batch-save с сохранением bytes opaque записи, persisted/visible state и отсутствием публикации.

`logs/i01-manual-live-i02-repository-api-red.log`: production Desktop compile успешен, Desktop test compile RED из-за отсутствия `planWithProviderFetcher` и `updateLibrary` (остальные ошибки — type inference от отсутствующих API). После этого разрешена минимальная реализация этих двух портов; ожидания не меняются.

## Live callback GREEN и review

`logs/i01-live-and-i02-repository-green.log`: 243 Desktop теста, три profile routing failures из-за классификации `[General]` как JSON-array; все live importer/fetcher tests и новый repository concurrency/save-failure GREEN. Жёстко проверены 11 live-provider кейсов, включая реальный fetcher с in-memory HTTP seam (без внешних запросов). Коммит `892ea45` содержит callback и fetcher wiring; строковый разбор diagnostic заменён formatter из структурированного providerName. Full suite этого запуска не GREEN.

Review выявил потерю raw семантики updateLibrary при преобразовании через typed catalog: исходный refresh удаляет opaque rows целевой группы и задаёт полный порядок результата; typed commit использует старые opaque rows/slots. До исправления добавлен тест exact full-library equality с исходным `DesktopServerLibraries.replaceSubscriptionServers`, target opaque и unrelated opaque rows.

## Raw refresh adapter и оставшиеся parser RED

`logs/i02-raw-library-red.log`: 244 Desktop теста, ровно один новый exact-library test падает. Затем updateLibrary сохраняет точный raw результат host transform под тем же lock; validation/high-water и общий save-before-publish helper сохраняют условия каталога. `logs/i01-provider-budget-json-array-red.log`: 246 тестов, все repository tests GREEN, два ожидаемых RED — 13 provider declarations и JSON-массив без объектов.

Git source `930eee6` подтверждает старую эвристику sections-before-JSON. `["metadata"]` ошибочно превращался в профиль ещё до этого продолжения; Android NoConfigObjects не создаёт сервера. До исправления тестируется no actions + InvalidConfig для Text/File, [] и ["metadata"]. Другой RED доказывает Desktop-only limit 12; Android callback/shared parser такого count limit не имеют.

## Оставшиеся transport различия (source audit)

Парсер работает с payload, переданным портом, и не подтверждает полную parity HTTP. После удаления provider-count budget остаются проверенные механизмы для S02/S03: Android любой 300–399 и repeat(3) request attempts (до двух hops), Desktop ограниченный набор status и пять hops; Android читает body полностью, Desktop decoded body limit 8 MiB; Desktop automatic-resource URL guard private/local/metadata/userinfo отличается от Android; Android timeout default 10s/clamp 3–600s, Desktop request default 30s/client connect10s/proxy clamp1–600s; Android blank UA SKIPI/version/Android, Desktop SKIPI Desktop; Android age public key/decrypt secret, Desktop пока нет age port; Desktop явно Accept-Encoding gzip/deflate и decode. Профильный fallback Skipi/version/Desktop — отдельный выполненный P01 контракт пользователя.

Это переносимые policy и missing bindings, не native ограничения. Новые карточки в этом продолжении не начинаются.

## Коммиттер

До удаления альтернативного ID/prepend алгоритма тесты обновлены на контракт без serverLibrary input/result. Count/mixed subscription/config assertions сохранены; ID/high-water/prepend/manual-group/selection после удаления максимального ID перенесены в настоящий Desktop repository + общий batch helper. `logs/i02-committer-snapshot-api-red.log`: Desktop production compile проходит, test compile RED ровно из-за обязательного старого serverLibrary argument. Только после этого разрешены удаление helper и подключение host callbacks.

## Итоговое подключение, отмена и GREEN

После committer API RED удалены отдельный server snapshot и `toManualStoredServers`. Android ручной import dispatches one ImportProxyServerBatch, empty по-прежнему показывает нулевой count и ничего не сохраняет; install shortcut и outer error handler сохранены. Desktop callback suspend, вход Text захватывается до launch, provider I/O с Android manual options, один flattened batch из latest catalog до subscription/config saves. Selection повторно не сохраняет возвращённый snapshot. Refresh metadata сохраняется в прежнем порядке, server rebase выполняется снова в updateLibrary transform на latest library.

`logs/i01-caller-cancellation-red-host-check.log`: shared/app allTests + Android testDebugUnitTest + assembleDebug успешны; весь combined запуск FAILED только из-за отсутствующего launch import Desktop (исправлен). Затем `logs/i01-caller-job-cancellation-red.log`: 246 Desktop тестов, ровно один RED cancelled caller job reaches simulated commit. Только после RED добавлен ensureActive на live planner return boundary. Explicit provider CE при активном parent всё ещё использует Android inline fallback; тест остаётся GREEN.

`logs/i01-i02-final-desktop-green.log`: BUILD SUCCESSFUL. Итоговые XML counts: Desktop 246, shared/app Desktop134/AndroidHost134, Android398; **912 исполнений, 0 failures/errors/skipped**. Это актуальные последние релевантные suites, не единый повторный запуск всех core/ui/yaml suites. Счётчики в `logs/i01-i02-final-suite-counts.json`, ключевые XML `i01-i02-green-*`. Android APKs universal/arm64/armv7/x86/x86_64 собраны; native tasks исключены, используется ранее собранный native artifact. Новый native FFM smoke и ручная Android/Desktop UI QA не выполнялись.

Commits: c59c5e2 shared store batch; 892ea45 live shared traversal; 51d80fc serialized exact raw library transform; 28e1b2b source/format/profile/JSON/cancellation routing; bf99d2d remove provider count budget; 4202f31 actual host callbacks and remove duplicate ID allocator. Source tree после commits содержит только прежнее пользовательское hevtun gitlink изменение и untracked local tasks evidence.

Read-only review (root + Luna): manual `fetch` без automatic guard намеренно соответствует Android direct manual callback. Нельзя добавлять Desktop-only generic policy в этот путь и объявлять это переносом. Subscription-only guard/прочие transport differences остаются явно в S02/S03; full network parity не объявляется. I01 закрывает общий parser/ports, I02 — manual catalog commits/concurrency; полная миграция ещё не завершена.

Текущие задачи завершены; по указанию пользователя пауза после отчёта, новые карточки не берутся. 7/39, 17,9% по карточкам.
