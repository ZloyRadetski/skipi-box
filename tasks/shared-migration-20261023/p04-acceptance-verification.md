# P04 — приёмка общего списка и визуального редактора профиля

Статус: завершена 23 октября 2026 года. Карточка P04 из `tasks/plan.md` закрыта по переносимым формам и host entry points Android/Desktop. Глобальный routing остаётся R01, полная app projection/inventory и VPN enforcement — R03, полный runtime/export — X03/X04.

## Критерии и доказательства

| Критерий P04 | Реализация и проверка |
| --- | --- |
| Общий список и контекстное меню профиля | Общая collection presentation/projection, Desktop и Android hosts; actual menu, metadata, unsupported-section notice и responsive rendering проверены в [collection checkpoint](../shared-migration-20261022/p04-config-collection-verification.md). Последующие lifecycle/menu/guard проверки — [P02 acceptance](p02-acceptance-verification.md). |
| Basics, General, DNS, Tunnel и Network | Общие формы и controller/store, source-derived Android oracles; Save и Back сохраняют профиль по прежней семантике. Actual формы, navigation и четырнадцать PNG двух размеров — [forms checkpoint](p04-profile-forms-screen-verification.md). Покрытие widgets представительское, не исчерпывающее для каждого поля. |
| Validation DNS и граница записи черновика | Новый actual UI baseline проверяет malformed/valid TUN DNS, Direct DNS server, domain и host; inline errors только там, где они существуют в source. До выхода нет library writes, Back пишет один раз; typed/raw SKIPI и прочие секции сохраняются. [Verification](p04-validation-resources-screen-verification.md). |
| Profile Routing, Rules и ProxyGroups | Переносимые screens/forms/selector flows подключены к обоим хостам. Android-путь Basics → Routing → Resources/Rules/ProxyGroups сохранён. [Rules](../shared-migration-20261020/p04-rules-verification.md), [ProxyGroups](../shared-migration-20261021/p04-proxy-groups-verification.md). Это profile-specific UI; глобальный routing отдельно в R01. |
| Profile Resources | Общий resource screen и host adapters уже проверены в [R02](../shared-migration-20261020/r02-verification.md). Дополнительный actual More → UI Edit inactive profile → Routing → Resources baseline доказывает immediate save, late same-ID document preservation и Back без дополнительной записи. [Verification](p04-validation-resources-screen-verification.md). |
| Per-App portable editor и реальные capabilities | Общий mode/menu/clipboard/pending-import/controller path подключён к Android/Desktop. Android сохраняет package/user/search/icon/native slots; Desktop показывает реальное ограничение и редактирует сохраняемый mode/list через clipboard. Восемь actual сценариев, включая late-row/Missing/Failed и Back, шесть PNG — [Per-App verification](p04-per-app-screen-verification.md). |
| Raw editor и SKIPI serialization | More → Raw Edit → valid Back / existing Desktop Save / invalid Back проверены в [raw editor checkpoint](../shared-migration-20261022/p04-raw-editor-screen-verification.md). Другие raw-секции и envelope сохраняются в пределах source codec; произвольный malformed/unknown SKIPI round-trip не обещается. |

## Финальная inventory и review

Свежий полный Per-App gate: **2822/2822 GREEN**, failures/errors/skips 0, 117 секунд. Все восемь test targets выполнены заново через per-task `--rerun`; core Desktop372/Android316, app355/347, UI120/120, Android584, Desktop608. Desktop compile/debug APK и реальные Core **7/7** GREEN. Production/tests — `35728b7`, отчёт — `5d6e474`. [Counts/XML/PNG](../../outputs/p04-per-app-screen-full-green-reports-20261023/counts.json).

После этого добавлены только два приёмочных UI-теста; реализация не менялась. Новый scoped baseline: **2/2 GREEN**, 30 секунд, SHA исходника неизменен во время прогона. [Counts/XML/provenance](../../outputs/p04-validation-resources-baseline-reports-20261023/counts.json). Итоговая inventory **2824 GREEN** складывается из явно названных двух прогонов; единого прогона 2824 не заявляется. Старые 608 Desktop cases не запускались повторно без production-изменения.

Независимый source review и root review проверили перенос Per-App, host binding, validation/resource tests и границы сохранения. Замечание о lifetime общего child scope устранено возвратом host-owned action scope, соответствующего исходному Android; потеря notice тестом не воспроизведена. Поведенческие ожидания не подгонялись; исправления navigation/semantics harness и compile dependency отражены отдельно в Per-App chronology. Новые DNS/Resources тесты прошли без production correction.

## Пределы и продолжение

Растер/Compose QA выполнялась в изолированных scenes; основная матрица использует 390×844 и 1100×800. Новые DNS/Resources functional cases используют 1100×1300 и 1100×900. Это не physical window/device testing, OS clipboard/file picker, Android framework/package discovery или VPN/TUN enforcement. Native HevTun не пересобирался; чужой submodule и пользовательские данные сохранены, APK не устанавливался, пользовательское приложение не перезапускалось.

Следующий малый срез R01: проверить подготовленные tests для `DesktopRoutingRepository`, получить missing-API RED, затем подключить существующие persisted routing settings к `RoutingRepository`/shared store. [Source research](r01-routing-repository-research.md), [test contract](../../outputs/drafts/DesktopRoutingRepositoryTest.spec.md). Repository-only срез не закрывает global routing UI и runtime planner.
