// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import app.shared.res.Res
import app.shared.res.common_cancel
import app.shared.res.common_restore
import app.shared.res.settings_restore_backup_counts
import app.shared.res.settings_restore_backup_created_at
import app.shared.res.settings_restore_backup_version
import app.shared.res.settings_restore_confirm_summary
import app.shared.res.settings_restore_confirm_title
import app.shared.res.settings_restore_warning_missing_chain_members
import app.shared.res.settings_restore_warning_missing_strategy_members
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import data.backup.AppBackupRestorePreview
import data.backup.AppBackupWarning
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.components.WarningConfirmDialog
import ui.text.formatTemplate
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsRestoreConfirmDialog(
    preview: AppBackupRestorePreview?,
    onDismissRequest: () -> Unit,
    onRestore: () -> Unit,
) {
    if (preview == null) return

    WarningConfirmDialog(
        show = true,
        title = stringResource(Res.string.settings_restore_confirm_title),
        summary = stringResource(Res.string.settings_restore_confirm_summary),
        dismissText = stringResource(Res.string.common_cancel),
        confirmText = stringResource(Res.string.common_restore),
        onDismissRequest = onDismissRequest,
        onConfirm = onRestore,
        detailsMaxHeight = 220.dp,
    ) {
        val warningColor = MiuixTheme.colorScheme.error
        RestoreInfoText(backupVersionText(preview))
        RestoreInfoText(backupCreatedAtText(preview))
        RestoreInfoText(backupCountsText(preview), bottomPadding = if (preview.warnings.isEmpty()) 0.dp else 12.dp)
        preview.warnings.forEachIndexed { index, warning ->
            RestoreInfoText(
                text = warningText(warning),
                color = warningColor,
                bottomPadding = if (index == preview.warnings.lastIndex) 0.dp else 10.dp,
            )
        }
    }
}

@Composable
private fun RestoreInfoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.onBackground,
    bottomPadding: Dp = 10.dp,
) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth().padding(bottom = bottomPadding),
        style = MiuixTheme.textStyles.body2,
        color = color,
    )
}

@Composable
private fun backupVersionText(preview: AppBackupRestorePreview): String {
    return stringResource(Res.string.settings_restore_backup_version).formatTemplate(
        "version" to preview.backup.version,
        "appVersion" to preview.backup.appVersionName.ifBlank { "-" },
    )
}

@Composable
private fun backupCreatedAtText(preview: AppBackupRestorePreview): String {
    val createdAt = preview.backup.createdAtMillis
        .takeIf { value -> value > 0L }
        ?.let { value -> DateFormat.getDateTimeInstance().format(Date(value)) }
        ?: "-"
    return stringResource(Res.string.settings_restore_backup_created_at).formatTemplate(
        "time" to createdAt,
    )
}

@Composable
private fun backupCountsText(preview: AppBackupRestorePreview): String {
    return stringResource(Res.string.settings_restore_backup_counts).formatTemplate(
        "groups" to preview.subscriptionGroupCount,
        "servers" to preview.proxyServerCount,
        "rules" to preview.routeRuleCount,
        "configs" to preview.trafficConfigCount,
    )
}

@Composable
private fun warningText(warning: AppBackupWarning): String {
    return when (warning) {
        is AppBackupWarning.MissingChainProxyMembers -> {
            stringResource(Res.string.settings_restore_warning_missing_chain_members).formatTemplate(
                "count" to warning.count,
            )
        }
        is AppBackupWarning.MissingStrategyGroupMembers -> {
            stringResource(Res.string.settings_restore_warning_missing_strategy_members).formatTemplate(
                "count" to warning.count,
            )
        }
    }
}
