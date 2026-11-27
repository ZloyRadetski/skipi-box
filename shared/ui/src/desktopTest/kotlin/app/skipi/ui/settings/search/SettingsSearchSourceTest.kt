// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings.search

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The catalog is written beside the pages, so these tests keep the two from drifting apart. */
class SettingsSearchSourceTest {
    private val shared = File("src/commonMain/kotlin/app/skipi/ui")
    private val desktop = File("../../desktopApp/src/main/kotlin/app/skipi/desktop")

    private fun sources(directory: File): List<File> {
        assertTrue(directory.isDirectory, directory.absolutePath)
        return directory.walkTopDown()
            .filter { it.extension == "kt" && "search" !in it.parentFile.name }
            .toList()
    }

    private val entries = settingsSearchEntries(SettingsSearchHost()) +
        settingsSearchEntries(SettingsSearchHost(pages = SettingsSearchPage.entries.toSet(), desktopVpnRows = true, systemProxy = true, desktopDiagnostics = true,
            general = app.skipi.ui.settings.GeneralSettingsCapabilities.Desktop,
            appearance = app.skipi.ui.settings.AppearanceSettingsCapabilities(navigationStyle = true, connectionLog = true)))

    @Test
    fun everyRowOfTheCatalogIsMarkedOnAPage() {
        val code = (sources(shared) + sources(desktop)).joinToString("\n") { it.readText() }
        val marked = // The id may be chosen by a condition, which nests one pair of parentheses.
        Regex("""SettingsSearchAnchor\(((?:[^()]|\([^()]*\))*)\)""").findAll(code)
            .flatMap { call -> Regex("\"([a-z0-9_]+)\"").findAll(call.groupValues[1]).map { it.groupValues[1] } }
            .toSet()
        val rows = entries.filterNot { it.isPage }.map { it.id }.toSet()
        assertEquals(emptySet(), rows - marked, "Rows no page marks")
        assertEquals(emptySet(), marked - rows, "Anchors the catalog does not know")
    }

    /** Titles of rows the search reaches another way, or not at all. */
    private val notSearched = setOf(
        // Found as the page they open.
        "settings_category_backup_reset", "settings_network_automation_title", "subscription_ping_settings",
        // The overview's entries are the pages themselves.
        "settings_category_appearance", "settings_category_vpn", "settings_category_local_proxy",
        "settings_category_subscriptions", "settings_category_general", "settings_category_speed_test",
        "settings_category_dns_leak", "settings_category_ip_info", "settings_category_logs", "settings_category_multilink",
        "settings_about_project", "settings_title",
        // Titles of pages and of the groups on them.
        "about_title", "about_other", "about_runtime", "settings_updates_title", "settings_local_proxy",
        "settings_local_proxy_network_params", "settings_proxy_vpn_service", "settings_auto_connect_title",
        "settings_stability_and_background", "settings_logs", "settings_feedback", "core_logs_title", "access_logs_title",
        // The button that rereads the log shown under its row.
        "common_refresh",
    )

    @Test
    fun everyRowOfASettingsPageIsInTheCatalog() {
        val pages = sources(File(shared, "settings")) + File(shared, "updater/SkipiAppUpdatesSettingsCard.kt") +
            File(desktop, "settings/DesktopSettingsScreen.kt") + File(desktop, "tunnel/DesktopVpnSettings.kt")
        // Sheets and dialogs are opened from a row, which is what the search finds.
        val rowFiles = pages.filterNot { file ->
            file.name.endsWith("Sheet.kt") || file.name.endsWith("Dialog.kt") || file.name == "SkipiUrlSchemesScreen.kt" ||
                file.name == "SubscriptionUserAgentsScreen.kt"
        }
        // The title of a row, written in place or handed over in a page's labels.
        val rowTitle = Regex("""\b(?:title|\w+Title|portLabel|httpPortLabel)\s*=\s*(?:\w+\.)*stringResource\(\s*(?:\w+\.)*Res\.string\.(\w+)""")
        val titles = rowFiles.flatMap { file -> rowTitle.findAll(file.readText()).map { it.groupValues[1] } }.toSet()
        assertTrue(titles.size > 50, "The pattern no longer finds the rows: $titles")
        val known = entries.map { it.id }.toSet() + notSearched
        // Section headers, dialogs and state lines carry titles too.
        val headers = Regex(
            """.*(_section|_section_title|_header|_dialog_title|_disable_title|_confirm_title|_picker_title)$|""" +
                """settings_header_.*|settings_custom_colors_.*|desktop_settings_tunnel_.*|desktop_settings_core_(not_)?found|""" +
                """desktop_settings_hide_log""",
        )
        val unknown = titles.filterNot { it in known || headers.matches(it) }.sorted()
        assertEquals(emptyList(), unknown, "Add these rows to the catalog, or say why they are not searched")
    }
}
