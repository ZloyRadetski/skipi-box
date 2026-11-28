// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings.search

import app.skipi.ui.resources.*
import org.jetbrains.compose.resources.StringResource

/**
 * Everything [host] can show, in the order of the settings pages.
 *
 * A new row of a settings page is added here and marked on its page with `SettingsSearchAnchor` under the same id;
 * `SettingsSearchSourceTest` fails when either is missing.
 */
fun settingsSearchEntries(host: SettingsSearchHost): List<SettingsSearchEntry> = buildList {
    appearance(host)
    vpn(host)
    networkAutomation(host)
    localProxy(host)
    multilink(host)
    subscriptions(host)
    general(host)
    backupReset(host)
    tools(host)
    logs(host)
    about(host)
}

/** Collects the rows of one page; a host without the page gets none of them. */
private class PageRows(
    private val entries: MutableList<SettingsSearchEntry>,
    private val page: SettingsSearchPage,
    private val section: StringResource,
) {
    fun row(
        id: String,
        title: StringResource,
        summary: StringResource? = null,
        keywords: String = "",
        parent: String? = null,
        shown: Boolean = true,
    ) {
        if (shown) entries += SettingsSearchEntry(
            id = id,
            page = page,
            title = title,
            summary = summary,
            section = section,
            keywords = keywords.words(),
            parent = parent,
        )
    }

    /** A row titled with a product name. */
    fun named(id: String, name: String, keywords: String = "", shown: Boolean = true) {
        if (shown) entries += SettingsSearchEntry(
            id = id,
            page = page,
            title = null,
            section = section,
            keywords = keywords.words(),
            name = name,
        )
    }
}

private fun String.words(): List<String> = split(' ').filter { it.isNotEmpty() }

private fun MutableList<SettingsSearchEntry>.page(
    host: SettingsSearchHost,
    page: SettingsSearchPage,
    title: StringResource,
    summary: StringResource? = null,
    section: StringResource? = null,
    keywords: String,
    rows: PageRows.() -> Unit = {},
) {
    if (page !in host.pages) return
    add(
        SettingsSearchEntry(
            id = "page:${page.name}",
            page = page,
            title = title,
            summary = summary,
            section = section,
            keywords = keywords.words(),
            isPage = true,
        ),
    )
    PageRows(this, page, title).rows()
}

private fun MutableList<SettingsSearchEntry>.appearance(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.Appearance,
    title = Res.string.settings_category_appearance,
    summary = Res.string.settings_category_appearance_summary,
    section = Res.string.settings_header_appearance,
    keywords = "appearance interface design интерфейс дизайн оформление вид",
) {
    val can = host.appearance
    row("settings_color_mode", Res.string.settings_color_mode, Res.string.settings_color_mode_summary,
        keywords = "theme dark light amoled night тема темная тёмная светлая ночная")
    row("settings_font_family", Res.string.settings_font_family, keywords = "font typeface шрифт", shown = can.fonts)
    row("settings_font_size", Res.string.settings_font_size,
        keywords = "font size text scale шрифт размер текст масштаб крупнее мельче", shown = can.fonts)
    row("settings_font_weight", Res.string.settings_font_weight,
        keywords = "font weight bold шрифт насыщенность толщина жирный", parent = "settings_font_family", shown = can.fonts)
    row("settings_app_icon", Res.string.settings_app_icon, keywords = "icon launcher иконка значок ярлык", shown = can.appIcon)

    row("settings_background_style", Res.string.settings_background_style,
        keywords = "background wallpaper aurora фон обои", shown = can.wallpaper)
    row("settings_background_photo_pick", Res.string.settings_background_photo_pick,
        keywords = "photo picture image wallpaper фото картинка изображение обои",
        parent = "settings_background_style", shown = can.wallpaper)
    row("settings_background_dim", Res.string.settings_background_dim,
        keywords = "dim darken затемнение яркость фон", parent = "settings_background_style", shown = can.wallpaper)
    row("settings_background_photo_blur", Res.string.settings_background_photo_blur,
        keywords = "blur размытие размыть фон фото", parent = "settings_background_style", shown = can.wallpaper)
    row("settings_background_photo_remove", Res.string.settings_background_photo_remove,
        keywords = "photo delete фото удалить убрать фон", parent = "settings_background_style", shown = can.wallpaper)
    row("settings_seasonal_events", Res.string.settings_seasonal_events, Res.string.settings_seasonal_events_summary,
        keywords = "new year snow holiday новый год снег гирлянда праздник", shown = can.wallpaper && can.seasonalEvents)

    row("custom_themes_save_current", Res.string.custom_themes_save_current,
        keywords = "theme save share тема сохранить поделиться палитра", shown = can.customColors)
    row("custom_themes_import", Res.string.custom_themes_import,
        keywords = "theme import link code тема импорт ссылка код загрузить", shown = can.customColors)

    row("settings_enable_material_you", Res.string.settings_enable_material_you,
        Res.string.settings_enable_material_you_summary,
        keywords = "material you monet dynamic colors динамические цвета системные", shown = can.customColors && can.dynamicColors)
    row("settings_color_accent", Res.string.settings_color_accent, Res.string.settings_color_accent_summary,
        keywords = "accent color цвет акцент",
        parent = "settings_enable_material_you".takeIf { can.dynamicColors } ?: "settings_enable_custom_colors",
        shown = can.customColors)
    row("settings_enable_custom_colors", Res.string.settings_enable_custom_colors,
        Res.string.settings_enable_custom_colors_summary,
        keywords = "color palette цвет цвета палитра свои", shown = can.customColors)

    fun color(id: String, title: StringResource, summary: StringResource? = null, keywords: String = "") = row(
        id, title, summary, keywords = "color цвет $keywords", parent = "settings_enable_custom_colors",
        shown = can.customColors,
    )
    color("settings_color_background", Res.string.settings_color_background,
        Res.string.settings_color_background_summary, "background фон")
    color("settings_color_surface", Res.string.settings_color_surface,
        Res.string.settings_color_surface_summary, "surface card карточка поверхность")
    color("settings_color_surface_variant", Res.string.settings_color_surface_variant,
        Res.string.settings_color_surface_variant_summary, "surface поверхность")
    color("settings_color_text", Res.string.settings_color_text, Res.string.settings_color_text_summary, "text текст")
    color("settings_color_text_secondary", Res.string.settings_color_text_secondary,
        Res.string.settings_color_text_secondary_summary, "text текст")
    color("settings_color_status_running", Res.string.settings_color_status_running,
        Res.string.settings_color_status_running_summary, "status connected статус подключено")
    color("settings_color_status_stopped", Res.string.settings_color_status_stopped,
        Res.string.settings_color_status_stopped_summary, "status disconnected статус отключено")
    color("settings_color_ping_fast", Res.string.settings_color_ping_fast,
        Res.string.settings_color_ping_fast_summary, "ping latency пинг задержка")
    color("settings_color_ping_medium", Res.string.settings_color_ping_medium,
        Res.string.settings_color_ping_medium_summary, "ping latency пинг задержка")
    color("settings_color_ping_slow", Res.string.settings_color_ping_slow,
        Res.string.settings_color_ping_slow_summary, "ping latency пинг задержка")
    color("settings_color_icons", Res.string.settings_color_icons, Res.string.settings_color_icons_summary, "icon иконка значок")
    color("settings_color_protocol_vless", Res.string.settings_color_protocol_vless, keywords = "vless protocol протокол")
    color("settings_color_protocol_vmess", Res.string.settings_color_protocol_vmess, keywords = "vmess protocol протокол")
    color("settings_color_protocol_hysteria2", Res.string.settings_color_protocol_hysteria2, keywords = "hysteria hy2 protocol протокол")
    color("settings_color_protocol_trojan", Res.string.settings_color_protocol_trojan, keywords = "trojan protocol протокол")
    color("settings_color_protocol_shadowsocks", Res.string.settings_color_protocol_shadowsocks, keywords = "shadowsocks ss protocol протокол")
    color("settings_color_protocol_wireguard", Res.string.settings_color_protocol_wireguard, keywords = "wireguard wg protocol протокол")
    color("settings_color_protocol_socks", Res.string.settings_color_protocol_socks, keywords = "socks protocol протокол")
    color("settings_color_protocol_http", Res.string.settings_color_protocol_http, keywords = "http protocol протокол")
    color("settings_color_protocol_strategy", Res.string.settings_color_protocol_strategy, keywords = "strategy group balancer стратегия группа")
    color("settings_color_protocol_chain", Res.string.settings_color_protocol_chain, keywords = "chain цепочка")
    color("settings_color_protocol_json", Res.string.settings_color_protocol_json, keywords = "json custom")
    color("settings_color_protocol_masque", Res.string.settings_color_protocol_masque, keywords = "masque warp protocol протокол")
    color("settings_colors_reset", Res.string.settings_colors_reset, Res.string.settings_colors_reset_summary, "reset сброс")

    row("settings_navigation_style", Res.string.settings_navigation_style, Res.string.settings_navigation_style_summary,
        keywords = "navigation sidebar bottom bar навигация боковая панель меню", shown = can.navigationStyle)
    row("settings_bottom_bar_size", Res.string.settings_bottom_bar_size, Res.string.settings_bottom_bar_size_summary,
        keywords = "navigation bar нижняя панель размер", parent = "settings_navigation_style".takeIf { can.navigationStyle },
        shown = can.bottomBarSize)
    row("settings_connection_display_mode", Res.string.settings_connection_display_mode,
        Res.string.settings_connection_display_mode_summary,
        keywords = "connect button home widget кнопка подключения главный экран виджет вид")
    row("settings_classic_button_size", Res.string.settings_classic_button_size,
        Res.string.settings_classic_button_size_summary, keywords = "connect button size кнопка размер",
        parent = "settings_connection_display_mode")
    row("settings_classic_show_floating_power_button", Res.string.settings_classic_show_floating_power_button,
        Res.string.settings_classic_show_floating_power_button_summary, keywords = "floating button плавающая кнопка",
        parent = "settings_connection_display_mode", shown = can.floatingPowerButton)
    row("settings_pin_connection_panel", Res.string.settings_pin_connection_panel,
        Res.string.settings_pin_connection_panel_summary, keywords = "pin panel home закрепить панель главный")
    row("settings_enable_subscription_swipe", Res.string.settings_enable_subscription_swipe,
        Res.string.settings_enable_subscription_swipe_summary, keywords = "swipe gesture свайп жест подписки листать",
        shown = can.subscriptionSwipe)
    row("settings_tunnel_memory_show_on_home", Res.string.settings_tunnel_memory_show_on_home,
        Res.string.settings_tunnel_memory_show_on_home_summary, keywords = "memory ram память озу главный")
    row("settings_tunnel_ip_show_on_home", Res.string.settings_tunnel_ip_show_on_home,
        Res.string.settings_tunnel_ip_show_on_home_summary, keywords = "ip address адрес главный")
    row("settings_tunnel_ip_mask_on_home", Res.string.settings_tunnel_ip_mask_on_home,
        Res.string.settings_tunnel_ip_mask_on_home_summary, keywords = "ip hide privacy скрыть приватность",
        parent = "settings_tunnel_ip_show_on_home")
    row("settings_connection_log_show_on_home", Res.string.settings_connection_log_show_on_home,
        Res.string.settings_connection_log_show_on_home_summary, keywords = "connections log соединения журнал лог",
        shown = can.connectionLog)
    row("settings_enable_all_proxy_group", Res.string.settings_enable_all_proxy_group,
        Res.string.settings_enable_all_proxy_group_summary, keywords = "all group servers все группа серверы")
    row("settings_show_server_search", Res.string.settings_show_server_search,
        Res.string.settings_show_server_search_summary, keywords = "search servers поиск серверов")
}

private fun MutableList<SettingsSearchEntry>.vpn(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.Vpn,
    title = Res.string.settings_category_vpn,
    summary = Res.string.settings_category_vpn_summary,
    section = Res.string.settings_header_network,
    keywords = "vpn tunnel network впн туннель сеть",
) {
    val can = host.vpn
    row("settings_tun_mode_title", Res.string.settings_tun_mode_title, Res.string.settings_tun_mode_summary,
        keywords = "tun adapter режим адаптер весь трафик", shown = host.desktopVpnRows)
    row("configs_local_dns", Res.string.configs_local_dns, Res.string.configs_local_dns_summary,
        keywords = "dns resolver локальный днс", parent = "settings_tun_mode_title", shown = host.desktopVpnRows)
    row("settings_tun", Res.string.settings_tun,
        keywords = "tun mtu dns ipv4 ipv6 cidr keepalive адрес интерфейс пакет туннель")
    row("configs_hevtun", Res.string.configs_hevtun, Res.string.configs_hevtun_summary,
        keywords = "hev tun2socks tunnel туннель", shown = can.hevTun)
    row("settings_strict_full_tunnel", Res.string.settings_strict_full_tunnel,
        Res.string.settings_strict_full_tunnel_summary,
        keywords = "leak full tunnel утечка весь трафик защита", shown = can.strictFullTunnel)
    row("settings_auto_connect_on_app_open_title", Res.string.settings_auto_connect_on_app_open_title,
        Res.string.settings_auto_connect_on_app_open_summary,
        keywords = "autoconnect startup автоподключение автозапуск при открытии")
    row("settings_auto_connect_on_boot_title", Res.string.settings_auto_connect_on_boot_title,
        Res.string.settings_auto_connect_on_boot_summary,
        keywords = "autoconnect boot startup autostart автоподключение автозапуск загрузка включение",
        shown = can.autoConnectOnBoot && !host.desktopVpnRows)
    // Desktop shows the boot switch under its own name; the anchor of the row stays the same.
    row("settings_launch_on_login_title", Res.string.settings_launch_on_login_title,
        Res.string.settings_launch_on_login_summary,
        keywords = "autostart startup login boot автозапуск вход загрузка системы",
        shown = can.autoConnectOnBoot && host.desktopVpnRows)
    row("settings_kill_switch", Res.string.settings_kill_switch, Res.string.settings_kill_switch_summary,
        keywords = "killswitch block leak аварийный выключатель блокировать утечка", shown = can.killSwitch)
    row("settings_system_vpn_settings", Res.string.settings_system_vpn_settings,
        Res.string.settings_system_vpn_settings_summary,
        keywords = "always on system android постоянный системные", shown = can.systemVpnSettings)
    row("settings_battery_optimization", Res.string.settings_battery_optimization,
        keywords = "battery power saving background батарея энергосбережение фон аккумулятор",
        shown = can.batteryOptimization)
    row("settings_wake_lock", Res.string.settings_wake_lock, Res.string.settings_wake_lock_summary,
        keywords = "wakelock sleep cpu сон спящий режим не засыпать", shown = can.wakeLock)
    row("settings_seamless_network_switching", Res.string.settings_seamless_network_switching,
        Res.string.settings_seamless_network_switching_summary,
        keywords = "wifi mobile lte switch handover вайфай мобильная сеть переключение", shown = can.seamlessSwitching)
}

private fun MutableList<SettingsSearchEntry>.networkAutomation(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.NetworkAutomation,
    title = Res.string.settings_network_automation_title,
    summary = Res.string.settings_network_automation_summary,
    section = Res.string.settings_header_network,
    keywords = "automation wifi ssid rules auto connect автоматизация вайфай правила сети автоподключение",
)

private fun MutableList<SettingsSearchEntry>.localProxy(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.LocalProxy,
    title = Res.string.settings_category_local_proxy,
    summary = Res.string.settings_category_local_proxy_summary,
    section = Res.string.settings_header_network,
    keywords = "proxy socks http port прокси порт",
) {
    val can = host.localProxy
    row("settings_local_proxy_dynamic_port", Res.string.settings_local_proxy_dynamic_port,
        Res.string.settings_local_proxy_dynamic_port_summary, keywords = "random port случайный порт",
        shown = can.dynamicPort)
    row("settings_local_proxy_listen_all_interfaces", Res.string.settings_local_proxy_listen_all_interfaces,
        Res.string.settings_local_proxy_listen_all_interfaces_summary,
        keywords = "lan share hotspot раздача локальная сеть точка доступа", shown = can.listenAllInterfaces)
    row("settings_local_proxy_port", Res.string.settings_local_proxy_port, keywords = "socks socks5 port порт прокси",
        parent = "settings_local_proxy_dynamic_port".takeIf { can.dynamicPort })
    row("settings_local_proxy_socks_auth_mode", Res.string.settings_local_proxy_socks_auth_mode,
        keywords = "socks auth password username пароль логин авторизация", shown = can.authentication)
    row("settings_local_proxy_http_inbound", Res.string.settings_local_proxy_http_inbound,
        Res.string.settings_local_proxy_http_inbound_summary, keywords = "http proxy прокси", shown = can.httpInbound)
    row("settings_local_proxy_http_port", Res.string.settings_local_proxy_http_port, keywords = "http port порт",
        parent = "settings_local_proxy_http_inbound", shown = can.httpInbound)
    row("settings_local_proxy_http_auth_mode", Res.string.settings_local_proxy_http_auth_mode,
        keywords = "http auth password username пароль логин авторизация",
        parent = "settings_local_proxy_http_inbound", shown = can.httpInbound && can.authentication)
    row("desktop_settings_use_system_proxy", Res.string.desktop_settings_use_system_proxy,
        Res.string.desktop_settings_use_system_proxy_summary, keywords = "system proxy системный прокси",
        parent = "settings_local_proxy_http_inbound", shown = host.systemProxy)
    row("desktop_settings_reset_system_proxy", Res.string.desktop_settings_reset_system_proxy,
        Res.string.desktop_settings_reset_system_proxy_summary,
        keywords = "system proxy reset clear системный прокси сбросить очистить", shown = host.systemProxy)
}

private fun MutableList<SettingsSearchEntry>.subscriptions(host: SettingsSearchHost) {
    val can = host.subscriptions
    page(
        host,
        SettingsSearchPage.Subscriptions,
        title = Res.string.settings_category_subscriptions,
        summary = Res.string.settings_category_subscriptions_summary,
        section = Res.string.settings_header_subscriptions,
        keywords = "subscription update provider подписка обновление провайдер",
    ) {
        row("settings_subscription_fetch_timeout", Res.string.settings_subscription_fetch_timeout,
            Res.string.settings_subscription_fetch_timeout_summary, keywords = "timeout таймаут ожидание",
            shown = can.fetchTimeout)
        row("settings_user_agents", Res.string.settings_user_agents, keywords = "user agent useragent юзер агент заголовок",
            shown = can.userAgents)
        row("settings_subscription_device_headers", Res.string.settings_subscription_device_headers,
            Res.string.settings_subscription_device_headers_summary,
            keywords = "hwid device headers устройство заголовки идентификатор", shown = can.deviceHeaders)
        row("subscription_expiry_notifications_enable", Res.string.subscription_expiry_notifications_enable,
            Res.string.subscription_expiry_notifications_enable_summary,
            keywords = "expire notification окончание срок уведомление истекает", shown = can.expiryNotifications)
        row("subscription_expiry_reminders_title", Res.string.subscription_expiry_reminders_title,
            Res.string.subscription_expiry_reminders_summary, keywords = "reminder напоминание срок",
            parent = "subscription_expiry_notifications_enable", shown = can.expiryNotifications)
    }
    if (!can.pingSettings) return
    page(
        host,
        SettingsSearchPage.SubscriptionPing,
        title = Res.string.subscription_ping_settings,
        section = Res.string.settings_header_subscriptions,
        keywords = "ping latency test пинг задержка проверка серверов",
    ) {
        row("subscription_ping_mode_section", Res.string.subscription_ping_mode_section,
            keywords = "ping http tcp mode пинг режим")
        row("subscription_ping_concurrency_label", Res.string.subscription_ping_concurrency_label,
            keywords = "ping concurrency parallel threads пинг потоки одновременно")
        row("subscription_ping_timeout_label", Res.string.subscription_ping_timeout_label,
            keywords = "ping timeout пинг таймаут ожидание")
        row("subscription_ping_url", Res.string.subscription_ping_url, keywords = "ping url address пинг адрес ссылка")
    }
}

private fun MutableList<SettingsSearchEntry>.general(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.General,
    title = Res.string.settings_category_general,
    summary = Res.string.settings_category_general_summary,
    section = Res.string.settings_header_general,
    keywords = "general основные общие",
) {
    val can = host.general
    row("settings_language", Res.string.settings_language,
        keywords = "language locale english russian язык локализация английский русский", shown = can.language)
    row("settings_enable_haptics", Res.string.settings_enable_haptics, Res.string.settings_enable_haptics_summary,
        keywords = "haptic vibration вибрация отклик", shown = can.haptics)
    row("settings_traffic_stats_notification", Res.string.settings_traffic_stats_notification,
        Res.string.settings_traffic_stats_notification_summary,
        keywords = "notification traffic speed уведомление трафик скорость шторка", shown = can.trafficStatsNotifications)
    row("settings_traffic_stats_notification_refresh_interval",
        Res.string.settings_traffic_stats_notification_refresh_interval,
        keywords = "refresh interval traffic обновление интервал скорость",
        parent = "settings_traffic_stats_notification", shown = can.trafficStatsNotifications)
    row("settings_resource_files_notifications", Res.string.settings_resource_files_notifications,
        Res.string.settings_resource_files_notifications_summary,
        keywords = "notification geoip geosite уведомление ресурсы файлы", shown = can.resourceFileNotifications)
    row("settings_deletion_confirmation", Res.string.settings_deletion_confirmation,
        Res.string.settings_deletion_confirmation_summary,
        keywords = "delete confirm удаление подтверждение", shown = can.deletionConfirmation)
    row("settings_url_schemes", Res.string.settings_url_schemes, Res.string.settings_url_schemes_summary,
        keywords = "url scheme deeplink link skipi ссылки команды автоматизация", shown = can.urlSchemes)
    row("settings_broadcast_control", Res.string.settings_broadcast_control,
        Res.string.settings_broadcast_control_summary,
        keywords = "broadcast intent tasker интент автоматизация управление", shown = can.broadcastControl)
    row("settings_external_control", Res.string.settings_external_control,
        Res.string.settings_external_control_summary,
        keywords = "external remote link внешнее управление ссылки", shown = can.externalControl)
    row("settings_close_to_background", Res.string.settings_close_to_background,
        Res.string.settings_close_to_background_summary,
        keywords = "close tray minimize закрытие трей свернуть фон", shown = can.closeToBackground)
    row("settings_graphics_acceleration", Res.string.settings_graphics_acceleration,
        Res.string.settings_graphics_acceleration_summary,
        keywords = "gpu graphics render видеокарта графика ускорение", shown = can.graphicsAcceleration)
}

private fun MutableList<SettingsSearchEntry>.backupReset(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.BackupReset,
    title = Res.string.settings_category_backup_reset,
    summary = Res.string.settings_category_backup_reset_summary,
    section = Res.string.settings_header_general,
    keywords = "backup restore reset бэкап резервная копия восстановление сброс",
) {
    row("settings_backup_user_data", Res.string.settings_backup_user_data,
        Res.string.settings_backup_user_data_summary, keywords = "backup export бэкап экспорт сохранить")
    row("settings_restore_user_data", Res.string.settings_restore_user_data,
        Res.string.settings_restore_user_data_summary, keywords = "restore import восстановить импорт загрузить")
    row("settings_reset_tunnel", Res.string.settings_reset_tunnel, Res.string.settings_reset_tunnel_summary,
        keywords = "reset tunnel сброс туннель")
    row("settings_reset_vpn", Res.string.settings_reset_vpn, Res.string.settings_reset_vpn_summary,
        keywords = "reset vpn сброс впн настроек", shown = host.resetVpn)
    row("settings_reset_app", Res.string.settings_reset_app, Res.string.settings_reset_app_summary,
        keywords = "reset factory clear wipe сброс очистить удалить все данные заводские")
}

private fun MutableList<SettingsSearchEntry>.tools(host: SettingsSearchHost) {
    page(
        host,
        SettingsSearchPage.SpeedTest,
        title = Res.string.settings_category_speed_test,
        summary = Res.string.settings_category_speed_test_summary,
        section = Res.string.settings_header_tools,
        keywords = "speed test download upload jitter скорость тест интернет",
    )
    page(
        host,
        SettingsSearchPage.DnsLeakTest,
        title = Res.string.settings_category_dns_leak,
        summary = Res.string.settings_category_dns_leak_summary,
        section = Res.string.settings_header_tools,
        keywords = "dns leak test утечка днс проверка",
    )
    page(
        host,
        SettingsSearchPage.IpInfo,
        title = Res.string.settings_category_ip_info,
        summary = Res.string.settings_category_ip_info_summary,
        section = Res.string.settings_header_tools,
        keywords = "ip address location country провайдер адрес страна местоположение",
    )
}

private fun MutableList<SettingsSearchEntry>.logs(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.Logs,
    title = Res.string.settings_category_logs,
    summary = Res.string.settings_category_logs_summary,
    section = Res.string.settings_header_logs,
    keywords = "logs journal debug diagnostics логи журнал отладка диагностика",
) {
    val can = host.logs
    row("settings_log_level", Res.string.settings_log_level,
        keywords = "log level debug verbose уровень логов отладка", shown = can.logLevel)
    row("settings_log_retention", Res.string.settings_log_retention, Res.string.settings_log_retention_summary,
        keywords = "log retention history хранение логов срок", shown = can.retention)
    row("settings_record_access_log", Res.string.settings_record_access_log,
        keywords = "access log запись журнал доступа", shown = can.accessLog)
    row("settings_core_logs", Res.string.settings_core_logs, keywords = "core xray log ядро логи", shown = can.coreLogs)
    row("settings_access_logs", Res.string.settings_access_logs, keywords = "access log connections доступ соединения",
        parent = "settings_record_access_log".takeIf { can.accessLog }, shown = can.accessLogs)
    row("settings_logcat", Res.string.settings_logcat, keywords = "logcat android системный лог", shown = can.logcat)

    val diagnostics = host.desktopDiagnostics
    row("desktop_settings_recheck_runtime", Res.string.desktop_settings_recheck_runtime,
        Res.string.desktop_settings_recheck_runtime_summary, keywords = "core runtime ядро проверить", shown = diagnostics)
    row("desktop_settings_open_logs_folder", Res.string.desktop_settings_open_logs_folder,
        keywords = "log folder directory папка логов открыть", shown = diagnostics)
    row("desktop_settings_show_recent_log", Res.string.desktop_settings_show_recent_log,
        keywords = "recent log последние записи", shown = diagnostics)
    row("desktop_settings_data_dir", Res.string.desktop_settings_data_dir,
        keywords = "data folder directory данные папка", shown = diagnostics)
    row("desktop_settings_copy_settings_path", Res.string.desktop_settings_copy_settings_path,
        keywords = "settings path file путь файл настроек", shown = diagnostics)
}

private fun MutableList<SettingsSearchEntry>.about(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.About,
    title = Res.string.settings_about_project,
    section = Res.string.settings_header_about,
    keywords = "about version о программе приложении версия",
) {
    val can = host.about
    row("settings_auto_check_updates_title", Res.string.settings_auto_check_updates_title,
        Res.string.settings_auto_check_updates_summary, keywords = "update обновление автообновление", shown = can.updates)
    row("settings_auto_install_night_title", Res.string.settings_auto_install_night_title,
        Res.string.settings_auto_install_night_summary, keywords = "update night обновление ночь установка",
        parent = "settings_auto_check_updates_title", shown = can.updates && host.nightUpdateInstall)
    row("settings_update_channel_title", Res.string.settings_update_channel_title,
        keywords = "update channel stable nightly beta обновление канал", shown = can.updates)
    row("settings_check_updates_now_action", Res.string.settings_check_updates_now_action,
        keywords = "update check обновление проверить", shown = can.updates)
    named("about_skipi_core", "SKIPI Core", keywords = "core version ядро версия", shown = can.runtime)
    named("about_xray_core", "Xray-core", keywords = "xray core version ядро версия", shown = can.runtime)
    row("about_replay_onboarding", Res.string.about_replay_onboarding, Res.string.about_replay_onboarding_summary,
        keywords = "onboarding tutorial интро обучение приветствие", shown = can.replayOnboarding)
    row("about_telegram_channel", Res.string.about_telegram_channel,
        keywords = "telegram channel support телеграм канал поддержка", shown = can.telegram)
    row("about_bug_report", Res.string.about_bug_report, Res.string.about_bug_report_summary,
        keywords = "bug report feedback issue ошибка баг отзыв сообщить", shown = can.bugReport)
    row("about_view_skipi_source", Res.string.about_view_skipi_source,
        keywords = "source code github исходный код", shown = can.source)
}

private fun MutableList<SettingsSearchEntry>.multilink(host: SettingsSearchHost) = page(
    host,
    SettingsSearchPage.Multilink,
    title = Res.string.settings_category_multilink,
    summary = Res.string.settings_category_multilink_summary,
    section = Res.string.settings_header_network,
    keywords = "multilink net-merge merge links bonding wifi ethernet phone tether speed объединение каналов два интернета " +
        "вайфай модем телефон скорость",
) {
    row("multilink_enabled", Res.string.multilink_enabled, Res.string.multilink_enabled_summary,
        keywords = "merge links объединять каналы")
    row("multilink_phone_cooling", Res.string.multilink_phone_cooling, keywords = "phone hot cool телефон греется охлаждение")
    row("multilink_cool_rate", Res.string.multilink_cool_rate, keywords = "phone speed телефон скорость",
        parent = "multilink_phone_cooling")
    row("multilink_probe_targets", Res.string.multilink_probe_targets, Res.string.multilink_probe_targets_help,
        keywords = "probe check internet blocked проверка интернета адреса блокировка")
}
