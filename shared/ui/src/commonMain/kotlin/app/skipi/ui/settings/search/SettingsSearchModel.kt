// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings.search

import app.skipi.ui.settings.AboutSettingsCapabilities
import app.skipi.ui.settings.AppearanceSettingsCapabilities
import app.skipi.ui.settings.GeneralSettingsCapabilities
import app.skipi.ui.settings.LocalProxySettingsCapabilities
import app.skipi.ui.settings.LogsSettingsCapabilities
import app.skipi.ui.settings.SubscriptionSettingsCapabilities
import app.skipi.ui.settings.VpnSettingsCapabilities
import org.jetbrains.compose.resources.StringResource

/** A page a search result opens. */
enum class SettingsSearchPage {
    Appearance,
    Vpn,
    NetworkAutomation,
    LocalProxy,
    Subscriptions,
    SubscriptionPing,
    General,
    BackupReset,
    SpeedTest,
    DnsLeakTest,
    IpInfo,
    Logs,
    About,
}

/** What a host can show, described with the capabilities its pages already receive. The defaults are Android's. */
data class SettingsSearchHost(
    val pages: Set<SettingsSearchPage> = SettingsSearchPage.entries.toSet(),
    val appearance: AppearanceSettingsCapabilities = AppearanceSettingsCapabilities(),
    val vpn: VpnSettingsCapabilities = VpnSettingsCapabilities(),
    val localProxy: LocalProxySettingsCapabilities = LocalProxySettingsCapabilities(),
    val subscriptions: SubscriptionSettingsCapabilities = SubscriptionSettingsCapabilities(),
    val general: GeneralSettingsCapabilities = GeneralSettingsCapabilities(),
    val logs: LogsSettingsCapabilities = LogsSettingsCapabilities(),
    val about: AboutSettingsCapabilities = AboutSettingsCapabilities(),
    val resetVpn: Boolean = true,
    /** Updates can be installed at night. */
    val nightUpdateInstall: Boolean = true,
    /** The desktop rows of the VPN page: TUN mode, local DNS, and the launch-on-login wording. */
    val desktopVpnRows: Boolean = false,
    val systemProxy: Boolean = false,
    /** The runtime, log and data rows desktop adds to the logs page. */
    val desktopDiagnostics: Boolean = false,
)

/**
 * One thing the settings search can find: a page, or a row of a page.
 *
 * A row's [id] is the anchor its page marks it with; [parent] is the anchor of the setting that has to be on for
 * the row to be shown, and is highlighted instead of it.
 */
data class SettingsSearchEntry(
    val id: String,
    val page: SettingsSearchPage,
    val title: StringResource?,
    val summary: StringResource? = null,
    val section: StringResource? = null,
    val keywords: List<String> = emptyList(),
    val parent: String? = null,
    val isPage: Boolean = false,
    /** A product name, which is the same in every language; set when [title] is null. */
    val name: String = "",
)
