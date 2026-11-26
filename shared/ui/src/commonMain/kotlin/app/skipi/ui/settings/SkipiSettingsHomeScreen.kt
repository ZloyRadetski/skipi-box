// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import app.skipi.ui.components.SkipiSearchField
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import app.skipi.ui.components.SkipiContentIcon
import app.skipi.ui.resources.settings_search_empty
import app.skipi.ui.resources.settings_search_hint
import app.skipi.ui.settings.search.LocalSettingsSearchController
import app.skipi.ui.settings.search.SettingsSearchHost
import app.skipi.ui.settings.search.SettingsSearchPage
import app.skipi.ui.settings.search.SettingsSearchResult
import app.skipi.ui.settings.search.rememberSettingsSearchResults
import app.skipi.ui.settings.search.settingsSearchEntries
import app.skipi.ui.layout.pageContentPaddingWithCutout
import app.skipi.ui.layout.pageListPadding
import top.yukonga.miuix.kmp.basic.Scaffold
import app.skipi.ui.layout.withTopBarGap
import app.skipi.ui.layout.fadeUnderTopBar
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import app.skipi.ui.navigation.OnNavigationReselect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.settings_network_automation_summary
import app.skipi.ui.resources.settings_network_automation_title
import app.skipi.ui.resources.settings_category_appearance
import app.skipi.ui.resources.settings_category_appearance_summary
import app.skipi.ui.resources.settings_category_backup_reset
import app.skipi.ui.resources.settings_category_backup_reset_summary
import app.skipi.ui.resources.settings_category_dns_leak
import app.skipi.ui.resources.settings_category_dns_leak_summary
import app.skipi.ui.resources.settings_category_general
import app.skipi.ui.resources.settings_category_general_summary
import app.skipi.ui.resources.settings_category_ip_info
import app.skipi.ui.resources.settings_category_ip_info_summary
import app.skipi.ui.resources.settings_category_local_proxy
import app.skipi.ui.resources.settings_category_local_proxy_summary
import app.skipi.ui.resources.settings_category_logs
import app.skipi.ui.resources.settings_category_logs_summary
import app.skipi.ui.resources.settings_category_subscriptions
import app.skipi.ui.resources.settings_category_subscriptions_summary
import app.skipi.ui.resources.settings_category_speed_test
import app.skipi.ui.resources.settings_category_speed_test_summary
import app.skipi.ui.resources.settings_category_vpn
import app.skipi.ui.resources.settings_category_vpn_summary
import app.skipi.ui.resources.settings_about_project
import app.skipi.ui.resources.settings_header_about
import app.skipi.ui.resources.settings_header_appearance
import app.skipi.ui.resources.settings_header_general
import app.skipi.ui.resources.settings_header_logs
import app.skipi.ui.resources.settings_header_network
import app.skipi.ui.resources.settings_header_subscriptions
import app.skipi.ui.resources.settings_header_tools
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

enum class SkipiSettingsDestination {
    Appearance,
    Vpn,
    NetworkAutomation,
    LocalProxy,
    Subscriptions,
    General,
    BackupReset,
    SpeedTest,
    DnsLeakTest,
    IpInfo,
    Logs,
    About,
}

data class SkipiSettingsHomeState(
    val versionLabel: String,
    val tunMtu: String,
    val localProxyPort: String,
    val coreLogLevel: String?,
    /** The icon's own color; null reads black or white on the tile. */
    val categoryIconGlyphColor: Color? = null,
    val visibleDestinations: Set<SkipiSettingsDestination> =
        SkipiSettingsDestination.entries
            .filter { it != SkipiSettingsDestination.NetworkAutomation }
            .toSet(),
    /** What the settings search may offer; it also reaches pages the overview does not list. */
    val searchHost: SettingsSearchHost = SettingsSearchHost(),
)

@Composable
fun SkipiSettingsHomeScreen(
    state: SkipiSettingsHomeState,
    padding: PaddingValues,
    onNavigate: (SkipiSettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
    isWideScreen: Boolean = false,
    onOpenSearchResult: (SettingsSearchPage) -> Unit = {},
) {
    val listState = rememberLazyListState()
    OnNavigationReselect { listState.animateScrollToItem(0) }
    val categoryIconColor = settingIconBackgroundColor(SkipiTheme.colors.accent)
    val categoryIconGlyphColor = settingIconGlyphColor(SkipiTheme.colors.onAccent, state.categoryIconGlyphColor)
    val visible = state.visibleDestinations
    val search = LocalSettingsSearchController.current
    val searching = search.query.isNotBlank()
    val searchEntries = remember(state.searchHost) { settingsSearchEntries(state.searchHost) }
    val results = if (searching) rememberSettingsSearchResults(search.query, searchEntries) else emptyList()
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(search.searchFieldRequested) {
        if (search.searchFieldRequested) {
            listState.requestScrollToItem(0)
            searchFocus.requestFocus()
            search.searchFieldFocused()
        }
    }
    // A new query shows its best results, not the place the previous list was left at.
    var shownQuery by rememberSaveable { mutableStateOf(search.query) }
    LaunchedEffect(search.query) {
        if (shownQuery != search.query) {
            shownQuery = search.query
            listState.requestScrollToItem(0)
        }
    }
    // An overview entry opens its page at the top, whatever an unfinished search asked for.
    val open: (SkipiSettingsDestination) -> Unit = { destination ->
        search.clearFocus()
        onNavigate(destination)
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize(),
        // No title bar: the search field opens the page. On a phone it still starts below the status bar.
        topBar = {
            if (!isWideScreen) Spacer(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars))
        },
    ) { innerPadding ->
        val contentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fadeUnderTopBar(contentPadding.calculateTopPadding())
                .scrollEndHaptic()
                .overScrollVertical()
                .fillMaxHeight(),
            contentPadding = pageListPadding(contentPadding).withTopBarGap(),
        ) {
            item(key = "settings_search") {
                SkipiSearchField(
                    value = search.query,
                    onValueChange = { search.query = it },
                    placeholder = stringResource(Res.string.settings_search_hint),
                    modifier = Modifier
                        .padding(horizontal = SkipiTheme.spacing.medium)
                        .padding(bottom = SkipiTheme.spacing.small)
                        .focusRequester(searchFocus),
                )
            }
            if (searching) settingsSearchResults(
                results = results,
                iconBackgroundColor = categoryIconColor,
                iconColor = categoryIconGlyphColor,
                onOpen = { result ->
                    // The page opens without the keyboard, and the way back does not bring it up again.
                    focusManager.clearFocus()
                    keyboard?.hide()
                    search.open(result.entry, searchEntries)
                    onOpenSearchResult(result.entry.page)
                },
            )
            if (!searching && (SkipiSettingsDestination.Appearance in visible)) item(key = "section_appearance") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_appearance), Modifier.testTag("settings-home-section:appearance"))
                SkipiSettingsCategoryGroupCard {
                    SettingsItem(
                        icon = Icons.Filled.Palette,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_appearance),
                        summary = stringResource(Res.string.settings_category_appearance_summary),
                        onClick = { open(SkipiSettingsDestination.Appearance) },
                    )
                }
            }
            if (!searching && (SkipiSettingsDestination.Vpn in visible || SkipiSettingsDestination.LocalProxy in visible || SkipiSettingsDestination.NetworkAutomation in visible)) item(key = "section_network") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_network), Modifier.testTag("settings-home-section:network"))
                SkipiSettingsCategoryGroupCard {
                    if (SkipiSettingsDestination.Vpn in visible) SettingsItem(
                        icon = Icons.Filled.Security,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_vpn),
                        summary = stringResource(Res.string.settings_category_vpn_summary),
                        value = "MTU: ${state.tunMtu}",
                        onClick = { open(SkipiSettingsDestination.Vpn) },
                        showDivider = SkipiSettingsDestination.LocalProxy in visible || SkipiSettingsDestination.NetworkAutomation in visible,
                    )
                    if (SkipiSettingsDestination.LocalProxy in visible) SettingsItem(
                        icon = Icons.Filled.Dns,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_local_proxy),
                        summary = stringResource(Res.string.settings_category_local_proxy_summary),
                        value = ":${state.localProxyPort}",
                        onClick = { open(SkipiSettingsDestination.LocalProxy) },
                        showDivider = SkipiSettingsDestination.NetworkAutomation in visible,
                    )
                    if (SkipiSettingsDestination.NetworkAutomation in visible) SettingsItem(
                        icon = Icons.Filled.Security,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_network_automation_title),
                        summary = stringResource(Res.string.settings_network_automation_summary),
                        onClick = { open(SkipiSettingsDestination.NetworkAutomation) },
                    )
                }
            }
            if (!searching && (SkipiSettingsDestination.Subscriptions in visible)) item(key = "section_subscriptions") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_subscriptions), Modifier.testTag("settings-home-section:subscriptions"))
                SkipiSettingsCategoryGroupCard {
                    SettingsItem(
                        icon = Icons.Filled.Sync,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_subscriptions),
                        summary = stringResource(Res.string.settings_category_subscriptions_summary),
                        onClick = { open(SkipiSettingsDestination.Subscriptions) },
                    )
                }
            }
            if (!searching && (SkipiSettingsDestination.General in visible || SkipiSettingsDestination.BackupReset in visible)) item(key = "section_general") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_general), Modifier.testTag("settings-home-section:general"))
                SkipiSettingsCategoryGroupCard {
                    if (SkipiSettingsDestination.General in visible) SettingsItem(
                        icon = Icons.Filled.Settings,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_general),
                        summary = stringResource(Res.string.settings_category_general_summary),
                        onClick = { open(SkipiSettingsDestination.General) },
                        showDivider = true,
                    )
                    if (SkipiSettingsDestination.BackupReset in visible) SettingsItem(
                        icon = Icons.Filled.Backup,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_backup_reset),
                        summary = stringResource(Res.string.settings_category_backup_reset_summary),
                        onClick = { open(SkipiSettingsDestination.BackupReset) },
                    )
                }
            }
            if (!searching && (SkipiSettingsDestination.SpeedTest in visible || SkipiSettingsDestination.DnsLeakTest in visible || SkipiSettingsDestination.IpInfo in visible)) item(key = "section_tools") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_tools), Modifier.testTag("settings-home-section:tools"))
                SkipiSettingsCategoryGroupCard {
                    if (SkipiSettingsDestination.SpeedTest in visible) SettingsItem(
                        icon = Icons.Filled.Bolt,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_speed_test),
                        summary = stringResource(Res.string.settings_category_speed_test_summary),
                        onClick = { open(SkipiSettingsDestination.SpeedTest) },
                        showDivider = SkipiSettingsDestination.DnsLeakTest in visible || SkipiSettingsDestination.IpInfo in visible,
                    )
                    if (SkipiSettingsDestination.DnsLeakTest in visible) SettingsItem(
                        icon = Icons.Filled.Security,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_dns_leak),
                        summary = stringResource(Res.string.settings_category_dns_leak_summary),
                        onClick = { open(SkipiSettingsDestination.DnsLeakTest) },
                        showDivider = SkipiSettingsDestination.IpInfo in visible,
                    )
                    if (SkipiSettingsDestination.IpInfo in visible) SettingsItem(
                        icon = Icons.Filled.Public,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_ip_info),
                        summary = stringResource(Res.string.settings_category_ip_info_summary),
                        onClick = { open(SkipiSettingsDestination.IpInfo) },
                    )
                }
            }
            if (!searching && (SkipiSettingsDestination.Logs in visible)) item(key = "section_logs") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_logs), Modifier.testTag("settings-home-section:logs"))
                SkipiSettingsCategoryGroupCard {
                    SettingsItem(
                        icon = Icons.Filled.Description,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_category_logs),
                        summary = stringResource(Res.string.settings_category_logs_summary),
                        value = state.coreLogLevel,
                        onClick = { open(SkipiSettingsDestination.Logs) },
                    )
                }
            }
            if (!searching && (SkipiSettingsDestination.About in visible)) item(key = "section_about") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_about), Modifier.testTag("settings-home-section:about"))
                SkipiSettingsCategoryGroupCard(bottomPadding = 0.dp) {
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        iconBackgroundColor = categoryIconColor,
                        iconColor = categoryIconGlyphColor,
                        title = stringResource(Res.string.settings_about_project),
                        summary = state.versionLabel,
                        onClick = { open(SkipiSettingsDestination.About) },
                    )
                }
            }
        }
    }
}

private fun LazyListScope.settingsSearchResults(
    results: List<SettingsSearchResult>,
    iconBackgroundColor: Color,
    iconColor: Color,
    onOpen: (SettingsSearchResult) -> Unit,
) {
    if (results.isEmpty()) item(key = "settings_search_empty") {
        Text(
            text = stringResource(Res.string.settings_search_empty),
            modifier = Modifier.fillMaxWidth().padding(SkipiTheme.spacing.medium).testTag("settings-search-empty"),
            textAlign = TextAlign.Center,
            style = SkipiTheme.typography.bodyMedium,
            color = SkipiTheme.colors.onSurfaceVariant,
        )
    }
    items(results, key = { "settings_search:" + it.entry.id }) { result ->
        SkipiSettingsCategoryGroupCard(
            modifier = Modifier.testTag("settings-search-result:" + result.entry.id),
            bottomPadding = SkipiTheme.spacing.small,
        ) {
            SkipiSettingsCategoryEntry(
                icon = result.entry.page.icon,
                iconBackgroundColor = iconBackgroundColor,
                iconColor = iconColor,
                title = result.title,
                titleHighlights = result.titleRanges,
                summary = result.path,
                onClick = { onOpen(result) },
                trailingIcon = Icons.Filled.ChevronRight,
            )
        }
    }
}

/** The icon the overview shows for the page a result leads to. */
private val SettingsSearchPage.icon: ImageVector
    get() = when (this) {
        SettingsSearchPage.Appearance -> Icons.Filled.Palette
        SettingsSearchPage.Vpn, SettingsSearchPage.NetworkAutomation, SettingsSearchPage.DnsLeakTest -> Icons.Filled.Security
        SettingsSearchPage.LocalProxy -> Icons.Filled.Dns
        SettingsSearchPage.Subscriptions, SettingsSearchPage.SubscriptionPing -> Icons.Filled.Sync
        SettingsSearchPage.General -> Icons.Filled.Settings
        SettingsSearchPage.BackupReset -> Icons.Filled.Backup
        SettingsSearchPage.SpeedTest -> Icons.Filled.Bolt
        SettingsSearchPage.IpInfo -> Icons.Filled.Public
        SettingsSearchPage.Logs -> Icons.Filled.Description
        SettingsSearchPage.About -> Icons.Filled.Info
    }

@Composable
private fun SettingsSectionTitle(text: String, modifier: Modifier = Modifier) {
    top.yukonga.miuix.kmp.basic.SmallTitle(text = text, modifier = modifier)
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBackgroundColor: Color,
    iconColor: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
    value: String? = null,
    showDivider: Boolean = false,
) {
    SkipiSettingsCategoryEntry(
        icon = icon,
        iconBackgroundColor = iconBackgroundColor,
        iconColor = iconColor,
        title = title,
        summary = summary,
        value = value,
        onClick = onClick,
        trailingIcon = Icons.Filled.ChevronRight,
        showDivider = showDivider,
    )
}
