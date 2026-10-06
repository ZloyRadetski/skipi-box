// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

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
import app.skipi.ui.settings.AboutRuntimeInfo
import app.skipi.ui.settings.AboutSettingsCapabilities
import app.skipi.ui.settings.AboutSettingsLabels
import app.skipi.ui.settings.AppearanceSettingsCapabilities
import app.skipi.ui.settings.IntegrationSettingsCapabilities
import app.skipi.ui.settings.IntegrationSettingsLabels
import app.skipi.ui.settings.IntegrationSettingsState
import app.skipi.ui.settings.LocalProxySettingsCapabilities
import app.skipi.ui.settings.LocalProxySettingsLabels
import app.skipi.ui.settings.LogsSettingsCapabilities
import app.skipi.ui.settings.LogsSettingsLabels
import app.skipi.ui.settings.LogsSettingsState
import app.skipi.ui.settings.SkipiAboutScreen
import app.skipi.ui.settings.SkipiAppearanceSettingsScreen
import app.skipi.ui.settings.SkipiIntegrationSettingsScreen
import app.skipi.ui.settings.SkipiLocalProxySettingsScreen
import app.skipi.ui.settings.SkipiLogsSettingsScreen
import app.skipi.ui.settings.SkipiSettingsDestination
import app.skipi.ui.settings.SkipiSettingsHomeScreen
import app.skipi.ui.settings.SkipiSettingsHomeState
import app.skipi.ui.settings.SkipiSubscriptionSettingsScreen
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

private val SettingsSurface = Color(0xFF202126)
private val SettingsBorder = Color(0xFF3C3E45)
private val SettingsText = Color(0xFFF4F4F6)
private val SettingsMuted = Color(0xFFA2A5AF)
private val SettingsGreen = Color(0xFF58D27A)
private val SettingsRed = Color(0xFFFF5B62)

private enum class DesktopSettingsDestination {
    Overview, Appearance, LocalProxy, Subscriptions, Integration, Diagnostics, About,
}

/** Desktop host for shared settings pages and operations owned by the desktop platform. */
@Composable
internal fun DesktopSettingsScreen(
    settings: DesktopAppSettings,
    onSettingsChange: (DesktopAppSettings) -> Unit,
    contentPadding: PaddingValues,
    isTunnelRunning: Boolean = false,
    onClearSystemProxy: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var destination by remember { mutableStateOf(DesktopSettingsDestination.Overview) }
    var message by remember { mutableStateOf("") }
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
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.Integration -> DesktopIntegrationSettings(
            onBack = { destination = DesktopSettingsDestination.Overview },
            onMessage = { message = it },
            contentPadding = contentPadding,
        )
        DesktopSettingsDestination.Diagnostics -> DesktopLogsSettings(
            settings = currentSettings,
            isTunnelRunning = isTunnelRunning,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onPersist = ::persist,
            onMessage = { message = it },
            contentPadding = contentPadding,
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
            versionLabel = "Desktop",
            tunMtu = "—",
            localProxyPort = settings.localProxyPort.toString(),
            coreLogLevel = settings.coreLogLevel.uppercase(),
            categoryIconColor = Color(0xFF5C6DB0),
            visibleDestinations = setOf(
                SkipiSettingsDestination.Appearance,
                SkipiSettingsDestination.LocalProxy,
                SkipiSettingsDestination.Subscriptions,
                SkipiSettingsDestination.Integration,
                SkipiSettingsDestination.Logs,
                SkipiSettingsDestination.About,
            ),
        ),
        padding = contentPadding,
        onNavigate = { selected ->
            when (selected) {
                SkipiSettingsDestination.Appearance -> onNavigate(DesktopSettingsDestination.Appearance)
                SkipiSettingsDestination.LocalProxy -> onNavigate(DesktopSettingsDestination.LocalProxy)
                SkipiSettingsDestination.Subscriptions -> onNavigate(DesktopSettingsDestination.Subscriptions)
                SkipiSettingsDestination.Integration -> onNavigate(DesktopSettingsDestination.Integration)
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
                color = SettingsMuted,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
    }
}

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
        hasCustomBackgroundPhoto = false,
        appIconTitle = "",
        onSettingsChange = { transform ->
            val next = draft.withAppearanceState(transform(draft.toAppearanceSettingsState()))
            draft = next
            onPersist(next, "Настройки оформления сохранены.")
            next.toAppearanceSettingsState()
        },
        onBack = onBack,
        onChooseBackgroundPhoto = {},
        onRemoveBackgroundPhoto = {},
        onRequestAppIconSelection = {},
        onColorsReset = {},
        capabilities = AppearanceSettingsCapabilities(
            systemTheme = false,
            appIcon = false,
            wallpaper = false,
            dynamicColors = false,
            customColors = false,
            fonts = false,
            bottomBarSize = false,
        ),
        platformContent = { SettingsStatusMessage(message) },
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
    var draft by remember(settings) { mutableStateOf(settings) }
    var portText by remember(settings.localProxyPort) { mutableStateOf(settings.localProxyPort.toString()) }
    var httpPortText by remember(settings.localHttpProxyPort) { mutableStateOf(settings.localHttpProxyPort.toString()) }
    var listenAddress by remember(settings.localProxyListenAddress) { mutableStateOf(settings.localProxyListenAddress) }
    var useSystemProxy by remember(settings.useSystemProxy) { mutableStateOf(settings.useSystemProxy) }
    var logLevel by remember(settings.coreLogLevel) { mutableStateOf(settings.coreLogLevel) }
    var logLevelExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(settings) { draft = settings }

    val port = portText.toIntOrNull()
    val portError = when {
        portText.isBlank() -> "Укажите порт SOCKS5."
        port == null || port !in 1..65_535 -> "Порт должен быть в диапазоне 1–65535."
        else -> null
    }
    val httpPort = httpPortText.toIntOrNull()
    val httpPortError = when {
        httpPortText.isBlank() -> "Укажите порт HTTP-прокси."
        httpPort == null || httpPort !in 1..65_535 -> "Порт должен быть в диапазоне 1–65535."
        httpPort == port -> "HTTP-порт должен отличаться от порта SOCKS5."
        else -> null
    }
    val addressError = if (listenAddress.isBlank()) "Укажите адрес прослушивания." else null
    val sharedState = draft.toLocalProxySettingsState().copy(
        port = portText,
        listenAllInterfaces = listenAddress.trim() != "127.0.0.1",
        portError = portError,
    )

    SkipiLocalProxySettingsScreen(
        state = sharedState,
        labels = LocalProxySettingsLabels(
            screenTitle = "Локальный прокси",
            networkSectionTitle = "ПАРАМЕТРЫ СЕТИ",
            dynamicPortTitle = "Динамический порт",
            dynamicPortSummary = "Автоматически выбирать доступный порт при запуске.",
            portLabel = "Порт SOCKS5",
            listenAllTitle = "Разрешить доступ из локальной сети",
            listenAllSummary = "Использовать 0.0.0.0 вместо loopback-адреса 127.0.0.1.",
            lanSectionTitle = "ДОСТУП ИЗ СЕТИ",
            lanInstruction = "Устройства локальной сети могут подключаться к локальному прокси.",
            ipLabel = "Адрес прослушивания",
            socksEndpointLabel = "SOCKS5",
            httpEndpointLabel = "HTTP",
            securitySectionTitle = "БЕЗОПАСНОСТЬ",
            authenticationTitle = "Аутентификация",
            authenticationSummary = "Требовать имя пользователя и пароль.",
            credentialsSectionTitle = "УЧЁТНЫЕ ДАННЫЕ",
            authorizationTitle = "Авторизация прокси",
            tapToCopy = "Нажмите, чтобы скопировать",
            usernameLabel = "Имя пользователя",
            passwordLabel = "Пароль",
            generateText = "Создать",
            showText = "Показать",
            hideText = "Скрыть",
            copyUsernameDescription = "Копировать имя пользователя",
            copyPasswordDescription = "Копировать пароль",
        ),
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onDynamicPortChange = {},
        onPortChange = { portText = it },
        onListenAllInterfacesChange = { allow -> listenAddress = if (allow) "0.0.0.0" else "127.0.0.1" },
        onAuthenticationChange = {},
        onGenerateCredentials = {},
        onCopy = { _, _ -> },
        capabilities = LocalProxySettingsCapabilities(
            dynamicPort = false,
            listenAllInterfaces = true,
            authentication = false,
            credentials = false,
            lanEndpoints = false,
        ),
        platformContent = {
            SettingsGroup(title = "НАСТРОЙКИ DESKTOP") {
                if (supportsSystemProxy) {
                    SettingsSwitchPreference(
                        title = "Использовать системный прокси",
                        summary = "SKIPI временно настраивает системный HTTP/SOCKS прокси во время подключения.",
                        checked = useSystemProxy,
                        onCheckedChange = { useSystemProxy = it },
                    )
                    OutlinedTextField(
                        value = httpPortText,
                        onValueChange = { httpPortText = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        label = { Text("Порт HTTP-прокси") },
                        singleLine = true,
                        isError = httpPortError != null,
                    )
                    if (httpPortError != null) SettingsInlineError(httpPortError)
                    SettingsInfoRow("HTTP endpoint", "127.0.0.1:${httpPort ?: "—"} · всегда слушает только loopback")
                }
                OutlinedTextField(
                    value = listenAddress,
                    onValueChange = { listenAddress = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Адрес прослушивания") },
                    singleLine = true,
                    isError = addressError != null,
                )
                if (addressError != null) SettingsInlineError(addressError)
                Box {
                    SettingsPreferenceRow(
                        title = "Уровень журналирования SKIPI Core",
                        summary = "Используется при следующем запуске локального туннеля.",
                        value = logLevel.uppercase(),
                        onClick = { logLevelExpanded = true },
                        showDivider = true,
                    )
                    DropdownMenu(expanded = logLevelExpanded, onDismissRequest = { logLevelExpanded = false }) {
                        DesktopCoreLogLevels.forEach { value ->
                            DropdownMenuItem(
                                text = { Text(value.uppercase()) },
                                onClick = { logLevel = value; logLevelExpanded = false },
                            )
                        }
                    }
                }
                Text(
                    "Изменения применяются при следующем подключении SKIPI Core.",
                    color = SettingsMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Button(
                    onClick = {
                        if (portError == null && httpPortError == null && addressError == null && port != null && httpPort != null) {
                            val next = draft.copy(
                                localProxyPort = port,
                                localHttpProxyPort = httpPort,
                                localProxyListenAddress = listenAddress.trim(),
                                useSystemProxy = supportsSystemProxy && useSystemProxy,
                                useWindowsSystemProxy = supportsSystemProxy && useSystemProxy,
                                coreLogLevel = logLevel,
                            )
                            draft = next
                            onPersist(next, "Параметры локального прокси сохранены.")
                        }
                    },
                    enabled = portError == null && httpPortError == null && addressError == null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Сохранить параметры") }
                if (supportsSystemProxy && onClearSystemProxy != null) {
                    OutlinedButton(
                        onClick = onClearSystemProxy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    ) { Text("Сбросить системный прокси") }
                }
                SettingsStatusMessage(message)
            }
        },
    )
}

@Composable
private fun DesktopSubscriptionSettings(
    settings: DesktopAppSettings,
    message: String,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    contentPadding: PaddingValues,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    var userAgent by remember(settings.subscriptionUserAgent) { mutableStateOf(settings.subscriptionUserAgent) }
    val hwid = remember(settings.installationUuid) { DesktopDeviceIdentity.computeHwid(settings.installationUuid) }
    LaunchedEffect(settings) { draft = settings }

    SkipiSubscriptionSettingsScreen(
        state = draft.toSubscriptionSettingsState(),
        labels = SubscriptionSettingsLabels(
            screenTitle = "Подписки",
            generalSectionTitle = "ЗАГРУЗКА И УДАЛЕНИЕ",
            fetchTimeoutTitle = "Тайм-аут загрузки",
            fetchTimeoutSummary = "Максимальное время HTTP-запроса при обновлении подписки.",
            timeoutOptions = listOf(10, 15, 20, 30, 45, 60, 90, 120).map { "$it с" },
            userAgentsTitle = "User-Agent",
            userAgentsSummary = "Значение по умолчанию для загрузки подписок.",
            deviceHeadersTitle = "Отправлять данные устройства и HWID",
            deviceHeadersSummary = "Передавать X-HWID, X-Device-ID, ОС и модель устройства при обновлении подписок.",
            deletionConfirmationTitle = "Подтверждать удаление",
            deletionConfirmationSummary = "Спрашивать перед удалением сервера, подписки или конфига.",
            expirySectionTitle = "СРОК ДЕЙСТВИЯ",
            expiryEnabledTitle = "Уведомлять об истечении подписки",
            expiryEnabledSummary = "Показывать напоминание до окончания подписки.",
            expiryRemindersTitle = "Напоминания",
            expiryRemindersSummary = "Настроить интервалы напоминаний.",
            pingSectionTitle = "ПРОВЕРКА PING",
            pingTitle = "Параметры проверки ping",
            disableHeadersDialogTitle = "Отключить данные устройства?",
            disableHeadersDialogSummary = "Сервер подписки может перестать обновляться без этих заголовков.",
            cancelText = "Отмена",
            disableHeadersAction = "Отключить",
        ),
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onFetchTimeoutChange = { seconds ->
            draft = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(fetchTimeoutSeconds = seconds))
        },
        onOpenUserAgents = {},
        onDeviceHeadersChange = { enabled ->
            draft = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(deviceHeadersEnabled = enabled))
        },
        onDeletionConfirmationChange = { enabled ->
            draft = draft.withSubscriptionSettingsState(draft.toSubscriptionSettingsState().copy(deletionConfirmationEnabled = enabled))
        },
        onExpiryNotificationsChange = {},
        onExpiryRemindersChange = {},
        onOpenPingSettings = {},
        capabilities = SubscriptionSettingsCapabilities(
            fetchTimeout = true,
            userAgents = false,
            deviceHeaders = true,
            deletionConfirmation = true,
            expiryNotifications = false,
            pingSettings = false,
        ),
        platformContent = {
            SettingsGroup(title = "ИДЕНТИФИКАЦИЯ УСТРОЙСТВА И USER-AGENT") {
                OutlinedTextField(
                    value = hwid,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("Идентификатор устройства (HWID)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                SettingsInfoRow(
                    title = "Использование HWID",
                    summary = "Идентификатор передаётся серверам подписок для учёта лимита активных устройств.",
                    showDivider = true,
                )
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("User-Agent по умолчанию") },
                    singleLine = true,
                )
                Text(
                    "Применяется по умолчанию ко всем подпискам. Для отдельного User-Agent настройте конкретную подписку.",
                    color = SettingsMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Button(
                    onClick = {
                        val next = draft.copy(
                            subscriptionUserAgent = userAgent.trim().ifBlank { DefaultDesktopSubscriptionUserAgent },
                        )
                        draft = next
                        onPersist(next, "Параметры подписок сохранены.")
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Сохранить параметры") }
                SettingsStatusMessage(message)
            }
        },
    )
}

@Composable
private fun DesktopIntegrationSettings(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    SkipiIntegrationSettingsScreen(
        state = IntegrationSettingsState(broadcastControlEnabled = false),
        labels = IntegrationSettingsLabels(
            screenTitle = "Интеграция",
            sectionTitle = "ИНТЕГРАЦИЯ",
            urlSchemesTitle = "URL-схемы SKIPI",
            urlSchemesSummary = "Команды подключения и импорта.",
            broadcastControlTitle = "Управление трансляцией",
            broadcastControlSummary = "Недоступно в Desktop.",
        ),
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onOpenUrlSchemes = {},
        onBroadcastControlChange = {},
        capabilities = IntegrationSettingsCapabilities(urlSchemes = false, broadcastControl = false),
        platformContent = {
            SettingsGroup(title = "КОМАНДЫ DESKTOP") {
                IntegrationCommandRow("skipi://connect", "Запустить подключение", onMessage)
                IntegrationCommandRow("skipi://disconnect", "Остановить подключение", onMessage, showDivider = true)
                IntegrationCommandRow("skipi://toggle", "Переключить состояние", onMessage, showDivider = true)
                IntegrationCommandRow("skipi://add/{url}", "Добавить подписку по URL", onMessage)
                IntegrationCommandRow("skipi://import/{base64}", "Импортировать закодированную ссылку", onMessage, showDivider = true)
                IntegrationCommandRow("skipi://conf/add/{base64}", "Добавить профиль конфигурации", onMessage, showDivider = true)
                SettingsInfoRow(
                    title = "Обработчик URL-схемы",
                    summary = "Обработчик skipi:// ещё не зарегистрирован системой. Используйте импорт из буфера или URL в приложении.",
                )
            }
        },
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
    contentPadding: PaddingValues,
) {
    val logLevels = DesktopCoreLogLevels

    SkipiLogsSettingsScreen(
        state = LogsSettingsState(
            coreLogLevelIndex = 0,
            retentionDays = 30,
            accessLogEnabled = false,
        ),
        labels = LogsSettingsLabels(
            screenTitle = "Диагностика",
            optionsSectionTitle = "ЖУРНАЛЫ",
            logLevelTitle = "Уровень журналирования SKIPI Core",
            logLevels = logLevels.map(String::uppercase),
            retentionTitle = "Срок хранения журналов",
            retentionSummary = "Недоступно в Desktop.",
            retentionOptions = listOf("30 дней"),
            retentionDays = listOf(30),
            accessLogTitle = "Журнал подключений",
            viewersSectionTitle = "ПРОСМОТР ЖУРНАЛОВ",
            coreLogsTitle = "Открыть папку с логами",
            accessLogsTitle = "Журнал подключений",
            logcatTitle = "Журнал приложения",
            feedbackSectionTitle = "ОБРАТНАЯ СВЯЗЬ",
            bugReportTitle = "Сообщить об ошибке",
            bugReportSummary = "Недоступно в Desktop.",
        ),
        padding = contentPadding,
        isWideScreen = true,
        showAccessLogs = false,
        onBack = onBack,
        onLogLevelChange = {},
        onRetentionDaysChange = {},
        onAccessLogChange = {},
        onOpenCoreLogs = {},
        onOpenAccessLogs = {},
        onOpenLogcat = {},
        onOpenBugReport = {},
        capabilities = LogsSettingsCapabilities(
            logLevel = false,
            retention = false,
            accessLog = false,
            coreLogs = false,
            accessLogs = false,
            logcat = false,
            bugReport = false,
        ),
        platformContent = {
            DesktopDiagnosticsContent(
                settings = settings,
                isTunnelRunning = isTunnelRunning,
                onMessage = onMessage,
                message = message,
            )
        },
    )
}

@Composable
private fun DesktopDiagnosticsContent(
    settings: DesktopAppSettings,
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

    SettingsGroup(title = "НАСТРОЙКИ DESKTOP") {
        SettingsInfoRow(
            title = if (isTunnelRunning) "Локальный туннель запущен" else "Локальный туннель остановлен",
            summary = if (isTunnelRunning) {
                "SKIPI Core работает в процессе приложения. Изменения применятся после переподключения."
            } else {
                "Выберите сервер на главном экране и подключитесь для запуска SKIPI Core."
            },
            accent = if (isTunnelRunning) SettingsGreen else SettingsMuted,
            showDivider = true,
        )
        SettingsInfoRow(
            title = if (runtime != null) "SKIPI Core найден" else "SKIPI Core не найден",
            summary = runtime?.directory?.toString()
                ?: runtimeCheck.exceptionOrNull()?.message.orEmpty().ifBlank { "Проверьте ресурсы установленного приложения." },
            accent = if (runtime != null) SettingsGreen else SettingsRed,
            showDivider = true,
        )
        SettingsPreferenceRow(
            title = "Проверить runtime снова",
            summary = "Найти библиотеку SKIPI Core и файлы geoip.dat/geosite.dat.",
            onClick = { runtimeCheck = DesktopCoreRuntimes.discover() },
            showDivider = true,
        )
        SettingsInfoRow(
            title = "Уровень журналирования",
            summary = "${settings.coreLogLevel.uppercase()} · изменяется в настройках локального прокси.",
            showDivider = true,
        )
        SettingsPreferenceRow(
            title = "Открыть папку с логами",
            summary = logDir.toString(),
            onClick = {
                val result = runCatching {
                    Files.createDirectories(logDir)
                    check(Desktop.isDesktopSupported()) { "Открытие папки не поддерживается системой." }
                    Desktop.getDesktop().open(logDir.toFile())
                }
                onMessage(result.fold({ "Папка журналов открыта." }, { "Не удалось открыть папку: ${it.message.orEmpty()}" }))
            },
            showDivider = true,
        )
        SettingsPreferenceRow(
            title = if (showRecentLogs) "Скрыть журнал" else "Показать последние записи журнала",
            summary = logSize?.let { "Размер файла: $it байт" } ?: "Файл журнала ещё не создан",
            onClick = {
                recentLogs = DesktopLogger.recentEntries(40)
                showRecentLogs = !showRecentLogs
            },
            showDivider = true,
        )
        if (showRecentLogs) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141416)),
                border = BorderStroke(1.dp, SettingsBorder),
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("skipi.log (последние записи)", color = SettingsMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Обновить",
                            color = SettingsGreen,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable { recentLogs = DesktopLogger.recentEntries(40) },
                        )
                    }
                    Text(
                        text = recentLogs.ifEmpty { listOf("Записей пока нет") }.joinToString("\n"),
                        color = Color(0xFFD4D4D4),
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
        SettingsPreferenceRow(
            title = "Каталог данных SKIPI",
            summary = dataDirectory?.toString().orEmpty(),
            onClick = {
                val result = dataDirectory?.let(::openDirectory)
                    ?: Result.failure(IllegalStateException("Каталог данных не найден."))
                onMessage(result.fold({ "Каталог данных открыт." }, { "Не удалось открыть каталог: ${it.message.orEmpty()}" }))
            },
            showDivider = true,
        )
        SettingsPreferenceRow(
            title = "Скопировать путь к settings.json",
            summary = settingsPath.toString(),
            onClick = {
                copyToClipboard(settingsPath.toString()).fold(
                    onSuccess = { onMessage("Путь к настройкам скопирован.") },
                    onFailure = { onMessage("Не удалось скопировать путь: ${it.message.orEmpty()}") },
                )
            },
        )
        SettingsStatusMessage(message)
    }
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
        labels = AboutSettingsLabels(
            screenTitle = "О SKIPI",
            updatesTitle = "ОБНОВЛЕНИЯ",
            runtimeTitle = "КОМПОНЕНТЫ",
            otherTitle = "О ПРОЕКТЕ",
            replayOnboardingTitle = "Повторить знакомство",
            replayOnboardingSummary = "Недоступно в Desktop.",
            telegramTitle = "Telegram",
            bugReportTitle = "Сообщить об ошибке",
            bugReportSummary = "Недоступно в Desktop.",
            sourceTitle = "Исходный код",
        ),
        runtime = AboutRuntimeInfo(
            appName = "SKIPI Desktop",
            appVersion = "",
            skipiCoreVersion = "",
            xrayCoreVersion = "",
            hevTunnelVersion = "",
        ),
        padding = contentPadding,
        isWideScreen = true,
        onBack = onBack,
        onOpenCoreInfo = {},
        onReplayOnboarding = {},
        onOpenTelegram = {},
        onOpenBugReport = {},
        onOpenSource = {},
        logo = { Text("SKIPI", fontSize = 28.sp, fontWeight = FontWeight.Black, color = SkipiTheme.colors.onSurface) },
        updatesContent = {},
        capabilities = AboutSettingsCapabilities(
            updates = false,
            runtime = false,
            replayOnboarding = false,
            telegram = false,
            bugReport = false,
            source = false,
        ),
        platformContent = {
            SettingsGroup(title = "КОМПОНЕНТЫ") {
                SettingsInfoRow("skipi-core", "Общие модели прокси, подписок, конфигов и туннеля", showDivider = true)
                SettingsInfoRow("SKIPI Core", "Встроенный runtime в процессе приложения", showDivider = true)
                SettingsInfoRow("Хранилище", settingsPath.toString())
            }
            SettingsGroup(title = "ЛИЦЕНЗИЯ") {
                SettingsPreferenceRow(
                    title = "GPL-3.0",
                    summary = "Исходный код и условия распространения находятся в корне проекта.",
                    onClick = {
                        copyToClipboard("GPL-3.0").onSuccess { onMessage("Название лицензии скопировано.") }
                    },
                )
            }
            SettingsStatusMessage(message)
        },
    )
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(
            title,
            color = SettingsMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SettingsSurface),
            border = BorderStroke(1.dp, SettingsBorder),
        ) { Column(content = content) }
    }
}

@Composable
private fun SettingsPreferenceRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
    value: String? = null,
    showDivider: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = SettingsText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (summary.isNotBlank()) {
                    Text(summary, color = SettingsMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(value, color = SettingsMuted, fontSize = 12.sp)
            }
        }
        if (showDivider) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = SettingsBorder.copy(alpha = 0.7f))
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    summary: String,
    accent: Color = SettingsMuted,
    showDivider: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp)) {
            Text(title, color = accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(summary, color = SettingsMuted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (showDivider) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = SettingsBorder.copy(alpha = 0.7f))
    }
}

@Composable
private fun SettingsSwitchPreference(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = SettingsText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(summary, color = SettingsMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsInlineError(text: String) {
    Text(text, color = SettingsRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
}

@Composable
private fun SettingsStatusMessage(message: String) {
    if (message.isNotBlank()) {
        Text(message, color = SettingsMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
private fun IntegrationCommandRow(
    command: String,
    description: String,
    onMessage: (String) -> Unit,
    showDivider: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(command, color = SettingsGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = SettingsMuted, fontSize = 12.sp)
            }
            IconButton(
                onClick = {
                    copyToClipboard(command).fold(
                        onSuccess = { onMessage("Команда скопирована: $command") },
                        onFailure = { onMessage("Не удалось скопировать команду: ${it.message.orEmpty()}") },
                    )
                },
            ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Копировать", tint = SettingsText) }
        }
        if (showDivider) HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = SettingsBorder.copy(alpha = 0.7f))
    }
}

private fun openDirectory(path: java.nio.file.Path): Result<Unit> = runCatching {
    Files.createDirectories(path)
    check(Desktop.isDesktopSupported()) { "Открытие папки не поддерживается системой." }
    Desktop.getDesktop().open(path.toFile())
}

private fun copyToClipboard(value: String): Result<Unit> = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
}
