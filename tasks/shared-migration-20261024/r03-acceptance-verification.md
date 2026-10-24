# R03 — итоговая приёмка

24 октября 2026 года. **R03 завершена.** Последовательность пользователя S03 → R03 выполнена. Закрыто **16 из 39 карточек по количеству**, без оценки доли объёма всего рефакторинга. Весь план Android → shared остаётся в работе.

## Что принято

P04 уже подключила общий profile-scoped Per-App editor/controller, режимы и clipboard import/export к Android и Desktop. R03 завершила перенос оставшейся чистой проекции и переносимого списка. Новая глобальная Per-App страница не создавалась: фактический Android entry point открывает список конкретного профиля.

- **shared/core:** plain package/user/item/tab records; name fallback, trimmed case-insensitive label/package search, user buckets/fallback tabs, shared-UID selection keys/counts, fixed-user UID remap/first-package dedup, selected-first refresh sorting. Чистый VPN-policy projector сохраняет mode/strict/global правила, parse user prefix до trim, bare legacy IDs, drop foreign/malformed keys и stable distinct.
- **shared/ui:** строки/checkbox/UID/SUID, tab counts и haptics, empty state, search с исходной задержкой 150 ms, keyed pager и двусторонняя синхронизация выбранного пользователя, scrollbar, pull-to-refresh и loading rendering. Иконка передаётся host composable slot. Общий UI подключён к существующей Android странице; заменённые native UI/effects удалены.
- **Android adapters:** PackageManager enumeration/labels/system scope, self exclusion, user-space facts и VPN current-user restriction, native Coil icon, provider lifecycle/cancel/toast/refresh flags, Default dispatcher wrappers и 500 ms SystemClock loading timing. `VpnService.Builder` и установленный-package fallback остались native. Active-profile overlay/invalid-active fallback/strict runtime override сохраняют профильные данные.
- **Desktop:** прежний общий editor/controller и реальное `configs.json` хранение сохраняют режимы и opaque IDs. Capability notice отражает отсутствие каталога/OS enforcement; фиктивный список и новый механизм application routing не добавлены.

Document/backup contracts различены явно: `[SKIPI]` profile codec trim/drop-empty/dedup в encounter order; common backup DTO копирует typed list точно, а Android restore пишет canonical profile document. Неизвестные package/user ID сохраняются как данные; round-trip неизвестных JSON properties/неописанных `[SKIPI]` полей не обещается.

## Tests-first и review

[Core/native срез](r03-foundation-verification.md): Android source baseline11 + Desktop temporary-store1 GREEN до production, matching common API compile RED, затем focused92 GREEN. Native Builder isolation correction и actual provider loader cases получили отдельный7 GREEN. Core production: `d3b285b`, `0a06c65`; native/preservation checkpoint: `c99b94f`.

[Presentation срез](r03-presentation-verification.md): семь scene fixtures написаны до UI production; API RED зафиксирован после исправления compiler fixture. JUnit void-signature и неверный scroll target в новом harness исправлены отдельно, с исходными логами/хэшами и доказательствами. Ожидания selection/search/tab-removal сохранены; production не менялся ради этих harness failures. Перед полным gate исправленный pager case прошёл1 GREEN. UI production/checkpoint: `ebfca81`.

Root и независимые Luna Max reviewers сравнили production с Android source. В native binding устранён пойманный review edge: Pages получает raw selected user ID, чтобы callback исправлял исчезнувшую вкладку; clipboard и tab rendering получают прежний валидированный fallback. Common imports не содержат Android/Coil/PackageManager/SystemClock. Core records остаются plain Kotlin без Compose runtime dependency; старый plain `AndroidUserSpace` представлен source typealias, все найденные потребители Kotlin.

## Свежий полный gate

Один Gradle прогон всех восьми test targets через task-specific `--rerun`, Desktop compile и debug APK: **2960/2960 GREEN, 1m19s**. Failures/errors/skips **0**; XML всех targets свежие относительно начала gate.

| Target | Tests |
|---|---:|
| shared/core Desktop | 400 |
| shared/core Android host | 341 |
| shared/app Desktop | 357 |
| shared/app Android host | 349 |
| shared/ui Desktop | 122 |
| shared/ui Android host | 122 |
| Android app | 615 |
| Desktop app | 654 |
| **Всего** | **2960** |

Все **7/7** новых actual shared-UI scenes и прежний Desktop Per-App route GREEN. Android tests включают source oracle, active/invalid/no-profile policy, strict preservation, synthetic PackageRepository/provider loader и реальный service policy method с test-only Builder recorder. Per-profile unknown/legacy/other-user IDs проверены через document, common backup codec, actual Android AppState backup/restore preview и временный Desktop editor/store.

С `SKIPI_CORE_INTEGRATION_DIR` выполнены реальные Desktop native **9/9**: CoreFfm4, selected-server3, profile-assets1, global-routing-resources1. Это проверки существующего Desktop Core; Android TUN/packet routing на физическом устройстве ими не подменяется.

Все **25 SHA-256** изменённых Kotlin-файлов совпали до/после полного gate. [Evidence](../../outputs/r03-final-full-reports-20261024): восемь XML-каталогов, `counts.json`, `native-counts.json`, `source-provenance-before.json`, `source-provenance.json`, полный `gradle-output.txt`, `apk.json`.

Debug APK: `app/build/outputs/apk/debug/app-universal-debug.apk`, **167228713 bytes**, SHA-256 `29ac973d0ce48b01c97307b9e4a236dab2d1bdc3983ed75e35fdf8f97136da9b`. APK не устанавливался. Посторонний dirty submodule `hevtun/src/main/jni/hev-socks5-tunnel` сохранён; его build/sync tasks исключены.

## Границы проверки и продолжение

Физическая Android/Desktop OS UI QA, реальные системные package/user/icon/clipboard операции и Android VPN packet enforcement не выполнялись. Native boundary smoke использует изолированный Robolectric PackageManager и Builder recorder; он не вызывает `establish()`. Новые shared components проверены настоящим offscreen Compose scene runtime; отдельная raster/визуальная съёмка этого среза не проводилась.

Для продолжения: [handoff](r03-handoff.md). Ближайшая незакрытая implementation-карточка в основном порядке — **H01**, группировка/сортировка/Home projection; C04/S01 зависимости закрыты. Она ещё не начата и не включена в приёмку R03. Следующие H02/H03 используют завершённые S03 и будущую H01. Приоритет H01 — рабочий следующий шаг по плану, пользователь может выбрать другую карточку.
