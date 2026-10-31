# T04 (Desktop TUN, Linux) и аудит карточек T01–T03

Дата: 31 октября 2026. Статус карточек не меняется: физическая проверка пользователем ещё не выполнена, галочки в `tasks/todo.md` не ставятся.

## T04. Что сделано для Desktop/Linux

Архитектура и причины выбора: NetworkManager создаёт tun для пользователя через D-Bus, приложение без прав подключает к нему fd и отдаёт его `hev-socks5-tunnel` (общая `.so`, собирается Gradle из копии сабмодуля, без детектора переполнения стека — он ломает JVM). Привязка сокетов Core к физическому интерфейсу (`SO_BINDTODEVICE`) включается до старта Core. Подробности и риски — в памяти проекта и в этом разделе.

| Срез | Тесты до кода | Результат |
|---|---|---|
| `HevSocks5TunnelYaml` в shared, Android делегирует | 3 теста; API RED, затем GREEN; Android hev-тесты 13 GREEN | выполнено |
| `TunInterfacePlan` (shared/core) | 5 тестов; API RED → GREEN, два таргета | выполнено |
| Go: `nmtun`, `netbind`, `tunhost`, экспорты `TunProbe/TunPrepare/TunOpen/TunClose` | настройки/права/Binder — Go-тесты; интеграционный тест с реальным NM (по `SKIPI_NM_INTEGRATION=1`) пользователь запускал | выполнено |
| Desktop: `DesktopTunRuntime`, ветка TUN в `DesktopTunnelController`, `DesktopTunHost`, FFM, страница «Служба VPN и TUN», автозапуск, автоподключение при открытии | API RED → GREEN на каждый класс | выполнено |
| Бэкап/сброс TUN-полей, кнопка «Сбросить VPN» | RED (нет `withVpnSettingsReset`) → GREEN | выполнено; per-config tunnel settings при сбросе не трогаются |
| keepalive/user-timeout в direct-аутбаунде профиля | RED (нет `xrayTcpSockopt`) → GREEN | профильный путь; сгенерированный путь (`transport = null`) и сырой Custom не затрагиваются |
| Строгий полный туннель | RED → GREEN; Android baseline `StrictFullTunnelTest` GREEN | routing как на Android; Custom отклоняется как на Android; профильные `[Rule]` не меняются (так же на Android) |
| Привязка до старта Core | RED (нет `Prepare`, `prepareTun`) → GREEN | выполнено; нужна проверка в реальном запуске |
| IPv6 в туннеле | RED → GREEN | только из `ipv6` активного профиля; глобальной настройки нет (см. T03) |

### Не проверено
Реальный запуск с итоговой сборкой после этих срезов, автозапуск при входе, смена сети (по решению пользователя не проверяется), Windows, macOS.

## T01–T03: аудит исходников (без изменений кода)

**T03 (глобальные DNS/Mux/Fragment).** В Android `SettingsSheetState` открывает только `openTunSettings`; `showDnsSettings`, `showMuxSettings`, `showFragmentSettings`, `showSubscriptionPingSettings`, `showLocalProxySettings` нигде не выставляются в `true` в `app/src/main`. Листы существуют в `SettingsBottomSheetsHost`, но недостижимы. Это тот же случай, что dormant глобальная `RoutingPage` в R01. По правилу проекта (не придумывать функции) глобальной Desktop-страницы DNS/Mux/Fragment не добавляется. Живой путь — настройки внутри профиля (Tunnel/Network формы), он на Desktop уже перенесён в P04. Глобальные Mux/Fragment Desktop сохраняет и применяет (коммиты 1b7180e7, b57fab63).

**T01/T02.** Android-маршруты настроек: Appearance, General, Vpn, NetworkAutomation, Subscriptions, Integration, Logs, BackupReset (+ инструменты, About). Desktop имеет Appearance, General, Vpn, LocalProxy, Subscriptions (+Ping, UserAgents), NetworkAutomation, BackupReset, SpeedTest, DnsLeakTest, IpInfo, UrlSchemes, Diagnostics, CoreLogs, AccessLogs, About. Отсутствует только Integration (исключён в `desktopSettingsVisibleDestinations` коммитом 25d04b70). Закрытие карточек требует решения пользователя и ручной проверки, не этого аудита.

## Остаточные риски
- Глобального переключателя IPv6 на Desktop нет; пока IPv6 в туннеле зависит от профиля.
- `hev` собирается с `TPFLAGS=` (без stack-overflow detector); в Gradle стоит проверка, что детектор не вернулся.
- В тестовом окружении пользователя свежие loopback-UDP сокеты получают посторонние датаграммы; клиент SOCKS-UDP DNS это учитывает.
