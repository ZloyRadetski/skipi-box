// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.skipi.ui.components.WarningConfirmDialog
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_cancel
import app.skipi.ui.resources.proxy_server_list_service_stopped
import app.skipi.ui.resources.settings_backup_export_failed
import app.skipi.ui.resources.settings_backup_exported
import app.skipi.ui.resources.settings_backup_restore
import app.skipi.ui.resources.settings_backup_user_data
import app.skipi.ui.resources.settings_backup_user_data_summary
import app.skipi.ui.resources.settings_category_backup_reset
import app.skipi.ui.resources.settings_reset
import app.skipi.ui.resources.settings_reset_app
import app.skipi.ui.resources.settings_reset_app_completed
import app.skipi.ui.resources.settings_reset_app_confirm
import app.skipi.ui.resources.settings_reset_app_confirm_summary
import app.skipi.ui.resources.settings_reset_app_confirm_title
import app.skipi.ui.resources.settings_reset_app_summary
import app.skipi.ui.resources.settings_reset_failed
import app.skipi.ui.resources.settings_reset_tunnel
import app.skipi.ui.resources.settings_reset_tunnel_completed
import app.skipi.ui.resources.settings_reset_tunnel_confirm
import app.skipi.ui.resources.settings_reset_tunnel_confirm_summary
import app.skipi.ui.resources.settings_reset_tunnel_confirm_title
import app.skipi.ui.resources.settings_reset_tunnel_failed
import app.skipi.ui.resources.settings_reset_tunnel_summary
import app.skipi.ui.resources.settings_reset_vpn
import app.skipi.ui.resources.settings_reset_vpn_summary
import app.skipi.ui.resources.settings_restore_completed
import app.skipi.ui.resources.settings_restore_failed
import app.skipi.ui.resources.settings_restore_read_failed
import app.skipi.ui.resources.settings_restore_user_data
import app.skipi.ui.resources.settings_restore_user_data_summary
import app.skipi.ui.settings.BackupResetSettingsLabels
import app.skipi.ui.settings.SettingsRestorePreviewState
import app.skipi.ui.settings.SettingsRestoreWarning
import app.skipi.ui.settings.SkipiBackupResetSettingsScreen
import app.skipi.ui.settings.SkipiSettingsRestoreConfirmDialog
import features.backup.AppBackupWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.text.DateFormat
import java.util.Date

/** The user-data operations Desktop's backup page needs from the application. */
internal interface DesktopBackupHost {
    fun currentUserData(): DesktopUserData

    /** Stops the running Core session; a failure aborts the destructive operation. */
    suspend fun stopTunnel(): Result<Unit>

    /** Persists [userData] to every store and publishes it to the running application. */
    suspend fun apply(userData: DesktopUserData): Result<Unit>
}

@Composable
internal fun DesktopBackupResetSettings(
    host: DesktopBackupHost,
    appVersionName: String,
    appVersionCode: Int,
    padding: PaddingValues,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pendingRestore by remember { mutableStateOf<DesktopRestorePreview?>(null) }
    var confirmTunnelReset by remember { mutableStateOf(false) }
    var confirmAppReset by remember { mutableStateOf(false) }
    val exportTitle = stringResource(Res.string.settings_backup_user_data)
    val restoreTitle = stringResource(Res.string.settings_restore_user_data)

    fun <T> runExclusive(block: suspend () -> T) {
        if (busy) return
        busy = true
        scope.launch { try { block() } finally { busy = false } }
    }

    SkipiBackupResetSettingsScreen(
        labels = BackupResetSettingsLabels(
            screenTitle = stringResource(Res.string.settings_category_backup_reset),
            backupSectionTitle = stringResource(Res.string.settings_backup_restore),
            backupTitle = exportTitle,
            backupSummary = stringResource(Res.string.settings_backup_user_data_summary),
            restoreTitle = restoreTitle,
            restoreSummary = stringResource(Res.string.settings_restore_user_data_summary),
            resetSectionTitle = stringResource(Res.string.settings_reset),
            resetTunnelTitle = stringResource(Res.string.settings_reset_tunnel),
            resetTunnelSummary = stringResource(Res.string.settings_reset_tunnel_summary),
            resetVpnTitle = stringResource(Res.string.settings_reset_vpn),
            resetVpnSummary = stringResource(Res.string.settings_reset_vpn_summary),
            resetAppTitle = stringResource(Res.string.settings_reset_app),
            resetAppSummary = stringResource(Res.string.settings_reset_app_summary),
        ),
        padding = padding,
        isWideScreen = true,
        onBack = onBack,
        onBackup = {
            runExclusive {
                val target = chooseDesktopBackupTarget(exportTitle) ?: return@runExclusive
                val data = host.currentUserData()
                runCatching {
                    withContext(Dispatchers.IO) {
                        writeDesktopBackup(
                            target,
                            data.toAppBackupFile(System.currentTimeMillis(), appVersionName, appVersionCode),
                        )
                    }
                }.onSuccess { onMessage(getString(Res.string.settings_backup_exported)) }
                    .onFailure { onMessage("${getString(Res.string.settings_backup_export_failed)}: ${it.message.orEmpty()}") }
            }
        },
        onRestore = {
            runExclusive {
                val source = chooseDesktopBackupSource(restoreTitle) ?: return@runExclusive
                val current = host.currentUserData()
                runCatching {
                    withContext(Dispatchers.IO) { readDesktopBackup(source).toDesktopRestorePreview(current) }
                }.onSuccess { pendingRestore = it }
                    .onFailure { onMessage("${getString(Res.string.settings_restore_read_failed)}: ${it.message.orEmpty()}") }
            }
        },
        onResetTunnel = { if (!busy) confirmTunnelReset = true },
        onResetVpn = {},
        onResetApp = { if (!busy) confirmAppReset = true },
        showResetVpn = false,
        overlays = {
            SkipiSettingsRestoreConfirmDialog(
                preview = pendingRestore?.toPreviewState(),
                onDismissRequest = { pendingRestore = null },
                onRestore = {
                    val preview = pendingRestore ?: return@SkipiSettingsRestoreConfirmDialog
                    runExclusive {
                        val result = host.stopTunnel().mapCatching { host.apply(preview.userData).getOrThrow() }
                        pendingRestore = null
                        result.onSuccess { onMessage(getString(Res.string.settings_restore_completed)) }
                            .onFailure { onMessage("${getString(Res.string.settings_restore_failed)}: ${it.message.orEmpty()}") }
                    }
                },
            )
            WarningConfirmDialog(
                show = confirmTunnelReset,
                title = stringResource(Res.string.settings_reset_tunnel_confirm_title),
                summary = stringResource(Res.string.settings_reset_tunnel_confirm_summary),
                dismissText = stringResource(Res.string.common_cancel),
                confirmText = stringResource(Res.string.settings_reset_tunnel_confirm),
                onDismissRequest = { confirmTunnelReset = false },
                onConfirm = {
                    confirmTunnelReset = false
                    runExclusive {
                        host.stopTunnel()
                            .onSuccess { onMessage(getString(Res.string.settings_reset_tunnel_completed)) }
                            .onFailure { onMessage("${getString(Res.string.settings_reset_tunnel_failed)}: ${it.message.orEmpty()}") }
                    }
                },
            )
            WarningConfirmDialog(
                show = confirmAppReset,
                title = stringResource(Res.string.settings_reset_app_confirm_title),
                summary = stringResource(Res.string.settings_reset_app_confirm_summary),
                dismissText = stringResource(Res.string.common_cancel),
                confirmText = stringResource(Res.string.settings_reset_app_confirm),
                onDismissRequest = { confirmAppReset = false },
                onConfirm = {
                    confirmAppReset = false
                    runExclusive {
                        val result = host.stopTunnel().mapCatching {
                            host.apply(host.currentUserData().factoryReset()).getOrThrow()
                        }
                        result.onSuccess { onMessage(getString(Res.string.settings_reset_app_completed)) }
                            .onFailure {
                                val reason = if (it.message.isNullOrBlank()) getString(Res.string.proxy_server_list_service_stopped) else it.message
                                onMessage("${getString(Res.string.settings_reset_failed)}: $reason")
                            }
                    }
                },
            )
        },
    )
}

private fun DesktopRestorePreview.toPreviewState() = SettingsRestorePreviewState(
    backupVersion = backupVersion,
    appVersionName = appVersionName,
    createdAt = createdAtMillis.takeIf { it > 0L }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "-",
    subscriptionGroupCount = subscriptionCount,
    proxyServerCount = serverCount,
    routeRuleCount = routeRuleCount,
    trafficConfigCount = profileCount,
    warnings = warnings.map { warning ->
        when (warning) {
            is AppBackupWarning.MissingChainProxyMembers -> SettingsRestoreWarning.MissingChainProxyMembers(warning.count)
            is AppBackupWarning.MissingStrategyGroupMembers -> SettingsRestoreWarning.MissingStrategyGroupMembers(warning.count)
        }
    },
)
