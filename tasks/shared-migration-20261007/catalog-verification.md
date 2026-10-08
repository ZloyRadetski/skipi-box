# Первый срез: каталог серверов и результат редактора

Дата: 7 октября 2026 года. Ветка: `refactor/shared-server-catalog`.
Исходный HEAD: `ff5f5d8f3cc32bf0b0fc91cfc96b6266103d5be8`.
Статус: общий create/edit и Android/Desktop integration проверены и закоммичены. Независимое ревью завершено; его lifecycle regression исправлен после поведенческого RED. Ручная UI QA ещё не выполнена.

## Сценарии и исходное поведение

| Сценарий | Зафиксированный контракт |
|---|---|
| Новый черновик | Сохраняемый ID выделяется из актуального каталога при commit; открытие/отмена не резервируют ID |
| Порядок нового сервера | Prepend по исходному Android helper; valid selection сохраняется |
| Edit существующего | Прежние ID, позиция, актуальная группа и metadata сохраняются |
| Edit после удаления записи | Восстановление с тем же ID и captured group, без нового stale-editor error; prepend по каноническому helper |
| ID занят импортом до сохранения NEW | Сохранить импортированный сервер и создать новый с актуальным ID |
| Counter/high-water | Не уменьшать счётчик, учитывать сохранённые ID; MAX — exhaustion sentinel, без частичной записи |
| Ошибка commit | Возвращать failure; success/group effects выполняются после Completed |
| Android persistence | Completed означает AppState update + существующий enqueue Room persistence, без новой гарантии durable ACK |
| Desktop persistence | Save до publication; существующий temp-file/replace контракт сохраняется |
| Opaque Desktop запись | Raw payload и существующий выбор сохраняются, ID участвует в high-water |
| Локальная raw .conf proxy group | ID −1 и собственный document consumer остаются отдельным сценарием; он не записывает app catalog |

## Причина минимального metadata расширения

Desktop сохраняет недекодируемые raw записи, которые отсутствуют в typed shared servers. Один nextServerId позволяет избежать большинства ID-коллизий, но не сообщает shared алгоритму, что selectedServerId принадлежит существующей opaque записи. Новый create поэтому ошибочно переключал выбор 99 → 100.

`ProxyServerCatalog.retainedServerIds` передаёт только ID таких реально сохранённых строк. Payload, формат JSON, raw merge и возможность исполнять неизвестный протокол остаются у Desktop adapter. Shared allocation/selection используют эти ID; DTO не становится ещё одним хранилищем opaque payload.

Это сохраняет прежнюю Desktop совместимость и не добавляет продуктную функцию. Android использует пустой набор. Поле не меняет persisted форматы.

## Последовательность тесты → код

1. Luna написал common create/edit tests и два store tests до production-файла.
2. `/tmp/skipi-catalog-c01-red.log`: исходный Gradle запуск остановился на отсутствующих create/edit API. Это compile RED, не поведенческий запуск.
3. После минимального common implementation `/tmp/skipi-catalog-c01-green.log`: `:shared:app:allTests` прошёл — 102 Android + 102 Desktop, 0 failures/errors/skips.
4. C02/C03 tests написаны до host integration. `/tmp/skipi-catalog-c02-c03-red.log`: Desktop opaque-selection regression реально упал с expected 99 / actual 100; Android seam отсутствовал. В Android тесте также исправлен неверный импорт аннотации JUnit.
5. `/tmp/skipi-catalog-c02-red-clean.log`: повторный Android RED имеет только отсутствующий DTO/outcome/apply API; неправильного JUnit import больше нет.
6. Три common retained-ID tests добавлены до metadata/model/helper изменений. `/tmp/skipi-catalog-c03-retained-red.log`: compile RED на отсутствующем поле retainedServerIds.
7. `/tmp/skipi-catalog-c02-c03-green.log`: shared/app 105 Android + 105 Desktop, Desktop adapter 20 tests, Android result seam 5 tests — GREEN.
8. Те же пять pure result-seam tests перенесены в commonTest без изменения ожидаемого поведения. Первоначальный checkpoint выявил Android-only импорт DefaultSubscriptionGroupId; заменён только test fixture с тем же значением 1.
9. `/tmp/skipi-catalog-c02-c03-checkpoint-green.log`: полный Q2 — core 216×2, app 110×2, ui 59×2, Desktop 191, Android 385: всего 1346 tests, 0 failures/errors/skips. Desktop compile и Android assembleDebug — GREEN. HevTun build/sync исключены, использованы существующие native artifacts.
10. Desktop Home и visual editor сохраняют captured group в общем DTO; Main применяет DTO общим сценарием. Удалены direct add/update/save из editor path. Ревью исправило NEW-сообщение и привязало reconnect к факту выбора Saved.serverId, согласно ранее написанным selection tests. Отдельный compile после этой binding-правки фиксируется в `/tmp/skipi-catalog-desktop-binding-green.log`.
11. Независимое ревью выявило изменение lifecycle: clearResult + direct dispatchAndAwait теряли consumed edit result при отмене caller, ожидающего store mutex. До fix написан `consumedNewDraftResultRemainsQueuedAfterCollectorCancellation`. `/tmp/skipi-catalog-lifecycle-red.log`: поведенческий RED, expected catalogUpdateCount 2 / actual 1.
12. Добавлен editor-only store-owned-await API; существующий cancellable dispatchAndAwait остальных actions не изменён. `/tmp/skipi-catalog-lifecycle-green-c04-red.log`: shared/app 111 Android + 111 Desktop GREEN (включая lifecycle regression). Desktop в том же запуске намеренно RED только на следующем, ещё не реализованном C04 typed-add contract.

Оставшиеся прямые writers импорта/refresh и выбор участника StrategyGroup относятся к последующим I/S/H-срезам; этот этап подключил editor add/edit. Новый результат редактора не создаёт общей транзакции для всех этих сценариев.

ADB доступен, но список устройств/эмуляторов пуст. Android manual UI QA не выполнена. Desktop visual QA и реальный native Core integration для этого среза не запускались; обычный test suite не доказывает FFM старт.

## Проверенные исходники

- [Common catalog operations](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerCatalogOperations.kt).
- [Common catalog contract](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/model/ProxyServerCatalog.kt).
- [Common catalog tests](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonTest/kotlin/app/skipi/app/proxy/ProxyServerCatalogOperationsTest.kt).
- [Store tests](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonTest/kotlin/app/skipi/app/store/SharedApplicationStoreTest.kt).
- [Desktop adapter tests](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/test/kotlin/app/skipi/desktop/DesktopProxyServerRepositoryTest.kt).
- [Editor result application tests](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonTest/kotlin/app/skipi/app/server/ProxyServerEditResultHandlerTest.kt).

## Сохранённые изменения

- `205932f`: постоянные правила tests-first/shared migration.
- `86cd255`: общий create/edit contract и tests-first verification.
- `b525f77`: retained opaque IDs и общий allocation/selection, adapter regression/concurrency tests.
- `0549c5d`: общий editor-result scenario, Android consumers и store-owned lifecycle fix с шестью common tests.
- `8be56c5`: Desktop editor save wiring, captured group и корректные host effects после Saved.
- Пользовательские изменения native submodule не включаются в коммиты среза. Tasks остаются локальными рабочими документами.
