// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.skipi.ui.components.WarningConfirmDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.*
import app.skipi.ui.text.formatTemplate
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class SettingsRestorePreviewState(
    val backupVersion: Int,
    val appVersionName: String,
    val createdAt: String,
    val subscriptionGroupCount: Int,
    val proxyServerCount: Int,
    val routeRuleCount: Int,
    val trafficConfigCount: Int,
    val warnings: List<SettingsRestoreWarning>,
)

sealed interface SettingsRestoreWarning {
    data class MissingChainProxyMembers(val count: Int) : SettingsRestoreWarning
    data class MissingStrategyGroupMembers(val count: Int) : SettingsRestoreWarning
}

@Composable
fun SkipiSettingsRestoreConfirmDialog(
    preview: SettingsRestorePreviewState?,
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
        RestoreInfoText(
            stringResource(Res.string.settings_restore_backup_version).formatTemplate(
                "version" to preview.backupVersion,
                "appVersion" to preview.appVersionName.ifBlank { "-" },
            ),
        )
        RestoreInfoText(
            stringResource(Res.string.settings_restore_backup_created_at).formatTemplate("time" to preview.createdAt),
        )
        RestoreInfoText(
            stringResource(Res.string.settings_restore_backup_counts).formatTemplate(
                "groups" to preview.subscriptionGroupCount,
                "servers" to preview.proxyServerCount,
                "rules" to preview.routeRuleCount,
                "configs" to preview.trafficConfigCount,
            ),
            bottomPadding = if (preview.warnings.isEmpty()) 0.dp else 12.dp,
        )
        preview.warnings.forEachIndexed { index, warning ->
            val message = when (warning) {
                is SettingsRestoreWarning.MissingChainProxyMembers -> stringResource(
                    Res.string.settings_restore_warning_missing_chain_members,
                ).formatTemplate("count" to warning.count)
                is SettingsRestoreWarning.MissingStrategyGroupMembers -> stringResource(
                    Res.string.settings_restore_warning_missing_strategy_members,
                ).formatTemplate("count" to warning.count)
            }
            RestoreInfoText(
                text = message,
                color = warningColor,
                bottomPadding = if (index == preview.warnings.lastIndex) 0.dp else 10.dp,
            )
        }
    }
}

@Composable
private fun ColumnScope.RestoreInfoText(
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
