// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.nio.file.Files

private val SettingsBackground: Color
    @Composable get() = DesktopContentBackground
private val SettingsSurface = Color(0xFF202126)
private val SettingsBorder = Color(0xFF3C3E45)
private val SettingsText = Color(0xFFF4F4F6)
private val SettingsMuted = Color(0xFFA2A5AF)
private val SettingsGreen = Color(0xFF58D27A)
private val SettingsRed = Color(0xFFFF5B62)

private enum class DesktopSettingsDestination {
    Overview,
    Appearance,
    LocalProxy,
    Subscriptions,
    Integration,
    Diagnostics,
    About,
}

/**
 * Desktop counterpart of Android's [features.settings.SettingsPage].
 *
 * The screen owns only its navigation and drafts. Every confirmed preference is
 * written with [DesktopSettingsLibraries], then reported to the desktop root so
 * it can rebuild active adapters (for example, Xray's local SOCKS inbound).
 */
@Composable
internal fun DesktopSettingsScreen(
    settings: DesktopAppSettings,
    onSettingsChange: (DesktopAppSettings) -> Unit,
    contentPadding: PaddingValues,
    isTunnelRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var destination by remember { mutableStateOf(DesktopSettingsDestination.Overview) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun persist(next: DesktopAppSettings, successMessage: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { DesktopSettingsLibraries.saveDefault(next) }
            result.onSuccess {
                onSettingsChange(next)
                message = successMessage
            }.onFailure { error ->
                message = "Не удалось сохранить настройки: ${error.message.orEmpty()}"
            }
        }
    }

    when (destination) {
        DesktopSettingsDestination.Overview -> DesktopSettingsOverview(
            settings = settings,
            message = message,
            onNavigate = { destination = it },
            contentPadding = contentPadding,
            modifier = modifier,
        )

        DesktopSettingsDestination.Appearance -> DesktopAppearanceSettings(
            settings = settings,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onPersist = ::persist,
            contentPadding = contentPadding,
            modifier = modifier,
        )

        DesktopSettingsDestination.LocalProxy -> DesktopLocalProxySettings(
            settings = settings,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onPersist = ::persist,
            contentPadding = contentPadding,
            modifier = modifier,
        )

        DesktopSettingsDestination.Subscriptions -> DesktopSubscriptionSettings(
            settings = settings,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onPersist = ::persist,
            contentPadding = contentPadding,
            modifier = modifier,
        )

        DesktopSettingsDestination.Integration -> DesktopIntegrationSettings(
            onBack = { destination = DesktopSettingsDestination.Overview },
            onMessage = { message = it },
            contentPadding = contentPadding,
            modifier = modifier,
        )

        DesktopSettingsDestination.Diagnostics -> DesktopDiagnosticsSettings(
            settings = settings,
            isTunnelRunning = isTunnelRunning,
            message = message,
            onBack = { destination = DesktopSettingsDestination.Overview },
            onMessage = { message = it },
            contentPadding = contentPadding,
            modifier = modifier,
        )

        DesktopSettingsDestination.About -> DesktopAboutSettings(
            onBack = { destination = DesktopSettingsDestination.Overview },
            onMessage = { message = it },
            contentPadding = contentPadding,
            modifier = modifier,
        )
    }
}

@Composable
private fun DesktopSettingsOverview(
    settings: DesktopAppSettings,
    message: String,
    onNavigate: (DesktopSettingsDestination) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val supportsWindowsSystemProxy = isDesktopWindowsSystemProxySupported()
    SettingsScreenColumn(
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        SettingsHeader(
            title = "Настройки",
            subtitle = "SKIPI Desktop",
        )

        SettingsGroup(title = "ВНЕШНИЙ ВИД") {
            SettingsCategoryRow(
                icon = Icons.Outlined.Tune,
                iconBackground = Color(0xFF5C6DB0),
                title = "Оформление",
                summary = "Тема, компоновка и главный экран",
                onClick = { onNavigate(DesktopSettingsDestination.Appearance) },
            )
        }

        SettingsGroup(title = "СЕТЬ") {
            SettingsCategoryRow(
                icon = Icons.Outlined.FolderOpen,
                iconBackground = Color(0xFF397A94),
                title = "Локальный прокси",
                summary = buildString {
                    append("SOCKS5 · ${settings.localProxyListenAddress}:${settings.localProxyPort}")
                    if (supportsWindowsSystemProxy && settings.useWindowsSystemProxy) append(" · системный HTTP")
                },
                onClick = { onNavigate(DesktopSettingsDestination.LocalProxy) },
                showDivider = supportsWindowsSystemProxy,
            )
            if (supportsWindowsSystemProxy) {
                SettingsCategoryRow(
                    icon = Icons.Outlined.Tune,
                    iconBackground = Color(0xFF397A94),
                    title = "Туннель Windows",
                    summary = if (settings.useWindowsSystemProxy) {
                        "Системный HTTP-прокси будет включаться вместе с Xray"
                    } else {
                        "Только локальный SOCKS5 — системный прокси выключен"
                    },
                    onClick = { onNavigate(DesktopSettingsDestination.LocalProxy) },
                )
            }
        }

        SettingsGroup(title = "ПОДПИСКИ") {
            SettingsCategoryRow(
                icon = Icons.Outlined.Link,
                iconBackground = Color(0xFF387D58),
                title = "Подписки",
                summary = "User-Agent и тайм-аут загрузки",
                onClick = { onNavigate(DesktopSettingsDestination.Subscriptions) },
            )
        }

        SettingsGroup(title = "ИНТЕГРАЦИЯ") {
            SettingsCategoryRow(
                icon = Icons.Outlined.Link,
                iconBackground = Color(0xFF9A6E30),
                title = "URL-схемы SKIPI",
                summary = "Команды подключения и импорта",
                onClick = { onNavigate(DesktopSettingsDestination.Integration) },
            )
        }

        SettingsGroup(title = "ДИАГНОСТИКА") {
            SettingsCategoryRow(
                icon = Icons.Outlined.Description,
                iconBackground = Color(0xFF8A536A),
                title = "Диагностика",
                summary = "Xray, журналы и каталог данных",
                value = settings.coreLogLevel.uppercase(),
                onClick = { onNavigate(DesktopSettingsDestination.Diagnostics) },
            )
        }

        SettingsGroup(title = "О ПРИЛОЖЕНИИ", bottomPadding = 0.dp) {
            SettingsCategoryRow(
                icon = Icons.Outlined.Hexagon,
                iconBackground = Color(0xFF5E6070),
                title = "О SKIPI",
                summary = "Desktop-клиент и skipi-core",
                onClick = { onNavigate(DesktopSettingsDestination.About) },
            )
        }

        SettingsStatusMessage(message)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DesktopAppearanceSettings(
    settings: DesktopAppSettings,
    message: String,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    SettingsScreenColumn(contentPadding, modifier) {
        SettingsHeader(
            title = "Оформление",
            subtitle = "Внешний вид и компоновка",
            onBack = onBack,
        )

        SettingsGroup(title = "ТЕМА") {
            SettingsDropdownPreference(
                title = "Режим цвета",
                summary = "Сохраняется для всего Desktop-клиента",
                value = settings.themeMode.displayName(),
                options = DesktopThemeMode.entries.map { it to it.displayName() },
                onSelected = { mode ->
                    onPersist(settings.copy(themeMode = mode), "Режим цвета сохранён.")
                },
            )
        }

        SettingsGroup(title = "ГЛАВНЫЙ ЭКРАН") {
            SettingsSwitchPreference(
                title = "Компактное подключение",
                summary = "Компактная карточка состояния вместо классической",
                checked = settings.compactHome,
                onCheckedChange = {
                    onPersist(settings.copy(compactHome = it), "Режим главного экрана сохранён.")
                },
                showDivider = true,
            )
        }

        SettingsGroup(title = "ДЕЙСТВИЯ") {
            SettingsSwitchPreference(
                title = "Подтверждать удаление",
                summary = "Спрашивать перед удалением сервера, подписки или конфига",
                checked = settings.confirmDeletion,
                onCheckedChange = {
                    onPersist(settings.copy(confirmDeletion = it), "Настройка удаления сохранена.")
                },
            )
        }

        SettingsStatusMessage(message)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DesktopLocalProxySettings(
    settings: DesktopAppSettings,
    message: String,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val supportsWindowsSystemProxy = isDesktopWindowsSystemProxySupported()
    var portText by remember(settings.localProxyPort) { mutableStateOf(settings.localProxyPort.toString()) }
    var httpPortText by remember(settings.localHttpProxyPort) { mutableStateOf(settings.localHttpProxyPort.toString()) }
    var useWindowsSystemProxy by remember(settings.useWindowsSystemProxy) {
        mutableStateOf(settings.useWindowsSystemProxy)
    }
    var listenAddress by remember(settings.localProxyListenAddress) {
        mutableStateOf(settings.localProxyListenAddress)
    }
    var logLevel by remember(settings.coreLogLevel) { mutableStateOf(settings.coreLogLevel) }
    var logLevelExpanded by remember { mutableStateOf(false) }
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
    val addressError = when {
        listenAddress.isBlank() -> "Укажите адрес прослушивания."
        else -> null
    }

    SettingsScreenColumn(contentPadding, modifier) {
        SettingsHeader(
            title = "Локальный прокси",
            subtitle = if (supportsWindowsSystemProxy) {
                "SOCKS5 и системный HTTP-прокси для Windows"
            } else {
                "Локальный SOCKS5-прокси"
            },
            onBack = onBack,
        )

        SettingsGroup(title = "ПАРАМЕТРЫ СЕТИ") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Порт SOCKS5") },
                    singleLine = true,
                    isError = portError != null,
                )
                if (portError != null) {
                    Text(portError, color = SettingsRed, fontSize = 12.sp)
                }
                if (supportsWindowsSystemProxy) {
                    OutlinedTextField(
                        value = httpPortText,
                        onValueChange = { httpPortText = it.filter(Char::isDigit).take(5) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Порт HTTP-прокси Windows") },
                        singleLine = true,
                        isError = httpPortError != null,
                    )
                    if (httpPortError != null) {
                        Text(httpPortError, color = SettingsRed, fontSize = 12.sp)
                    }
                }
                OutlinedTextField(
                    value = listenAddress,
                    onValueChange = { listenAddress = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Адрес прослушивания") },
                    singleLine = true,
                    isError = addressError != null,
                )
                if (addressError != null) {
                    Text(addressError, color = SettingsRed, fontSize = 12.sp)
                }
            }
            SettingsSwitchPreference(
                title = "Разрешить доступ из локальной сети",
                summary = "Использовать 0.0.0.0 вместо loopback-адреса 127.0.0.1",
                checked = listenAddress.trim() != "127.0.0.1",
                onCheckedChange = { allowLan ->
                    listenAddress = if (allowLan) "0.0.0.0" else "127.0.0.1"
                },
                showDivider = true,
            )
            if (supportsWindowsSystemProxy) {
                SettingsSwitchPreference(
                    title = "Использовать системный прокси Windows",
                    summary = "При подключении SKIPI временно направит HTTP/HTTPS Windows на локальный Xray и восстановит прежние параметры при отключении. HTTP всегда слушает только 127.0.0.1.",
                    checked = useWindowsSystemProxy,
                    onCheckedChange = { useWindowsSystemProxy = it },
                )
            }
        }

        SettingsGroup(title = "ЛОГИ") {
            Box {
                SettingsPreferenceRow(
                    title = "Уровень журналирования Xray",
                    summary = "Используется при следующем запуске локального туннеля",
                    value = logLevel.uppercase(),
                    onClick = { logLevelExpanded = true },
                )
                DropdownMenu(
                    expanded = logLevelExpanded,
                    onDismissRequest = { logLevelExpanded = false },
                ) {
                    DesktopLogLevels.forEach { value ->
                        DropdownMenuItem(
                            text = { Text(value.uppercase()) },
                            onClick = {
                                logLevel = value
                                logLevelExpanded = false
                            },
                        )
                    }
                }
            }
        }

        SettingsGroup(title = "ТЕКУЩАЯ КОНЕЧНАЯ ТОЧКА") {
            SettingsInfoRow(
                title = "SOCKS5",
                summary = "${listenAddress.trim().ifBlank { "127.0.0.1" }}:${port ?: "—"}",
            )
            if (supportsWindowsSystemProxy) {
                SettingsInfoRow(
                    title = "HTTP Windows",
                    summary = if (useWindowsSystemProxy) {
                        "127.0.0.1:${httpPort ?: "—"} · будет применяться при подключении"
                    } else {
                        "Выключен — используйте SOCKS5 вручную"
                    },
                )
            }
        }

        Button(
            onClick = {
                if (port != null && port in 1..65_535 && httpPort != null && httpPort in 1..65_535 &&
                    httpPort != port && listenAddress.isNotBlank()) {
                    onPersist(
                        settings.copy(
                            localProxyPort = port,
                            localHttpProxyPort = httpPort,
                            useWindowsSystemProxy = supportsWindowsSystemProxy && useWindowsSystemProxy,
                            localProxyListenAddress = listenAddress.trim(),
                            coreLogLevel = logLevel,
                        ),
                        "Параметры локального прокси сохранены.",
                    )
                }
            },
            enabled = portError == null && httpPortError == null && addressError == null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить параметры")
        }
        Text(
            "Изменения применяются при следующем подключении Xray.",
            color = SettingsMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SettingsStatusMessage(message)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DesktopSubscriptionSettings(
    settings: DesktopAppSettings,
    message: String,
    onBack: () -> Unit,
    onPersist: (DesktopAppSettings, String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    var userAgent by remember(settings.subscriptionUserAgent) { mutableStateOf(settings.subscriptionUserAgent) }
    var timeoutSeconds by remember(settings.subscriptionFetchTimeoutSeconds) {
        mutableStateOf(settings.subscriptionFetchTimeoutSeconds)
    }
    var timeoutExpanded by remember { mutableStateOf(false) }

    SettingsScreenColumn(contentPadding, modifier) {
        SettingsHeader(
            title = "Подписки",
            subtitle = "Загрузка и проверка подписок",
            onBack = onBack,
        )

        SettingsGroup(title = "ЗАГРУЗКА") {
            Box {
                SettingsPreferenceRow(
                    title = "Тайм-аут загрузки",
                    summary = "Максимальное время HTTP-запроса",
                    value = "$timeoutSeconds с",
                    onClick = { timeoutExpanded = true },
                )
                DropdownMenu(
                    expanded = timeoutExpanded,
                    onDismissRequest = { timeoutExpanded = false },
                ) {
                    DesktopSubscriptionTimeouts.forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text("$seconds с") },
                            onClick = {
                                timeoutSeconds = seconds
                                timeoutExpanded = false
                            },
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("User-Agent") },
                    singleLine = true,
                )
                Text(
                    "Передаётся провайдеру при следующем обновлении подписки.",
                    color = SettingsMuted,
                    fontSize = 12.sp,
                )
            }
        }

        SettingsGroup(title = "ПОВЕДЕНИЕ") {
            SettingsInfoRow(
                title = "Удаление",
                summary = if (settings.confirmDeletion) {
                    "Подтверждение удаления включено в разделе «Оформление»."
                } else {
                    "Удаление выполняется без подтверждения."
                },
            )
        }

        Button(
            onClick = {
                onPersist(
                    settings.copy(
                        subscriptionUserAgent = userAgent.trim().ifBlank { DefaultDesktopSubscriptionUserAgent },
                        subscriptionFetchTimeoutSeconds = timeoutSeconds,
                    ),
                    "Параметры подписок сохранены.",
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Сохранить параметры")
        }
        SettingsStatusMessage(message)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DesktopIntegrationSettings(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    SettingsScreenColumn(contentPadding, modifier) {
        SettingsHeader(
            title = "Интеграция",
            subtitle = "Команды для автоматизации SKIPI",
            onBack = onBack,
        )

        SettingsGroup(title = "ПОДКЛЮЧЕНИЕ") {
            IntegrationCommandRow("skipi://connect", "Запустить подключение", onMessage)
            IntegrationCommandRow("skipi://disconnect", "Остановить подключение", onMessage, showDivider = true)
            IntegrationCommandRow("skipi://toggle", "Переключить состояние", onMessage, showDivider = true)
        }

        SettingsGroup(title = "ИМПОРТ") {
            IntegrationCommandRow("skipi://add/{url}", "Добавить подписку по URL", onMessage)
            IntegrationCommandRow("skipi://import/{base64}", "Импортировать закодированную ссылку", onMessage, showDivider = true)
            IntegrationCommandRow("skipi://conf/add/{base64}", "Добавить профиль конфигурации", onMessage, showDivider = true)
        }

        SettingsGroup(title = "СОВМЕСТИМОСТЬ") {
            SettingsInfoRow(
                title = "Внешние форматы",
                summary = "Серверы и конфиги можно импортировать из буфера или по URL.",
            )
        }
        Text(
            "Команды можно скопировать. В этой сборке обработчик skipi:// для Windows ещё не зарегистрирован; используйте импорт из буфера или URL в самом приложении.",
            color = SettingsMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DesktopDiagnosticsSettings(
    settings: DesktopAppSettings,
    isTunnelRunning: Boolean,
    message: String,
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    var runtimeCheck by remember { mutableStateOf(DesktopXrayRuntimes.discover()) }
    val settingsPath = remember { DesktopSettingsLibraries.defaultPath() }
    val dataDirectory = remember { settingsPath.parent }
    val runtime = runtimeCheck.getOrNull()

    SettingsScreenColumn(contentPadding, modifier) {
        SettingsHeader(
            title = "Диагностика",
            subtitle = "Xray, журналы и файлы клиента",
            onBack = onBack,
        )

        SettingsGroup(title = "XRAY") {
            SettingsInfoRow(
                title = if (isTunnelRunning) "Локальный туннель запущен" else "Локальный туннель остановлен",
                summary = if (isTunnelRunning) {
                    "Процесс Xray работает. Изменения сетевых параметров применятся после переподключения."
                } else {
                    "Выберите сервер на главном экране и подключитесь для запуска Xray."
                },
                accent = if (isTunnelRunning) SettingsGreen else SettingsMuted,
                showDivider = true,
            )
            SettingsInfoRow(
                title = if (runtime != null) "Среда Xray найдена" else "Среда Xray не найдена",
                summary = runtime?.directory?.toString()
                    ?: runtimeCheck.exceptionOrNull()?.message.orEmpty().ifBlank { "Проверьте ресурсы установленного приложения." },
                accent = if (runtime != null) SettingsGreen else SettingsRed,
            )
            SettingsPreferenceRow(
                title = "Проверить снова",
                summary = "Повторно найти xray.exe, geoip.dat и geosite.dat",
                onClick = { runtimeCheck = DesktopXrayRuntimes.discover() },
                showDivider = true,
            )
        }

        SettingsGroup(title = "ЖУРНАЛЫ") {
            SettingsInfoRow(
                title = "Уровень журналирования",
                summary = settings.coreLogLevel.uppercase() + " · настраивается в «Локальном прокси»",
            )
        }

        SettingsGroup(title = "ДАННЫЕ SKIPI") {
            SettingsPreferenceRow(
                title = "Каталог данных",
                summary = dataDirectory?.toString().orEmpty(),
                onClick = {
                    val result = runCatching {
                        val path = requireNotNull(dataDirectory)
                        Files.createDirectories(path)
                        check(Desktop.isDesktopSupported()) { "Открытие папки не поддерживается системой." }
                        Desktop.getDesktop().open(path.toFile())
                    }
                    onMessage(
                        result.fold(
                            onSuccess = { "Каталог данных открыт." },
                            onFailure = { "Не удалось открыть каталог: ${it.message.orEmpty()}" },
                        ),
                    )
                },
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
                showDivider = true,
            )
        }
        SettingsStatusMessage(message)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DesktopAboutSettings(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier,
) {
    val settingsPath = remember { DesktopSettingsLibraries.defaultPath() }
    SettingsScreenColumn(contentPadding, modifier) {
        SettingsHeader(
            title = "О SKIPI",
            subtitle = "Кроссплатформенный клиент",
            onBack = onBack,
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SettingsSurface),
            border = BorderStroke(1.dp, SettingsBorder),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("SKIPI", color = SettingsText, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text(
                    "Desktop-клиент использует общие модели, парсер и контракт туннеля из skipi-core.",
                    color = SettingsMuted,
                    fontSize = 14.sp,
                )
            }
        }

        SettingsGroup(title = "КОМПОНЕНТЫ") {
            SettingsInfoRow("skipi-core", "Общие модели прокси, подписок, конфигов и туннеля")
            SettingsInfoRow("Xray", "Встроенный runtime для Windows", showDivider = true)
            SettingsInfoRow("Хранилище", settingsPath.toString(), showDivider = true)
        }

        SettingsGroup(title = "ЛИЦЕНЗИЯ", bottomPadding = 0.dp) {
            SettingsPreferenceRow(
                title = "GPL-3.0",
                summary = "Исходный код и условия распространения находятся в корне проекта.",
                onClick = {
                    copyToClipboard("GPL-3.0").onSuccess {
                        onMessage("Название лицензии скопировано.")
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsScreenColumn(
    contentPadding: PaddingValues,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SettingsBackground)
            .padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 980.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад", tint = SettingsText)
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SettingsText, fontSize = 25.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = SettingsMuted, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    bottomPadding: androidx.compose.ui.unit.Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = SettingsMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SettingsSurface),
            border = BorderStroke(1.dp, SettingsBorder),
        ) {
            Column(content = content)
        }
        if (bottomPadding > 0.dp) Spacer(Modifier.height(bottomPadding))
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    iconBackground: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
    value: String? = null,
    showDivider: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBackground),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = SettingsText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    color = SettingsMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(10.dp))
                Text(value, color = SettingsMuted, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text("›", color = SettingsMuted, fontSize = 28.sp, lineHeight = 28.sp)
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                color = SettingsBorder.copy(alpha = 0.7f),
            )
        }
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = SettingsText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(summary, color = SettingsMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (!value.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(value, color = SettingsMuted, fontSize = 13.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text("›", color = SettingsMuted, fontSize = 25.sp)
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp),
                color = SettingsBorder.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    summary: String,
    accent: Color = SettingsText,
    showDivider: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text(title, color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(summary, color = SettingsMuted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp),
                color = SettingsBorder.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SettingsSwitchPreference(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, color = SettingsText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(summary, color = SettingsMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp),
                color = SettingsBorder.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun <T> SettingsDropdownPreference(
    title: String,
    summary: String,
    value: String,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsPreferenceRow(
            title = title,
            summary = summary,
            value = value,
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (item, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelected(item)
                    },
                )
            }
        }
    }
}

@Composable
private fun IntegrationCommandRow(
    command: String,
    description: String,
    onMessage: (String) -> Unit,
    showDivider: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(command, color = SettingsGreen, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(description, color = SettingsMuted, fontSize = 13.sp)
            }
            IconButton(
                onClick = {
                    copyToClipboard(command).fold(
                        onSuccess = { onMessage("Команда скопирована: $command") },
                        onFailure = { onMessage("Не удалось скопировать команду: ${it.message.orEmpty()}") },
                    )
                },
            ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Копировать", tint = SettingsText)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp),
                color = SettingsBorder.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SettingsStatusMessage(message: String) {
    if (message.isBlank()) return
    Text(
        message,
        color = SettingsMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

private fun DesktopThemeMode.displayName(): String = when (this) {
    DesktopThemeMode.Dark -> "Темная"
    DesktopThemeMode.Amoled -> "AMOLED"
}

private fun copyToClipboard(value: String): Result<Unit> = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value), null)
}

private val DesktopLogLevels = DesktopCoreLogLevels
private val DesktopSubscriptionTimeouts = listOf(10, 15, 20, 30, 45, 60, 90, 120)
