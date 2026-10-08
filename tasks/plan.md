# План завершения переноса Android → shared

Дата: 7 октября 2026 года. Статус: **подтверждён пользователем, выполняется**.
Реализация начата 7 октября 2026 года после подтверждения пользователя. Первый срез: B01/B02 → C01 → C02 → C03, сначала поведенческие тесты.

Этот документ и [tasks/todo.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/todo.md) — текущие рабочие документы. Предыдущие планы сохранены как история; их ограничения на перенос существующих мобильных сценариев в Desktop не определяют текущий scope.

## Цель и критерий результата

Большая часть приложения — модели, алгоритмы, операции, состояние экранов и переносимый Compose UI — находится в shared. Android и Desktop содержат composition roots, интеграцию хранения и конкретные операции ОС/нативного runtime. Переносится существующая функциональность Android с прежней семантикой; новые продуктовые функции не проектируются.

В Desktop подключаются уже существующие мобильные переносимые сценарии и общий UI. Само отсутствие сценария в текущем Desktop не считается платформенным ограничением. Ограничение подтверждается конкретным API ОС или отсутствующим native/runtime механизмом, фиксируется явно и не маскируется успешной заглушкой. Новые native протоколы, VPN-режимы и способы установки приложения этот план не предусматривает.

Миграция завершена, когда у каждого переносимого сценария есть один общий алгоритм, оба нужных host entry points используют его, форматы данных и Android-поведение сохранены, а каждый остаток в хостах объясняется реальной платформенной работой. Наличие общей формы или модели само по себе завершением сценария не считается.

## Проверенная отправная точка

Аудит выполнен для main, HEAD `ff5f5d8f3cc32bf0b0fc91cfc96b6266103d5be8`. Перед реализацией проверяется актуальный HEAD и рабочее дерево.

- SharedApplicationStore подключён в Android на уровне процесса; AppState всё ещё является источником части сохранённых данных и имеет прямых потребителей.
- Home UI/reducer и многие формы/модели редакторов общие; бизнес-операции и вызовы эффектов ещё распределены между хостами.
- Desktop редактор серверов ограничен Custom; визуальный редактор профиля не подключён. Не все отсутствующие возможности объясняются платформой.
- Алгоритмы группировки, Shadowrocket translation и Xray composition имеют параллельные пути. Desktop SKIPI-настройки DNS/Mux/Fragment применяются неполно; FullJson возвращает persisted JSON вместо Android Xray export.
- Android новый черновик получает предполагаемый ID слишком рано: статически подтверждён риск перезаписи сервера после конкурентного импорта/refresh. Риск существовал до текущего переноса. Порядок вставки также требует восстановления прежней prepend-семантики.
- Routing/resource contracts общие, но Desktop composition root их не подключает полностью. Settings save, profile lifecycle, диагностика, backup и другие области ещё содержат переносимую host-логику.

В аудите успешно выполнены shared/core, shared/app, shared/ui на Android и Desktop, полный desktopApp:test и app:testDebugUnitTest: **1302 исполнения тестов, без ошибок и пропусков в JUnit-отчётах**. Общие тесты считаются для двух платформ отдельно. Нативная FFM-проверка без заданного `SKIPI_CORE_INTEGRATION_DIR` возвращается досрочно: этот baseline не доказывает запуск реального Core. APK/native rebuild и ручная UI-проверка не выполнялись. Лог предыдущего запуска: `/tmp/skipi-shared-audit-20261007-gradle.log`.

Пользовательские изменения в `hevtun/src/main/jni/hev-socks5-tunnel`, существующие tasks и реальные данные не включаются в миграционные изменения.

## Границы модулей

| Область | Где находится результат |
|---|---|
| Типизированные модели, нормализация, парсеры, policies, расчёты, planner и JSON/document builders | shared/core |
| Прикладные сценарии, последовательность действий, согласование каталогов, feature state/controllers, repository contracts | shared/app |
| Существующие переносимые страницы, формы, диалоги, presentation, тема и локализация | shared/ui |
| Room/SharedPreferences/Desktop JSON, файловый и сетевой I/O, clipboard/pickers, system facts | Узкие Android/Desktop adapters |
| VpnService/permission/TUN/JNI/HevTun, Desktop FFM/Core runner/system proxy/window/tray | Соответствующий host |
| WorkManager, receiver, widgets, Quick Settings, logcat, package enumeration, SSID, APK installer | Android host; чистые решения и данные — shared |
| Native Go Core | Существующий отдельный skipi-core; переписывание Core в план не входит |

Не создаётся второй универсальный AppState или новый параллельный источник истины. Общие контракты расширяются по существующим feature-полям, а прямые мутации AppState убираются по одному подключённому сценарию. Сетевой вызов сам по себе не делает весь use case платформенным: общие порядок, parsing, policy и состояние отделяются от transport/binding.

Форматы Room/SharedPreferences, Desktop JSON, .conf/SKIPI, backup и публичных URL остаются совместимыми. Изменение одного поля сохраняет соседние и неизвестные данные по текущему контракту. Модель Desktop-хранения не объявляется канонической моделью мобильного профиля.

## Сохранение поведения

До переноса следующего сценария фиксируются Android defaults, validation, нормализация, ID и порядок, выбор/fallback, тексты, ошибки, частичный успех, cancel/reentry, последовательность side effects, расписание и внешний формат. При уже обнаруженном расхождении после миграции сверяется соответствующий Android-путь в истории.

Для чистого переноса используется одинаковый corpus входов и ожидаемого результата. JSON сравнивается семантически; значимый порядок массивов сохраняется. Для .conf проверяется фактическая Android-сериализация: вне заменяемой секции raw сохраняется, [SKIPI] переписывается известными полями. Для typed Desktop profile JSON и backup codec не обещается сохранение неизвестных ключей, которое исходная реализация не обеспечивает. Платформенные inbounds/paths/native facts исключаются из сравнения только явно; DNS, Mux, Fragment и маршруты не скрываются нормализацией.

Известные исправления оформляются отдельно от механического переноса: коллизия ID, prepend-порядок, FullJson и применение portable SKIPI runtime-параметров. Сначала воспроизводящий тест, затем исправление. Неизвестное продуктное поведение сначала исследуется по Android-коду/истории; новый вариант не придумывается.

Результат updateCatalog и фиксация на диск — разные гарантии. Проверяются фактические ошибки adapters и порядок multi-repository update; глобальная транзакция между Room, JSON и файлами не предполагается.

## Обязательное правило: сначала тесты, затем код

Прямое указание пользователя: **«сначала под это что-то пишутся тесты, а только потом код; не тесты пишутся под код, а код пишется под тесты»**. Оно применяется ко всем implementation-срезам этого плана и передаётся каждому исполнителю.

1. По Android-оригиналу описать сценарий, входы, ожидаемый результат и ошибки.
2. Написать поведенческие тесты/fixtures для следующего малого среза **до изменения production-кода**. Существующие Android-тесты можно переносить/расширять как исходную спецификацию; ожидания не выводятся из новой реализации.
3. Запустить тесты на исходном состоянии. Для дефекта или отсутствующего подключения получить ожидаемый RED по сути сценария. При чистом переносе зафиксировать результат на работающем Android-оригинале и тот же контракт для общего пути; искусственно ломать корректный оригинал ради RED не нужно.
4. Написать минимальный production-код под установленный контракт и получить GREEN.
5. Упростить/удалить заменённый путь при сохранении GREEN; выполнить релевантную интеграционную проверку.

Не подгонять expectations, snapshots или нормализацию под полученный код и не отключать тесты для успешной сборки. Если тест выявил ошибку в спецификации, сверить Android-источник и объяснить исправление ожидания отдельно.

Для UI до интеграционной правки тестами фиксируются state/actions, save/cancel/back и значимые результаты подключения. Compile и ручная QA дополняют эти проверки. Тесты не проверяют внутреннюю структуру и не дублируют реализацию.

На сложном срезе один Luna-исполнитель готовит тесты/fixtures, оркестратор проверяет спецификацию и исходный результат, затем другой исполнитель пишет production-код. Параллельная production-реализация этого же среза до готовности тестов не начинается. В отчёте сохраняются имена тестов, исходный результат и GREEN после изменения.

Постоянные правила проекта: [AGENTS.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/AGENTS.md).

## Порядок и организация работы

Карточки ниже — единицы приёмки дорожной карты. Крупная карточка исполняется несколькими малыми срезами: одна операция → тесты и исходный запуск → общий код → Android adapter → Desktop adapter → GREEN/интеграция → удаление заменённого пути. Объём не наращивается ради выполнения всей карточки за одну правку. Уже корректный общий код повторно не переписывается.

Начальная последовательность: **B01 → B02 → C01 → C02 → C03**. Это даёт первый законченный результат: безопасное создание/редактирование серверов в обоих хостах с прежним порядком вставки.

После стабилизации каталога независимые ветки идут параллельно: parser fixtures/I01, profile semantics/P01 и grouping/H01. X01 возможен после P01; T01 — после H01. Порядок остальных действий определяется зависимостями карточек, а не только их расположением в документе. S02 создаёт совместимую точку передачи связанного профиля; полная связь завершается в P03. H03 проверяется без FullJson; экспорт закрывается в X04.

Оркестратор распределяет работу, удерживает контракты и проверяет итог. Research и написание кода выполняют **gpt-6-luna с reasoning max**, до трёх исполнителей одновременно. Параллельные изменения разрешены только для независимых файлов/сценариев со стабильными входными контрактами; каталог, store и composition roots изменяются последовательно. Запуск проверок координируется, чтобы независимые исполнители не запускали одинаковую полную сборку.

Контрольные точки технические: после них показываются выполненные изменения, проверки и оставшиеся ограничения. Дополнительные подтверждения на каждый этап не требуются. Подтверждение пользователя перед началом относится ко всему этому плану. Новый продуктный сценарий или реальная неоднозначность вне него требуют отдельного уточнения.

## Этапы

| Этап | Карточки | Проверяемый результат |
|---|---|---|
| 0 | B01–B02 | Android-эталон и проверки поведения |
| 1 | C01–C04, I01–I02 | Один контракт каталога, создание/редактирование и импорт |
| 2 | S01–S03, H01–H03 | Общие подписки и полный переносимый Home flow |
| 3 | P01–P04 | Одна семантика профиля, lifecycle, refresh и visual/raw editor |
| 4 | X01–X04 | Канонические routing/outbound/runtime/export builders |
| 5 | T01–T04 | Настройки используют общие state/actions и влияют на runtime |
| 6 | R01–R03 | Routing/resources/app-selection с реальными host adapters |
| 7 | A01–A02, D01–D02, L01, U01, K01 | Остальные существующие мобильные сценарии |
| 8 | N01, Z01–Z02 | Узкие оболочки, удалённые дубли и итоговая проверка |

## 0. Исходное поведение и проверка границ

<a id="b01"></a>

### B01. Зафиксировать исходное поведение и карту потребителей

**Зависимости:** нет.

**Работа:** Для каждого сценария проследить UI → операция → репозиторий → хранение/runtime. Использовать ff5f5d8 как проверенную исходную точку; перед началом проверить текущий HEAD и пользовательские изменения. Если миграция уже изменила поведение, сверить соответствующий Android-путь до переноса, не откатывая посторонние изменения.

**Критерии приёмки:**

- Создана матрица сценариев: уже общий / переносимый остаток / системный адаптер / платформенное ограничение.
- Для переноса записаны Android-инварианты: значения по умолчанию, порядок, выбор, сообщения, ошибки, отмена и форматы.
- Существующая работа в native submodule, tasks и пользовательские данные сохранены.

**Проверки:** Чтение фактических вызовов и истории; существующие тесты и синтетические fixtures. Полный baseline не повторять без изменений кода/условий.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin); [desktopApp/src/main/kotlin](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin); [shared](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="b02"></a>

### B02. Закрепить правила границ и сравнительной проверки

**Зависимости:** [B01](#b01).

**Работа:** Сформировать небольшие общие тестовые входы для серверов, подписок, профилей и настроек. Определять необходимый контракт по одному следующему сценарию; не проектировать универсальное приложение заранее. Написать и запустить тесты следующего малого среза до production-изменений; они задают контракт реализации.

**Критерии приёмки:**

- Для чистого переноса есть проверки сохранения поведения; для исправления дефекта — воспроизводящий поведенческий тест.
- JSON сравнивается семантически с сохранением значимого порядка массивов; raw .conf проверяется по существующим Android-правилам сериализации секций.
- Исключаемые из сравнения платформенные поля перечислены явно; DNS, Mux, Fragment и правила не маскируются нормализацией.

**Проверки:** Сфокусированный запуск существующих наборов на контрольных fixtures; ревью критериев перед production-изменениями.

**Проверенные точки входа/каталоги:** [shared/core/src/commonTest](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonTest); [shared/app/src/commonTest](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonTest); [app/src/test](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/test); [desktopApp/src/test](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/test). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## 1. Каталог серверов и импорт

<a id="c01"></a>

### C01. Разделить создание нового сервера и редактирование существующего

**Зависимости:** [B02](#b02).

**Работа:** Ввести минимальные операции создания/редактирования поверх существующего updateCatalog. Новый ID выделяется из актуального каталога при фиксации создания; временная идентичность черновика не считается ID сохранённого сервера.

**Критерии приёмки:**

- Новое создание получает уникальный положительный ID и обновляет nextServerId в одной атомарной операции.
- Сохранены prepend-семантика Android, позиция при редактировании, группа и прежние правила выбора.
- Переполнение Int и исчерпание диапазона завершаются понятной ошибкой без частичного изменения; неизвестные Desktop-записи учитываются при выдаче ID.

**Проверки:** Контрактные тесты каталога: параллельное создание, high-water mark, opaque records, пустой каталог, порядок, выбор и граница Int.MAX_VALUE.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/model/ProxyServerCatalog.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/model/ProxyServerCatalog.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerCollectionOperations.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerCollectionOperations.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/store/SharedApplicationStore.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/store/SharedApplicationStore.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/repository/RepositoryContracts.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/repository/RepositoryContracts.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="c02"></a>

### C02. Подключить создание и сохранение в Android и закрыть коллизию ID

**Зависимости:** [C01](#c01).

**Работа:** Перевести результат нового черновика на create, существующего — на edit. Коллизия устаревшего ID оформляется отдельным исправлением; восстановление прежнего порядка вставки отдельно видно в тестах и ревью.

**Критерии приёмки:**

- Сценарий «открыть черновик → подписка заняла предполагаемый ID → сохранить» сохраняет оба сервера.
- Редактирование не создаёт новый ID и не меняет позицию; отмена черновика не создаёт запись.
- Успех, ошибки и навигация соответствуют Android; успех не показывается при отказе операции.

**Проверки:** Поведенческий RED на коллизию до исправления; Android result-handler/repository tests и ручное создание/редактирование/отмена.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/features/proxy/server/list/ProxyServerListActions.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/list/ProxyServerListActions.kt); [app/src/main/kotlin/features/proxy/server/list/ProxyServerEditResultHandler.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/list/ProxyServerEditResultHandler.kt); [app/src/main/kotlin/features/proxy/server/editor/ProxyServerPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/editor/ProxyServerPage.kt); [app/src/main/kotlin/app/navigation/Route.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/app/navigation/Route.kt); [app/src/main/kotlin/data/repository/AndroidProxyServerRepository.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/data/repository/AndroidProxyServerRepository.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="c03"></a>

### C03. Подключить каталог Desktop к тем же операциям

**Зависимости:** [C01](#c01), [C02](#c02).

**Работа:** Оставить JSON и filesystem платформенным адаптером. Перевести изменения серверов и выбор на общие операции без повторного назначения ID или собственного преобразования коллекции.

**Критерии приёмки:**

- Сохранён существующий JSON, opaque records и монотонный счётчик ID.
- Ошибка сохранения не публикует успешное изменение и не вызывает успешный runtime/UI-эффект.
- Общие правила порядка, выбора и редактирования совпадают с Android.

**Проверки:** DesktopServerLibraryTest, DesktopProxyServerRepositoryTest и contract tests с save failure/reload.

**Проверенные точки входа/каталоги:** [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyServerRepository.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyServerRepository.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopServerLibrary.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopServerLibrary.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt); [desktopApp/src/test/kotlin/app/skipi/desktop](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/test/kotlin/app/skipi/desktop). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="c04"></a>

### C04. Перенести фабрику черновиков и подключить обычные типы серверов

**Зависимости:** [C02](#c02), [C03](#c03).

**Работа:** Использовать одну фабрику существующих Android-типов с прежними default-значениями. Подключить общий редактор в хостах; отделить отсутствие native runtime конкретного протокола от отсутствия формы.

**Критерии приёмки:**

- Для каждого уже существующего Android-типа черновик создаётся общим кодом без новых параметров.
- Обычные новые серверы идут в ручную группу, StrategyGroup — по существующему Android-правилу.
- Desktop предлагает обычные поддержанные протоколы; реальные ограничения native протоколов явно зафиксированы и не подменены заглушками.

**Проверки:** Табличные тесты фабрики; сохранение/валидация/предупреждение/отмена для обычного сервера, StrategyGroup и ChainProxy.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/features/proxy/server/usecase/ProxyServerUseCases.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/usecase/ProxyServerUseCases.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/server](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/server); [shared/ui/src/commonMain/kotlin/app/skipi/ui/server/editor](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/server/editor); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyServerEditorScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyServerEditorScreen.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="i01"></a>

### I01. Унифицировать разбор payload и Mihomo YAML

**Зависимости:** [B02](#b02).

**Работа:** Сохранить Android-порядок форматных парсеров, BOM/Base64 и recursive-provider safeguards. Переиспользовать существующий YAML-декодер/конвертер через подходящий JVM-адаптер; убрать ограниченный альтернативный алгоритм после подключения и проверки.

**Критерии приёмки:**

- Те же URL, Custom Xray JSON, WG .conf и Mihomo документы распознаются с теми же результатами и ошибками.
- Поддержанные Android YAML-конструкции обрабатываются одинаково; глубина, циклы и дубликаты providers сохраняют прежние правила.
- Сеть, файлы, clipboard и QR передают данные через порты; выбор формата и интерпретация общие.

**Проверки:** Один corpus fixtures на двух host adapters: URL протоколов, BOM/Base64, malformed/empty payload, YAML anchors/blocks, nested providers и cancellation.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/proxy/server/usecase/importer](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/proxy/server/usecase/importer); [shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerImportOperations.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerImportOperations.kt); [app/src/main/kotlin/features/proxy/server/usecase/importer](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/usecase/importer); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopMihomoPayloadImporter.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopMihomoPayloadImporter.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyImport.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyImport.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="i02"></a>

### I02. Унифицировать планирование и фиксацию импорта

**Зависимости:** [I01](#i01), [C03](#c03).

**Работа:** Импорт фиксировать общей операцией каталога из актуального состояния после загрузки. Разбор подписочных install/deep-link и profile payload переиспользует существующие модели; связанное применение профиля завершается в P-задачах.

**Критерии приёмки:**

- Массовый импорт сохраняет порядок, ручную группу и выбранный сервер по правилам Android.
- Параллельные импорт, refresh и ручное создание не повторяют ID и не теряют чужие записи.
- Пустой или ошибочный импорт не создаёт мусор; частичный успех и уведомления сохраняют существующую семантику.

**Проверки:** Общие import-operation tests + реальные Android/Desktop adapter tests; source variants file/clipboard/QR проходят одну операцию.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/proxy](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy); [app/src/main/kotlin/features/proxy/server/list/ProxyServerListActions.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/list/ProxyServerListActions.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyImportCommitter.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyImportCommitter.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## 2. Подписки и Home

<a id="s01"></a>

### S01. Перенести CRUD подписок и ручных групп

**Зависимости:** [C03](#c03).

**Работа:** Расширять модель только реально существующими Android-полями подписки: настройки загрузки, enabled, builtIn, override, расписание/метаданные. Общими сделать создание/редактирование/удаление/перестановку групп; storage mapping оставить хостам.

**Критерии приёмки:**

- Сохранены ID, порядок, built-in ограничения, ручные группы и все существующие поля подписки.
- Удаление правильно очищает серверы и ссылки составных серверов через каноническую операцию.
- Изменение одного поля не обнуляет метаданные или неизвестные сохранённые поля.

**Проверки:** Subscription library/adapter tests, metadata round-trip, remove-linked-server tests и общие CRUD/invariant tests.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/subscription](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/subscription); [shared/app/src/commonMain/kotlin/app/skipi/app/model/PersistedModels.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/model/PersistedModels.kt); [app/src/main/kotlin/features/subscription/SubscriptionGroupListPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/subscription/SubscriptionGroupListPage.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionLibrary.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionLibrary.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="s02"></a>

### S02. Перенести обновление одной подписки и сверку результата

**Зависимости:** [I02](#i02), [S01](#s01).

**Работа:** Общая операция владеет порядком load/validate/rebase/commit, identity подписки, сверкой серверов и metadata. Fetcher остаётся портом. Связанную конфигурацию передавать общей profile operation после P01–P02.

**Критерии приёмки:**

- Сохранены Android-правила empty/partial response, matching ID/latency, override и metadata.
- Сохранены фактические Android-проверки identity/актуальности подписки и результат при её удалении/изменении; общая фиксация каталога использует актуальные данные. Новые правила конфликтов профиля не вводятся.
- Запись нескольких хранилищ имеет проверенный порядок и результат частичного сбоя; глобальная транзакция не предполагается.

**Проверки:** Refresh/reconciliation tests: delayed response, unrelated additions, target changed/deleted, cancellation, empty body, failed save и metadata.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/subscription/SubscriptionRefreshUseCase.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/subscription/SubscriptionRefreshUseCase.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/proxy/SubscriptionServerCollectionReconciler.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy/SubscriptionServerCollectionReconciler.kt); [app/src/main/kotlin/features/subscription/usecase/SubscriptionUpdateUseCase.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/subscription/usecase/SubscriptionUpdateUseCase.kt); [app/src/main/kotlin/features/proxy/server/usecase/ProxyServerUseCases.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/usecase/ProxyServerUseCases.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionRefreshCommitAdapter.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionRefreshCommitAdapter.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="s03"></a>

### S03. Перенести массовое обновление, install и правила расписаний

**Зависимости:** [S02](#s02).

**Работа:** Переиспользовать refresh одной подписки для mass refresh, install URI/deep-link и планового обновления. Прогресс, агрегирование ошибок, eligibility и параметры обновления общие; WorkManager/таймер и системные уведомления — адаптеры.

**Критерии приёмки:**

- Массовая операция обновляет те же Android eligible-группы, изолирует ошибки и сохраняет отмену.
- Сохранены UA, Age, update-via-proxy, device-header и timeout правила, с фактами устройства от адаптера.
- Один общий сценарий подключён к Android UI/background и Desktop; неподключённый refresh не возвращает ложный успех.

**Проверки:** Batch/install/scheduling tests плюс host workers/planner; mixed success/failure, disabled/empty URL и повторный запуск.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/subscription](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/subscription); [shared/core/src/commonMain/kotlin/features/subscription/runtime](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/subscription/runtime); [app/src/main/kotlin/features/subscription/SubscriptionInstallConfigUseCase.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/subscription/SubscriptionInstallConfigUseCase.kt); [app/src/main/kotlin/features/subscription/runtime](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/subscription/runtime); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionRefreshPlanner.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionRefreshPlanner.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="h01"></a>

### H01. Свести группировку, сортировку и Home-проекцию

**Зависимости:** [C04](#c04), [S01](#s01).

**Работа:** Выбрать один общий алгоритм на основе существующих Android-правил для All/manual/subscription/AutoBalancer/config-derived strategy groups. Home projection остаётся отделённой от persisted/runtime state.

**Критерии приёмки:**

- Совпадают порядок и видимость групп, disabled-семантика, All, пустая группа и fallback выбора.
- Поиск/сортировка/перестановка сохраняют Android-правила; пользовательская сортировка переживает повторный запуск Desktop.
- Проекция использует актуальные данные репозиториев, не создавая вторую независимую копию каталога.

**Проверки:** Общие projection/grouping tests + Android/Desktop input tests на одинаковых fixtures.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerGrouping.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerGrouping.kt); [shared/core/src/commonMain/kotlin/features/proxy/server/presentation/ProxyGroupCatalog.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/proxy/server/presentation/ProxyGroupCatalog.kt); [app/src/main/kotlin/features/proxy/server/list/ProxyServerListGroups.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/list/ProxyServerListGroups.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyGroups.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyGroups.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="h02"></a>

### H02. Вынести прикладные Home-операции и runtime-решения

**Зависимости:** [I02](#i02), [S03](#s03), [H01](#h01).

**Работа:** По одной операции переносить выбор, перемещение, удаление, cleanup tools, refresh, latency coordination и решения о stop/reconnect. Общий presentation reducer не превращать в универсальный handler; feature use cases вызывают существующие tunnel/latency порты.

**Критерии приёмки:**

- Доменные проверки и преобразования общие; хост выполняет конкретную команду runtime, навигацию, haptics, clipboard/QR и сообщение.
- Сохранены busy/reentrant, остановка перед удалением, выбранный сервер, отмена latency и порядок side effects.
- Один пользовательский жест вызывает один сценарий; не остаётся параллельного старого обработчика.

**Проверки:** Use-case/bridge tests с fake runtime плюс существующие stop/delete/latency tests; success/failure/cancellation и изменение каталога в полёте.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/home](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/home); [app/src/main/kotlin/features/proxy/server/list/ProxyServerListPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/list/ProxyServerListPage.kt); [app/src/main/kotlin/features/proxy/server/usecase/ProxyServerRuntimeUseCases.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/usecase/ProxyServerRuntimeUseCases.kt); [app/src/main/kotlin/features/proxy/server/list/ProxyServerListDeleteCoordinator.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/list/ProxyServerListDeleteCoordinator.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="h03"></a>

### H03. Подключить Home и переносимые инструменты в обоих хостах

**Зависимости:** [H02](#h02).

**Работа:** Сокращать платформенные экраны после подключения общих операций. Подключить существующие мобильные инструменты, обычные server editors и доступности; native ограничения указывать отдельно. Полный JSON завершается в X04.

**Критерии приёмки:**

- Android UX, редактор, подтверждения, сообщения и жесты сохранены.
- Desktop вызывает те же переносимые операции; нет no-op кнопок и неподключённых заявленных capabilities.
- Для каждого удалённого legacy пути проверено отсутствие UI/background/deep-link потребителей.

**Проверки:** Android/Desktop Home effect tests и ручной smoke: add/edit/select/move/delete/tools/refresh/latency; FullJson пока проверяется отдельной задачей X04.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/features/proxy/server/list](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/list); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyServerEditorScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyServerEditorScreen.kt); [shared/ui/src/commonMain/kotlin/app/skipi/ui/home](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/home). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## 3. Профили Shadowrocket/SKIPI

<a id="p01"></a>

### P01. Закрепить общую семантику профиля и адаптер Desktop JSON

**Зависимости:** [B02](#b02), [C03](#c03).

**Работа:** Использовать существующую общую модель настроек/исполнения профиля. ConfigProfile/ConfigProfileLibrary сохраняются как формат Desktop storage с явным mapping. Чтение/редактирование документа следует текущей Android-семантике: raw вне заменяемой секции сохраняется, [SKIPI] сериализуется известными полями. Новое сохранение неизвестных ключей внутри [SKIPI] или typed configs.json не добавляется.

**Критерии приёмки:**

- Сохранены raw-секции/строки, которые сохраняет Android; замена содержимого [SKIPI] известными полями повторяет нынешний write contract, включая судьбу неизвестных ключей/комментариев.
- Различаются отсутствующие и явно заданные значения; defaults/legacy fallback совпадают с Android.
- Старый configs.json загружается и сохраняется с полями действующей схемы, selection и соседними профилями. Неизвестные JSON-ключи не обещаются как opaque round-trip.

**Проверки:** Shadowrocket/Skipi document/metadata + Desktop legacy schema round-trip; .conf без SKIPI и с unknown sections/comments/custom keys внутри и вне SKIPI. Ожидаемый результат берётся из Android, не из желаемого улучшенного serializer.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/config/TrafficConfigDocument.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/config/TrafficConfigDocument.kt); [shared/core/src/commonMain/kotlin/features/config/SkipiConfigDocument.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/config/SkipiConfigDocument.kt); [shared/core/src/commonMain/kotlin/features/config/ConfigProfileLibrary.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/config/ConfigProfileLibrary.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigLibrary.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigLibrary.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSkipiProfileMetadata.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSkipiProfileMetadata.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="p02"></a>

### P02. Перенести полный жизненный цикл профиля

**Зависимости:** [P01](#p01), [C03](#c03).

**Работа:** Создание, duplicate, selection, remove, raw/UI save и import/refresh используют общие операции. Сохранить Android-правила ID, порядка, последнего профиля, active fallback, dedup по имени/URL и update lock. Эту задачу разделить на отдельные CRUD/import срезы при превышении разумного размера.

**Критерии приёмки:**

- На одинаковых portable входах обе платформы получают один результат операции.
- Ошибка валидации/сохранения не выдаётся за успешное применение; поле raw и модель настроек синхронизируются по Android-правилам.
- Выбор/удаление/дублирование сохраняют существующий активный профиль и ограничения последнего профиля.

**Проверки:** TrafficConfigCollectionOperations/DocumentImport/EditorOperations + оба adapter suites; malformed import, duplicate source, active deletion и stale save.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/config](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/config); [app/src/main/kotlin/features/config/TrafficConfigState.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/config/TrafficConfigState.kt); [app/src/main/kotlin/features/config/TrafficConfigPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/config/TrafficConfigPage.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="p03"></a>

### P03. Сохранить обновление профиля, расписание и связанные группы

**Зависимости:** [P02](#p02), [S02](#s02).

**Работа:** Общими сделать eligibility, intervals, ручной/автоматический refresh commit и конфигурацию из подписки. Scheduler/network facts остаются адаптерами. Reconcile config-derived groups выполнять актуальными общими операциями каталога.

**Критерии приёмки:**

- Locked/disabled/empty URL и интервал следуют Android-правилам; auto-update профиля и geo ресурсов различаются.
- Редактирование/refresh/смена active profile сохраняют ID и актуальное членство связанных групп.
- Manual/auto-refresh повторяет Android commit semantics, включая текущую запись ответа без revision/CAS; новая защита от перезаписи поздним ответом в scope точного переноса не входит.

**Проверки:** TrafficConfigRefreshScheduling, group isolation/persistence, embedded profile conflict tests и adapters; ручной edit → refresh → смена profile.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/config/runtime](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/config/runtime); [shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ConfigProxyGroupReconciler.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ConfigProxyGroupReconciler.kt); [app/src/main/kotlin/features/config/runtime](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/config/runtime); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionRefreshCommitAdapter.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionRefreshCommitAdapter.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="p04"></a>

### P04. Подключить общий список и визуальный редактор профиля

**Зависимости:** [P02](#p02), [P03](#p03).

**Работа:** Перенести остатки state/controller/screens для существующих mobile форм General, DNS, routing, proxy groups, ресурсов и raw edit. Desktop использует те же portable формы. Для TUN/per-app/network-specific частей общая форма вызывает platform callbacks или учитывает реальные capabilities.

**Критерии приёмки:**

- Сохранены поля, validation, Save/Back, контекстное меню и оба режима редактора. Back сохраняет так же, как Android; новый discard flow не добавляется.
- Редактирование секции следует существующим Android-правилам, включая сериализацию [SKIPI]; остальные raw-секции сохраняются там, где их сохраняет оригинал.
- UI edit доступен Desktop для переносимых частей; raw editor сохраняется.

**Проверки:** Shared form/state tests, Android editor integration и Desktop editor tests; визуальная QA режимов, формы/списка/меню на двух размерах окна.

**Проверенные точки входа/каталоги:** [shared/ui/src/commonMain/kotlin/app/skipi/ui/config](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/config); [app/src/main/kotlin/features/config/TrafficConfigEditorPages.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/config/TrafficConfigEditorPages.kt); [app/src/main/kotlin/features/config/TrafficConfigRuleEditor.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/config/TrafficConfigRuleEditor.kt); [app/src/main/kotlin/features/config/TrafficConfigProxyGroupEditor.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/config/TrafficConfigProxyGroupEditor.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopConfigsScreen.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## 4. Планирование Xray, runtime JSON и экспорт

<a id="x01"></a>

### X01. Свести преобразование Shadowrocket rules к одному алгоритму

**Зависимости:** [P01](#p01).

**Работа:** Перенести каноническую Android-интерпретацию rule types/policies и удалить альтернативную translation-ветку после доказательства совпадения. Resource capabilities и geo validators приходят от хоста; различия ресурсов не меняют общий алгоритм.

**Критерии приёмки:**

- Одинаковое правило даёт одинаковую portable route representation.
- Сохранены Android DOMAIN/DOMAIN-SET/GEOSITE/GEOIP/RULE-SET, FINAL и unsupported-rule поведение.
- Разница доступных geo ресурсов/нативных функций выражена явно и покрыта проверкой.

**Проверки:** ShadowrocketRouteRuleConversionTest, TrafficConfigRuntimePlannerTest, TrafficProfileXrayPlannerTest и сравнительные rule fixtures.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/config/ShadowrocketRouteRuleConversion.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/config/ShadowrocketRouteRuleConversion.kt); [shared/core/src/commonMain/kotlin/features/config/TrafficConfigRuntimePlan.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/config/TrafficConfigRuntimePlan.kt); [shared/core/src/commonMain/kotlin/engine/xray/TrafficProfileXrayPlanner.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/engine/xray/TrafficProfileXrayPlanner.kt); [app/src/main/kotlin/app/TrafficConfigRuntime.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/app/TrafficConfigRuntime.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTrafficProfileXrayConfig.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTrafficProfileXrayConfig.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="x02"></a>

### X02. Перенести чистое outbound-планирование Android

**Зависимости:** [C04](#c04), [P03](#p03), [X01](#x01).

**Работа:** Подать typed records/options вместо AppState; сохранить Android алгоритмы выбора targets, chain/strategy membership, fallback, observatory, tags и allowFragment. Разделить сначала обычные/chain, затем strategy/balancer срезы.

**Критерии приёмки:**

- Android план до/после одинаков на тех же fixtures, включая order/tags и выбранного участника.
- AppState не проходит в common planner; runtime overlays/сетевые факты передаются как значения или существующие порты.
- Desktop использует общий planner для подтверждённых существующим Core/runner типов. Составлена явная матрица обычных/Custom/StrategyGroup/ChainProxy/native bridge типов; отказ legacy planner сам по себе не доказывает платформенное ограничение.

**Проверки:** Android XrayOutboundPlannerBalancer/Burst/chain tests перенести/переиспользовать как общий corpus; циклы/пустые members/fallback/latency.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/engine/xray/XrayOutboundPlanner.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/engine/xray/XrayOutboundPlanner.kt); [shared/core/src/commonMain/kotlin/engine/xray/XrayOutboundPlan.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/engine/xray/XrayOutboundPlan.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/proxy/StrategyGroupMemberResolver.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy/StrategyGroupMemberResolver.kt); [shared/core/src/commonMain/kotlin/engine/xray/TrafficProfileXrayPlanner.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/engine/xray/TrafficProfileXrayPlanner.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="x03"></a>

### X03. Перенести общую сборку runtime Xray JSON

**Зависимости:** [X02](#x02).

**Работа:** Переиспользовать existing generated/DNS/routing/mux/fragment/custom JSON builders. Общая композиция принимает profile/settings и явные inbounds, log/asset/runtime facts; pure решения strict tunnel и native overlays переносить при отделимости, syscall/runner оставить адаптеру.

**Критерии приёмки:**

- Android generated/custom/speed-test конфигурации сохраняют прежние существенные поля и правила.
- Desktop применяет portable SKIPI DNS/hosts/Mux/Fragment/sniffing по Android-семантике.
- VPN TUN/DNS hijack/per-app/bridge wiring и Desktop SOCKS/HTTP/system-proxy задаются реальными host inputs. Bridge/native-only тип без существующего Desktop adapter отмечен конкретной причиной unsupported, без успешной заглушки.

**Проверки:** XrayConfig/Dns/Outbound/Custom/StrictFullTunnel и DesktopTrafficProfileXrayConfig tests; отдельный RED для игнорируемых SKIPI параметров, common-output сравнение.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/engine/xray/XrayConfig.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/engine/xray/XrayConfig.kt); [app/src/main/kotlin/engine/xray/XrayOutboundJson.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/engine/xray/XrayOutboundJson.kt); [app/src/main/kotlin/engine/xray/CustomXrayConfigRewriter.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/engine/xray/CustomXrayConfigRewriter.kt); [shared/core/src/commonMain/kotlin/engine/xray](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/engine/xray); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTrafficProfileXrayConfig.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopTrafficProfileXrayConfig.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="x04"></a>

### X04. Унифицировать полный JSON и экспорт профилей

**Зависимости:** [X03](#x03), [P04](#p04), [H03](#h03).

**Работа:** Shared экспорт использует ту же каноническую композицию и Android export policy. Подключить FullJson в Home/editor и .conf/Base64 export существующих профилей; запись clipboard/file и QR rendering — адаптеры.

**Критерии приёмки:**

- FullJson выдаёт Xray-конфигурацию, а не persisted-модель сервера.
- URL/default/full formats, составные серверы, validation и ошибки совпадают с Android.
- Профильные .conf и Base64 сохраняют внешний формат/содержимое; экспорт не переносит ненужные runtime service inputs.

**Проверки:** Copy/export behavior tests + fixture JSON для обычного/strategy/chain/custom; ручной импорт экспортированного результата.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/engine/xray/XrayExportConfig.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/engine/xray/XrayExportConfig.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerTextCopy.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/proxy/ProxyServerTextCopy.kt); [app/src/main/kotlin/features/proxy/server/usecase/ProxyServerCopyText.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/server/usecase/ProxyServerCopyText.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyHome.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyServerEditorScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopProxyServerEditorScreen.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## 5. Настройки

<a id="t01"></a>

### T01. Перенести настройки оформления, Home и общие preferences

**Зависимости:** [H01](#h01).

**Работа:** Расширять модели по реальным полям; не копировать AppState целиком. Перенести state/actions и portable theme/font/color preferences; Desktop подключить существующие mobile controls, включая custom colors/fonts, где нет настоящего OS-ограничения.

**Критерии приёмки:**

- Прежние Android defaults, нормализация, значения и тексты сохранены.
- Все portable settings сохраняются через общий contract с совместимым mapping в обоих хостах.
- OS dynamic colors, app icon, wallpaper picker и haptics выполняет adapter; общая theme logic не дублируется.

**Проверки:** Appearance/General/Theme tests, legacy preferences/JSON reload и ручная смена оформления с перезапуском.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/model/PersistedModels.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/model/PersistedModels.kt); [shared/ui/src/commonMain/kotlin/app/skipi/ui/settings](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/settings); [app/src/main/kotlin/features/settings/SettingsAppearancePage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/settings/SettingsAppearancePage.kt); [app/src/main/kotlin/features/settings/SettingsGeneralPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/settings/SettingsGeneralPage.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="t02"></a>

### T02. Перенести local proxy и subscription preferences

**Зависимости:** [S03](#s03), [T01](#t01).

**Работа:** Общий typed state/action покрывает существующие local-proxy port/auth/listen настройки и subscription UA/ping/timeout/headers/expiry preferences. Доступные переносимые настройки связываются с соответствующим shared/runtime сценарием.

**Критерии приёмки:**

- Изменение поля сохраняет остальные; invalid inputs и генерация credentials следуют Android-правилам.
- Настройки действительно влияют на загрузку/latency/runtime, а не только на UI.
- Реальные device facts, network interface enumeration, notifications и restart execution остаются adapters.

**Проверки:** SettingsDrafts, LocalProxySettingsValues, subscription ping/settings and adapter tests; config/fetch options after update.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/settings](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/settings); [shared/ui/src/commonMain/kotlin/app/skipi/ui/settings](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/settings); [app/src/main/kotlin/features/settings/LocalProxySettingsPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/settings/LocalProxySettingsPage.kt); [app/src/main/kotlin/features/settings/SettingsSubscriptionsPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/settings/SettingsSubscriptionsPage.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="t03"></a>

### T03. Перенести DNS, Mux и Fragment preferences

**Зависимости:** [X03](#x03), [T01](#t01).

**Работа:** Перевести черновики и сохранение на feature models/actions. Разделить глобальные значения, параметры profile и runtime, сохранив Android приоритеты их применения.

**Критерии приёмки:**

- Сохранены DNS/FakeDNS зависимости, ranges, defaults и mux/fragment validation.
- Save/cancel и изменение одной группы параметров сохраняют другие значения.
- Оба хоста используют общую нормализацию; настройки отражаются в Xray по установленным Android приоритетам.

**Проверки:** SettingsDraftsTest и DNS/Mux/Fragment composition tests; global/profile precedence и reload.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/settings/SettingsDrafts.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/settings/SettingsDrafts.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/model](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/model); [shared/ui/src/commonMain/kotlin/app/skipi/ui/settings](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/settings); [app/src/main/kotlin/features/settings/SettingsBottomSheetsHost.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/settings/SettingsBottomSheetsHost.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="t04"></a>

### T04. Перенести VPN/TUN, service-control и системно зависимые settings

**Зависимости:** [T01](#t01), [X03](#x03).

**Работа:** Общими сделать данные, проверки, условия и переносимые формы существующих VPN/TUN/service-control настроек. Android permission/service/battery/receiver и OS scheduling вызываются через текущие adapters. Подключить только фактически реализуемые системные операции хоста.

**Критерии приёмки:**

- Android сохраняет MTU/CIDR/DNS/timeouts/kill switch/on-demand/scheduler правила и эффект настройки.
- У common моделей и UI нет Android Context/service references.
- Для отсутствующих native capabilities нет успешных заглушек; остаток явно внесён в карту платформенных ограничений.

**Проверки:** ServiceControlValidation, Vpn/Tun/StrictFullTunnel tests, storage mapping и Android permission/lifecycle smoke.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/settings/servicecontrol](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/settings/servicecontrol); [shared/app/src/commonMain/kotlin/app/skipi/app/settings](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/settings); [app/src/main/kotlin/features/settings/SettingsVpnPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/settings/SettingsVpnPage.kt); [app/src/main/kotlin/features/settings/SettingsSheetState.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/settings/SettingsSheetState.kt); [app/src/main/kotlin/features/automation](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/automation). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## 6. Маршрутизация, ресурсы и выбор приложений

<a id="r01"></a>

### R01. Перенести routing CRUD и редакторы

**Зависимости:** [C03](#c03), [P04](#p04), [X01](#x01).

**Работа:** Свести route state/operations/import-export/editor orchestration к общим сценариям и подключить RoutingRepository в Desktop. Текст правила, ID, порядок, default outbound и selector semantics сохранить по Android.

**Критерии приёмки:**

- Create/edit/remove/reorder/enable/import/export имеют одну реализацию правил.
- Ссылки на server/strategy/resource остаются корректными после изменений каталога.
- Desktop подключён к существующему переносимому routing UI; installed-package facts приходят от Android adapter.

**Проверки:** RouteRuleCollection/Clipboard tests, adapter compatibility и одинаковый generated routing на fixtures.

**Проверенные точки входа/каталоги:** [shared/app/src/commonMain/kotlin/app/skipi/app/routing](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/routing); [shared/ui/src/commonMain/kotlin/app/skipi/ui/routing](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/routing); [app/src/main/kotlin/features/routing](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/routing); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="r02"></a>

### R02. Перенести каталог ресурсов и правила обновления

**Зависимости:** [P03](#p03), [R01](#r01).

**Работа:** Модель, CRUD, validation, выбор источника, очередь/статусы и pure update/retention decisions перенести в shared. Загрузка/replace/file paths/geo asset validation реализуются host adapter на основе существующего Android поведения. Сначала свести фактический profile resourceSettings из Android UI и отдельный AppState mapping репозитория: источник/UA/очередь берутся из того же активного профиля, без второго независимого каталога настроек.

**Критерии приёмки:**

- Сохранены профильные URLs/source/User-Agent, очередь, отмена и частичные ошибки; UI, repository и updater используют согласованный профильный источник настроек.
- Ресурсный каталог/экран используют common state; Desktop resource adapter подключён для переносимых операций.
- Файл публикуется/заменяется только после прежних проверок; platform paths и notifications не проникают в доменные операции.

**Проверки:** Resource coordinator/repository queue/cancel/atomic-replace tests, routing resource validation, Desktop staged assets compatibility и ручной sample update.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/resources](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/resources); [shared/app/src/commonMain/kotlin/app/skipi/app/repository/RepositoryContracts.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/repository/RepositoryContracts.kt); [app/src/main/kotlin/features/resources](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/resources); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopCoreRuntime.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopCoreRuntime.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopApplicationRepositories.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="r03"></a>

### R03. Перенести app selection и платформенную привязку per-app

**Зависимости:** [P04](#p04), [R01](#r01).

**Работа:** Перенести pure selection/filter/search/serialization и portable presentation. Installed-package enumeration/icons, user spaces и применение per-app VPN policy остаются Android adapters. Desktop application routing не изобретать при отсутствии соответствующего runtime. Общие state/editor подключаются и в Desktop; отсутствие PackageManager ограничивает получение списка/иконок и применение policy, а не хранение/редактирование уже существующих переносимых данных.

**Критерии приёмки:**

- Сохранены Android include/exclude modes, выбранные packages, поиск и неизвестные package IDs.
- Backup/profile round-trip сохраняет per-app данные даже на хосте без применения.
- Общий редактор/состояние подключены к двум хостам. Enumeration/runtime policy выполняются реальным platform adapter; отсутствие Desktop enforcement явно отражено и не мешает сохранению существующих данных.

**Проверки:** ProxyAppListSelection + profile/backup per-app tests; Android package list and VPN policy smoke.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/features/proxy/app](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/proxy/app); [app/src/main/kotlin/system](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/system); [shared/core/src/commonMain/kotlin/features/config](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/config); [shared/app/src/commonMain/kotlin/app/skipi/app/config](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/config). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## 7. Остальные существующие мобильные сценарии

<a id="a01"></a>

### A01. Перенести Network Automation и profile activation

**Зависимости:** [T04](#t04), [P03](#p03).

**Работа:** Политика уже общая: перенести оставшиеся CRUD/editor/state, rule ordering и прикладную координацию решения. OS network facts/SSID permissions/monitoring остаются адаптерами. Desktop подключается только через реальные доступные сетевые факты. Общие правила, editor и сохранение подключаются в Desktop независимо от наличия системного наблюдателя; условными остаются получение фактов и исполнение решения.

**Критерии приёмки:**

- Сохранены Android rule priority, on-demand, activation и no-change/disconnect/switch решения.
- Состояние правил и общий редактор используются в двух хостах; Android permission callbacks не дублируют policy, Desktop сохраняет существующие правила без имитации сетевого мониторинга.
- Нет придуманных Desktop VPN/SSID возможностей; реально недоступные операции отмечены отдельными ограничениями.

**Проверки:** NetworkAutomationPolicy/activation/settings adapter tests; Android Wi-Fi/ethernet/cellular/offline scenarios.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/networkautomation](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/networkautomation); [shared/app/src/commonMain/kotlin/app/skipi/app/settings](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/settings); [app/src/main/kotlin/features/networkautomation](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/networkautomation); [app/src/main/kotlin/features/config/TrafficConfigNetworkActivationEditor.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/config/TrafficConfigNetworkActivationEditor.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="a02"></a>

### A02. Перенести onboarding и переносимый app flow

**Зависимости:** [I02](#i02), [T01](#t01).

**Работа:** Общими сделать последовательность шагов, state/actions, выбор языка/темы, initial import и completion. Android разрешения — callbacks; Desktop использует переносимые шаги существующего мобильного сценария без нового контента.

**Критерии приёмки:**

- Сохранены порядок/тексты/выбор и результат завершения Android onboarding.
- Повторный запуск и import из onboarding используют общие операции; completion сохраняется через adapter.
- Переносимые шаги доступны Desktop; platform-only запросы отображаются только при реальной поддержке.

**Проверки:** Onboarding state/import/completion tests и ручной clean-profile/re-entry smoke.

**Проверенные точки входа/каталоги:** [shared/ui/src/commonMain/kotlin/app/skipi/ui/onboarding](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/onboarding); [app/src/main/kotlin/features/onboarding/OnboardingPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/onboarding/OnboardingPage.kt); [app/src/main/kotlin/app/AppContent.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/app/AppContent.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="d01"></a>

### D01. Перенести модели и координацию диагностических инструментов

**Зависимости:** [X03](#x03), [T02](#t02).

**Работа:** Проверить speed/DNS-leak/IP-info engines и перенести parsing, расчёты, порядок замеров, state/error/cancel policies и UI orchestration. HTTP/socket/network binding/native test call — host ports; использовать существующие мобильные сервисы/URLs/алгоритмы.

**Критерии приёмки:**

- Results/verdict/units/timeout/cancel/error совпадают с Android на одинаковых response fixtures.
- Common операции не принимают Android Network/Context; platform binding выделен узко.
- Android pages используют shared state/controllers, сохраняя существующие действия и представление.

**Проверки:** SpeedTestMetrics/DnsLeakPolicy/IpInfoSummary + parser/coordinator tests с controlled transport и clock; existing Android engine tests.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/tools](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/tools); [shared/ui/src/commonMain/kotlin/app/skipi/ui/diagnostics](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/diagnostics); [app/src/main/kotlin/features/tools](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/tools). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="d02"></a>

### D02. Подключить диагностику к реальным adapters двух хостов

**Зависимости:** [D01](#d01).

**Работа:** Связать общие инструменты с существующими core/proxy/HTTP adapters. Добавляемые Desktop пункты повторяют существующие мобильные функции; обход отсутствующего native протокола не имитирует успешный тест.

**Критерии приёмки:**

- Поддержанные tools реально выполняются и дают общую интерпретацию результата.
- Не происходит незаметного переключения способа проверки TCP/real-connection или сети относительно выбранного Android сценария.
- Источник native/network ограничений указан в capabilities и отчёте.

**Проверки:** Host adapter tests; ручные start/cancel/offline/failure probes на тестовом профиле, без смены пользовательских данных.

**Проверенные точки входа/каталоги:** [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopCoreNative.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopCoreNative.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionFetcher.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSubscriptionFetcher.kt); [app/src/main/kotlin/features/tools](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/tools). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="l01"></a>

### L01. Перенести общую логику журналов и настройки интеграций

**Зависимости:** [T01](#t01).

**Работа:** Перенести записи/парсинг/filter/batching/retention decisions, viewer state и общие компоненты. Core files, logcat, clock formatting, clear/delete и external URI dispatch — adapters. Deep-link parsing/command policies общие, OS registration/receiver — хост.

**Критерии приёмки:**

- Сохранены log levels, порядок, лимиты, filter/clear/retention и сообщения.
- Common log/viewer не зависит от Android sources; оба хоста используют переносимое представление.
- URL schemes/integration actions сохраняют existing validation; platform регистрация не имитируется.

**Проверки:** Core log parser/batch/retention + deep-link policy tests; Android logcat/core и Desktop file-backed smoke.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/features/logs](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/logs); [shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiIntegrationAndLogsSettingsScreens.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiIntegrationAndLogsSettingsScreens.kt); [shared/app/src/commonMain/kotlin/app/skipi/app/runtime](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app/runtime); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopLogger.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopLogger.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="u01"></a>

### U01. Перенести чистую логику обновлений приложения

**Зависимости:** [T01](#t01).

**Работа:** Инвентаризировать и переносить только фактические pure model/version/comparison/release parsing/state transitions/banner logic. Android WorkManager/download file/notifier/APK install остаются adapters. Существующие мобильные данные и проверки сохраняются; Desktop installer/updater не проектируется без существующего платформенного механизма.

**Критерии приёмки:**

- Pure transitions и interpretation имеют одну реализацию и прежние правила.
- Android download/ready/install/error/cancel поток и сохранённый state не меняются.
- Платформенная установка явно отделена; новых update policy/каналов/способов установки не добавлено.

**Проверки:** Existing release/coordinator/worker tests + shared transition fixtures; ручной Android banner/dismiss/progress/installer smoke при доступном test artifact.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/features/updater](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/updater); [shared/app/src/commonMain/kotlin/app/skipi/app](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/app/src/commonMain/kotlin/app/skipi/app); [shared/ui/src/commonMain/kotlin/app/skipi/ui](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="k01"></a>

### K01. Перенести backup/restore/reset над общими данными

**Зависимости:** [C03](#c03), [S03](#s03), [P03](#p03), [T04](#t04), [R02](#r02), [A01](#a01), [T02](#t02), [T03](#t03), [R03](#r03).

**Работа:** Общий mapper/preview/apply использует мигрированные feature records; формат/версия backup остаются совместимыми. Android Uri/Desktop file I/O и остановка/синхронизация runtime — ports. Подключить переносимые backup/reset actions к хостам, восстановление тестировать только на изолированных данных.

**Критерии приёмки:**

- Старые backup-файлы загружаются; известные поля, IDs, selection и platform-specific данные сохраняют существующий контракт. Неизвестные JSON-ключи сейчас игнорируются codec и не обещаются как round-trip сохранённые.
- Preview/apply/reset сохраняют Android validation/warnings/order и корректную синхронизацию runtime.
- Desktop использует ту же переносимую операцию; Android-специфичные настройки входят в backup по общей схеме без обещания исполнить их на Desktop.

**Проверки:** AppBackupCompatibility/Mapper/restore tests, old fixtures и изолированный round-trip; live user data для QA не заменять.

**Проверенные точки входа/каталоги:** [shared/core/src/commonMain/kotlin/features/backup](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/core/src/commonMain/kotlin/features/backup); [app/src/main/kotlin/data/backup](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/data/backup); [app/src/main/kotlin/features/settings/SettingsBackupResetPage.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/features/settings/SettingsBackupResetPage.kt); [shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingsRestoreDialog.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/settings/SkipiSettingsRestoreDialog.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/DesktopSettingsScreen.kt). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## 8. Оболочки, очистка и итоговая интеграция

<a id="n01"></a>

### N01. Сократить composition roots и дублирующий UI

**Зависимости:** [H03](#h03), [X04](#x04), [T04](#t04), [R03](#r03), [A02](#a02), [D02](#d02), [L01](#l01), [U01](#u01), [K01](#k01), [T02](#t02), [T03](#t03).

**Работа:** После подключения отдельных features перенести остатки portable destinations/page state/controllers и theme/text/components. Хост собирает adapters и окно/activity. Android back/predictive-back, package data, Quick Settings/widgets/notifications остаются платформенными; их portable decision logic и selectors проверить отдельно.

**Критерии приёмки:**

- Composition roots не содержат повторных feature algorithms и несвязанных прямых мутаций мигрированных полей.
- Сохранены маршруты, возврат результата, анимации, gesture/back, adaptive layout и существующие локализации.
- Каждая оставшаяся ссылка на AppState/host library объясняется mapping/persistence/OS operation или записана как незавершённая.

**Проверки:** Navigation/pager/theme/components and host smoke; rg consumer inventory, default/ru/fa/zh resources, lifecycle/background/deep-link entry points.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin/app/App.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/app/App.kt); [app/src/main/kotlin/app/AppContent.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/app/AppContent.kt); [app/src/main/kotlin/app/AppServices.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin/app/AppServices.kt); [desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin/app/skipi/desktop/Main.kt); [shared/ui/src/commonMain/kotlin/app/skipi/ui/navigation](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared/ui/src/commonMain/kotlin/app/skipi/ui/navigation). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="z01"></a>

### Z01. Убрать заменённые пути и провести независимое ревью

**Зависимости:** [N01](#n01).

**Работа:** В каждом срезе удалять только доказанно заменённый duplicate path; завершить общий audit code boundaries, lost fields, dead wrappers и тестов. Сомнительный неиспользуемый элемент исследовать до удаления; не удалять native пользовательские изменения.

**Критерии приёмки:**

- Нет двух разных portable algorithms для одного перенесённого сценария.
- Нет успешных заглушек, inert repositories и неподключённых объявленных UI действий.
- Карточка каждого сценария содержит реальные entry points, common operation и host adapter.

**Проверки:** Независимое ревью оркестратора, diff/checks и архитектурный inventory; при спорном продуктном поведении сверка Android source/истории.

**Проверенные точки входа/каталоги:** [app/src/main/kotlin](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app/src/main/kotlin); [desktopApp/src/main/kotlin](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/src/main/kotlin); [shared](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

<a id="z02"></a>

### Z02. Провести итоговую интеграцию и зафиксировать ограничения

**Зависимости:** [Z01](#z01).

**Работа:** Запустить все нужные suites, сборки и ручные сценарии. Изменение CI требуется только если новые реальные tests/targets не входят в текущий прогон. Native rebuild/integration и UI проверки отчётно отделяются от unit/compile/APK.

**Критерии приёмки:**

- Shared Android/Desktop, Android app и полный Desktop test suite проходят; оба Kotlin-host приложения собираются.
- Android APK, Desktop package/native integration и ручная QA выполняются при наличии среды, с точным указанием выполненного.
- Непроверенные native/platform/UI ограничения перечислены; миграция не объявлена полностью законченной при обязательном незавершённом срезе.

**Проверки:** Команды Q1–Q4 ниже; fixtures/isolated smoke matrix и финальная карта остатков.

**Проверенные точки входа/каталоги:** [.github/workflows/ci.yml](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/.github/workflows/ci.yml); [tasks/plan.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/plan.md); [tasks/todo.md](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/tasks/todo.md); [app](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/app); [desktopApp](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp); [shared](/home/vqsego/TorvaldsVPN/Skipi/skipi-box/shared). Это ориентиры существующего кода; точный набор изменённых файлов определяется малым срезом.

## Контрольные точки и проверки

| Точка | Когда | Что подтверждается |
|---|---|---|
| CP1 | C/I завершены | Нет коллизий ID/потери данных, прежний порядок, совместимость storage и payload |
| CP2 | S/H завершены | Home/подписки вызывают общие операции; busy/cancel/refresh/selection и host эффекты сохранены |
| CP3 | P/X завершены | Visual/raw редакторы, .conf round-trip, связанные группы и Android-equivalent runtime/export |
| CP4 | T/R/A/D/L/U/K завершены | Настройки и остальные страницы используют общие данные/сценарии; OS эффекты реальны |
| CP5 | N/Z завершены | Нет переносимых дублей и неучтённых AppState consumers, итоговые сборки/QA и карта ограничений |

### Q1. Проверка малого среза

Запускать существующие и новые тесты затронутого use case, документа, planner или adapter. Новые тесты доказывают поведение, а не повторяют структуру реализации. Перед UI/controller интеграцией написать тесты значимого поведения подключения; compile/smoke дополняют их. Механическую структуру обёртки не использовать как предмет теста.

После успеха не повторять/не расширять проверки без нового изменения, ошибки или нерешённой зависимости. На контрольных точках добавляются релевантные module suites и compile checks. Полный baseline выполняется после существенного интеграционного этапа и в финале, а не после каждой небольшой правки.

### Q2. Полный проверенный unit baseline

В текущей среде известен рабочий запуск из `/home/vqsego/TorvaldsVPN/Skipi/skipi-box`:

```bash
env JAVA_HOME=/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2 \
    ANDROID_HOME=/home/vqsego/Android/Sdk \
    ANDROID_SDK_ROOT=/home/vqsego/Android/Sdk \
    ./gradlew --offline \
    :shared:core:allTests :shared:app:allTests :shared:ui:allTests \
    :desktopApp:test :app:testDebugUnitTest \
    -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
```

Gradle использует JDK 26; Android unit launcher настроен на JDK 21. При необходимости его путь задаётся `-PskipiAndroidTestJavaHome=/home/vqsego/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`. Offline подходит при наличии зависимостей в cache. Исключения HevTun означают использование существующих native artifacts, не свежую сборку C/JNI.

### Q3. Сборки и UI

С теми же JAVA_HOME/SDK переменными:

```bash
./gradlew --offline :desktopApp:compileKotlin :app:assembleDebug \
    -x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion
```

Фактическая Desktop-задача компиляции — `:desktopApp:compileKotlin`. Перед проверкой дистрибутива уточняется доступная packaging task текущего хоста; используются существующие build scripts, без нового способа доставки.

Ручная QA проводится на отдельном тестовом каталоге/профиле: чистый запуск, сохранение и reload, Home/server editors/import/subscription refresh, visual/raw profile, runtime/export, settings/routing/resources, tools и back/deep-link. В Desktop — минимум обычный и узкий размер окна; Android — доступное устройство/эмулятор. Restore/reset не выполняются на пользовательских данных. Отсутствие устройства, display или packaging tool записывается как непроведённая проверка.

### Q4. Реальный native Core

Отдельно от unit/compile/APK, после готовности библиотек и geo assets:

```bash
./gradlew :desktopApp:prepareDesktopCoreRuntime
env SKIPI_CORE_INTEGRATION_DIR=/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/build/generated/skipi-core-resources/common \
    ./gradlew :desktopApp:test \
    --tests app.skipi.desktop.DesktopCoreFfmIntegrationTest
```

Перед первой командой проверяются JDK/Go/C toolchain, доступность существующего Core checkout и geo assets. Вариант с заранее собранной библиотекой поддерживает существующее свойство `skipiCoreDesktopLibrary`. Native rebuild не затрагивает пользовательские изменения HevTun. Android VPN/JNI/HevTun запускается отдельно на доступном устройстве; Desktop FFM проверяет настоящий start/stop Core и geo rule.

Успешный unit suite без environment variable не заменяет Q4. Если требуемой среды нет, отчёт явно различает «прошло», «не выполнено» и «платформенное ограничение».

## Результат каждого среза и итоговый отчёт

- Карточка и checklist обновлены только по фактически выполненному.
- Тесты написаны и запущены до production-изменения; зафиксированы исходный результат и GREEN. Ожидания основаны на Android-контракте.
- Указаны общий сценарий, изменённые adapters, сохранённые инварианты, запущенные проверки и известные ограничения.
- Старый путь удалён после проверки всех UI/background/deep-link consumers.
- В конце обновлена матрица областей: common code, Android adapter, Desktop adapter, оставшаяся работа/обоснованное platform-only.
- Миграция не объявляется завершённой при обязательном неперенесённом сценарии или непроверенном результате; ограничения среды и runtime перечисляются отдельно.

