<p align="center">
  <img src="design/skipi_logo_long.svg" alt="SKIPI Banner" width="650" />
</p>

<p align="center">
  <strong><a href="README.md">Русский</a> | <a href="README_EN.md">English</a> | <a href="README_ZH.md">简体中文</a> | <a href="README_FA.md">فارسی</a></strong>
</p>

<p align="center">
  کلاینت چندسکویی برای اندروید، ویندوز، لینوکس و macOS
</p>

<p align="center">
  <a href="https://github.com/ZloyRadetski/skipi-box"><img src="https://img.shields.io/badge/Platform-Android%20%7C%20Windows%20%7C%20Linux%20%7C%20macOS-3DDC84.svg?style=flat-square" alt="Platform"></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin"></a>
  <a href="https://www.jetbrains.com/compose-multiplatform/"><img src="https://img.shields.io/badge/Compose-Multiplatform-4285F4.svg?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose Multiplatform"></a>
  <a href="https://t.me/skipi_public"><img src="https://img.shields.io/badge/Telegram-@skipi__public-2CA5E0.svg?style=flat-square&logo=telegram&logoColor=white" alt="Telegram Channel"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL--3.0-blue.svg?style=flat-square" alt="License"></a>
</p>

---

<div dir="rtl">

## درباره پروژه

SKIPI یک کلاینت چندسکویی برای کار با پروکسی و تونل است. یک برنامه واحد با منطق و رابط کاربری مشترک روی اندروید ۷٫۰ و بالاتر و همچنین روی ویندوز، لینوکس و macOS اجرا می‌شود. رابط کاربری با Compose Multiplatform و به سبک Miuix نوشته شده است.

بخش شبکه را Xray-core، هسته اختصاصی پروژه یعنی SKIPI Core، hev-socks5-tunnel و موتور AmneziaWG انجام می‌دهند.

نسخه‌های macOS برای پردازنده‌های Apple Silicon ساخته می‌شوند و فعلاً امضا و تأیید نشده‌اند.

---

## تصاویر

</div>

<p align="center">
  <img src="design/screenshots/main_classic_view.jpg" width="19%" alt="نمای کلاسیک" />
  <img src="design/screenshots/full_app_customization.jpg" width="19%" alt="سفارشی‌سازی تم" />
  <img src="design/screenshots/routing_rules.jpg" width="19%" alt="قوانین مسیریابی" />
  <img src="design/screenshots/about_program.jpg" width="19%" alt="درباره برنامه" />
</p>

---

<div dir="rtl">

## امکانات

این کلاینت از VLESS، VMess، Trojan، Shadowsocks، Hysteria 2، WireGuard و AmneziaWG پشتیبانی می‌کند و SOCKS5، HTTP و پیکربندی‌های دلخواه Xray را در قالب JSON نیز می‌پذیرد. سرورها را می‌توان در زنجیره‌ها و در گروه‌هایی با راهبرد انتخاب، مثلاً توزیع خودکار بار بر اساس تأخیر، ترکیب کرد.

مسیریابی با قوانینی در قالب Shadowrocket (‎.conf)، فهرست‌های دامنه، GeoIP و GeoSite توصیف می‌شود و برای قوانین یک ویرایشگر بصری وجود دارد. اشتراک‌ها در قالب‌های v2rayNG، Clash، Clash Meta (از جمله رمزگذاری با کلید age) و Base64 وارد می‌شوند و اشتراک‌ها و پایگاه‌های Geo طبق زمان‌بندی به‌روزرسانی می‌شوند.

در اندروید می‌توان فقط برنامه‌های انتخاب‌شده را از پروکسی عبور داد. در سیستم‌های رومیزی ترافیک یا از طریق پروکسی سیستم یا از طریق رابط TUN هدایت می‌شود و نمادی در سینی سیستم برای اتصال، قطع اتصال و خروج وجود دارد.

از امکانات دیگر می‌توان به آزمایش تأخیر سرورها، تست سرعت، بررسی نشت DNS، تشخیص نشانی IP خارجی، روشن‌شدن خودکار بر اساس شبکه و پشتیبان‌گیری و بازیابی داده‌های کاربر اشاره کرد. ظاهر برنامه با تم‌ها و رنگ‌های تأکیدی و در اندروید با آیکون برنامه قابل تنظیم است. به‌روزرسانی برنامه را می‌توان از کانال پایدار یا از کانال nightly که نسخه‌های آزمایشی در آن منتشر می‌شوند دریافت کرد.

---

## پروتکل‌ها

VLESS با Reality، XTLS Vision و TLS و روی TCP، WebSocket، gRPC، HTTP/2 و mKCP در دسترس است. VMess روی همان انتقال‌ها کار می‌کند. Trojan از TLS روی TCP، WebSocket و gRPC پشتیبانی می‌کند. Shadowsocks رمزهای AEAD و SS-2022 با blake3 را پشتیبانی می‌کند. Hysteria 2 از UDP و QUIC استفاده می‌کند. WireGuard مانند یک تونل معمولی UDP کار می‌کند و AmneziaWG سرآیندهای قابل تنظیم و بسته‌های زائد را برای مقابله با DPI به آن اضافه می‌کند. SOCKS5 و HTTP به‌عنوان پروکسی‌های معمولی، با احراز هویت یا بدون آن، به کار می‌روند.

---

## کانال و گفتگوی تلگرام

آخرین نسخه‌ها، اخبار توسعه، بحث درباره پیکربندی‌ها و کمک در زمینه مسیریابی در کانال پروژه [@skipi_public](https://t.me/skipi_public) گردآوری شده است.

</div>

<p align="center">
  <a href="https://t.me/skipi_public">
    <img src="https://img.shields.io/badge/Join%20Telegram-@skipi__public-2CA5E0?style=for-the-badge&logo=telegram&logoColor=white" alt="Join Telegram" height="42" />
  </a>
</p>

---

<div dir="rtl">

## مجوز

این پروژه متن‌باز است و تحت مجوز [GPL-3.0](LICENSE) منتشر می‌شود.

</div>
