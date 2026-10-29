// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import app.skipi.ui.networkautomation.NetworkAutomationServerOption
import app.skipi.ui.networkautomation.SkipiNetworkAutomationScreen
import features.networkautomation.policy.NetworkAutomationRules
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_copied
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import app.skipi.ui.settings.SkipiSettingsTextField
import app.skipi.ui.settings.AboutRuntimeInfo
import app.skipi.ui.settings.AboutSettingsCapabilities
import app.skipi.ui.settings.AboutSettingsLabels
import app.skipi.ui.settings.AppearanceSettingsCapabilities
import app.skipi.ui.settings.GeneralSettingsCapabilities
import app.skipi.ui.settings.GeneralSettingsState
import app.skipi.ui.settings.GeneralSettingsValues
import app.skipi.ui.settings.IntegrationSettingsCapabilities
import app.skipi.ui.settings.IntegrationSettingsLabels
import app.skipi.ui.settings.IntegrationSettingsState
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
import app.skipi.ui.settings.rememberIntegrationSettingsLabels
import app.skipi.ui.settings.SkipiAppearanceSettingsScreen
import app.skipi.ui.settings.SkipiGeneralSettingsScreen
import app.skipi.ui.settings.SkipiIntegrationSettingsScreen
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


private enum class DesktopSettingsDestination {
    Overview, General, Appearance, LocalProxy, Subscriptions, SubscriptionPing, SubscriptionUserAgents,
    NetworkAutomation, BackupReset, SpeedTest, DnsLeakTest, IpInfo, UrlSchemes, Diagnostics, CoreLogs, AccessLogs, About,
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
    onClearSystemProxy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var destination by remember { mutableStateOf(DesktopSettingsDestination.Overview) }
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
            result.onSuccess { message = successMessage }
                .onFailure { error -> message = "Не удалось сохранить настройки: ${error.message.orEmpty()}" }
        }
    }

    val automationSavedMessage = "Правила сети сохранены."
    when (destination) {
        DesktopSettingsDestination.Overview -> DesktopSettingsOverview(
            settings = currentSettings,
            message = message,
            contentPadding = contentPadding,
            modifier = modifier,
            onNavigate = { destination = it },
        )
        DesktopSettingsDestination.Appearance -> DesktopAppearanceSettings(
            settings = currentSettings,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onPersist = ::persist,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.General -> DesktopGeneralSettings(
            settings = currentSettings,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onOpenUrlSchemes = { destination = DesktopSettingsDestination.UrlSchemes },
            onOpenBackupReset = { destination = DesktopSettingsDestination.BackupReset },
            onPersist = ::persist,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.LocalProxy -> DesktopLocalProxySettings(
            settings = currentSettings,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onPersist = ::persist,
            onClearSystemProxy = onClearSystemProxy,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.Subscriptions -> DesktopSubscriptionSettings(
            settings = currentSettings,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onPersist = ::persist,
            onOpenPingSettings = { destination = DesktopSettingsDestination.SubscriptionPing },
            onOpenUserAgents = { destination = DesktopSettingsDestination.SubscriptionUserAgents },
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.SubscriptionPing -> DesktopSubscriptionPingSettings(
            settings = currentSettings,
            onBack = { destination = DesktopSettingsDestination.Subscriptions },
            onPersist = ::persist,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.SubscriptionUserAgents -> DesktopSubscriptionUserAgents(
            settings = currentSettings,
            onBack = { destination = DesktopSettingsDestination.Subscriptions },
            onPersist = ::persist,
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.BackupReset -> DesktopBackupResetSettings(
            host = backupHost,
            appVersionName = ProjectInfo.VERSION_NAME,
            appVersionCode = ProjectInfo.VERSION_CODE,
            padding = contentPadding,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onMessage = { message = it },
        )
        DesktopSettingsDestination.NetworkAutomation -> SkipiNetworkAutomationScreen(
            networkAutomationEnabled = currentSettings.enableNetworkAutomation,
            onDemandEnabled = currentSettings.enableOnDemandVpn,
            rules = currentSettings.networkAutomationRules,
            servers = automationServers,
            padding = contentPadding,
            isWideScreen = true,
            onBack = { destination = DesktopSettingsDestination.Overview },
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
            // Desktop reads no Wi-Fi name, so the editor offers no "insert current Wi-Fi" action.
            onRequestCurrentWifi = null,
            footer = {
                SkipiSettingsStatusMessage("Desktop сохраняет правила, но не отслеживает смену сети: они применяются только в Android.")
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
            onBack = { destination = DesktopSettingsDestination.General },
            onCopyCommand = { command ->
                copyToClipboard(command).onSuccess { message = copiedMessage }
            },
        )
        DesktopSettingsDestination.Diagnostics -> DesktopLogsSettings(
            settings = currentSettings,
            isTunnelRunning = isTunnelRunning,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
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
            onBack = { destination = DesktopSettingsDestination.Diagnostics },
            onClear = coreLogs::clearCore,
            onMessage = { message = it },
        )
        DesktopSettingsDestination.AccessLogs -> DesktopLogViewer(
            title = stringResource(Res.string.access_logs_title),
            repository = coreLogs.access,
            padding = contentPadding,
            onBack = { destination = DesktopSettingsDestination.Diagnostics },
            onClear = coreLogs::clearAccess,
            onMessage = { message = it },
        )
        DesktopSettingsDestination.About -> DesktopAboutSettings(
            onBack = { destination = DesktopSettingsDestination.Overview },
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
    onNavigate: (DesktopSettingsDestination) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        SkipiSettingsHomeScreen(
        state = SkipiSettingsHomeState(
            versionLabel = "v${ProjectInfo.VERSION_NAME} (${ProjectInfo.VERSION_CODE})",
            tunMtu = "—",
            localProxyPort = settings.localProxyPort.toString(),
            coreLogLevel = settings.coreLogLevel.uppercase(),
            categoryIconColor = Color(0xFF5C6DB0),
            visibleDestinations = desktopSettingsVisibleDestinations(),
        ),
        padding = contentPadding,
        onNavigate = { selected ->
            when (selected) {
                SkipiSettingsDestination.Appearance -> onNavigate(DesktopSettingsDestination.Appearance)
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
    dynamicColors = false,
    customColors = true,
    fonts = true,
    bottomBarSize = false,
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
            )
            onPersist(next, "Общие настройки сохранены.")
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
        currentKeyColor = SkipiTheme.colors.accent,
        hasCustomBackgroundPhoto = DesktopBackgroundPhoto.version >= 0L && DesktopBackgroundPhoto.exists(),
        appIconTitle = "",
        onSettingsChange = { transform ->
            val next = draft.withAppearanceState(transform(draft.toAppearanceSettingsState()))
            draft = next
            onPersist(next, "Настройки оформления сохранены.")
            next.toAppearanceSettingsState()
        },
        onBack = onBack,
        onChooseBackgroundPhoto = {
            DesktopBackgroundPhoto.choose()?.let { source ->
                if (DesktopBackgroundPhoto.save(source)) {
                    val next = draft.copy(backgroundStyle = AppearanceSettingValues.BackgroundStylePhoto)
                    draft = next
                    onPersist(next, "Настройки оформления сохранены.")
                }
            }
        },
        onRemoveBackgroundPhoto = { DesktopBackgroundPhoto.clear() },
        onRequestAppIconSelection = {},
        onColorsReset = { message -> onPersist(draft, message) },
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
    val saved = "Параметры локального прокси сохранены."
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
        portText.toInt() == settings.localHttpProxyPort -> "Порт SOCKS5 должен отличаться от порта HTTP-прокси."
        else -> null
    }
    val httpPortError = when {
        !isValidLocalProxyPort(httpPortText) -> portInvalid
        httpPortText.toInt() == settings.localProxyPort -> "HTTP-порт должен отличаться от порта SOCKS5."
        else -> null
    }
    val displayIp = lanAddress ?: settings.localProxyListenAddress
    val sharedState = settings.toLocalProxySettingsState().copy(
        port = portText,
        displayIp = displayIp,
        socksEndpoint = "$displayIp:${settings.localProxyPort}",
        httpEndpoint = "$displayIp:${settings.localHttpProxyPort}",
        portError = portError,
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
        onListenAllInterfacesChange = { allow -> onPersist(settings.withListenAllInterfaces(allow), saved) },
        onAuthenticationChange = { enabled -> onPersist(settings.copy(enableLocalProxyAuth = enabled), saved) },
        onGenerateCredentials = {
            onPersist(
                settings.copy(
                    localProxyUsername = generateRandomProxyCredential(),
                    localProxyPassword = generateRandomProxyCredential(),
                ),
                generatedToast,
            )
        },
        onCopy = { text, label ->
            copyToClipboard(text).fold(
                onSuccess = { feedbackMessage = copiedTemplate.replace("%s", label).replace("%1\$s", label) },
                onFailure = { feedbackMessage = "Не удалось скопировать $label: ${it.message.orEmpty()}" },
            )
        },
        capabilities = LocalProxySettingsCapabilities(
            dynamicPort = true,
            listenAllInterfaces = true,
            authentication = true,
            credentials = true,
            lanEndpoints = true,
        ),
        platformContent = {
            if (supportsSystemProxy) {
                SkipiSettingsGroup(title = "СИСТЕМНЫЙ ПРОКСИ") {
                    SkipiSettingsSwitchRow(
                        title = "Использовать системный прокси",
                        summary = "SKIPI временно настраивает системный HTTP/SOCKS прокси во время подключения.",
                        checked = settings.useSystemProxy,
                        onCheckedChange = { enabled -> onPersist(settings.withSystemProxy(enabled, supportsSystemProxy), saved) },
                    )
                    SkipiSettingsNumberField(
                        value = httpPortText,
                        onValueChange = { text ->
                            httpPortText = sanitizeLocalProxyPort(text)
                            settings.withLocalHttpProxyPort(httpPortText)?.let { next -> onPersist(next, saved) }
                        },
                        label = "Порт HTTP-прокси",
                        error = httpPortError,
                    )
                    if (onClearSystemProxy != null) {
                        SkipiSettingsActionRow(
                            title = "Сбросить системный прокси",
                            summary = "Вернуть прежние настройки прокси Windows.",
                            onClick = onClearSystemProxy,
                        )
                    }
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
    var userAgent by remember { mutableStateOf(settings.subscriptionUserAgent) }
    val hwid = remember(settings.installationUuid) { DesktopDeviceIdentity.computeHwid(settings.installationUuid) }
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
            onPersist(next, "Параметры подписок сохранены.")
        },
        onOpenUserAgents = onOpenUserAgents,
        onDeviceHeadersChange = { enabled ->
            val next = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(deviceHeadersEnabled = enabled))
            draft = next
            onPersist(next, "Параметры подписок сохранены.")
        },
        onDeletionConfirmationChange = { enabled ->
            val next = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(deletionConfirmationEnabled = enabled))
            draft = next
            onPersist(next, "Параметры подписок сохранены.")
        },
        onExpiryNotificationsChange = { enabled ->
            val next = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(expiryNotificationsEnabled = enabled))
            draft = next
            onPersist(next, "Параметры подписок сохранены.")
        },
        onExpiryRemindersChange = { reminders ->
            val next = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(expiryReminders = reminders))
            draft = next
            onPersist(next, "Параметры подписок сохранены.")
        },
        onOpenPingSettings = onOpenPingSettings,
        capabilities = SubscriptionSettingsCapabilities(
            fetchTimeout = true,
            userAgents = true,
            deviceHeaders = true,
            deletionConfirmation = true,
            expiryNotifications = false,
            pingSettings = true,
        ),
        platformContent = {
            SkipiSettingsGroup(title = "ЗАПАСНОЙ USER-AGENT И УСТРОЙСТВО") {
                SkipiSettingsTextField(
                    value = userAgent,
                    onValueChange = { text ->
                        userAgent = text
                        onPersist(
                            draft.copy(subscriptionUserAgent = text.trim().ifBlank { DefaultDesktopSubscriptionUserAgent }),
                            "Параметры подписок сохранены.",
                        )
                    },
                    label = "User-Agent, если у подписки не задан свой",
                )
                SkipiSettingsTextField(
                    value = hwid,
                    onValueChange = {},
                    label = "Идентификатор устройства (HWID)",
                    readOnly = true,
                )
            }
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
        onModeChange = { value -> onPersist(settings.copy(subscriptionPingMode = value), "Параметры проверки ping сохранены.") },
        onConcurrencyChange = { value -> onPersist(settings.copy(subscriptionPingConcurrency = value), "Параметры проверки ping сохранены.") },
        onTimeoutMillisChange = { value -> onPersist(settings.copy(subscriptionPingTimeoutMillis = value), "Параметры проверки ping сохранены.") },
        onUrlChange = { value -> onPersist(settings.copy(subscriptionPingUrl = value.trim()), "Параметры проверки ping сохранены.") },
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
            onPersist(settings.copy(subscriptionUserAgents = values), "User-Agent сохранены.")
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
    val saved = "Параметры журналов сохранены."
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

    SkipiSettingsGroup(title = "СОСТОЯНИЕ DESKTOP") {
        SkipiSettingsInfoRow(
            title = if (isTunnelRunning) "Локальный туннель запущен" else "Локальный туннель остановлен",
            summary = if (isTunnelRunning) {
                "SKIPI Core работает в процессе приложения. Изменения применятся после переподключения."
            } else {
                "Выберите сервер на главном экране и подключитесь для запуска SKIPI Core."
            },
        )
        SkipiSettingsInfoRow(
            title = if (runtime != null) "SKIPI Core найден" else "SKIPI Core не найден",
            summary = runtime?.directory?.toString()
                ?: runtimeCheck.exceptionOrNull()?.message.orEmpty().ifBlank { "Проверьте ресурсы установленного приложения." },
        )
        SkipiSettingsActionRow(
            title = "Проверить runtime снова",
            summary = "Найти библиотеку SKIPI Core и файлы geoip.dat/geosite.dat.",
            onClick = { runtimeCheck = DesktopCoreRuntimes.discover() },
        )
    }
    SkipiSettingsGroup(title = "ЖУРНАЛ ПРИЛОЖЕНИЯ") {
        SkipiSettingsActionRow(
            title = "Открыть папку с логами",
            summary = logDir.toString(),
            onClick = {
                val result = openDirectory(logDir)
                onMessage(result.fold({ "Папка журналов открыта." }, { "Не удалось открыть папку: ${it.message.orEmpty()}" }))
            },
        )
        SkipiSettingsActionRow(
            title = if (showRecentLogs) "Скрыть журнал" else "Показать последние записи журнала",
            summary = logSize?.let { "Размер файла: $it байт" } ?: "Файл журнала ещё не создан",
            onClick = {
                recentLogs = DesktopLogger.recentEntries(40)
                showRecentLogs = !showRecentLogs
            },
        )
        if (showRecentLogs) {
            Text(
                text = recentLogs.ifEmpty { listOf("Записей пока нет") }.joinToString("\n"),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                lineHeight = 15.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SkipiSettingsActionRow(
                title = "Обновить",
                summary = "",
                onClick = { recentLogs = DesktopLogger.recentEntries(40) },
            )
        }
    }
    SkipiSettingsGroup(title = "ДАННЫЕ") {
        SkipiSettingsActionRow(
            title = "Каталог данных SKIPI",
            summary = dataDirectory?.toString().orEmpty(),
            onClick = {
                val result = dataDirectory?.let(::openDirectory)
                    ?: Result.failure(IllegalStateException("Каталог данных не найден."))
                onMessage(result.fold({ "Каталог данных открыт." }, { "Не удалось открыть каталог: ${it.message.orEmpty()}" }))
            },
        )
        SkipiSettingsActionRow(
            title = "Скопировать путь к settings.json",
            summary = settingsPath.toString(),
            onClick = {
                copyToClipboard(settingsPath.toString()).fold(
                    onSuccess = { onMessage("Путь к настройкам скопирован.") },
                    onFailure = { onMessage("Не удалось скопировать путь: ${it.message.orEmpty()}") },
                )
            },
        )
    }
    SkipiSettingsStatusMessage(message)
}

@Composable
private fun DesktopAboutSettings(
    onBack: () -> Unit,
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
        onReplayOnboarding = {},
        onOpenTelegram = { browse(SkipiTelegramChannelUri, onMessage) },
        onOpenBugReport = { browse(SkipiBugReportUri, onMessage) },
        onOpenSource = { browse(SkipiSourceUri, onMessage) },
        updatesContent = {},
        capabilities = AboutSettingsCapabilities(updates = false, replayOnboarding = false),
        platformContent = {
            SkipiSettingsGroup(title = "ХРАНИЛИЩЕ") {
                SkipiSettingsInfoRow("Хранилище", settingsPath.toString())
            }
            SkipiSettingsStatusMessage(message)
        },
    )
}

private fun browse(uri: String, onMessage: (String) -> Unit) {
    runCatching {
        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "Открытие ссылок не поддерживается системой."
        }
        Desktop.getDesktop().browse(java.net.URI(uri))
    }.onFailure { onMessage("Не удалось открыть ссылку: ${it.message.orEmpty()}") }
}

private fun openDirectory(path: java.nio.file.Path): Result<Unit> = runCatching {
    Files.createDirectories(path)
    check(Desktop.isDesktopSupported()) { "Открытие папки не поддерживается системой." }
    Desktop.getDesktop().open(path.toFile())
}

private fun copyToClipboard(value: String): Result<Unit> = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
}
