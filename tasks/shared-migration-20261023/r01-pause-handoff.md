# Пауза после R01 — 23 октября 2026

> Последующее указание пользователя: работа возобновлена; завершить S03, затем перейти к R03. Запись ниже сохраняет историческую точку паузы, а текущий порядок определяют tasks/plan.md и tasks/todo.md.

Пользователь попросил завершить R01 и сделать паузу. **R01 завершена; следующая карточка не начата.** Возобновлять работу после нового указания пользователя. Root остаётся оркестратором gpt-6-luna/max; порядок: Android source → мыслить/спецификация → тесты и исходный запуск → минимальный перенос → GREEN/review → commit.

## Состояние

- Repository: `/home/vqsego/TorvaldsVPN/Skipi/skipi-box`, branch `refactor/desktop-shared-outbound-plan`.
- Routing repository checkpoint `a5cba46`, no-profile runtime checkpoint `efcdde7`, resource-policy/host wiring checkpoint `7a213bd`.
- [R01 acceptance](r01-acceptance-verification.md) и [полный evidence bundle](../../outputs/r01-final-full-reports-20261023/counts.json): один свежий прогон 2859/2859 GREEN за 94s, failures/errors/skips 0; Core9/9, Desktopcompile/debugAPK GREEN. Исходники до/после совпали по SHA. APK не устанавливалась, приложение пользователя не перезапускалось.
- По checklist закрыто 14 из 39 карточек. Это количество карточек, не доля оставшейся работы.
- Посторонний modified submodule `hevtun/src/main/jni/hev-socks5-tunnel` сохранён; не reset/stage/update его.

## Что не нужно повторять

Не повторять прошедшие проверки без изменения source или новой причины. Не переносить заново P04 profile editors, common routing CRUD/codec/list/form. Global `RoutingPage` была недоступна из Android navigation уже в initial commit; не придумывать Desktop entrypoint. [Source audit](r01-routing-source-audit.md) фиксирует историю. Android profile runtime перекрывает global routing; Custom/profile приоритет сохранён.

Resource validation uses the same captured global asset directory as Core for no-profile starts; profiles keep their own directory. Null data-directory preview/export compatibility remains explicit. External domain preflight keeps Android's literal filename behavior, including the no-suffix distinction. Missing files/malformed references produce the existing errors; a global external-file catalog was not added.

## При следующем продолжении

Проверить `git status --short` и последние commits, прочитать актуальные `tasks/plan.md` / `tasks/todo.md`. Выбрать следующий малый срез по зависимостям; R03 — готовый по P04/R01 кандидат, но сейчас работа над ним не начата. У R03 уже есть общие Per-App codec/controller/screen из P04; остаются исходные Android package inventory/user/search/icon и runtime/backup границы. Не создавать Desktop fake package inventory или enforcement. Проверить source до выбора тестов.

Root координирует единственный Gradle процесс и Git; агенты не запускают сборки. Использовать Gradle wrapper/offline и JDK/SDK из проверенных команд в [resource verification](r01-routing-resources-verification.md). Для fresh full test targets применяется per-task `--rerun`, без `--rerun-tasks`. Real Core требует `SKIPI_CORE_INTEGRATION_DIR`; обычный unit suite сам по себе не доказывает FFM запуск.
