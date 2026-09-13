<p align="center">
  <img src="design/skipi_logo_long.svg" alt="SKIPI Banner" width="650" />
</p>

<p align="center">
  <strong><a href="README.md">Русский</a> | <a href="README_EN.md">English</a> | <a href="README_ZH.md">简体中文</a> | <a href="README_FA.md">فارسی</a></strong>
</p>

<p align="center">
  <strong>Быстрый и удобный прокси-клиент для Android</strong>
</p>

<p align="center">
  <a href="https://github.com/ZloyRadetski/skipi-box"><img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://t.me/skipi_public"><img src="https://img.shields.io/badge/Telegram-@skipi__public-2CA5E0.svg?style=flat-square&logo=telegram&logoColor=white" alt="Telegram Channel"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## Telegram-канал и чат

Канал проекта: **[@skipi_public](https://t.me/skipi_public)**

* Свежие сборки и релизы
* Обсуждение конфигов и помощь с маршрутизацией
* Новости разработки

<p align="center">
  <a href="https://t.me/skipi_public">
    <img src="https://img.shields.io/badge/Вступить%20в%20Telegram-@skipi__public-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Join Telegram" height="42" />
  </a>
</p>

---

## О проекте

SKIPI - клиент для Android, объединяющий гибкую работу с сетью и понятный интерфейс на Jetpack Compose с поддержкой тем Material You.

Под капотом трудятся Xray-core, SKIPI Core, hev-socks5-tunnel, а также движки AmneziaWG и olcRTC.

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

## Возможности

* Поддержка VLESS, AmneziaWG, olcRTC, Hysteria 2, Trojan, VMess, Shadowsocks и WireGuard
* Маршрутизация на основе правил Shadowrocket (.conf), списков доменов, GeoIP и GeoSite с визуальным редактором
* Импорт подписок v2rayNG, Clash, Clash Meta с age-ключами и Base64
* Фоновое обновление подписок и геобаз по расписанию через WorkManager
* Проверка задержки узлов, балансировка нагрузки и цепочки прокси
* Выборочное проксирование отдельных приложений
* Настройка темы, акцентных цветов и выбор иконки приложения

---

## Поддерживаемые протоколы

| Протокол | Транспорт и защита |
| :--- | :--- |
| VLESS | Reality, XTLS Vision, TLS, gRPC, WebSocket, TCP, HTTP/2, mKCP |
| AmneziaWG | UDP с кастомными заголовками и защитой от DPI |
| olcRTC | WebRTC транспорт |
| WireGuard | Стандартный UDP туннель |
| Hysteria 2 | UDP / QUIC |
| VMess | TLS, WebSocket, gRPC, TCP, HTTP/2, mKCP |
| Trojan | TLS, gRPC, WebSocket, TCP |
| Shadowsocks | AEAD, SS-2022 (blake3) |
| SOCKS5 / HTTP | TCP / Auth |
| Custom JSON | Пользовательские конфигурации Xray |

---

## Лицензия

Проект открыт и распространяется под лицензией [GPL-3.0](LICENSE).
