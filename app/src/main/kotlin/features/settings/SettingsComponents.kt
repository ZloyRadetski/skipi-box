// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.ui.settings.SkipiSettingsCategoryEntry
import app.skipi.ui.settings.SkipiSettingsCategoryGroupCard
import app.skipi.ui.settings.SkipiSettingsSectionCard
import app.R
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.text.formatTemplate

internal val SettingsLogLevelOptions = listOf("debug", "info", "warning", "error", "none")

internal val SettingsLogRetentionOptionValues = listOf(
    1 to R.string.settings_log_retention_1_day,
    3 to R.string.settings_log_retention_3_days,
    7 to R.string.settings_log_retention_7_days,
    14 to R.string.settings_log_retention_14_days,
    30 to R.string.settings_log_retention_30_days,
    0 to R.string.settings_log_retention_unlimited,
)

@Composable
internal fun SettingsSectionCard(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    SkipiSettingsSectionCard(
        modifier = modifier,
        bottomPadding = bottomPadding,
        content = content,
    )
}

@Composable
internal fun SettingsCategoryGroupCard(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    SkipiSettingsCategoryGroupCard(
        modifier = modifier,
        bottomPadding = bottomPadding,
        content = content,
    )
}

@Composable
internal fun SettingsCategoryEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color = MiuixTheme.colorScheme.onPrimary,
    iconBackgroundColor: Color = MiuixTheme.colorScheme.primary,
    title: String,
    summary: String? = null,
    value: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = false,
) {
    SkipiSettingsCategoryEntry(
        icon = icon,
        title = title,
        summary = summary,
        value = value,
        onClick = onClick,
        modifier = modifier,
        iconColor = iconColor,
        iconBackgroundColor = iconBackgroundColor,
        trailingIcon = SettingsIcons.ChevronRight,
        showDivider = showDivider,
    )
}


@Composable
internal fun localProxySettingsSummary(
    port: String,
    listenAllInterfaces: Boolean,
): String {
    val summary = if (listenAllInterfaces) {
        stringResource(R.string.settings_local_proxy_summary_all_interfaces)
    } else {
        stringResource(R.string.settings_local_proxy_summary_fixed)
    }
    return summary.formatTemplate("port" to port)
}

@Composable
internal fun subscriptionPingSettingsSummary(
    url: String,
    timeoutMillis: String,
): String {
    return stringResource(R.string.subscription_ping_settings_summary).formatTemplate(
        "url" to url,
        "timeout" to timeoutMillis,
    )
}
