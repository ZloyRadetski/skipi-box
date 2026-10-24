# S03 — итоговая приёмка, 24 октября 2026

**S03 завершена. Следующая карточка по указанию пользователя — R03.** Исходный checkpoint `3d87f1c`, проверенный source HEAD `20bbe106fe9870d3b57a874e130bd8b3226d90a7`. Root оркестрировал Luna Max; поведение выводилось из Android-источника и используемой WorkManager2.11.2, затем тесты/исходный запуск, минимальный код, GREEN и review. Карточка закрыта после интегрированного gate, а не по одному аудиту.

## Что принято

| Требование | Реализация и доказательство |
| --- | --- |
| Массовое обновление с прежними eligible-группами | Общий retained batch уже был подключён к обоим хостам. Сохранены enabled/nonblank URL, изоляция ошибок, progress, повторный вход, per-ID координация и latest-state/partial-write/cancellation commit. Восстановлена потерянная Android Default-граница load/import; Desktop batch использует ту же проверенную background load-функцию, что single refresh. |
| Одна загрузка/сверка/запись результата | Android UI/install/worker достигают существующего общего load/apply пути. Desktop manual/install/batch/scheduled используют один real single adapter и общие load/commit, с aggregate commit для нескольких успешных batch responses. Metadata/embedded profile/latency и runtime reconnect используют реальные receipts/change flags. |
| Install и повторный URL | Общая операция сначала ожидает сохранение latest catalog, затем вызывает один refresh. Exact-URL duplicate сохраняет всю запись и saved title, включая enabled=false; explicit install refresh всё равно выполняется. Ошибка fetch оставляет новую запись сохранённой. Общий formatter сохраняет Android duplicate notice. Desktop ожидает реальный результат и принимает SKIPI subscription links через существующий clipboard route. |
| Расписание и worker outcomes | Общие work specs сохраняют eligibility, unique UPDATE/CONNECTED и backoff15m; shared periodic state воспроизводит enqueue/completion/retry timing и cap5h используемой WorkManager. Android остаётся реальным WorkManager adapter. Desktop Main-level timer работает независимо от открытого Proxy screen; прежняя очередь ID и UI consumer удалены. |
| Ошибки, сеть и отмена | Одна Desktop admission для manual/install/batch/auto; после ожидания заново читаются запись и scheduled eligibility, параметры берутся из актуальных настроек/Core. Load failures классифицируются общим Android policy; storage failures постоянные, stale/no-error receipt — success. Offline после ожидания даёт deferral, без ложного success; потеря CONNECTED отменяет активный child и сохраняет due work. Cancel не проглатывается. |
| Параметры fetch | Сохранённые UA/Age/proxy и текущие device-header/timeout передаются в реальные host fetchers; shared refresh не создаёт второй transport. Проверены реальные headers на изолированном local HTTP, options mapping и сохранённые поля disabled duplicate. Host preference defaults отдельно относятся к T02: текущие Android10s/Desktop30s не объявляются общей канонической настройкой этой карточкой. |

## Tests-first и исправления тестовых фикстур

До production: Android dispatcher дал **2 поведенческих RED**; classifier/install имели работающие Android baselines и compile/API RED общего пути; новые periodic/operation/coordinator API были проверены отсутствующими до реализации. Для последних CONNECTED гонок после добавления только порта сетевого факта получен **настоящий behavior RED: 14 cases/2 failures**, затем **14/14 GREEN** под теми же hashes.

Первый Desktop gate был 149/151. Две ошибки новых тестов исправлены после сверки с прежним Android/shared контрактом: явная cancellation check в NonCancellable fixture и semantic routing assertions вместо ошибочной raw-byte equality профиля с canonical SKIPI metadata. Production под эти ошибки не менялся. Первые/исправленные hashes и исходные failures сохранены; неизменность всех первоначальных ожиданий не заявляется. Подробности — [Desktop wiring verification](s03-desktop-wiring-verification.md).

Малые checkpoints: `df64c72` аудит/возобновление, `8f539e2` Android background load, `6151e58` shared failure policy, `f21e245` shared install, `2e7f475` periodic state/spec, `5dc98e8` Desktop wiring, `20bbe10` CONNECTED fix. Отчёты срезов и исходные исследования сохранены в этой директории и `outputs/drafts/s03-*`.

## Финальный интегрированный gate

Один свежий Gradle-прогон восьми test targets с отдельным `--rerun` для каждого, **3m53s**:

| Target | Cases | Failures/errors/skips |
| --- | ---: | ---: |
| shared/core Desktop | 388 | 0 |
| shared/core Android | 329 | 0 |
| shared/app Desktop | 357 | 0 |
| shared/app Android | 349 | 0 |
| shared/ui Desktop | 122 | 0 |
| shared/ui Android | 122 | 0 |
| Android app | 593 | 0 |
| Desktop app | 646 | 0 |
| **Всего** | **2906** | **0** |

В составе Desktop suite реальные FFM/Core **9/9**: Core4, global routing resources1, profile assets1, selected-server3. `SKIPI_CORE_INTEGRATION_DIR` указывает на фактические generated native/assets. Desktop compile и assembleDebug GREEN. Все **30 Kotlin hashes** до/после полного gate совпали. Root и независимые Luna reviewers проверили source wiring; [финальный source audit](../../outputs/drafts/s03-final-acceptance-audit.md) не нашёл дополнительного S03 blocker.

Evidence: `outputs/s03-final-full-reports-20261024/`: восемь XML-каталогов, `counts.json`, `native-counts.json`, `source-provenance-before.json`, `source-provenance.json`, `gradle-output.txt`, `apk.json`. Debug universal APK — `app/build/outputs/apk/debug/app-universal-debug.apk`, 167227445 bytes, SHA-256 `f825a664c9544397a330f3ed68e77194412e0290101fb5630f5f023725f20530`; на устройство этот artifact не устанавливался.

## Границы доказательства и продолжение

Desktop scheduler работает при живом app host; daemon/обновление после выхода не добавлялись. Переносимые deep-link parsing/install подключены; Desktop OS registration/CLI/external receiver остаются host integration границей L01. Full expiry preferences/runtime hookup и проверка settings defaults остаются T02; Android сохраняет существующие alarm/notifier. Эти границы не исключают будущий перенос Android-функции из-за её отсутствия в Desktop. Полного физического Android UI/OS intent dispatch и Desktop external-launch/manual UI прогона здесь не было; реальные offscreen Compose сценарии входят в suites.

Закрыто **15/39 карточек по количеству**, не по объёму. Следующая — **R03: app selection / portable per-app editor/state/serialization**, с Android package/icons/user-space/VPN adapters. Desktop per-app enforcement без соответствующего runtime не проектируется. R03 этим checkpoint не закрывается и её production не начат. Посторонняя native submodule правка сохранена отдельно.
