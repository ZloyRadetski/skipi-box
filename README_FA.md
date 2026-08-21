<p align="center">
  <img src="design/skipi_logo_long.svg" alt="SKIPI Banner" width="650" />
</p>

<p align="center">
  <strong><a href="README.md">Русский</a> | <a href="README_EN.md">English</a> | <a href="README_ZH.md">简体中文</a> | <a href="README_FA.md">فارسی</a></strong>
</p>

<p align="center">
  <strong>کلاینت پروکسی زیبا، سریع و مدرن برای اندروید</strong>
</p>

<p align="center">
  <a href="https://github.com/ZloyRadetski/skipi-box"><img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://t.me/skipi_public"><img src="https://img.shields.io/badge/Telegram-@skipi__public-2CA5E0.svg?style=flat-square&logo=telegram&logoColor=white" alt="Telegram Channel"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## 📢 جامعه و کانال تلگرام

به کانال رسمی تلگرام ما بپیوندید: **[@skipi_public](https://t.me/skipi_public)** ✈️

* 🚀 **آخرین نسخه‌ها و فایل‌های APK:** دسترسی زودهنگام به نسخه‌های آزمایشی و قابلیت‌های جدید.
* 💬 **جامعه و گفتگوها:** سوالات خود را بپرسید، کانفیگ به اشتراک بگذارید و راهنمایی بگیرید.
* 📢 **اخبار و نقشه راه:** از روند پیشرفت توسعه و اطلاعیه‌ها مطلع شوید.

<p align="center">
  <a href="https://t.me/skipi_public">
    <img src="https://img.shields.io/badge/عضویت%20در%20تلگرام-@skipi__public-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Join Telegram" height="42" />
  </a>
</p>

---

## درباره پروژه

**SKIPI** یک کلاینت مدرن برای دور زدن سانسور اینترنت و مدیریت ترافیک شبکه در اندروید است.

ایده اصلی این پروژه ارائه ابزاری قدرتمند و چندمنظوره با رابط کاربری روان، تمیز و دلنشین برای استفاده روزمره است.

در بخش زیرساخت، برنامه از [Xray-core](https://github.com/XTLS/Xray-core)، [SKIPI Core](https://github.com/ZloyRadetski/skipi-core) و [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) بهره می‌برد. در بخش ظاهری، رابط کاربری با Jetpack Compose ساخته شده و از رنگ‌های پویای Material You و شخصی‌سازی عمیق پشتیبانی می‌کند.

---

## تصاویر برنامه

<p align="center">
  <img src="design/screenshots/main_compact_view.jpg" width="19%" alt="نمای فشرده" />
  <img src="design/screenshots/main_classic_view.jpg" width="19%" alt="نمای کلاسیک" />
  <img src="design/screenshots/full_app_customization.jpg" width="19%" alt="شخصی‌سازی تم" />
  <img src="design/screenshots/routing_rules.jpg" width="19%" alt="قوانین مسیریابی" />
  <img src="design/screenshots/about_program.jpg" width="19%" alt="درباره برنامه" />
</p>

---

## قابلیت‌های کلیدی

* **پشتیبانی از پروتکل‌های اصلی:** VLESS (با پشتیبانی از XTLS Reality و Vision)، VMess، Trojan، Shadowsocks (شامل SS-2022)، Hysteria 2، WireGuard و پروکسی‌های استاندارد SOCKS5/HTTP.
* **مسیریابی هوشمند ترافیک:** سازگاری کامل با قوانین Shadowrocket (`.conf`). مسیریابی انعطاف‌پذیر بر اساس دامنه، GeoIP، GeoSite و محدوده‌های IP، همراه با جابجایی آسان قوانین.
* **پشتیبانی آسان از اشتراک‌ها:** خواندن فرمت‌های v2rayNG، Clash، Clash Meta (شامل رمزگشایی کلیدهای age) و Base64.
* **اتوماسیون و زمان‌بندی:** به‌روزرسانی خودکار اشتراک‌ها و پایگاه داده‌های GeoIP/GeoSite در پس‌زمینه از طریق WorkManager و تغییر خودکار سرور بر اساس شبکه Wi-Fi فعال (On-Demand).
* **تعادل بار و زنجیره پروکسی:** گروه‌بندی سرورها برای انتخاب سریع‌ترین گره (URL-Test)، جایگزینی در صورت قطعی (Fallback) یا ساخت زنجیره‌های چندمرحله‌ای.
* **پروکسی به تفکیک برنامه (Per-App Proxy):** عبور دادن ترافیک برنامه‌های دلخواه از تونل VPN.
* **شخصی‌سازی گسترده:** پشتیبانی از رنگ‌های پویای Material You، انتخاب پالت‌های رنگی دلخواه و ۹ استایل مختلف آیکون برنامه.
* **اشتراک‌گذاری پروکسی (Hotspot & LAN):** سرویس‌های داخلی HTTP و SOCKS5 برای اشتراک اتصال VPN با سایر دستگاه‌ها از طریق Wi-Fi.

---

## پروتکل‌های پشتیبانی‌شده

| پروتکل | انتقال و امنیت |
| :--- | :--- |
| **VLESS** | Reality, XTLS Vision, TLS, gRPC, WebSocket, TCP, HTTP/2, mKCP |
| **VMess** | TLS, WebSocket, gRPC, TCP, HTTP/2, mKCP |
| **Trojan** | TLS, gRPC, WebSocket, TCP |
| **Shadowsocks** | AEAD, SS-2022 (blake3) |
| **Hysteria 2** | UDP / QUIC |
| **WireGuard** | UDP |
| **SOCKS5 / HTTP** | TCP / Auth |
| **Custom JSON** | پیکربندی‌های دلخواه و سفارشی Xray |

---

## مجوز (License)

این پروژه متن‌باز بوده و تحت مجوز [GPL-3.0 License](LICENSE) منتشر شده است.
