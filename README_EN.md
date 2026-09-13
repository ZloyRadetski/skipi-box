<p align="center">
  <img src="design/skipi_logo_long.svg" alt="SKIPI Banner" width="650" />
</p>

<p align="center">
  <strong><a href="README.md">Русский</a> | <a href="README_EN.md">English</a> | <a href="README_ZH.md">简体中文</a> | <a href="README_FA.md">فارسی</a></strong>
</p>

<p align="center">
  <strong>Fast and convenient proxy client for Android</strong>
</p>

<p align="center">
  <a href="https://github.com/ZloyRadetski/skipi-box"><img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://t.me/skipi_public"><img src="https://img.shields.io/badge/Telegram-@skipi__public-2CA5E0.svg?style=flat-square&logo=telegram&logoColor=white" alt="Telegram Channel"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## Telegram Channel & Chat

Project channel: **[@skipi_public](https://t.me/skipi_public)**

* Latest builds and releases
* Config discussions and routing help
* Development news

<p align="center">
  <a href="https://t.me/skipi_public">
    <img src="https://img.shields.io/badge/Join%20Telegram-@skipi__public-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Join Telegram" height="42" />
  </a>
</p>

---

## About The Project

SKIPI is an Android client that combines flexible networking capabilities with an intuitive Jetpack Compose interface supporting Material You themes.

Under the hood, it is powered by Xray-core, SKIPI Core, hev-socks5-tunnel, as well as AmneziaWG and olcRTC engines.

---

## Screenshots

<p align="center">
  <img src="design/screenshots/main_compact_view.jpg" width="19%" alt="Compact View" />
  <img src="design/screenshots/main_classic_view.jpg" width="19%" alt="Classic View" />
  <img src="design/screenshots/full_app_customization.jpg" width="19%" alt="Theme Customization" />
  <img src="design/screenshots/routing_rules.jpg" width="19%" alt="Routing Rules" />
  <img src="design/screenshots/about_program.jpg" width="19%" alt="About Page" />
</p>

---

## Features

* Support for VLESS, AmneziaWG, olcRTC, Hysteria 2, Trojan, VMess, Shadowsocks, and WireGuard
* Shadowrocket rule-based routing (.conf), domain lists, GeoIP, and GeoSite with a visual rule editor
* Subscription import from v2rayNG, Clash, Clash Meta (with age-key decryption), and Base64
* Scheduled background updates for subscriptions and Geo databases via WorkManager
* Node latency testing, load balancing, and proxy chains
* Per-app proxying
* Customization for themes, accent colors, and app icon selection

---

## Supported Protocols

| Protocol | Transport & Security |
| :--- | :--- |
| VLESS | Reality, XTLS Vision, TLS, gRPC, WebSocket, TCP, HTTP/2, mKCP |
| AmneziaWG | UDP with custom headers and anti-DPI protection |
| olcRTC | WebRTC transport |
| WireGuard | Standard UDP tunnel |
| Hysteria 2 | UDP / QUIC |
| VMess | TLS, WebSocket, gRPC, TCP, HTTP/2, mKCP |
| Trojan | TLS, gRPC, WebSocket, TCP |
| Shadowsocks | AEAD, SS-2022 (blake3) |
| SOCKS5 / HTTP | TCP / Auth |
| Custom JSON | Custom Xray configurations |

---

## License

This project is open-source and distributed under the [GPL-3.0](LICENSE) license.
