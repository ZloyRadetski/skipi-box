# R03 — перенос списка приложений в shared/ui

Статус на 24 октября: срез принят, [R03 закрыта итоговым gate](r03-acceptance-verification.md). Core/native checkpoint `c99b94f`. [Первый проверенный срез](r03-foundation-verification.md) содержит core projection/policy и сохранение per-app данных; общий mode/clipboard editor был подключён в P04.

## Контракт до production

Source map и API: [presentation spec](../../outputs/drafts/r03-presentation-slice-spec.md). Android `ProxyAppListComponents`, list/pager из `ProxyAppListPage` и search/pager effects из `ProxyAppListPageState` определяют переносимое поведение. Desktop реальная Per-App route сохраняет capability notice и существующее редактирование данных профиля; каталог приложений для него не создаётся.

Перед production написаны семь actual Compose scene tests. Они проверяют полные selection keys shared-UID группы, checked state при выбранном companion ID, uncheck, Global no-op/disabled checkbox, UID/SUID/label/package/icon slot, tab count/callback, внешнюю смену пользователя, горизонтальную прокрутку → selected user, удаление выбранной вкладки, единственную current-user страницу без tabs, latest-only search debounce 150 ms и empty state. Fixture rows передаются прямо общим компонентам в изолированном `ImageComposeScene`; продуктовый Desktop provider/route не подменяется.

У Android unit test target нет готового Compose scene harness. Исходная опора — Android source, работающие oracle/selection tests и одинаковый common core corpus; native Android UI до переноса отдельно не рендерился. Новые сцены используют существующий Desktop Compose host test harness.

Первый UI compile дал отсутствующие API и независимую ошибку fixture: composable `MiuixScrollBehavior()` был вызван внутри non-composable `remember` lambda. Исправлен вызов на прямой; ожидания и debounce timing сохранены. После исправления зафиксирован **чистый API compile RED, 2 s**: три отсутствующие common UI функции и зависимые ошибки типов/лямбд. Сцены до реализации ещё не исполнялись; это не behavioral RED. Final test SHA: `5d6c3a8c50581aebf0a07ab942dc808a9be5c7d5490e7eecaf16124ff282aaea`.

Evidence: [outputs/r03-presentation-reports-20261024](../../outputs/r03-presentation-reports-20261024), initial/final source hashes, два compiler logs и fixture correction. Production разрешён после этого RED. Package/user providers, loader flags/cancellation/toasts, Coil icon content и `SystemClock` minimum-loading timing остаются Android adapters; переносимые list/search/tab/page rendering и interaction идут в shared/ui.

## Следующая проверка

Production перенесён в четыре common UI файла: search/tabs/empty, card/UID, pages/list/pull-refresh и loading rendering. Android оставляет native icon и lifecycle/loading clock. При source review исправлена native передача выбранного user ID: Pages получает raw state, чтобы callback исправлял исчезнувший ID; clipboard/tabs сохраняют прежний валидированный fallback. Перенесённые эффекты и параметры сравнили root и независимый Luna Max reviewer. Desktop продуктовая route не менялась.

Первый runtime gate скомпилировал оба хоста; Android **19/19 GREEN**, в том числе actual AppState → backup JSON → restore preview с раздельными global/profile per-app значениями; прежние Desktop P04 UI **8/8 GREEN**. Новый класс сцен остановился на JUnit initialization error: последняя `assertNotNull` в tab test вывела возвращаемый `SemanticsNode`. Исправлен только `runBlocking<Unit>`; ожидания сохранены.

После этого исполнились все семь сцен: **6 GREEN, 1 failure** на горизонтальной прокрутке. Диагностика доказала ошибку цели в harness: first-match `ScrollBy + HorizontalScrollAxisRange` выбирал Miuix tab strip (`bounds=17,5..463,40`, тексты вкладок), а pager находился ниже (`bounds=0,45..480,780`, тексты package rows). Независимый reviewer подтвердил `TabRowWithContour → LazyRow` по текущему cached Miuix bytecode. Harness теперь находит horizontal-scroll предка строки `Personal mail` и проверяет принятие ScrollBy. Прежние selected-user/tab-removal assertions и timing сохранены; production после первого runtime gate не менялся.

Исправленный tab/page test: **1/1 GREEN, 7 s**. Это отдельная focused проверка, не единый финальный семитестовый прогон; свежий полный gate ниже проверит все сцены вместе. Сохранены XML/log всех попыток, JUnit correction и pager diagnostic/correction records, before/after hashes. Final UI test SHA: `1bd3164a3e83d447d7036e03509f5fd9ca843b7e586e5c24ade42a5eca885026`. Неизменность assertions не подменяется утверждением о неизменности всего тестового файла: два runtime harness исправления описаны явно.

Свежий full gate **2960/2960 GREEN, 1m19s**, failures/errors/skips 0: core400+341, app357+349, ui122+122, Android615, Desktop654. Все семь новых сцен прошли вместе на final harness, прежние P04 UI cases тоже GREEN. Desktop compile/debug APK и реальные Core9/9 GREEN; 25 изменённых Kotlin SHA неизменны до/после gate. [Итоговый evidence](../../outputs/r03-final-full-reports-20261024). Физическая device/OS UI QA не выполнялась.
