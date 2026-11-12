// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import app.skipi.ui.networkautomation.NetworkAutomationServerOption
import app.skipi.ui.networkautomation.SkipiNetworkAutomationScreen
import app.skipi.ui.resources.common_refresh
import app.skipi.ui.resources.desktop_open_link_unsupported
import app.skipi.ui.resources.desktop_settings_admin_rights_failed
import app.skipi.ui.resources.desktop_settings_app_log_header
import app.skipi.ui.resources.desktop_settings_appearance_saved
import app.skipi.ui.resources.desktop_settings_automation_saved
import app.skipi.ui.resources.desktop_settings_check_app_resources
import app.skipi.ui.resources.desktop_settings_copy_failed
import app.skipi.ui.resources.desktop_settings_copy_settings_path
import app.skipi.ui.resources.desktop_settings_core_found
import app.skipi.ui.resources.desktop_settings_core_not_found
import app.skipi.ui.resources.desktop_settings_data_dir
import app.skipi.ui.resources.desktop_settings_data_dir_not_found
import app.skipi.ui.resources.desktop_settings_data_dir_open_failed
import app.skipi.ui.resources.desktop_settings_data_dir_opened
import app.skipi.ui.resources.desktop_settings_data_header
import app.skipi.ui.resources.desktop_settings_folder_open_failed
import app.skipi.ui.resources.desktop_settings_folder_open_unsupported
import app.skipi.ui.resources.desktop_settings_general_saved
import app.skipi.ui.resources.desktop_settings_hide_log
import app.skipi.ui.resources.desktop_settings_http_port_conflict
import app.skipi.ui.resources.desktop_settings_launcher_unknown
import app.skipi.ui.resources.desktop_settings_link_open_failed
import app.skipi.ui.resources.desktop_settings_local_proxy_saved
import app.skipi.ui.resources.desktop_settings_log_empty
import app.skipi.ui.resources.desktop_settings_log_file_size
import app.skipi.ui.resources.desktop_settings_log_not_created
import app.skipi.ui.resources.desktop_settings_logs_folder_opened
import app.skipi.ui.resources.desktop_settings_logs_saved
import app.skipi.ui.resources.desktop_settings_open_logs_folder
import app.skipi.ui.resources.desktop_settings_path_copied
import app.skipi.ui.resources.desktop_settings_path_copy_failed
import app.skipi.ui.resources.desktop_settings_ping_saved
import app.skipi.ui.resources.desktop_settings_recheck_runtime
import app.skipi.ui.resources.desktop_settings_recheck_runtime_summary
import app.skipi.ui.resources.desktop_settings_reset_system_proxy
import app.skipi.ui.resources.desktop_settings_reset_system_proxy_summary
import app.skipi.ui.resources.desktop_settings_save_failed
import app.skipi.ui.resources.desktop_settings_show_recent_log
import app.skipi.ui.resources.desktop_settings_socks_port_conflict
import app.skipi.ui.resources.desktop_settings_state_header
import app.skipi.ui.resources.desktop_settings_storage
import app.skipi.ui.resources.desktop_settings_storage_header
import app.skipi.ui.resources.desktop_settings_subscriptions_saved
import app.skipi.ui.resources.desktop_settings_system_proxy_header
import app.skipi.ui.resources.desktop_settings_tunnel_running
import app.skipi.ui.resources.desktop_settings_tunnel_running_summary
import app.skipi.ui.resources.desktop_settings_tunnel_stopped
import app.skipi.ui.resources.desktop_settings_tunnel_stopped_summary
import app.skipi.ui.resources.desktop_settings_use_system_proxy
import app.skipi.ui.resources.desktop_settings_use_system_proxy_summary
import app.skipi.ui.resources.desktop_settings_user_agents_saved
import app.skipi.ui.resources.desktop_settings_vpn_saved
import features.networkautomation.policy.NetworkAutomationRules
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_copied
import app.skipi.ui.resources.app_update_available_title
import app.skipi.ui.resources.app_update_check_failed
import app.skipi.ui.resources.app_update_checking
import app.skipi.ui.resources.app_update_downloading_action
import app.skipi.ui.resources.app_update_already_latest
import app.skipi.ui.resources.network_automation_no_wifi_detected
import app.skipi.ui.settings.AppearanceSettingValues
import app.skipi.ui.resources.settings_local_proxy_port_invalid
import app.skipi.ui.resources.settings_local_proxy_copied_toast
import app.skipi.ui.resources.settings_local_proxy_generated_toast
import app.skipi.ui.resources.about_bug_report
import app.skipi.ui.resources.about_bug_report_summary
import app.skipi.ui.resources.settings_access_logs
import app.skipi.ui.resources.settings_category_logs
import app.skipi.ui.resources.settings_core_logs
import app.skipi.ui.resources.settings_feedback
import app.skipi.ui.resources.settings_log_level
import app.skipi.ui.resources.settings_log_retention
import app.skipi.ui.resources.settings_log_retention_summary
import app.skipi.ui.resources.settings_logcat
import app.skipi.ui.resources.settings_logs
import app.skipi.ui.resources.settings_record_access_log
import app.skipi.ui.resources.access_logs_title
import app.skipi.ui.resources.core_logs_title
import app.skipi.ui.resources.settings_log_retention_14_days
import app.skipi.ui.resources.settings_log_retention_1_day
import app.skipi.ui.resources.settings_log_retention_30_days
import app.skipi.ui.resources.settings_log_retention_3_days
import app.skipi.ui.resources.settings_log_retention_7_days
import app.skipi.ui.resources.settings_log_retention_unlimited
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.skipi.ui.diagnostics.SkipiDnsLeakTestScreen
import app.skipi.ui.diagnostics.SkipiIpInfoScreen
import app.skipi.ui.diagnostics.SkipiSpeedTestScreen
import app.skipi.ui.navigation.SkipiTool
import app.skipi.ui.navigation.OnNavigationReselect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.settings.SkipiSettingsActionRow
import app.skipi.ui.settings.SkipiSettingsGroup
import app.skipi.ui.settings.SkipiSettingsInfoRow
import app.skipi.ui.settings.SkipiSettingsNumberField
import app.skipi.ui.settings.SkipiSettingsStatusMessage
import app.skipi.ui.settings.SkipiSettingsSwitchRow
import app.skipi.ui.settings.AboutRuntimeInfo
import app.skipi.ui.settings.AboutSettingsCapabilities
import app.skipi.ui.settings.AboutSettingsLabels
import app.skipi.ui.updater.SkipiAppUpdatesSettingsCard
import features.updater.AppUpdateChannel
import features.updater.AppUpdateState
import app.skipi.ui.settings.AppearanceSettingsCapabilities
import app.skipi.ui.settings.GeneralSettingsCapabilities
import app.skipi.ui.settings.GeneralSettingsState
import app.skipi.ui.settings.GeneralSettingsValues
import app.skipi.ui.settings.LocalProxySettingsCapabilities
import app.skipi.ui.settings.LocalProxySettingsLabels
import app.skipi.ui.settings.LogsSettingsCapabilities
import app.skipi.ui.settings.LogsSettingsLabels
import app.skipi.ui.settings.LogsSettingsState
import app.skipi.ui.settings.SkipiAboutScreen
import app.skipi.ui.settings.SkipiBugReportUri
import app.skipi.ui.settings.SkipiCoreSourceUri
import app.skipi.ui.settings.SkipiSourceUri
import app.skipi.ui.settings.SkipiTelegramChannelUri
import app.skipi.ui.settings.SkipiUrlSchemesScreen
import app.skipi.ui.settings.rememberAboutSettingsLabels
import app.skipi.ui.settings.rememberLocalProxySettingsLabels
import app.skipi.ui.settings.rememberSubscriptionSettingsLabels
import app.skipi.ui.settings.SkipiAppearanceSettingsScreen
import app.skipi.ui.settings.SkipiGeneralSettingsScreen
import app.skipi.ui.settings.SkipiLocalProxySettingsScreen
import app.skipi.ui.settings.SkipiLogsSettingsScreen
import app.skipi.ui.settings.SkipiSettingsDestination
import app.skipi.ui.settings.SkipiSettingsHomeScreen
import app.skipi.ui.settings.SkipiSettingsHomeState
import app.skipi.ui.settings.SkipiSubscriptionSettingsScreen
import app.skipi.ui.settings.SkipiSubscriptionPingSettingsScreen
import app.skipi.ui.settings.SubscriptionPingPageState
import app.skipi.ui.settings.SubscriptionUserAgentsScreen
import app.skipi.ui.settings.SubscriptionUserAgentsState
import app.skipi.ui.settings.SubscriptionSettingsCapabilities
import app.skipi.ui.settings.SubscriptionSettingsLabels
import app.skipi.ui.theme.SkipiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files
import java.util.Locale
import app.skipi.app.settings.generateRandomProxyCredential
import app.skipi.app.settings.localNetworkIpAddresses
import app.skipi.ui.settings.isValidLocalProxyPort
import app.skipi.ui.settings.sanitizeLocalProxyPort
import app.modes.SubscriptionPingModeHttp


internal enum class DesktopSettingsDestination {
    Overview, General, Appearance, Vpn, LocalProxy, Subscriptions, SubscriptionPing, SubscriptionUserAgents,
    NetworkAutomation, BackupReset, SpeedTest, DnsLeakTest, IpInfo, UrlSchemes, Diagnostics, CoreLogs, AccessLogs, About,
}

/** Where Desktop takes a tool of the navigation: a page of the settings, or the rules of the selected profile. */
internal sealed interface DesktopToolTarget {
    data class SettingsPage(val destination: DesktopSettingsDestination) : DesktopToolTarget

    /** The rule editor of the profile that is selected on the Configs screen. */
    data object SelectedProfileRules : DesktopToolTarget
}

internal fun SkipiTool.desktopTarget(): DesktopToolTarget = when (this) {
    SkipiTool.SpeedTest -> DesktopToolTarget.SettingsPage(DesktopSettingsDestination.SpeedTest)
    SkipiTool.DnsLeakTest -> DesktopToolTarget.SettingsPage(DesktopSettingsDestination.DnsLeakTest)
    SkipiTool.IpInfo -> DesktopToolTarget.SettingsPage(DesktopSettingsDestination.IpInfo)
    SkipiTool.CoreLogs -> DesktopToolTarget.SettingsPage(DesktopSettingsDestination.CoreLogs)
    SkipiTool.RoutingRules -> DesktopToolTarget.SelectedProfileRules
}

/** The page a settings page returns to; null for the overview, which has nowhere to go back to. */
internal fun desktopSettingsParent(destination: DesktopSettingsDestination): DesktopSettingsDestination? = when (destination) {
    DesktopSettingsDestination.Overview -> null
    DesktopSettingsDestination.SubscriptionPing,
    DesktopSettingsDestination.SubscriptionUserAgents,
    -> DesktopSettingsDestination.Subscriptions
    DesktopSettingsDestination.UrlSchemes -> DesktopSettingsDestination.General
    DesktopSettingsDestination.CoreLogs,
    DesktopSettingsDestination.AccessLogs,
    -> DesktopSettingsDestination.Diagnostics
    else -> DesktopSettingsDestination.Overview
}

/** Desktop host for shared settings pages and operations owned by the desktop platform. */
@Composable
internal fun DesktopSettingsScreen(
    settings: DesktopAppSettings,
    onSettingsChange: (DesktopAppSettings) -> Unit,
    contentPadding: PaddingValues,
    diagnostics: DesktopDiagnostics,
    coreLogs: DesktopCoreLogs,
    automationServers: List<NetworkAutomationServerOption>,
    backupHost: DesktopBackupHost,
    isTunnelRunning: Boolean = false,
    tunHost: DesktopTunHost? = null,
    escapeHandler: DesktopEscapeHandler = DesktopEscapeHandler(),
    appUpdateState: AppUpdateState = AppUpdateState(),
    onCheckForAppUpdate: suspend () -> DesktopAppUpdateCheck = { DesktopAppUpdateCheck.Failed },
    onAppUpdateAction: () -> Unit = {},
    onClearSystemProxy: (() -> Unit)? = null,
    onExitApplication: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** The page the screen opens on; a pinned tool opens Settings straight on its page. */
    initialDestination: DesktopSettingsDestination = DesktopSettingsDestination.Overview,
) {
    var destination by remember { mutableStateOf(initialDestination) }
    var message by remember { mutableStateOf("") }
    val copiedMessage = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.common_copied)
    val scope = rememberCoroutineScope()
    val saveMutex = remember { Mutex() }
    var currentSettings by remember(settings) { mutableStateOf(settings) }
    LaunchedEffect(settings) { currentSettings = settings }

    fun persist(next: DesktopAppSettings, successMessage: String) {
        currentSettings = next
        onSettingsChange(next)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                saveMutex.withLock { DesktopSettingsLibraries.saveDefault(next) }
            }
            // Android applies settings silently; only failures are reported.
            result.onSuccess { message = "" }
                .onFailure { error -> message = desktopText(Res.string.desktop_settings_save_failed, error.message.orEmpty()) }
        }
    }

    val noWifiMessage = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.network_automation_no_wifi_detected)
    val automationSavedMessage = stringResource(Res.string.desktop_settings_automation_saved)
    val goBack: () -> Unit = {
        desktopSettingsParent(destination)?.let { parent -> destination = parent }
    }
    OnNavigationReselect {
        if (destination != DesktopSettingsDestination.Overview) {
            diagnostics.speedTest.stop()
            diagnostics.dnsLeak.stop()
            diagnostics.ipInfo.stop()
            destination = DesktopSettingsDestination.Overview
        }
    }
    DisposableEffect(destination, escapeHandler) {
        escapeHandler.register(goBack.takeIf { destination != DesktopSettingsDestination.Overview })
        onDispose { escapeHandler.register(null) }
    }
    when (destination) {
        DesktopSettingsDestination.Overview -> DesktopSettingsOverview(
            settings = currentSettings,
            message = message,
            contentPadding = contentPadding,
            modifier = modifier,
            vpnPage = tunHost != null,
            onNavigate = { destination = it },
        )
        DesktopSettingsDestination.Vpn -> {
            val tunUnavailableReason by produceState<String?>(initialValue = "…", tunHost) {
                value = withContext(Dispatchers.IO) { tunUnavailableReasonFor(tunHost) }
                value?.let { reason -> DesktopLogger.info("TunHost", "TUN mode unavailable: $reason") }
            }
            DesktopVpnSettings(
                settings = currentSettings,
                tunUnavailableReason = tunUnavailableReason,
                tunNeedsElevation = tunHost?.needsElevation() == true,
                onRequestTunElevation = {
                    // The elevated instance starts with TUN on; a declined UAC prompt puts the setting back.
                    persist(currentSettings.copy(enableTunMode = true), desktopText(Res.string.desktop_settings_vpn_saved))
                    scope.launch {
                        val launcher = ProcessHandle.current().info().command().orElse(null)
                        val currentArguments = ProcessHandle.current().info().arguments().orElse(emptyArray()).toList()
                        // The elevated instance waits for this one to exit before it claims the single-instance channel.
                        val arguments = currentArguments.withoutWaitForPid() + listOf(WaitForPidOption, ProcessHandle.current().pid().toString())
                        val result = withContext(Dispatchers.IO) {
                            if (launcher == null) {
                                Result.failure(IllegalStateException(desktopText(Res.string.desktop_settings_launcher_unknown)))
                            } else {
                                relaunchElevated(launcher, arguments, exitDelaySeconds = 0)
                            }
                        }
                        result.onSuccess { onExitApplication() }
                            .onFailure { error ->
                                persist(currentSettings.copy(enableTunMode = false), "")
                                message = desktopText(Res.string.desktop_settings_admin_rights_failed, error.message.orEmpty())
                            }
                    }
                },
                onBack = goBack,
                onPersist = ::persist,
                onApplyLaunchOnLogin = { enabled ->
                    resolveAutostartLauncher(ProcessHandle.current().info().command().orElse(null))
                        .mapCatching { launcher -> desktopAutostartFor().apply(enabled, launcher).getOrThrow() }
                },
                onOpenNetworkAutomation = { destination = DesktopSettingsDestination.NetworkAutomation },
                contentPadding = contentPadding,
            )
        }
        DesktopSettingsDestination.Appearance -> DesktopAppearanceSettings(
            settings = currentSettings,
            message = message,
            onBack = goBack,
            onPersist = ::persist,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.General -> DesktopGeneralSettings(
            settings = currentSettings,
            message = message,
            onBack = goBack,
            onOpenUrlSchemes = { destination = DesktopSettingsDestination.UrlSchemes },
            onOpenBackupReset = { destination = DesktopSettingsDestination.BackupReset },
            onPersist = ::persist,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.LocalProxy -> DesktopLocalProxySettings(
            settings = currentSettings,
            message = message,
            onBack = goBack,
            onPersist = ::persist,
            onClearSystemProxy = onClearSystemProxy,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.Subscriptions -> DesktopSubscriptionSettings(
            settings = currentSettings,
            message = message,
            onBack = goBack,
            onPersist = ::persist,
            onOpenPingSettings = { destination = DesktopSettingsDestination.SubscriptionPing },
            onOpenUserAgents = { destination = DesktopSettingsDestination.SubscriptionUserAgents },
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.SubscriptionPing -> DesktopSubscriptionPingSettings(
            settings = currentSettings,
            onBack = goBack,
            onPersist = ::persist,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.SubscriptionUserAgents -> DesktopSubscriptionUserAgents(
            settings = currentSettings,
            onBack = goBack,
            onPersist = ::persist,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.BackupReset -> DesktopBackupResetSettings(
            host = backupHost,
            appVersionName = ProjectInfo.VERSION_NAME,
            appVersionCode = ProjectInfo.VERSION_CODE,
            padding = contentPadding,
            onBack = goBack,
            onMessage = { message = it },
        )
        DesktopSettingsDestination.NetworkAutomation -> SkipiNetworkAutomationScreen(
            networkAutomationEnabled = currentSettings.enableNetworkAutomation,
            onDemandEnabled = currentSettings.enableOnDemandVpn,
            rules = currentSettings.networkAutomationRules,
            servers = automationServers,
            padding = contentPadding,
            isWideScreen = true,
            onBack = goBack,
            onNetworkAutomationChange = { enabled ->
                persist(currentSettings.copy(enableNetworkAutomation = enabled), automationSavedMessage)
            },
            onOnDemandChange = { enabled ->
                persist(currentSettings.copy(enableOnDemandVpn = enabled), automationSavedMessage)
            },
            onSaveRule = { rule ->
                persist(
                    currentSettings.copy(
                        networkAutomationRules = NetworkAutomationRules.upsert(currentSettings.networkAutomationRules, rule),
                    ),
                    automationSavedMessage,
                )
            },
            onDeleteRule = { id ->
                persist(
                    currentSettings.copy(
                        networkAutomationRules = NetworkAutomationRules.remove(currentSettings.networkAutomationRules, id),
                    ),
                    automationSavedMessage,
                )
            },
            onRuleEnabledChange = { id, enabled ->
                persist(
                    currentSettings.copy(
                        networkAutomationRules = NetworkAutomationRules.setEnabled(
                            currentSettings.networkAutomationRules, id, enabled,
                        ),
                    ),
                    automationSavedMessage,
                )
            },
            onRequestCurrentWifi = { onSsidReceived ->
                scope.launch {
                    val ssid = withContext(Dispatchers.IO) { readDesktopWifiSsid() }
                    if (ssid.isNullOrBlank()) message = noWifiMessage else onSsidReceived(ssid)
                }
            },
        )
        DesktopSettingsDestination.SpeedTest -> {
            val state by diagnostics.speedTest.state.collectAsState()
            SkipiSpeedTestScreen(
                state = state,
                padding = contentPadding,
                isWideScreen = true,
                onBack = { diagnostics.speedTest.stop(); destination = DesktopSettingsDestination.Overview },
                onStart = diagnostics.speedTest::start,
                onStop = diagnostics.speedTest::stop,
            )
        }
        DesktopSettingsDestination.DnsLeakTest -> {
            val state by diagnostics.dnsLeak.state.collectAsState()
            SkipiDnsLeakTestScreen(
                state = state,
                padding = contentPadding,
                isWideScreen = true,
                onBack = { diagnostics.dnsLeak.stop(); destination = DesktopSettingsDestination.Overview },
                onStart = diagnostics.dnsLeak::start,
                onStop = diagnostics.dnsLeak::stop,
            )
        }
        DesktopSettingsDestination.IpInfo -> {
            val state by diagnostics.ipInfo.state.collectAsState()
            SkipiIpInfoScreen(
                state = state,
                padding = contentPadding,
                isWideScreen = true,
                onBack = { diagnostics.ipInfo.stop(); destination = DesktopSettingsDestination.Overview },
                onRefresh = diagnostics.ipInfo::refresh,
                onCopy = { text, confirmation ->
                    runCatching {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
                    }.onSuccess { message = confirmation }
                },
            )
        }
        DesktopSettingsDestination.UrlSchemes -> SkipiUrlSchemesScreen(
            padding = contentPadding,
            isWideScreen = true,
            onBack = goBack,
            onCopyCommand = { command ->
                copyToClipboard(command).onSuccess { message = copiedMessage }
            },
        )
        DesktopSettingsDestination.Diagnostics -> DesktopLogsSettings(
            settings = currentSettings,
            isTunnelRunning = isTunnelRunning,
            message = message,
            onBack = goBack,
            onPersist = ::persist,
            onMessage = { message = it },
            onPruneLogs = coreLogs::pruneOlderThanDays,
            onOpenCoreLogs = { destination = DesktopSettingsDestination.CoreLogs },
            onOpenAccessLogs = { destination = DesktopSettingsDestination.AccessLogs },
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.CoreLogs -> DesktopLogViewer(
            title = stringResource(Res.string.core_logs_title),
            repository = coreLogs.core,
            padding = contentPadding,
            onBack = goBack,
            onClear = coreLogs::clearCore,
            onMessage = { message = it },
        )
        DesktopSettingsDestination.AccessLogs -> DesktopLogViewer(
            title = stringResource(Res.string.access_logs_title),
            repository = coreLogs.access,
            padding = contentPadding,
            onBack = goBack,
            onClear = coreLogs::clearAccess,
            onMessage = { message = it },
        )
        DesktopSettingsDestination.About -> DesktopAboutSettings(
            settings = currentSettings,
            appUpdateState = appUpdateState,
            onCheckForAppUpdate = onCheckForAppUpdate,
            onAppUpdateAction = onAppUpdateAction,
            onAutoCheckChange = { enabled -> persist(currentSettings.copy(autoCheckAppUpdates = enabled), "") },
            onUpdateChannelChange = { channel -> persist(currentSettings.copy(appUpdateChannelId = channel.id), "") },
            onReplayOnboarding = { persist(currentSettings.copy(onboardingCompleted = false), "") },
            onBack = goBack,
            onMessage = { message = it },
            message = message,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun DesktopSettingsOverview(
    settings: DesktopAppSettings,
    message: String,
    contentPadding: PaddingValues,
    modifier: Modifier,
    vpnPage: Boolean,
    onNavigate: (DesktopSettingsDestination) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        SkipiSettingsHomeScreen(
        state = SkipiSettingsHomeState(
            versionLabel = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
            tunMtu = settings.tunMtu,
            localProxyPort = settings.localProxyPort.toString(),
            coreLogLevel = settings.coreLogLevel.uppercase(),
            categoryIconColor = Color(0xFF5C6DB0),
            visibleDestinations = desktopSettingsVisibleDestinations(vpnPage = vpnPage),
        ),
        padding = contentPadding,
        isWideScreen = true,
        onNavigate = { selected ->
            when (selected) {
                SkipiSettingsDestination.Appearance -> onNavigate(DesktopSettingsDestination.Appearance)
                SkipiSettingsDestination.Vpn -> onNavigate(DesktopSettingsDestination.Vpn)
                SkipiSettingsDestination.General -> onNavigate(DesktopSettingsDestination.General)
                SkipiSettingsDestination.LocalProxy -> onNavigate(DesktopSettingsDestination.LocalProxy)
                SkipiSettingsDestination.Subscriptions -> onNavigate(DesktopSettingsDestination.Subscriptions)
                SkipiSettingsDestination.NetworkAutomation -> onNavigate(DesktopSettingsDestination.NetworkAutomation)
                SkipiSettingsDestination.BackupReset -> onNavigate(DesktopSettingsDestination.BackupReset)
                SkipiSettingsDestination.SpeedTest -> onNavigate(DesktopSettingsDestination.SpeedTest)
                SkipiSettingsDestination.DnsLeakTest -> onNavigate(DesktopSettingsDestination.DnsLeakTest)
                SkipiSettingsDestination.IpInfo -> onNavigate(DesktopSettingsDestination.IpInfo)
                SkipiSettingsDestination.Logs -> onNavigate(DesktopSettingsDestination.Diagnostics)
                SkipiSettingsDestination.About -> onNavigate(DesktopSettingsDestination.About)
                else -> Unit
            }
        },
            modifier = Modifier.fillMaxSize(),
        )
        if (message.isNotBlank()) {
            Text(
                message,
                color = SkipiTheme.colors.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
    }
}

internal fun desktopAppearanceSettingsCapabilities() = AppearanceSettingsCapabilities(
    systemTheme = false,
    appIcon = false,
    wallpaper = true,
    dynamicColors = true,
    customColors = true,
    fonts = true,
    bottomBarSize = true,
    connectionLog = true,
    navigationStyle = true,
    floatingPowerButton = false,
    subscriptionSwipe = false,
)

@Composable
private fun DesktopGeneralSettings(
    settings: DesktopAppSettings,
    message: String,
    onBack: () -> Unit,
    onOpenUrlSchemes: () -> Unit,
    onOpenBackupReset: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    contentPadding: PaddingValues,
) {
    val locale = remember { DesktopApplicationLocale(Locale.getDefault(), Locale::setDefault) }
    val languageMode = settings.languageTag.toGeneralLanguageMode()
    val state = GeneralSettingsState(
        languageMode = languageMode,
        enableHaptics = settings.hapticsEnabled,
        enableTrafficStatsNotification = false,
        trafficStatsNotificationRefreshIntervalSeconds = GeneralSettingsValues.MinTrafficRefreshSeconds,
        enableResourceFileNotifications = false,
        enableDeletionConfirmation = settings.confirmDeletion,
        enableBroadcastControl = false,
        closeToBackground = settings.closeToBackground,
    )
    SkipiGeneralSettingsScreen(
        state = state,
        padding = contentPadding,
        isWideScreen = true,
        onSettingsChange = { transform ->
            val updated = transform(state)
            val languageChanged = updated.languageMode != state.languageMode
            val languageTag = if (languageChanged) updated.languageMode.toDesktopLanguageTag() else settings.languageTag
            if (languageChanged) locale.apply(languageTag)
            val next = settings.copy(
                languageTag = languageTag,
                hapticsEnabled = updated.enableHaptics,
                confirmDeletion = updated.enableDeletionConfirmation,
                closeToBackground = updated.closeToBackground,
            )
            onPersist(next, desktopText(Res.string.desktop_settings_general_saved))
            next.toGeneralSettingsState()
        },
        onBack = onBack,
        onOpenUrlSchemes = onOpenUrlSchemes,
        onOpenBackupReset = onOpenBackupReset,
        capabilities = GeneralSettingsCapabilities.Desktop,
    )
    if (message.isNotBlank()) SkipiSettingsStatusMessage(message)
}

private fun String?.toGeneralLanguageMode(): Int = when (this?.substringBefore('-')?.lowercase()) {
    "en" -> GeneralSettingsValues.LanguageEnglish
    "zh" -> GeneralSettingsValues.LanguageChinese
    "ru" -> GeneralSettingsValues.LanguageRussian
    "fa" -> GeneralSettingsValues.LanguagePersian
    else -> GeneralSettingsValues.LanguageSystem
}

private fun Int.toDesktopLanguageTag(): String? = when (this) {
    GeneralSettingsValues.LanguageEnglish -> "en"
    GeneralSettingsValues.LanguageChinese -> "zh"
    GeneralSettingsValues.LanguageRussian -> "ru"
    GeneralSettingsValues.LanguagePersian -> "fa"
    else -> null
}

private fun DesktopAppSettings.toGeneralSettingsState() = GeneralSettingsState(
    languageMode = languageTag.toGeneralLanguageMode(),
    enableHaptics = hapticsEnabled,
    enableTrafficStatsNotification = false,
    trafficStatsNotificationRefreshIntervalSeconds = GeneralSettingsValues.MinTrafficRefreshSeconds,
    enableResourceFileNotifications = false,
    enableDeletionConfirmation = confirmDeletion,
    enableBroadcastControl = false,
    closeToBackground = closeToBackground,
)

@Composable
private fun DesktopAppearanceSettings(
    settings: DesktopAppSettings,
    message: String,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    contentPadding: PaddingValues,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    LaunchedEffect(settings) { draft = settings }
    SkipiAppearanceSettingsScreen(
        state = draft.toAppearanceSettingsState(),
        padding = contentPadding,
        isWideScreen = true,
        hasCustomBackgroundPhoto = DesktopBackgroundPhoto.version >= 0L && DesktopBackgroundPhoto.exists(),
        appIconTitle = "",
        onSettingsChange = { transform ->
            val next = draft.withAppearanceState(transform(draft.toAppearanceSettingsState()))
            draft = next
            onPersist(next, desktopText(Res.string.desktop_settings_appearance_saved))
            next.toAppearanceSettingsState()
        },
        onBack = onBack,
        onChooseBackgroundPhoto = {
            DesktopBackgroundPhoto.choose()?.let { source ->
                if (DesktopBackgroundPhoto.save(source)) {
                    val next = draft.copy(backgroundStyle = AppearanceSettingValues.BackgroundStylePhoto)
                    draft = next
                    onPersist(next, desktopText(Res.string.desktop_settings_appearance_saved))
                }
            }
        },
        onRemoveBackgroundPhoto = { DesktopBackgroundPhoto.clear() },
        onRequestAppIconSelection = {},
        onColorsReset = { message -> onPersist(draft, message) },
        onCopyText = { text, confirmation ->
            runCatching {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
            }.onSuccess { onPersist(draft, confirmation) }
        },
        capabilities = desktopAppearanceSettingsCapabilities(),
        platformContent = { SkipiSettingsStatusMessage(message) },
    )
}

@Composable
private fun DesktopLocalProxySettings(
    settings: DesktopAppSettings,
    message: String,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    onClearSystemProxy: (() -> Unit)?,
    contentPadding: PaddingValues,
) {
    val supportsSystemProxy = isDesktopSystemProxySupported()
    val saved = stringResource(Res.string.desktop_settings_local_proxy_saved)
    val portInvalid = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.settings_local_proxy_port_invalid)
    val copiedTemplate = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.settings_local_proxy_copied_toast)
    val generatedToast = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.settings_local_proxy_generated_toast)
    var feedbackMessage by remember(message) { mutableStateOf(message) }
    var portText by remember(settings.localProxyPortRaw, settings.localProxyPort) {
        mutableStateOf(settings.localProxyPortRaw ?: settings.localProxyPort.toString())
    }
    var httpPortText by remember(settings.localHttpProxyPort) { mutableStateOf(settings.localHttpProxyPort.toString()) }
    val lanAddress = remember { localNetworkIpAddresses().firstOrNull() }

    val portError = when {
        settings.enableDynamicLocalProxyPort -> null
        !isValidLocalProxyPort(portText) -> portInvalid
        portText.toInt() == settings.localHttpProxyPort -> desktopText(Res.string.desktop_settings_socks_port_conflict)
        else -> null
    }
    val httpPortError = when {
        !isValidLocalProxyPort(httpPortText) -> portInvalid
        httpPortText.toInt() == settings.localProxyPort -> desktopText(Res.string.desktop_settings_http_port_conflict)
        else -> null
    }
    val displayIp = lanAddress ?: settings.localProxyListenAddress
    val sharedState = settings.toLocalProxySettingsState().copy(
        port = portText,
        httpPort = httpPortText,
        displayIp = displayIp,
        socksEndpoint = "$displayIp:${settings.localProxyPort}",
        httpEndpoint = "$displayIp:${settings.localHttpProxyPort}",
        portError = portError,
        httpPortError = httpPortError.takeIf { settings.httpInboundEnabled() && !settings.enableDynamicLocalProxyPort },
    )

    SkipiLocalProxySettingsScreen(
        state = sharedState,
        labels = rememberLocalProxySettingsLabels(),
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onDynamicPortChange = { enabled -> onPersist(settings.copy(enableDynamicLocalProxyPort = enabled), saved) },
        onPortChange = { text ->
            portText = text
            settings.withLocalProxyPort(text)?.let { next -> onPersist(next, saved) }
        },
        onHttpEnabledChange = { enabled -> onPersist(settings.withHttpInbound(enabled), saved) },
        onHttpPortChange = { text ->
            httpPortText = text
            settings.withLocalHttpProxyPort(text)?.let { next -> onPersist(next, saved) }
        },
        onListenAllInterfacesChange = { allow -> onPersist(settings.withListenAllInterfaces(allow), saved) },
        onSocksAuthModeChange = { mode -> onPersist(settings.withSocksAuthMode(mode), saved) },
        onHttpAuthModeChange = { mode -> onPersist(settings.withHttpAuthMode(mode), saved) },
        onSocksCredentialsChange = { username, password -> onPersist(settings.withSocksCredentials(username, password), saved) },
        onHttpCredentialsChange = { username, password -> onPersist(settings.withHttpCredentials(username, password), saved) },
        onGenerateSocksCredentials = { onPersist(settings.withGeneratedSocksCredentials(), generatedToast) },
        onGenerateHttpCredentials = { onPersist(settings.withGeneratedHttpCredentials(), generatedToast) },
        onCopy = { text, label ->
            copyToClipboard(text).fold(
                onSuccess = { feedbackMessage = copiedTemplate.replace("%s", label).replace("%1\$s", label) },
                onFailure = { feedbackMessage = desktopText(Res.string.desktop_settings_copy_failed, label, it.message.orEmpty()) },
            )
        },
        capabilities = LocalProxySettingsCapabilities(
            dynamicPort = true,
            httpInbound = true,
            listenAllInterfaces = true,
            authentication = true,
            credentials = true,
            lanEndpoints = true,
        ),
        // The system proxy points at the HTTP inbound, so it is offered inside the HTTP card, where the inbound is on.
        httpPlatformContent = if (supportsSystemProxy) {
            {
                SkipiSettingsSwitchRow(
                    title = stringResource(Res.string.desktop_settings_use_system_proxy),
                    summary = stringResource(Res.string.desktop_settings_use_system_proxy_summary),
                    checked = settings.useSystemProxy,
                    onCheckedChange = { enabled -> onPersist(settings.withSystemProxy(enabled, supportsSystemProxy), saved) },
                )
            }
        } else {
            null
        },
        platformContent = {
            if (supportsSystemProxy && onClearSystemProxy != null) {
                SkipiSettingsGroup(title = stringResource(Res.string.desktop_settings_system_proxy_header)) {
                    SkipiSettingsActionRow(
                        title = stringResource(Res.string.desktop_settings_reset_system_proxy),
                        summary = stringResource(Res.string.desktop_settings_reset_system_proxy_summary),
                        onClick = onClearSystemProxy,
                    )
                }
            }
            SkipiSettingsStatusMessage(feedbackMessage)
        },
    )
}

@Composable
private fun DesktopSubscriptionSettings(
    settings: DesktopAppSettings,
    message: String,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    onOpenPingSettings: () -> Unit,
    onOpenUserAgents: () -> Unit,
    contentPadding: PaddingValues,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    LaunchedEffect(settings) { draft = settings }

    SkipiSubscriptionSettingsScreen(
        state = draft.toSubscriptionSettingsState(),
        labels = rememberSubscriptionSettingsLabels(draft.subscriptionUserAgents.size),
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onFetchTimeoutChange = { seconds ->
            val next = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(fetchTimeoutSeconds = seconds))
            draft = next
            onPersist(next, desktopText(Res.string.desktop_settings_subscriptions_saved))
        },
        onOpenUserAgents = onOpenUserAgents,
        onDeviceHeadersChange = { enabled ->
            val next = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(deviceHeadersEnabled = enabled))
            draft = next
            onPersist(next, desktopText(Res.string.desktop_settings_subscriptions_saved))
        },
        onExpiryNotificationsChange = { enabled ->
            val next = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(expiryNotificationsEnabled = enabled))
            draft = next
            onPersist(next, desktopText(Res.string.desktop_settings_subscriptions_saved))
        },
        onExpiryRemindersChange = { reminders ->
            val next = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(expiryReminders = reminders))
            draft = next
            onPersist(next, desktopText(Res.string.desktop_settings_subscriptions_saved))
        },
        onOpenPingSettings = onOpenPingSettings,
        capabilities = SubscriptionSettingsCapabilities(
            fetchTimeout = true,
            userAgents = true,
            deviceHeaders = true,
            expiryNotifications = false,
            pingSettings = true,
        ),
        platformContent = {
            SkipiSettingsStatusMessage(message)
        },
    )
}

@Composable
private fun DesktopSubscriptionPingSettings(
    settings: DesktopAppSettings,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    contentPadding: PaddingValues,
) {
    SkipiSubscriptionPingSettingsScreen(
        state = SubscriptionPingPageState(
            mode = settings.subscriptionPingMode,
            concurrency = settings.subscriptionPingConcurrency,
            timeoutMillis = settings.subscriptionPingTimeoutMillis,
            url = settings.subscriptionPingUrl,
        ),
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onModeChange = { value -> onPersist(settings.copy(subscriptionPingMode = value), desktopText(Res.string.desktop_settings_ping_saved)) },
        onConcurrencyChange = { value -> onPersist(settings.copy(subscriptionPingConcurrency = value), desktopText(Res.string.desktop_settings_ping_saved)) },
        onTimeoutMillisChange = { value -> onPersist(settings.copy(subscriptionPingTimeoutMillis = value), desktopText(Res.string.desktop_settings_ping_saved)) },
        onUrlChange = { value -> onPersist(settings.copy(subscriptionPingUrl = value.trim()), desktopText(Res.string.desktop_settings_ping_saved)) },
        onPresetSelected = {},
    )
}

@Composable
private fun DesktopSubscriptionUserAgents(
    settings: DesktopAppSettings,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    contentPadding: PaddingValues,
) {
    SubscriptionUserAgentsScreen(
        state = SubscriptionUserAgentsState(settings.subscriptionUserAgents),
        padding = contentPadding,
        isWideScreen = true,
        onValuesChange = { values ->
            onPersist(settings.copy(subscriptionUserAgents = values), desktopText(Res.string.desktop_settings_user_agents_saved))
        },
        onBack = onBack,
    )
}

@Composable
private fun DesktopLogsSettings(
    settings: DesktopAppSettings,
    isTunnelRunning: Boolean,
    message: String,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    onMessage: (String) -> Unit,
    onPruneLogs: (Int) -> Unit,
    onOpenCoreLogs: () -> Unit,
    onOpenAccessLogs: () -> Unit,
    contentPadding: PaddingValues,
) {
    val saved = stringResource(Res.string.desktop_settings_logs_saved)
    SkipiLogsSettingsScreen(
        state = LogsSettingsState(
            coreLogLevelIndex = DesktopCoreLogLevels.indexOf(settings.coreLogLevel).coerceAtLeast(0),
            retentionDays = settings.logRetentionDays,
            accessLogEnabled = settings.enableAccessLog,
        ),
        labels = LogsSettingsLabels(
            screenTitle = stringResource(Res.string.settings_category_logs),
            optionsSectionTitle = stringResource(Res.string.settings_logs),
            logLevelTitle = stringResource(Res.string.settings_log_level),
            logLevels = DesktopCoreLogLevels,
            retentionTitle = stringResource(Res.string.settings_log_retention),
            retentionSummary = stringResource(Res.string.settings_log_retention_summary),
            retentionOptions = DesktopLogRetentionDays.map { days -> stringResource(logRetentionLabel(days)) },
            retentionDays = DesktopLogRetentionDays,
            accessLogTitle = stringResource(Res.string.settings_record_access_log),
            viewersSectionTitle = stringResource(Res.string.settings_access_logs),
            coreLogsTitle = stringResource(Res.string.settings_core_logs),
            accessLogsTitle = stringResource(Res.string.settings_access_logs),
            logcatTitle = stringResource(Res.string.settings_logcat),
            feedbackSectionTitle = stringResource(Res.string.settings_feedback),
            bugReportTitle = stringResource(Res.string.about_bug_report),
            bugReportSummary = stringResource(Res.string.about_bug_report_summary),
        ),
        padding = contentPadding,
        isWideScreen = true,
        showAccessLogs = settings.enableAccessLog,
        onBack = onBack,
        onLogLevelChange = { index ->
            DesktopCoreLogLevels.getOrNull(index)?.let { level -> onPersist(settings.copy(coreLogLevel = level), saved) }
        },
        onRetentionDaysChange = { days ->
            onPersist(settings.copy(logRetentionDays = days), saved)
            onPruneLogs(days)
        },
        onAccessLogChange = { enabled -> onPersist(settings.copy(enableAccessLog = enabled), saved) },
        onOpenCoreLogs = onOpenCoreLogs,
        onOpenAccessLogs = onOpenAccessLogs,
        onOpenLogcat = {},
        onOpenBugReport = { browse(SkipiBugReportUri, onMessage) },
        // Logcat is an Android-only log source.
        capabilities = LogsSettingsCapabilities(logcat = false),
        platformContent = {
            DesktopDiagnosticsContent(
                isTunnelRunning = isTunnelRunning,
                onMessage = onMessage,
                message = message,
            )
        },
    )
}

private fun logRetentionLabel(days: Int): StringResource = when (days) {
    1 -> Res.string.settings_log_retention_1_day
    3 -> Res.string.settings_log_retention_3_days
    7 -> Res.string.settings_log_retention_7_days
    14 -> Res.string.settings_log_retention_14_days
    30 -> Res.string.settings_log_retention_30_days
    else -> Res.string.settings_log_retention_unlimited
}

@Composable
private fun DesktopDiagnosticsContent(
    isTunnelRunning: Boolean,
    onMessage: (String) -> Unit,
    message: String,
) {
    var runtimeCheck by remember { mutableStateOf(DesktopCoreRuntimes.discover()) }
    val settingsPath = remember { DesktopSettingsLibraries.defaultPath() }
    val dataDirectory = remember { settingsPath.parent }
    val runtime = runtimeCheck.getOrNull()
    var showRecentLogs by remember { mutableStateOf(false) }
    var recentLogs by remember { mutableStateOf(DesktopLogger.recentEntries(30)) }
    val logFile = remember { DesktopLogger.logFile() }
    val logDir = remember { DesktopLogger.logDirectory() }
    val logSize = remember(logFile) { runCatching { Files.size(logFile) }.getOrNull() }

    SkipiSettingsGroup(title = stringResource(Res.string.desktop_settings_state_header)) {
        SkipiSettingsInfoRow(
            title = if (isTunnelRunning) stringResource(Res.string.desktop_settings_tunnel_running) else stringResource(Res.string.desktop_settings_tunnel_stopped),
            summary = if (isTunnelRunning) {
                stringResource(Res.string.desktop_settings_tunnel_running_summary)
            } else {
                stringResource(Res.string.desktop_settings_tunnel_stopped_summary)
            },
        )
        SkipiSettingsInfoRow(
            title = if (runtime != null) stringResource(Res.string.desktop_settings_core_found) else stringResource(Res.string.desktop_settings_core_not_found),
            summary = runtime?.directory?.toString()
                ?: runtimeCheck.exceptionOrNull()?.message.orEmpty().ifBlank { stringResource(Res.string.desktop_settings_check_app_resources) },
        )
        SkipiSettingsActionRow(
            title = stringResource(Res.string.desktop_settings_recheck_runtime),
            summary = stringResource(Res.string.desktop_settings_recheck_runtime_summary),
            onClick = { runtimeCheck = DesktopCoreRuntimes.discover() },
        )
    }
    SkipiSettingsGroup(title = stringResource(Res.string.desktop_settings_app_log_header)) {
        SkipiSettingsActionRow(
            title = stringResource(Res.string.desktop_settings_open_logs_folder),
            summary = logDir.toString(),
            onClick = {
                val result = openDirectory(logDir)
                onMessage(result.fold({ desktopText(Res.string.desktop_settings_logs_folder_opened) }, { desktopText(Res.string.desktop_settings_folder_open_failed, it.message.orEmpty()) }))
            },
        )
        SkipiSettingsActionRow(
            title = if (showRecentLogs) stringResource(Res.string.desktop_settings_hide_log) else stringResource(Res.string.desktop_settings_show_recent_log),
            summary = logSize?.let { stringResource(Res.string.desktop_settings_log_file_size, it) } ?: stringResource(Res.string.desktop_settings_log_not_created),
            onClick = {
                recentLogs = DesktopLogger.recentEntries(40)
                showRecentLogs = !showRecentLogs
            },
        )
        if (showRecentLogs) {
            Text(
                text = recentLogs.ifEmpty { listOf(stringResource(Res.string.desktop_settings_log_empty)) }.joinToString("\n"),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SkipiSettingsActionRow(
                title = stringResource(Res.string.common_refresh),
                summary = "",
                onClick = { recentLogs = DesktopLogger.recentEntries(40) },
            )
        }
    }
    SkipiSettingsGroup(title = stringResource(Res.string.desktop_settings_data_header)) {
        SkipiSettingsActionRow(
            title = stringResource(Res.string.desktop_settings_data_dir),
            summary = dataDirectory?.toString().orEmpty(),
            onClick = {
                val result = dataDirectory?.let(::openDirectory)
                    ?: Result.failure(IllegalStateException(desktopText(Res.string.desktop_settings_data_dir_not_found)))
                onMessage(result.fold({ desktopText(Res.string.desktop_settings_data_dir_opened) }, { desktopText(Res.string.desktop_settings_data_dir_open_failed, it.message.orEmpty()) }))
            },
        )
        SkipiSettingsActionRow(
            title = stringResource(Res.string.desktop_settings_copy_settings_path),
            summary = settingsPath.toString(),
            onClick = {
                copyToClipboard(settingsPath.toString()).fold(
                    onSuccess = { onMessage(desktopText(Res.string.desktop_settings_path_copied)) },
                    onFailure = { onMessage(desktopText(Res.string.desktop_settings_path_copy_failed, it.message.orEmpty())) },
                )
            },
        )
    }
    SkipiSettingsStatusMessage(message)
}

@Composable
private fun DesktopAboutSettings(
    settings: DesktopAppSettings,
    appUpdateState: AppUpdateState,
    onCheckForAppUpdate: suspend () -> DesktopAppUpdateCheck,
    onAppUpdateAction: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
    onUpdateChannelChange: (AppUpdateChannel) -> Unit,
    onBack: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onMessage: (String) -> Unit,
    message: String,
    contentPadding: PaddingValues,
) {
    val settingsPath = remember { DesktopSettingsLibraries.defaultPath() }
    SkipiAboutScreen(
        labels = rememberAboutSettingsLabels(),
        runtime = AboutRuntimeInfo(
            appName = ProjectInfo.PROJECT_NAME,
            appVersion = "${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
            skipiCoreVersion = ProjectInfo.SKIPI_CORE_VERSION,
            xrayCoreVersion = ProjectInfo.XRAY_CORE_VERSION,
            hevTunnelVersion = "",
        ),
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onOpenCoreInfo = { browse(SkipiCoreSourceUri, onMessage) },
        onReplayOnboarding = onReplayOnboarding,
        onOpenTelegram = { browse(SkipiTelegramChannelUri, onMessage) },
        onOpenBugReport = { browse(SkipiBugReportUri, onMessage) },
        onOpenSource = { browse(SkipiSourceUri, onMessage) },
        updatesContent = {
            val scope = rememberCoroutineScope()
            var checking by remember { mutableStateOf(false) }
            var result by remember { mutableStateOf<DesktopAppUpdateCheck?>(null) }
            val checkingText = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.app_update_checking)
            val latestText = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.app_update_already_latest)
            val failedText = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.app_update_check_failed)
            val availableText = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.app_update_available_title)
            val downloadingText = org.jetbrains.compose.resources.stringResource(app.skipi.ui.resources.Res.string.app_update_downloading_action)
            val installedPackage = remember { detectDesktopUpdatePackage(DesktopRuntimeFacts.current()) }
            val known0 = appUpdateState.availableUpdate
            val actionText = known0?.let { desktopUpdateActionLabel(it, appUpdateState.status == features.updater.AppUpdateDownloadStatus.READY_TO_INSTALL, installedPackage) }
            SkipiAppUpdatesSettingsCard(
                autoCheckEnabled = settings.autoCheckAppUpdates,
                onAutoCheckChange = onAutoCheckChange,
                nightInstallEnabled = null,
                onNightInstallChange = {},
                channel = settings.appUpdateChannel,
                onChannelChange = { channel ->
                    result = null
                    onUpdateChannelChange(channel)
                },
                checkSummary = when {
                    checking -> checkingText
                    known0 != null -> when (appUpdateState.status) {
                        features.updater.AppUpdateDownloadStatus.QUEUED,
                        features.updater.AppUpdateDownloadStatus.DOWNLOADING -> {
                            val percent = if (appUpdateState.totalBytes > 0L) " ${appUpdateState.downloadedBytes * 100 / appUpdateState.totalBytes}%" else ""
                            "$downloadingText$percent"
                        }
                        features.updater.AppUpdateDownloadStatus.FAILED ->
                            "$availableText v${known0.versionName}: ${appUpdateState.error.orEmpty()}"
                        else -> "$availableText v${known0.versionName} · $actionText"
                    }
                    result == DesktopAppUpdateCheck.UpToDate -> latestText
                    result == DesktopAppUpdateCheck.Failed -> failedText
                    else -> null
                },
                onCheckNow = {
                    val known = appUpdateState.availableUpdate
                    if (known != null) {
                        onAppUpdateAction()
                    } else if (!checking) {
                        checking = true
                        scope.launch {
                            result = onCheckForAppUpdate()
                            checking = false
                        }
                    }
                },
            )
        },
        capabilities = AboutSettingsCapabilities(),
        platformContent = {
            SkipiSettingsGroup(title = stringResource(Res.string.desktop_settings_storage_header)) {
                SkipiSettingsInfoRow(stringResource(Res.string.desktop_settings_storage), settingsPath.toString())
            }
            SkipiSettingsStatusMessage(message)
        },
    )
}

internal fun browse(uri: String, onMessage: (String) -> Unit) {
    runCatching {
        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            desktopText(Res.string.desktop_open_link_unsupported)
        }
        Desktop.getDesktop().browse(java.net.URI(uri))
    }.onFailure { onMessage(desktopText(Res.string.desktop_settings_link_open_failed, it.message.orEmpty())) }
}

private fun openDirectory(path: java.nio.file.Path): Result<Unit> = runCatching {
    Files.createDirectories(path)
    check(Desktop.isDesktopSupported()) { desktopText(Res.string.desktop_settings_folder_open_unsupported) }
    Desktop.getDesktop().open(path.toFile())
}

internal fun copyToClipboard(value: String): Result<Unit> = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
}
