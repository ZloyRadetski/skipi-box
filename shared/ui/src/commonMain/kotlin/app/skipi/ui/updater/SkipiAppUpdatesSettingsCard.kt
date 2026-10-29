// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.updater

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.settings_auto_check_updates_summary
import app.skipi.ui.resources.settings_auto_check_updates_title
import app.skipi.ui.resources.settings_auto_install_night_summary
import app.skipi.ui.resources.settings_auto_install_night_title
import app.skipi.ui.resources.settings_check_updates_now_action
import app.skipi.ui.settings.SkipiSettingsSectionCard
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * Shared "Updates" card of About. Night installation is an Android-only transfer policy, so a host
 * without an installer passes `null` for [nightInstallEnabled].
 */
@Composable
fun SkipiAppUpdatesSettingsCard(
    autoCheckEnabled: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    nightInstallEnabled: Boolean?,
    onNightInstallChange: (Boolean) -> Unit,
    checkSummary: String?,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SkipiSettingsSectionCard(modifier = modifier, bottomPadding = 12.dp) {
        SwitchPreference(
            title = stringResource(Res.string.settings_auto_check_updates_title),
            summary = stringResource(Res.string.settings_auto_check_updates_summary),
            checked = autoCheckEnabled,
            onCheckedChange = onAutoCheckChange,
        )
        if (autoCheckEnabled && nightInstallEnabled != null) {
            SwitchPreference(
                title = stringResource(Res.string.settings_auto_install_night_title),
                summary = stringResource(Res.string.settings_auto_install_night_summary),
                checked = nightInstallEnabled,
                onCheckedChange = onNightInstallChange,
            )
        }
        ArrowPreference(
            title = stringResource(Res.string.settings_check_updates_now_action),
            summary = checkSummary,
            onClick = onCheckNow,
        )
    }
}
