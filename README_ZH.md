<p align="center">
  <img src="design/skipi_logo_long.svg" alt="SKIPI Banner" width="650" />
</p>

<p align="center">
  <strong><a href="README.md">Русский</a> | <a href="README_EN.md">English</a> | <a href="README_ZH.md">简体中文</a> | <a href="README_FA.md">فارسی</a></strong>
</p>

<p align="center">
  <strong>美观、快速且现代的 Android 代理客户端</strong>
</p>

<p align="center">
  <a href="https://github.com/ZloyRadetski/skipi-box"><img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://t.me/skipi_public"><img src="https://img.shields.io/badge/Telegram-@skipi__public-2CA5E0.svg?style=flat-square&logo=telegram&logoColor=white" alt="Telegram Channel"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## 📢 社区与 Telegram 频道

欢迎加入我们的官方 Telegram 频道：**[@skipi_public](https://t.me/skipi_public)** ✈️

* 🚀 **最新版本与 APK：** 抢先体验测试版和最新功能。
* 💬 **社区交流与讨论：** 提问、分享配置并获得社区帮助。
* 📢 **项目动态与路线图：** 实时了解开发进展和重要公告。

<p align="center">
  <a href="https://t.me/skipi_public">
    <img src="https://img.shields.io/badge/加入%20Telegram-@skipi__public-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Join Telegram" height="42" />
  </a>
</p>

---

## 关于项目

**SKIPI** 是一款适用于 Android 的现代化反审查与网络流量管理客户端。

项目的核心理念是打造一个功能强大全面、同时拥有丝滑整洁、适合日常舒适使用的现代用户界面。

在底层，SKIPI 集成了 [Xray-core](https://github.com/XTLS/Xray-core)、[AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) 和 [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)。在前端，基于 Jetpack Compose 构建了高度可定制的用户界面，完美支持 Material You 动态主题取色与深度视觉个性化。

---

## 应用截图

<p align="center">
  <img src="design/screenshots/main_compact_view.jpg" width="19%" alt="紧凑视图" />
  <img src="design/screenshots/main_classic_view.jpg" width="19%" alt="经典视图" />
  <img src="design/screenshots/full_app_customization.jpg" width="19%" alt="主题定制" />
  <img src="design/screenshots/routing_rules.jpg" width="19%" alt="路由规则" />
  <img src="design/screenshots/about_program.jpg" width="19%" alt="关于页面" />
</p>

---

## 核心功能

* **支持所有关键协议：** VLESS（支持 XTLS Reality 与 Vision）、VMess、Trojan、Shadowsocks（包括 SS-2022）、Hysteria 2、WireGuard 以及标准 SOCKS5/HTTP 代理。
* **智能流量路由：** 完全兼容 Shadowrocket (`.conf`) 规则与配置。支持基于域名、GeoIP、GeoSite 和 IP CIDR 的灵活规则路由，可视化直观拖拽调整规则顺序。
* **便捷的订阅管理：** 支持 v2rayNG、Clash、Clash Meta（包括 age 密钥解密）及 Base64 订阅格式。
* **自动化与定时任务：** 通过 WorkManager 后台定时更新订阅与 GeoIP/GeoSite 规则库，根据 Wi-Fi 网络自动切换服务器（On-Demand）。
* **负载均衡与分流链：** 支持代理组自动测速优选（URL-Test）、故障转移（Fallback）及多跳代理链。
* **应用分流（Per-App Proxy）：** 仅让选定的应用程序通过代理隧道。
* **深度个性化：** 支持 Material You 动态取色、自定义强调色，并在主题设置中提供 9 种精美矢量应用图标样式。
* **局域网代理共享（Hotspot & LAN）：** 内置 HTTP 和 SOCKS5 服务，可通过 Wi-Fi 热点将代理连接共享给其他设备。

---

## 支持的协议

| 协议 | 传输与安全协议 |
| :--- | :--- |
| **VLESS** | Reality, XTLS Vision, TLS, gRPC, WebSocket, TCP, HTTP/2, mKCP |
| **VMess** | TLS, WebSocket, gRPC, TCP, HTTP/2, mKCP |
| **Trojan** | TLS, gRPC, WebSocket, TCP |
| **Shadowsocks** | AEAD, SS-2022 (blake3) |
| **Hysteria 2** | UDP / QUIC |
| **WireGuard** | UDP |
| **SOCKS5 / HTTP** | TCP / Auth |
| **Custom JSON** | 任意自定义 Xray 配置 |

---

## 开源许可证

本项目基于 [GPL-3.0 许可证](LICENSE) 开源发布。
