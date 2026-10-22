# P03 — группы после импорта профиля из подписки

22 октября 2026. Проверенный малый срез; полная карточка P03 ещё открыта.

Android после одиночного и пакетного импорта embedded-профиля сравнивает полный список профилей и active ID, затем вызывает общий reconcile связанных групп (`ProxyServerUseCases.kt`, `applyEmbeddedConfig` и `updateMetadataAndProfiles`). Desktop сохранял и публиковал профили, но пропускал этот шаг. Флаг `profileLibraryChanged` отражает только изменение активного профиля для переподключения и не подходит как условие пересчёта: группа с `skipi-display=always` должна обновляться и у неактивного профиля.

## Тесты до реализации

Добавлены пять тестов `DesktopSubscriptionProfileGroupReconciliationTest`, использующих настоящие временные `configs.json`/`servers.json`, Desktop repositories/adapters и shared commit entry points:

- Одиночное обновление существующего неактивного профиля сохраняет выбранный sibling, ID и порядок групп; меняет состав `Stable` и выбирает первый допустимый узел после удаления прежнего участника. `activate=true` для существующего профиля не переключает active ID, `profileLibraryChanged=false`.
- Пакетный импорт создаёт обе группы в порядке профилей, с правильными source ID и следующим ID.
- Невалидный документ с двумя `FINAL` не сохраняет профиль и не запускает пересчёт даже намеренно устаревшей группы.
- Ошибка сохранения профиля оставляет уже сохранённые серверы/подписку, не публикует профиль и не сохраняет группы.
- Ошибка сохранения новой группы сообщает PROFILE failure, сохраняя уже записанный профиль и прежнее состояние серверного файла.

До RED review исправил ожидание active ID по фактическому shared import: существующий профиль 7 остаётся неактивным при выбранном 8. Также stateless helpers вынесены из внешнего instance scope, чтобы вложенная fixture компилировалась. Эти изменения сделаны до production. После RED ожидания и source теста не менялись; SHA-256 `432694c359be5760a506d9c4cadb0c8e118214c9f7c3552a665086bb1c8b66bb` совпадает в обоих gates.

Исходный прогон: **5 executions, 2 PASS / 3 FAIL**, BUILD FAILED за 48 s. Одиночный refresh получил `[10]` вместо `[11, 12]`; batch не нашёл сгенерированные группы; injected group-save failure получил `null` вместо PROFILE. Это поведенческий RED, не ошибка компиляции. XML/counts: [RED evidence](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-embedded-profile-groups-red-reports-20261022/counts.json). Log: `outputs/p03-embedded-profile-groups-red-20261022.log`.

## Изменение и GREEN

Оба Desktop commit adapters после успешных save/publish и установки reconnect-флага вызывают существующий `DesktopTrafficConfigProxyGroupReconciler`, если изменились configs или selected ID. Batch делает это один раз после агрегированного импорта. Алгоритм reconcile остаётся shared; используется общий `stripLeadingCountryFlag`. Ошибка пересчёта выходит через существующий PROFILE/aggregate failure boundary. Два сообщения Main уточнены до «обновление профиля не завершено», поскольку профиль к моменту ошибки групп уже может быть сохранён.

Проверка: **38/38 GREEN**, failures/errors/skips 0, BUILD SUCCESSFUL за 13 s, Desktop compilation GREEN:

| Suite | Executions |
| --- | ---: |
| Новые embedded profile/group tests | 5 |
| DesktopSubscriptionRefreshCommitAdapterTest | 18 |
| DesktopSubscriptionRefreshBatchCommitAdapterTest | 2 |
| DesktopSubscriptionRefreshBatchTest | 3 |
| DesktopTrafficConfigAutoRefreshTest | 7 |
| DesktopTrafficConfigProxyGroupReconcilerTest | 3 |

XML/counts: [GREEN evidence](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/outputs/p03-embedded-profile-groups-green-reports-20261022/counts.json), timestamps `2026-10-22T20:27:37Z`. Log: `outputs/p03-embedded-profile-groups-green-20261022.log`. Root review подтвердил отдельные условия reconciliation и reconnect, порядок save → publish → flag → reconcile и сохранённую batch/invalid-import семантику.

Команда обоих gates: `:desktopApp:test` с указанными suite filters, GREEN также `:desktopApp:compileKotlin`; Gradle offline, JDK 26, test JDK 21, исключены `:hevtun:buildHevTun` и `:hevtun:syncHevSocks5TunnelVersion`.

Этот gate не является повторным полным project suite, APK/native/UI прогоном. Временные файлы изолированы; пользовательское приложение и native submodule не менялись. Следующий подтверждённый пробел P03 — direct manual profile refresh с текущими opt-in device headers; тесты готовятся отдельно до реализации.
