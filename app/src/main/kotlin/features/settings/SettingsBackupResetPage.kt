// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:OptIn(ExperimentalScrollBarApi::class)

package features.settings

import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.AppState
import app.LocalAppChromeState
import app.LocalAppServices
import app.LocalAppStateStore
import app.LocalIsWideScreen
import app.LocalNavigator
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import app.withVpnSettingsReset
import data.backup.AppBackupRestorePreview
import features.proxy.server.usecase.ProxyServiceResult
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.preference.ArrowPreference
import ui.AppTheme
import ui.components.BackNavigationIcon
import ui.components.WarningConfirmDialog
import ui.layout.AdaptiveTopAppBar
import ui.layout.pageContentPaddingWithCutout
import ui.layout.pageListPadding
import ui.layout.pageScrollModifiers

@Composable
fun SettingsBackupResetPage(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
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
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    var backupRestoreInProgress by rememberSaveable { mutableStateOf(false) }
    var resetInProgress by rememberSaveable { mutableStateOf(false) }
    var pendingRestorePreview by remember { mutableStateOf<AppBackupRestorePreview?>(null) }
    var showVpnResetConfirmation by rememberSaveable { mutableStateOf(false) }
    var showAppResetConfirmation by rememberSaveable { mutableStateOf(false) }

    val serviceStoppedMessage = stringResource(R.string.proxy_server_list_service_stopped)
    val backupExportedMessage = stringResource(R.string.settings_backup_exported)
    val backupExportFailedMessage = stringResource(R.string.settings_backup_export_failed)
    val restoreReadFailedMessage = stringResource(R.string.settings_restore_read_failed)
    val restoreCompletedMessage = stringResource(R.string.settings_restore_completed)
    val restoreFailedMessage = stringResource(R.string.settings_restore_failed)
    val vpnResetCompletedMessage = stringResource(R.string.settings_reset_vpn_completed)
    val appResetCompletedMessage = stringResource(R.string.settings_reset_app_completed)
    val resetFailedMessage = stringResource(R.string.settings_reset_failed)

    Scaffold(
        topBar = {
            key(languageMode) {
                AdaptiveTopAppBar(
                    title = stringResource(R.string.settings_category_backup_reset),
                    isWideScreen = isWideScreen,
                    scrollBehavior = topAppBarScrollBehavior,
                    navigationIcon = {
                        BackNavigationIcon(
                            onClick = { navigator.pop() },
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        val innerContentPadding = pageContentPaddingWithCutout(
            innerPadding = innerPadding,
            outerPadding = padding,
            isWideScreen = isWideScreen,
        )
        val innerListPadding = pageListPadding(innerContentPadding)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppTheme.colors.background),
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppTheme.colors.background)
                    .pageScrollModifiers(topAppBarScrollBehavior),
                contentPadding = innerListPadding,
            ) {
                item(key = "backup_restore_card") {
                    SmallTitle(text = stringResource(R.string.settings_backup_restore))
                    SettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(R.string.settings_backup_user_data),
                            summary = stringResource(R.string.settings_backup_user_data_summary),
                            onClick = {
                                if (!backupRestoreInProgress) {
                                    val currentState = appState
                                    backupRestoreInProgress = true
                                    scope.launch {
                                        try {
                                            runCatching {
                                                appBackupUseCase.export(currentState)
                                            }.onSuccess { exported ->
                                                if (exported) {
                                                    tipNotifier.show(backupExportedMessage)
                                                }
                                            }.onFailure { error ->
                                                tipNotifier.showError(error, backupExportFailedMessage)
                                            }
                                        } finally {
                                            backupRestoreInProgress = false
                                        }
                                    }
                                }
                            },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_restore_user_data),
                            summary = stringResource(R.string.settings_restore_user_data_summary),
                            onClick = {
                                if (!backupRestoreInProgress) {
                                    backupRestoreInProgress = true
                                    scope.launch {
                                        try {
                                            runCatching {
                                                appBackupUseCase.readRestorePreview()
                                            }.onSuccess { preview ->
                                                pendingRestorePreview = preview
                                            }.onFailure { error ->
                                                tipNotifier.showError(error, restoreReadFailedMessage)
                                            }
                                        } finally {
                                            backupRestoreInProgress = false
                                        }
                                    }
                                }
                            },
                        )
                    }
                }

                item(key = "reset_card") {
                    SmallTitle(text = stringResource(R.string.settings_reset))
                    SettingsSectionCard {
                        ArrowPreference(
                            title = stringResource(R.string.settings_reset_vpn),
                            summary = stringResource(R.string.settings_reset_vpn_summary),
                            onClick = { showVpnResetConfirmation = true },
                        )
                        ArrowPreference(
                            title = stringResource(R.string.settings_reset_app),
                            summary = stringResource(R.string.settings_reset_app_summary),
                            onClick = { showAppResetConfirmation = true },
                        )
                    }
                }
            }

            VerticalScrollBar(
                adapter = rememberScrollBarAdapter(lazyListState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                trackPadding = innerContentPadding,
            )

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
                                    is ProxyServiceResult.Success -> Unit
                                    ProxyServiceResult.MissingServer -> Unit
                                    is ProxyServiceResult.Failed -> {
                                        tipNotifier.showError(result.error, serviceStoppedMessage)
                                        return@launch
                                    }
                                }
                                updateAppState {
                                    restorePreview.restoredState
                                }
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
                                    is ProxyServiceResult.Success -> Unit
                                    ProxyServiceResult.MissingServer -> Unit
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
                                    is ProxyServiceResult.Success -> Unit
                                    ProxyServiceResult.MissingServer -> Unit
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
        }
    }
}
