// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import app.skipi.ui.settings.SettingsRestorePreviewState
import app.skipi.ui.settings.SettingsRestoreWarning
import app.skipi.ui.settings.SkipiSettingsRestoreConfirmDialog
import data.backup.AppBackupRestorePreview
import data.backup.AppBackupWarning
import java.text.DateFormat
import java.util.Date

@Composable
internal fun SettingsRestoreConfirmDialog(
    preview: AppBackupRestorePreview?,
    onDismissRequest: () -> Unit,
    onRestore: () -> Unit,
) {
    SkipiSettingsRestoreConfirmDialog(
        preview = preview?.let {
            SettingsRestorePreviewState(
                backupVersion = it.backup.version,
                appVersionName = it.backup.appVersionName,
                createdAt = it.backup.createdAtMillis
                    .takeIf { value -> value > 0L }
                    ?.let { value -> DateFormat.getDateTimeInstance().format(Date(value)) }
                    ?: "-",
                subscriptionGroupCount = it.subscriptionGroupCount,
                proxyServerCount = it.proxyServerCount,
                routeRuleCount = it.routeRuleCount,
                trafficConfigCount = it.trafficConfigCount,
                warnings = it.warnings.map { warning ->
                    when (warning) {
                        is AppBackupWarning.MissingChainProxyMembers ->
                            SettingsRestoreWarning.MissingChainProxyMembers(warning.count)
                        is AppBackupWarning.MissingStrategyGroupMembers ->
                            SettingsRestoreWarning.MissingStrategyGroupMembers(warning.count)
                    }
                },
            )
        },
        onDismissRequest = onDismissRequest,
        onRestore = onRestore,
    )
}
