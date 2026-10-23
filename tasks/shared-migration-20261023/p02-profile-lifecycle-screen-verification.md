# P02 — жизненный цикл профиля через Desktop menu

23 октября 2026, Europe/Moscow. Начальный baseline проверил существующую реализацию. Затем independent audit выявил ложный success status при guarded delete; он исправлен после отдельного поведенческого RED. Итоговая карта приёмки: [P02 acceptance](p02-acceptance-verification.md).

Тесты сначала описаны по текущим Android `TrafficConfigPage`/shared collection операциям, затем добавлены в `DesktopTrafficConfigCollectionScreenIntegrationTest`. Все четыре сценария проходят через настоящие More menu, editor, confirmation и public semantics, сохраняют через `DesktopTrafficConfigRepository` в изолированный `configs.json` и проверяют результат после reload.

| Сценарий | Ожидаемое поведение |
| --- | --- |
| Duplicate snapshot → draft → Save | При открытом меню исходный ID 7 успевает получить новую запись, появляется sibling ID 15 и новый counter 40. Duplicate берёт захваченный старый profile snapshot, но выделяет ID из latest library: `[7,3,15,40]`, next 41, active 3. Копия сохраняет старые URL/lock и `[Vendor]` marker, получает локализованное copy name и timestamp 0. Новая исходная запись и оба siblings сохранены. Правка поля имени остаётся локальным draft до отдельного Save. |
| Enable | Выбор ID 7 вместо 3 меняет только selected ID. Все профили, их порядок и next counter остаются прежними. |
| Active Delete с поздней записью | При открытой confirmation к `[3,7,12]` добавляется ID 40, next 41. Удаление ID 7 применено к latest library: `[3,12,40]`, active fallback 3, counter 41. Поздняя запись не теряется. |
| Last-profile Delete | Подтверждённое удаление единственного ID 8 оставляет целую библиотеку, выбранный ID и counter 17 прежними. Один guarded screen transform attempt не означает удаление или обязательную новую disk write. |

Перед baseline root добавил проверки полного сохранения active sibling в duplicate snapshots и исправил planned fixture counter: поздний ID 40/next 41 сохраняет исходный high-water 29, вместо искусственного понижения counter из первоначального draft. Duplicate marker расположен вне `[SKIPI]`, поскольку writer не обещает сохранять неизвестные ключи этой секции.

Initial root run: **4 executions, 1 PASS / 3 FAIL**, BUILD FAILED **30 s**. Enable прошёл; остальные cases остановились на неверных test selectors: shared menu labels в test locale были `Save`/`Delete`, а legacy Desktop editor/confirmation используют literal `Сохранить`/`Удалить`. Это harness failure до соответствующих действий, не product lifecycle RED. По production source исправлены только три селектора, expectations/timeouts и код приложения не менялись. [Исходные XML/counts](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p02-profile-lifecycle-screen-initial-reports-20261023/counts.json).

Окончательный baseline: **4/4 GREEN**, failures/errors/skips 0, BUILD SUCCESSFUL **10 s**. [XML/counts/source SHA](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p02-profile-lifecycle-screen-reports-20261023/counts.json), UTC timestamp `2026-10-22T21:07:34Z` (уже 23 октября в Москве).

Команда: offline `:desktopApp:test --tests 'app.skipi.desktop.DesktopTrafficConfigCollectionScreenIntegrationTest.profileMenu*'`, JDK 26/test JDK 21, native hevtun tasks исключены. Logs: `outputs/p02-profile-lifecycle-screen-{baseline,baseline-retry}-20261023.log`.

Это четыре новых JUnit cases, без повторного общего suite/APK/Core запуска: production после [P03 полного checkpoint 2751 GREEN](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261022/p03-acceptance-verification.md) не менялся. Остальные 22 collection cases не выдаются за повторно выполненные в этом filtered gate. [Raw Edit 3/3](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261022/p04-raw-editor-screen-verification.md) — отдельный предыдущий baseline. Новых функций, production policies и физических OS/file-picker/clipboard UI проверок этот срез не добавляет.

## Исправление обратной связи после independent audit

Хранение и last-profile guard работали правильно, однако Desktop `persist` показывал «Конфиг удалён.» при любом не бросившем исключение transform, включая no-op. Android guard оставляет профиль без success toast. До production изменения в тот же экранный класс добавлены две проверки: настоящее удаление показывает успех, guarded delete последнего профиля не показывает его.

Поведенческий **RED: 4 executions, 3 PASS / 1 FAIL, 10 s**, единственная ошибка `The last-profile guard must not show the deletion success message`. [RED XML/counts](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p02-delete-feedback-red-reports-20261023/counts.json). После этого изменён только confirmation callback в `DesktopConfigsScreen`: результат remove сравнивается с latest configs; success message появляется через `afterSave` только после фактического изменения и успешного commit. Успешный no-op очищает прежнюю обратную связь, ошибка сохранения сохраняет прежний failure path.

Окончательный связанный gate **44/44 GREEN**, failures/errors/skips 0, BUILD SUCCESSFUL **23 s**: полный `DesktopTrafficConfigCollectionScreenIntegrationTest` **26**, `DesktopConfigLibraryTest` **9**, `DesktopConfigEditorSaveTest` **4**, `DesktopTrafficConfigEditorProfileStoreTest` **5**. [GREEN XML/counts/source SHA](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p02-delete-feedback-green-reports-20261023/counts.json). Source SHA тестов одинаков до и после исправления: `6f8190ced66d11be1f9ffc148437c82369de8ea0e6f318f1b0548062bd9d9116`.

Команда GREEN: offline `:desktopApp:test` с четырьмя указанными `--tests` class filters, теми же JDK/SDK и исключениями native tasks. Logs: `outputs/p02-delete-feedback-{red,green}-20261023.log`. Это scoped gate после последнего полного P03 checkpoint, а не новый полный прогон всех модулей.
