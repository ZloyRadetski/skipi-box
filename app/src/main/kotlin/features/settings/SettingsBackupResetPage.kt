// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.withTunnelStopped
import app.withVpnSettingsReset
import app.skipi.ui.settings.BackupResetSettingsLabels
import app.skipi.ui.settings.SkipiBackupResetSettingsScreen
import data.backup.AppBackupRestorePreview
import features.proxy.server.usecase.ProxyServiceResult
import kotlinx.coroutines.launch
import ui.components.WarningConfirmDialog

@Composable
fun SettingsBackupResetPage(
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    val languageMode = app.LocalAppChromeState.current.languageMode
    val isWideScreen = LocalIsWideScreen.current
    val navigator = LocalNavigator.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current
    val services = LocalAppServices.current
    val appBackupUseCase = services.appBackupUseCase
    val proxyServiceUseCase = services.proxyServiceUseCase
    val tipNotifier = services.tipNotifier
    val scope = rememberCoroutineScope()

    var backupRestoreInProgress by rememberSaveable { mutableStateOf(false) }
    var resetInProgress by rememberSaveable { mutableStateOf(false) }
    var pendingRestorePreview by remember { mutableStateOf<AppBackupRestorePreview?>(null) }
    var tunnelResetInProgress by rememberSaveable { mutableStateOf(false) }
    var showTunnelResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var showVpnResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var showAppResetConfirmation by rememberSaveable { mutableStateOf(false) }

    val serviceStoppedMessage = stringResource(R.string.proxy_server_list_service_stopped)
    val backupExportedMessage = stringResource(R.string.settings_backup_exported)
    val backupExportFailedMessage = stringResource(R.string.settings_backup_export_failed)
    val restoreReadFailedMessage = stringResource(R.string.settings_restore_read_failed)
    val restoreCompletedMessage = stringResource(R.string.settings_restore_completed)
    val restoreFailedMessage = stringResource(R.string.settings_restore_failed)
    val tunnelResetCompletedMessage = stringResource(R.string.settings_reset_tunnel_completed)
    val tunnelResetFailedMessage = stringResource(R.string.settings_reset_tunnel_failed)
    val vpnResetCompletedMessage = stringResource(R.string.settings_reset_vpn_completed)
    val appResetCompletedMessage = stringResource(R.string.settings_reset_app_completed)
    val resetFailedMessage = stringResource(R.string.settings_reset_failed)

    key(languageMode) {
        SkipiBackupResetSettingsScreen(
            labels = BackupResetSettingsLabels(
                screenTitle = stringResource(R.string.settings_category_backup_reset),
                backupSectionTitle = stringResource(R.string.settings_backup_restore),
                backupTitle = stringResource(R.string.settings_backup_user_data),
                backupSummary = stringResource(R.string.settings_backup_user_data_summary),
                restoreTitle = stringResource(R.string.settings_restore_user_data),
                restoreSummary = stringResource(R.string.settings_restore_user_data_summary),
                resetSectionTitle = stringResource(R.string.settings_reset),
                resetTunnelTitle = stringResource(R.string.settings_reset_tunnel),
                resetTunnelSummary = stringResource(R.string.settings_reset_tunnel_summary),
                resetVpnTitle = stringResource(R.string.settings_reset_vpn),
                resetVpnSummary = stringResource(R.string.settings_reset_vpn_summary),
                resetAppTitle = stringResource(R.string.settings_reset_app),
                resetAppSummary = stringResource(R.string.settings_reset_app_summary),
            ),
            padding = padding,
            isWideScreen = isWideScreen,
            onBack = { navigator.pop() },
            onBackup = {
                if (!backupRestoreInProgress) {
                    val currentState = appState
                    backupRestoreInProgress = true
                    scope.launch {
                        try {
                            runCatching { appBackupUseCase.export(currentState) }
                                .onSuccess { exported -> if (exported) tipNotifier.show(backupExportedMessage) }
                                .onFailure { error -> tipNotifier.showError(error, backupExportFailedMessage) }
                        } finally {
                            backupRestoreInProgress = false
                        }
                    }
                }
            },
            onRestore = {
                if (!backupRestoreInProgress) {
                    backupRestoreInProgress = true
                    scope.launch {
                        try {
                            runCatching { appBackupUseCase.readRestorePreview() }
                                .onSuccess { preview -> pendingRestorePreview = preview }
                                .onFailure { error -> tipNotifier.showError(error, restoreReadFailedMessage) }
                        } finally {
                            backupRestoreInProgress = false
                        }
                    }
                }
            },
            onResetTunnel = { if (!tunnelResetInProgress) showTunnelResetConfirmation = true },
            onResetVpn = {
                if (!resetInProgress && !tunnelResetInProgress) showVpnResetConfirmation = true
            },
            onResetApp = {
                if (!resetInProgress && !tunnelResetInProgress) showAppResetConfirmation = true
            },
            overlays = {
                SettingsRestoreConfirmDialog(
                    preview = pendingRestorePreview,
                    onDismissRequest = { pendingRestorePreview = null },
                    onRestore = {
                        val restorePreview = pendingRestorePreview
                        if (restorePreview != null && !backupRestoreInProgress) {
                            backupRestoreInProgress = true
                            scope.launch {
                                try {
                                    when (val result = proxyServiceUseCase.shutdown(appState.runMode)) {
                                        is ProxyServiceResult.Success, ProxyServiceResult.MissingServer -> Unit
                                        is ProxyServiceResult.Failed -> {
                                            tipNotifier.showError(result.error, serviceStoppedMessage)
                                            return@launch
                                        }
                                    }
                                    updateAppState { restorePreview.restoredState }
                                    pendingRestorePreview = null
                                    tipNotifier.show(restoreCompletedMessage)
                                } catch (error: Throwable) {
                                    tipNotifier.showError(error, restoreFailedMessage)
                                } finally {
                                    backupRestoreInProgress = false
                                }
                            }
                        }
                    },
                )
                WarningConfirmDialog(
                    show = showTunnelResetConfirmation,
                    title = stringResource(R.string.settings_reset_tunnel_confirm_title),
                    summary = stringResource(R.string.settings_reset_tunnel_confirm_summary),
                    dismissText = stringResource(R.string.common_cancel),
                    confirmText = stringResource(R.string.settings_reset_tunnel_confirm),
                    onDismissRequest = { showTunnelResetConfirmation = false },
                    onConfirm = {
                        if (!tunnelResetInProgress) {
                            tunnelResetInProgress = true
                            showTunnelResetConfirmation = false
                            scope.launch {
                                try {
                                    when (val result = proxyServiceUseCase.forceShutdown()) {
                                        is ProxyServiceResult.Success, ProxyServiceResult.MissingServer -> {
                                            updateAppState { state -> state.withTunnelStopped() }
                                            tipNotifier.show(tunnelResetCompletedMessage)
                                        }
                                        is ProxyServiceResult.Failed -> tipNotifier.showError(result.error, tunnelResetFailedMessage)
                                    }
                                } catch (error: Throwable) {
                                    tipNotifier.showError(error, tunnelResetFailedMessage)
                                } finally {
                                    tunnelResetInProgress = false
                                }
                            }
                        }
                    },
                )
                WarningConfirmDialog(
                    show = showVpnResetConfirmation,
                    title = stringResource(R.string.settings_reset_vpn_confirm_title),
                    summary = stringResource(R.string.settings_reset_vpn_confirm_summary),
                    dismissText = stringResource(R.string.common_cancel),
                    confirmText = stringResource(R.string.settings_reset_vpn_confirm),
                    onDismissRequest = { showVpnResetConfirmation = false },
                    onConfirm = {
                        if (!resetInProgress) {
                            resetInProgress = true
                            showVpnResetConfirmation = false
                            scope.launch {
                                try {
                                    when (val result = proxyServiceUseCase.shutdown(appState.runMode)) {
                                        is ProxyServiceResult.Success, ProxyServiceResult.MissingServer -> Unit
                                        is ProxyServiceResult.Failed -> {
                                            tipNotifier.showError(result.error, serviceStoppedMessage)
                                            return@launch
                                        }
                                    }
                                    updateAppState { state -> state.withVpnSettingsReset() }
                                    tipNotifier.show(vpnResetCompletedMessage)
                                } catch (error: Throwable) {
                                    tipNotifier.showError(error, resetFailedMessage)
                                } finally {
                                    resetInProgress = false
                                }
                            }
                        }
                    },
                )
                WarningConfirmDialog(
                    show = showAppResetConfirmation,
                    title = stringResource(R.string.settings_reset_app_confirm_title),
                    summary = stringResource(R.string.settings_reset_app_confirm_summary),
                    dismissText = stringResource(R.string.common_cancel),
                    confirmText = stringResource(R.string.settings_reset_app_confirm),
                    onDismissRequest = { showAppResetConfirmation = false },
                    onConfirm = {
                        if (!resetInProgress) {
                            resetInProgress = true
                            showAppResetConfirmation = false
                            scope.launch {
                                try {
                                    when (val result = proxyServiceUseCase.shutdown(appState.runMode)) {
                                        is ProxyServiceResult.Success, ProxyServiceResult.MissingServer -> Unit
                                        is ProxyServiceResult.Failed -> {
                                            tipNotifier.showError(result.error, serviceStoppedMessage)
                                            return@launch
                                        }
                                    }
                                    stateStore.resetToStockState()
                                    tipNotifier.show(appResetCompletedMessage)
                                } catch (error: Throwable) {
                                    tipNotifier.showError(error, resetFailedMessage)
                                } finally {
                                    resetInProgress = false
                                }
                            }
                        }
                    },
                )
            },
        )
    }
}
