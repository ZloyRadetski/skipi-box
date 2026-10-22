# P03 — итоговая приёмка обновления профилей

22 октября 2026. **P03 завершена.** Проверенный production commit: `e4b25cc` (после `3188fbf`, `be905eb`, `9f665aa`). P02/P04 остаются открытыми как самостоятельные полные карточки; их оставшиеся экранные проверки не выдаются за выполненные в этой приёмке.

## Критерии и подключение

| Критерий P03 | Общая операция и реальные host entry points | Проверка |
| --- | --- | --- |
| Eligibility/intervals: locked, disabled, empty URL; profile отдельно от geo | `TrafficConfigRefreshScheduling`, общий profile WorkSpec и periodic timing record; Android schedule gateway/worker, Desktop отдельный profile updater и generic loop в Main | Shared schedule/timing suites; Desktop owner/loop tests: отмена/повторное включение, offline admission, UPDATE, retry, отсутствие дубликата после устаревшего due snapshot |
| Один automatic fetch → normalize/validate → commit → reconcile | Shared/app `executeTrafficConfigAutoUpdate`; Android worker делегирует ему; Desktop `DesktopTrafficConfigAutoRefresh` и реальный периодический host подключены в Main | Семь source-derived Android/common execution cases; Desktop bridge/owner; HTTP server → настоящий fetcher → временные profile/server JSON → общий group reconcile |
| Manual и automatic используют Android commit semantics без revision/CAS | Общие request/confirmation/`TrafficConfigProfileRefreshOperations.apply`; Android Page/Worker и Desktop Screen/AutoRefresh применяют ответ к latest same-ID row | Shared/Android commit suites; Desktop actual menu: captured snapshot/trimmed URL, late edit, response fields, lock confirmation, удаление/прочие строки и ID metadata |
| Ручной fetch direct, automatic — через фактический running proxy | Main передаёт разные instances `DesktopTrafficConfigProfileFetcher`; оба читают свежие settings, manual `activeProxy={null}`, automatic — actual runtime endpoint | Transport binding UA/timeout/headers/credentials tests; root и независимое source review Main wiring |
| Edit/refresh/active selection сохраняют ID и актуальное членство групп | Shared `reconcileConfigProxyGroups`; Desktop repository adapter; оба embedded import adapters и Main config transform callback | Actual menu → suspended fetch → late same-ID edit → profile selection 7→8 → response → async reconcile → disk reload: group ID/member retention, sibling isolation, opaque selection/counters |
| Embedded profile import из подписки | Одиночный и batch commits используют общий import; после save/publish вызывают существующий shared group reconcile | Temp-file tests: inactive existing import, два batch profiles, invalid document, profile-save failure, generated-group-save failure и partial persistence |
| Batch cancellation и post-save failure | Desktop metadata/profile boundary соответствует синхронной Android записи; runner commit notification сохраняет существующий Main reconnect при partial failure | Late cancellation после actual server JSON save; пять actual runner/adapter callback cases; fresh Core state binding проверен source review |

Scheduler/network facts, HTTP, WorkManager, clock/job ownership, filesystem и Core остаются host adapters. Чистые policies/commit/import/reconcile/timing используют shared. Новые defaults, stale-response fences, NonCancellable, retry policies или автоматическое переподключение profile worker не добавлены. Старый Android-only execution helper заменён shared executor; старый geo-only timing holder заменён общим record с прежней resource eligibility.

## Tests-first история

- [Automatic executor и Desktop transport](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261022/p03-profile-auto-refresh-execution-verification.md): Android helper API RED → source baseline, common API RED → Android delegate/shared executor, Desktop bridge и binding API RED → GREEN.
- [Общий timing и Desktop periodic host](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261022/p03-periodic-profile-verification.md): source baseline, common/host API RED, отдельный воспроизведённый stale due-snapshot duplicate → fix под тем же regression test.
- [Embedded profile groups](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261022/p03-embedded-profile-groups-verification.md): поведенческий RED 3/5 → 38/38 GREEN.
- [Manual screen/transport](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261022/p03-manual-profile-refresh-verification.md): окончательные четыре теста до production, API RED → 59/59 GREEN. Исходный ошибочный UI selector сохранён в evidence; исправлена адресация действия без изменения expectations/timeouts.
- [Batch finalization](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261022/p03-batch-profile-finalization-verification.md): source-corrected cancellation behavioral RED + callback API RED → 37/37 GREEN с совпадающими pre-code/GREEN hashes. Первое неверное промежуточное ожидание server ordering исправлено по каноническому reconciler до production; исходный результат тоже сохранён.

Root и независимые Luna reviews проверили source semantics и итоговые изменения. Найденные batch cancellation/reconnect findings исправлены отдельным tests-first срезом; required findings не осталось.

## Полный итоговый прогон

**Один успешный invocation: BUILD SUCCESSFUL за 1 min 15 s; 2751 executions, failures/errors/skips 0.** Все восемь suite groups относятся к этому прогону, без объединения старых filtered XML.

| Suite group | Tests |
| --- | ---: |
| shared/core Desktop / Android host | 361 / 305 |
| shared/app Desktop / Android host | 348 / 340 |
| shared/ui Desktop / Android host | 120 / 120 |
| Android app unit | 574 |
| Desktop app | 583 |
| **Всего** | **2751** |

Desktop compile и Android debug APK GREEN. В Desktop count включены **7/7 actual FFM tests**: Core 4, profile assets 1, selected-server Core 2, с заданным `SKIPI_CORE_INTEGRATION_DIR` и существующей `libskipicore.so`. Это фактические Core starts/operations, не только обычные unit tests.

[Полные XML/counts/timestamps](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-final-full-reports-20261022/counts.json): `2026-10-22T20:51:18Z`–`20:52:23Z`. Log: `outputs/p03-final-full-gate-20261022.log`.

```sh
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
  ANDROID_HOME=/home/vqsego/Android/Sdk \
  ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
  SKIPI_CORE_INTEGRATION_DIR=/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/build/generated/skipi-core-resources/common \
  ./gradlew --offline \
  :shared:core:allTests :shared:app:allTests :shared:ui:allTests \
  :app:testDebugUnitTest :desktopApp:test :desktopApp:compileKotlin :app:assembleDebug \
  -PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 \
  -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
```

## Границы доказательства и продолжение

Физический Android/Desktop UI QA и запуск Android WorkManager через framework не выполнялись. Worker input/context guards и Main runtime wiring проверены source/compile; portable worker execution проверен отдельно. Periodic tests используют управляемое время, без многосуточного ожидания реального timer. Manual host/reconnect callbacks в Main проверены source review, без перезапуска пользовательского приложения. Native hevtun не пересобирался; чужое изменение submodule и пользовательские данные сохранены.

Следующий малый P02/P04 gate — actual profile More → Raw Edit → valid Back/существующий Desktop Save/invalid Back. Источник Android сохранён в подготовленном test-only draft; production change потребуется только при воспроизведённом расхождении. Общий lifecycle и остальные формы уже имеют controller/store evidence, их экранная/visual приёмка остаётся отдельной работой.
