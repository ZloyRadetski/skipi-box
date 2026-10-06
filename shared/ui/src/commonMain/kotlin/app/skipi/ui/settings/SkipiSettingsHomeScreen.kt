// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
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
import app.skipi.ui.resources.settings_category_integration
import app.skipi.ui.resources.settings_category_integration_summary
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
import app.skipi.ui.resources.settings_title
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

enum class SkipiSettingsDestination {
    Appearance,
    Vpn,
    LocalProxy,
    Subscriptions,
    General,
    BackupReset,
    SpeedTest,
    DnsLeakTest,
    IpInfo,
    Logs,
    Integration,
    About,
}

data class SkipiSettingsHomeState(
    val versionLabel: String,
    val tunMtu: String,
    val localProxyPort: String,
    val coreLogLevel: String?,
    val categoryIconColor: Color,
    val visibleDestinations: Set<SkipiSettingsDestination> =
        SkipiSettingsDestination.entries.filter { it != SkipiSettingsDestination.Integration }.toSet(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkipiSettingsHomeScreen(
    state: SkipiSettingsHomeState,
    padding: PaddingValues,
    onNavigate: (SkipiSettingsDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    val listState = rememberLazyListState()
    val categoryIconColor = state.categoryIconColor
    val visible = state.visibleDestinations
    val topBarScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = SkipiTheme.colors.onSurface,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize().nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    androidx.compose.foundation.layout.Column {
                        Text(stringResource(Res.string.settings_title))
                        Text(
                            text = state.versionLabel,
                            style = SkipiTheme.typography.bodySmall,
                            color = SkipiTheme.colors.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                scrollBehavior = topBarScrollBehavior,
            )
        },
    ) { innerPadding ->
        val contentPadding = PaddingValues(
            start = maxOf(padding.calculateStartPadding(layoutDirection), innerPadding.calculateStartPadding(layoutDirection)) + 8.dp,
            // The top app bar already applies the status-bar inset and Scaffold
            // includes its measured height here; adding host padding duplicates it.
            top = innerPadding.calculateTopPadding(),
            end = maxOf(padding.calculateEndPadding(layoutDirection), innerPadding.calculateEndPadding(layoutDirection)) + 8.dp,
            bottom = padding.calculateBottomPadding() + innerPadding.calculateBottomPadding() + 12.dp,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            if (SkipiSettingsDestination.Appearance in visible) item(key = "section_appearance") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_appearance))
                SkipiSettingsCategoryGroupCard {
                    SettingsItem(
                        icon = Icons.Filled.Palette,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_appearance),
                        summary = stringResource(Res.string.settings_category_appearance_summary),
                        onClick = { onNavigate(SkipiSettingsDestination.Appearance) },
                    )
                }
            }
            if (SkipiSettingsDestination.Vpn in visible || SkipiSettingsDestination.LocalProxy in visible) item(key = "section_network") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_network))
                SkipiSettingsCategoryGroupCard {
                    if (SkipiSettingsDestination.Vpn in visible) SettingsItem(
                        icon = Icons.Filled.Security,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_vpn),
                        summary = stringResource(Res.string.settings_category_vpn_summary),
                        value = "MTU: ${state.tunMtu}",
                        onClick = { onNavigate(SkipiSettingsDestination.Vpn) },
                        showDivider = SkipiSettingsDestination.LocalProxy in visible,
                    )
                    if (SkipiSettingsDestination.LocalProxy in visible) SettingsItem(
                        icon = Icons.Filled.Dns,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_local_proxy),
                        summary = stringResource(Res.string.settings_category_local_proxy_summary),
                        value = ":${state.localProxyPort}",
                        onClick = { onNavigate(SkipiSettingsDestination.LocalProxy) },
                    )
                }
            }
            if (SkipiSettingsDestination.Subscriptions in visible) item(key = "section_subscriptions") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_subscriptions))
                SkipiSettingsCategoryGroupCard {
                    SettingsItem(
                        icon = Icons.Filled.Sync,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_subscriptions),
                        summary = stringResource(Res.string.settings_category_subscriptions_summary),
                        onClick = { onNavigate(SkipiSettingsDestination.Subscriptions) },
                    )
                }
            }
            if (SkipiSettingsDestination.General in visible || SkipiSettingsDestination.BackupReset in visible) item(key = "section_general") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_general))
                SkipiSettingsCategoryGroupCard {
                    if (SkipiSettingsDestination.General in visible) SettingsItem(
                        icon = Icons.Filled.Settings,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_general),
                        summary = stringResource(Res.string.settings_category_general_summary),
                        onClick = { onNavigate(SkipiSettingsDestination.General) },
                        showDivider = true,
                    )
                    if (SkipiSettingsDestination.BackupReset in visible) SettingsItem(
                        icon = Icons.Filled.Backup,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_backup_reset),
                        summary = stringResource(Res.string.settings_category_backup_reset_summary),
                        onClick = { onNavigate(SkipiSettingsDestination.BackupReset) },
                    )
                }
            }
            if (SkipiSettingsDestination.SpeedTest in visible || SkipiSettingsDestination.DnsLeakTest in visible || SkipiSettingsDestination.IpInfo in visible) item(key = "section_tools") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_tools))
                SkipiSettingsCategoryGroupCard {
                    if (SkipiSettingsDestination.SpeedTest in visible) SettingsItem(
                        icon = Icons.Filled.Bolt,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_speed_test),
                        summary = stringResource(Res.string.settings_category_speed_test_summary),
                        onClick = { onNavigate(SkipiSettingsDestination.SpeedTest) },
                        showDivider = SkipiSettingsDestination.DnsLeakTest in visible || SkipiSettingsDestination.IpInfo in visible,
                    )
                    if (SkipiSettingsDestination.DnsLeakTest in visible) SettingsItem(
                        icon = Icons.Filled.Security,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_dns_leak),
                        summary = stringResource(Res.string.settings_category_dns_leak_summary),
                        onClick = { onNavigate(SkipiSettingsDestination.DnsLeakTest) },
                        showDivider = SkipiSettingsDestination.IpInfo in visible,
                    )
                    if (SkipiSettingsDestination.IpInfo in visible) SettingsItem(
                        icon = Icons.Filled.Public,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_ip_info),
                        summary = stringResource(Res.string.settings_category_ip_info_summary),
                        onClick = { onNavigate(SkipiSettingsDestination.IpInfo) },
                    )
                }
            }
            if (SkipiSettingsDestination.Logs in visible) item(key = "section_logs") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_logs))
                SkipiSettingsCategoryGroupCard {
                    SettingsItem(
                        icon = Icons.Filled.Description,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_logs),
                        summary = stringResource(Res.string.settings_category_logs_summary),
                        value = state.coreLogLevel,
                        onClick = { onNavigate(SkipiSettingsDestination.Logs) },
                    )
                }
            }
            if (SkipiSettingsDestination.Integration in visible) item(key = "section_integration") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_general))
                SkipiSettingsCategoryGroupCard {
                    SettingsItem(
                        icon = Icons.Filled.Link,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_category_integration),
                        summary = stringResource(Res.string.settings_category_integration_summary),
                        onClick = { onNavigate(SkipiSettingsDestination.Integration) },
                    )
                }
            }
            if (SkipiSettingsDestination.About in visible) item(key = "section_about") {
                SettingsSectionTitle(stringResource(Res.string.settings_header_about))
                SkipiSettingsCategoryGroupCard(bottomPadding = 0.dp) {
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        iconBackgroundColor = categoryIconColor,
                        title = stringResource(Res.string.settings_about_project),
                        summary = state.versionLabel,
                        onClick = { onNavigate(SkipiSettingsDestination.About) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    top.yukonga.miuix.kmp.basic.SmallTitle(text = text)
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBackgroundColor: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
    value: String? = null,
    showDivider: Boolean = false,
) {
    SkipiSettingsCategoryEntry(
        icon = icon,
        iconBackgroundColor = iconBackgroundColor,
        title = title,
        summary = summary,
        value = value,
        onClick = onClick,
        trailingIcon = Icons.Filled.ChevronRight,
        showDivider = showDivider,
    )
}
