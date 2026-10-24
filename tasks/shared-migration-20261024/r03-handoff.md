# Продолжение после R03

24 октября 2026 года. **R03 закрыта**, S03 также закрыта. Текущая ветка `refactor/desktop-shared-outbound-plan`; production UI checkpoint `ebfca81`, policy `d3b285b`, projection `0a06c65`, native/preservation `c99b94f`. [Итоговая приёмка](r03-acceptance-verification.md) и [план](../plan.md) задают актуальный статус.

Последний единый full gate: **2960 GREEN1m19s**, 8 свежих targets, failures/errors/skips0; shared core400+341, app357+349, ui122+122, Android615, Desktop654; real Core9/9, Desktop compile и debug APK GREEN. Все25 изменённых Kotlin SHA неизменны. Evidence `outputs/r03-final-full-reports-20261024`. После этого только документация/коммиты; повторять проверки без новой причины не нужно.

## Результат и ограничения

- P04 common mode/clipboard/controller сохранён. R03 добавила common projection/policy и полный portable catalog list UI. Android остался поставщиком package/user/icon facts и исполнителем VPN Builder; Desktop сохраняет profile per-app данные с прежним capability notice.
- Runtime strict override не переписывает persisted profile. User prefix разбирается до trim; unknown IDs сохраняются storage/editor, отсутствующие приложения не выдумываются. Empty/all-stale whitelist native fallback считает успешные Builder adds, `NameNotFoundException` пропускается, другие ошибки не скрываются.
- Search150ms/pager/checkbox/tab count/UID/common rendering проверены7 actual Compose scenes. Raw selected ID передаётся в common Pages, валидированный fallback — в clipboard/tabs. Source Android loading clock500ms и Default wrappers сохранены.
- Profile codec canonicalizes trim/empty/distinct; backup DTO exact typed copy и live Android canonical restore различены. Desktop temporary store и Android AppState backup/restore неизвестные package IDs сохраняют.
- Нет Desktop per-app OS enforcement, нового inventory, physical UI/device/Android packet QA. Synthetic native smoke не устанавливает TUN. Foreign dirty native submodule сохранён.

## Дальнейшая работа

Ближайшая незакрытая implementation-карточка основного порядка — **H01**, единая Android grouping/sorting/Home projection для All/manual/subscription/AutoBalancer/config-derived strategy groups. Зависимости C04/S01 выполнены. H01 production и тесты ещё не начаты; исходники сначала исследовать, corpus/spec и tests написать/запустить до кода. Затем H02/H03; не вводить новую Home семантику или runtime функции.

Корневой агент — orchestrator/reviewer, основную research/tests/implementation работу отдавать **gpt-6-luna, reasoning max**. Root координирует единственный Gradle и Git commits; агенты меняют только свои paths. Не использовать реальные пользовательские files/clipboard/settings/device для QA. `hevtun/src/main/jni/hev-socks5-tunnel` не stage/reset/rebuild.

Проверенная full команда: JDK26 `/home/vqsego/.gradle/jdks/eclipse_adoptium-26-amd64-linux.2`, SDK `/home/vqsego/Android/Sdk`, Android tests JDK21 property; `SKIPI_CORE_INTEGRATION_DIR=/home/vqsego/TorvaldsVPN/Skipi/skipi-box/desktopApp/build/generated/skipi-core-resources/common`; `--offline`, восемь targets с individual `--rerun`, `:desktopApp:compileKotlin :app:assembleDebug`, `-x :hevtun:buildHevTun -x :hevtun:syncHevSocks5TunnelVersion`. Полная выполненная команда/вывод сохранены в evidence/turn tools; тестовые XML shared Android лежат в `testAndroidHostTest`.
