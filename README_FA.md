<p align="center">
  <img src="design/skipi_logo_long.svg" alt="SKIPI Banner" width="650" />
</p>

<p align="center">
  <strong><a href="README.md">Русский</a> | <a href="README_EN.md">English</a> | <a href="README_ZH.md">简体中文</a> | <a href="README_FA.md">فارسی</a></strong>
</p>

<p align="center">
  <strong>کلاینت پروکسی سریع و راحت برای اندروید</strong>
</p>

<p align="center">
  <a href="https://github.com/ZloyRadetski/skipi-box"><img src="https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat-square&logo=android&logoColor=white" alt="Platform"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  <a href="https://t.me/skipi_public"><img src="https://img.shields.io/badge/Telegram-@skipi__public-2CA5E0.svg?style=flat-square&logo=telegram&logoColor=white" alt="Telegram Channel"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License"></a>
</p>

---

## کانال و گروه گفتگوی تلگرام

کانال پروژه: **[@skipi_public](https://t.me/skipi_public)**

* نسخه‌ها و فایل‌های نصبی جدید
* گفتگو پیرامون کانفیگ‌ها و راهنمایی در مسیریابی
* اخبار و روند توسعه برنامه

<p align="center">
  <a href="https://t.me/skipi_public">
    <img src="https://img.shields.io/badge/عضویت%20در%20تلگرام-@skipi__public-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Join Telegram" height="42" />
  </a>
</p>

---

## درباره پروژه

برنامه SKIPI یک کلاینت اندروید است که عملکرد منعطف شبکه را با رابط کاربری روان و ساده بر پایه Jetpack Compose و پشتیبانی از تم‌های Material You ترکیب می‌کند.

در بخش زیرساخت، از هسته‌های Xray-core، SKIPI Core، hev-socks5-tunnel و همچنین موتورهای AmneziaWG و olcRTC استفاده می‌شود.

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

## امکانات و قابلیت‌ها

* پشتیبانی از پروتکل‌های VLESS، AmneziaWG، olcRTC، Hysteria 2، Trojan، VMess، Shadowsocks و WireGuard
* مسیریابی بر پایه قوانین Shadowrocket (.conf)، فهرست دامنه‌ها، GeoIP و GeoSite همراه با ویرایشگر بصری
* وارد کردن اشتراک‌های v2rayNG، Clash، Clash Meta (شامل رمزگشایی کلیدهای age) و Base64
* به‌روزرسانی خودکار اشتراک‌ها و پایگاه‌های داده در پس‌زمینه بر اساس زمان‌بندی از طریق WorkManager
* بررسی تأخیر پینگ سرورها، تعادل بار (Load Balancing) و ایجاد زنجیره پروکسی
* پروکسی انتخابی برای برنامه‌های مشخص (Per-App Proxy)
* تنظیمات ظاهر و تم، انتخاب رنگ شاخص و تغییر آیکون برنامه

---

## پروتکل‌های پشتیبانی‌شده

| پروتکل | انتقال و امنیت |
| :--- | :--- |
| VLESS | Reality, XTLS Vision, TLS, gRPC, WebSocket, TCP, HTTP/2, mKCP |
| AmneziaWG | پروتکل UDP با هدرهای سفارشی و محافظت در برابر DPI |
| olcRTC | انتقال بر بستر WebRTC |
| WireGuard | تونل استاندارد UDP |
| Hysteria 2 | UDP / QUIC |
| VMess | TLS, WebSocket, gRPC, TCP, HTTP/2, mKCP |
| Trojan | TLS, gRPC, WebSocket, TCP |
| Shadowsocks | AEAD, SS-2022 (blake3) |
| SOCKS5 / HTTP | TCP / Auth |
| Custom JSON | کانفیگ‌های سفارشی Xray |

---

## مجوز

این پروژه متن‌باز بوده و تحت مجوز [GPL-3.0](LICENSE) منتشر می‌شود.
