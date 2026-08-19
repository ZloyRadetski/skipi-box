<p align="center">
  <img src="design/skipi_logo_long.svg" alt="SKIPI Banner" width="650" />
</p>

<p align="center">
  <strong>Красивый, быстрый и удобный прокси-клиент для Android</strong>
</p>

<p align="center">
  <a href="https://github.com/ZloyRadetski/skipi-box"><img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## О проекте

**SKIPI(СКИПИ)** - это современный клиент для обхода блокировок и управления сетевым трафиком на Android.

Главная идея проекта - сделать мощный инструмент с возможностями комбайна, но с приятным, аккуратным интерфейсом, которым приятно и удобно пользоваться каждый день. Все плавно, понятно и нативно.

Внутри трудится связка из [Xray-core](https://github.com/XTLS/Xray-core), [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) и [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel), а снаружи - полностью кастомизируемый UI на Jetpack Compose с поддержкой динамических цветов Material You и полной кастомизацией оформления.

---

## Скриншоты

<p align="center">
  <img src="design/screenshots/main_compact_view.jpg" width="19%" alt="Компактный вид" />
  <img src="design/screenshots/main_classic_view.jpg" width="19%" alt="Классический вид" />
  <img src="design/screenshots/full_app_customization.jpg" width="19%" alt="Кастомизация тем" />
  <img src="design/screenshots/routing_rules.jpg" width="19%" alt="Правила роутинга" />
  <img src="design/screenshots/about_program.jpg" width="19%" alt="О программе" />
</p>

---

## Что умеет SKIPI

* **Все ключевые протоколы под капотом**: VLESS (с поддержкой Reality и XTLS Vision), VMess, Trojan, Shadowsocks (включая 2022), Hysteria 2, WireGuard и обычные SOCKS5/HTTP прокси.
* **Умная маршрутизация трафика**: Полная совместимость с правилами и профилями формата Shadowrocket (`.conf`). Можно гибко настраивать правила по доменам, GeoIP, GeoSite и IP-диапазонам, а также перетаскивать правила пальцем в удобном визуальном редакторе.
* **Подписки без головной боли**: Умеет читать ссылки и форматы v2rayNG, Clash, Clash Meta (включая расшифровку age-ключей) и Base64.
* **Автоматизация и расписание**: Фоновое обновление подписок и баз правил GeoIP/GeoSite через WorkManager, переключение по Wi-Fi сетям и расписанию.
* **Балансировка и цепочки**: Создание групп серверов для автовыбора самого быстрого узла (URL-Test), резервного переключения (Fallback) или построения цепочек прокси.
* **Выборочное проксирование**: Разделение трафика по приложениям (Per-App Proxy), чтобы пускать через туннель только то, что действительно нужно.
* **Глубокая кастомизация**: Поддержка динамических цветов системы Material You, ручной выбор акцентных цветов интерфейса и выбор из 9 векторных стилей иконки приложения прямо в настройках темы.

---

## Поддерживаемые протоколы

| Протокол | Транспорт и защита |
| :--- | :--- |
| **VLESS** | Reality, XTLS Vision, TLS, gRPC, WebSocket, TCP, HTTP/2, mKCP |
| **VMess** | TLS, WebSocket, gRPC, TCP, HTTP/2, mKCP |
| **Trojan** | TLS, gRPC, WebSocket, TCP |
| **Shadowsocks** | AEAD, SS-2022 (blake3) |
| **Hysteria 2** | UDP / QUIC |
| **WireGuard** | UDP |
| **SOCKS5 / HTTP** | TCP / Auth |
| **Custom JSON** | Любые кастомные конфигурации Xray |

---

## Лицензия

Проект открыт и распространяется под лицензией [GPL-3.0](LICENSE).
