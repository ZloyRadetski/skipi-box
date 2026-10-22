# P03 — batch cancellation и завершение частичного commit

22 октября 2026. Scoped GREEN; итоговая проверка полной P03 ещё следующая.

## Источник и дефекты

Shared `commitSubscriptionRefreshBatch` проверяет отмену перед записью серверов, затем входит в `updateMetadataAndProfiles` без нового cancellation fence. Android `AndroidSubscriptionRefreshBatchCommitPort` обновляет metadata и все embedded profiles внутри одной синхронной `updateAppState` границы, затем вызывает общий group reconcile. Desktop repositories также выполняют фактические записи синхронно под lock, но три дополнительных `ensureActive` в metadata/profile адаптере останавливали эту границу после уже сохранённых серверов.

После добавленного embedded-profile group reconcile у Desktop стал достижим partial failure после успешного сохранения активного профиля. Runner сохранял change flags в `finally`, а Main проверял их только после нормального возврата `run()`, поэтому исключение пересчёта пропускало существующий Desktop reconnect request.

## Тесты до кода и RED

Новый `lateBatchCancellationStillCommitsMetadataProfilesAndGeneratedGroups` отменяет child Job one-shot hook'ом после успешного настоящего server JSON save. Проверяет сохранённые metadata обоих providers, оба embedded profiles, active ID, generated groups и flags, при этом `await()` по-прежнему бросает CancellationException.

Первый прогон: 6 executions, 5 PASS / 1 FAIL, 23 s. До metadata assertions обнаружилось ошибочное ожидание порядка тестового снимка: shared `SubscriptionServerCollectionReconciler` формирует `importedServers + otherServers + updatedCompositeServers`. Root source review подтвердил порядок `[50,51,10,11,12]` и group IDs `[4,5,null,null,null]`; исправлены только эти две промежуточные проверки **до production fix**. [Первоначальные XML/counts](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-batch-profile-cancellation-red-reports-20261022/counts.json).

Повторный содержательный behavioral RED: **6 executions, 5 PASS / 1 FAIL, 5 s**. После server save metadata остались `[Provider 4, Provider 5]` вместо `[Updated Four title, Updated Five title]`. [RED evidence](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-batch-profile-cancellation-red-retry-reports-20261022/counts.json).

До callback implementation добавлены пять `DesktopSubscriptionRefreshBatchRunnerCallbackTest` с настоящими temporary subscription/config/server JSON и runner/adapters:

- обычный server-only commit уведомляет один раз после flags `(true,false)`;
- активный профиль сохранён, новая группа не сохранилась — исключение распространяется, уведомление одно, flags `(false,true)`;
- все fetch завершились ошибками — commit callback отсутствует;
- серверы и профили не изменились — callback отсутствует;
- отмена после server file save сохраняет captured flags и уведомление, а caller остаётся отменённым.

Чистый API RED **2 s**, единственная ошибка `No parameter with name 'onCommittedLibrariesChanged' found`, tests executed 0. [API evidence/hash](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-batch-profile-finalization-api-red-reports-20261022/result.json). Оба окончательных test source SHA совпадают до production и в GREEN; expectations после содержательного RED не менялись.

## Исправление и GREEN

Убраны три metadata/profile cancellation checks; shared pre-commit и Desktop server-stage guard сохранены. Не добавлены NonCancellable, новые retries или конфликтные политики. Отмена продолжает распространяться обычными coroutine границами.

Runner получил узкий optional `onCommittedLibrariesChanged`, вызываемый из существующего commit `finally` после захвата flags, один раз при server/active-profile delta. Это commit boundary, не общая финализация `run()`. Main переносит прежний reconnect request в этот callback и читает свежий `coreController.state().isRunning`; прежний after-run check удалён. Это сохраняет существующую Desktop host policy при partial failure, не вводит общий Android reconnect algorithm и не меняет automatic profile worker.

Проверка **37/37 GREEN**, failures/errors/skips 0, BUILD SUCCESSFUL **11 s**, Desktop compile GREEN: embedded/group suite 6, runner callback 5, single adapter 18, batch adapter 2, batch runner 3, group reconciler 3. [XML/counts/hashes](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-batch-profile-finalization-green-reports-20261022/counts.json), timestamp `2026-10-22T20:49:00Z`.

Независимое Luna review не нашло required findings. Для callback явно записан non-throwing host contract: текущий Main только читает snapshot и обновляет reconnect state, без I/O. Новый catch/error policy не добавлен.

Logs: `outputs/p03-batch-profile-cancellation-{red,red-retry}-20261022.log`, `outputs/p03-batch-profile-finalization-{api-red,green}-20261022.log`. Offline Gradle `:desktopApp:test` с указанными filters и `:desktopApp:compileKotlin`, JDK 26/test JDK 21, native hevtun tasks исключены. Main binding проверен source review; UI/Core reconnect физически не запускался. Этот scoped gate не является полным suite/APK/native run, пользовательские данные и приложение сохранены.
