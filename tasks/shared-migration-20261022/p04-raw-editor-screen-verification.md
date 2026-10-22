# P02/P04 — raw editor через меню профиля

22 октября 2026. Проверочный срез без production изменений. P02/P04 остаются открытыми: остальные lifecycle/form/render критерии идут отдельно.

Android `TrafficConfigRawEditorPage` при Back сохраняет непустой документ без Error diagnostics, нормализует до одного завершающего newline и выходит. Ошибочный/пустой документ не записывает, но также выходит. Shared `TrafficConfigEditorController.leaveRaw` сохраняет эту семантику; storage failure — отдельное основание остаться в редакторе. Новых Save/discard сценариев для Android не добавляли. Существующая Desktop кнопка Save проверена отдельно как текущий host surface, использующий shared writer.

До любого нового production написаны три actual Compose screen tests в `DesktopTrafficConfigCollectionScreenIntegrationTest`:

- Profile More → Raw Edit → valid Android oracle `.conf` → Back. Синхронизируются raw и name/URL/lock/profile/resource settings; сохраняются target ID/time, порядок, активный sibling и next ID. General/Proxy/Vendor data остаются в документе, newline нормализован.
- Тот же маршрут → изменение name/URL/lock через form widgets и General в raw → существующая Desktop Save. Та же проверка shared metadata writer и library envelope.
- Тот же маршрут → два `FINAL` → видимые Error diagnostics → Back. Документ отклонён, редактор закрыт, persistence не вызван, вся библиотека прежняя.

Тесты используют public semantics, scoped profile-row More и узкий setter по точному текущему EditableText. Product callbacks напрямую не вызываются. Valid fixture совпадает с `TrafficConfigEditorAndroidOracleTest.rawSaveUsesAndroidCollectionUpdaterToReadThenRewriteKnownSkipiSettings`; expected поля заданы явно, без вычисления ожидаемого результата через writer.

Current-code baseline **3/3 GREEN**, failures/errors/skips 0, BUILD SUCCESSFUL **27 s**. Реализацию менять не потребовалось, RED не заявляется. Root command: offline `:desktopApp:test --tests 'app.skipi.desktop.DesktopTrafficConfigCollectionScreenIntegrationTest.rawEditMenu*'`, JDK 26/test JDK 21, исключены native hevtun tasks. Предварительная попытка agent wrapper не стартовала из-за sandbox cache-lock permissions и не считается тестовым прогоном; фактический gate запускал root.

[XML/counts/source hash](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p04-raw-editor-screen-reports-20261022/counts.json), timestamp `2026-10-22T20:58:04Z`; log `outputs/p04-raw-editor-screen-baseline-20261022.log`. Это только три новых cases; остальные 19 collection cases относятся к [предыдущему полному P03 checkpoint 2751 GREEN](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/shared-migration-20261022/p03-acceptance-verification.md), не к этому filtered run. Production не менялся, повторные APK/native/full-suite проверки для test-only дополнения не выполнялись. PNG/device QA этот gate не доказывает.

Дополнительно source audit подтвердил, что explicit Add → URL dialog создаёт/добавляет профиль на обеих платформах через `createTrafficConfigProfile`. Отдельный URL-from-clipboard path делает dedup import через `importTrafficConfigDocument`. Расхождения на этих двух разных public routes нет, код не менялся.

Далее — actual Basics/General/DNS/Tunnel/Network forms и их narrow/wide render matrix, затем недостающая actual profile lifecycle menu приёмка. Source-derived specs подготовлены отдельно до тестов/production.
