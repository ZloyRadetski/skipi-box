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
import app.skipi.ui.text.formatTemplate

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
internal fun subscriptionPingSettingsSummary(
    url: String,
    timeoutMillis: String,
): String {
    return stringResource(R.string.subscription_ping_settings_summary).formatTemplate(
        "url" to url,
        "timeout" to timeoutMillis,
    )
}
