<p align="center">
  <img src="design/skipi_logo_long.svg" alt="SKIPI Banner" width="650" />
</p>

<p align="center">
  <strong><a href="README.md">Русский</a> | <a href="README_EN.md">English</a> | <a href="README_ZH.md">简体中文</a> | <a href="README_FA.md">فارسی</a></strong>
</p>

<p align="center">
  <strong>快速便捷的 Android 代理客户端</strong>
</p>

<p align="center">
  <a href="https://github.com/ZloyRadetski/skipi-box"><img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://t.me/skipi_public"><img src="https://img.shields.io/badge/Telegram-@skipi__public-2CA5E0.svg?style=flat-square&logo=telegram&logoColor=white" alt="Telegram Channel"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## Telegram 频道与交流群

官方频道：**[@skipi_public](https://t.me/skipi_public)**

* 最新安装包与版本发布
* 配置交流与分流规则答疑
* 开发动态与最新资讯

<p align="center">
  <a href="https://t.me/skipi_public">
    <img src="https://img.shields.io/badge/加入%20Telegram-@skipi__public-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Join Telegram" height="42" />
  </a>
</p>

---

## 关于项目

SKIPI 是一款 Android 客户端，将灵活的网络连接能力与基于 Jetpack Compose 的直观界面相结合，并支持 Material You 动态主题。

底层由 Xray-core、SKIPI Core、hev-socks5-tunnel 以及 AmneziaWG 和 olcRTC 引擎提供强力驱动。

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

* 支持 VLESS、AmneziaWG、olcRTC、Hysteria 2、Trojan、VMess、Shadowsocks 和 WireGuard
* 基于 Shadowrocket 规则 (.conf)、域名列表、GeoIP 与 GeoSite 的灵活分流路由，并配备可视化编辑器
* 支持导入 v2rayNG、Clash、Clash Meta（包含 age 密钥解密）及 Base64 订阅
* 通过 WorkManager 定时在后台更新订阅和地理规则数据库
* 节点延迟测速、负载均衡与多跳代理链
* 应用分流（分应用代理）
* 主题个性化、强调色选择及应用图标自定义

---

## 支持的协议

| 协议 | 传输与安全保护 |
| :--- | :--- |
| VLESS | Reality, XTLS Vision, TLS, gRPC, WebSocket, TCP, HTTP/2, mKCP |
| AmneziaWG | 带自定义报头及防 DPI 保护的 UDP |
| olcRTC | WebRTC 传输 |
| WireGuard | 标准 UDP 隧道 |
| Hysteria 2 | UDP / QUIC |
| VMess | TLS, WebSocket, gRPC, TCP, HTTP/2, mKCP |
| Trojan | TLS, gRPC, WebSocket, TCP |
| Shadowsocks | AEAD, SS-2022 (blake3) |
| SOCKS5 / HTTP | TCP / 认证 |
| Custom JSON | 自定义 Xray 配置文件 |

---

## 开源许可证

本项目开源并遵循 [GPL-3.0](LICENSE) 许可证分发。
