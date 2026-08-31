// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Hexagon
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import features.config.analyzeShadowrocketConfig
import features.config.defaultShadowrocketConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.DefaultLocalSocksPort
import platform.LocalProxyXrayConfigFactory
import java.nio.file.Path

@OptIn(ExperimentalMaterial3Api::class)
fun main() = application {
    val xrayController = remember { DesktopXrayProcessController() }
    Window(
        state = WindowState(size = DpSize(1180.dp, 860.dp)),
        onCloseRequest = {
            xrayController.stop()
            exitApplication()
        },
        title = "SKIPI",
    ) {
        MaterialTheme(colorScheme = SkipiDesktopColors) {
            Surface(modifier = Modifier.fillMaxSize(), color = SkipiBackground) {
                val subscriptionScope = rememberCoroutineScope()
                val subscriptionFetcher = remember { DesktopSubscriptionFetcher() }
                var profileText by remember { mutableStateOf(defaultShadowrocketConfig()) }
                var profilePath by remember { mutableStateOf<Path?>(null) }
                var configLibrary by remember { mutableStateOf(DesktopConfigLibraries.loadDefault().getOrElse { DesktopConfigLibrary() }) }
                var fileMessage by remember { mutableStateOf("Создайте или откройте профиль .conf.") }
                var serverLink by remember { mutableStateOf("") }
                var subscriptionUrl by remember { mutableStateOf("") }
                var subscriptionUpdate by remember { mutableStateOf<DesktopSubscriptionUpdate?>(null) }
                var subscriptionMessage by remember { mutableStateOf("") }
                var subscriptionLibrary by remember {
                    mutableStateOf(DesktopSubscriptionLibraries.loadDefault().getOrElse { DesktopSubscriptionLibrary() })
                }
                var currentSection by remember { mutableStateOf(DesktopSection.Proxy) }
                var serverLibrary by remember {
                    mutableStateOf(DesktopServerLibraries.loadDefault().getOrElse { DesktopServerLibrary() })
                }
                var serverLibraryMessage by remember { mutableStateOf("") }
                var xrayProcessState by remember { mutableStateOf(xrayController.state()) }
                var xrayProcessMessage by remember { mutableStateOf("") }
                val profile = remember(profileText) { profileText.analyzeShadowrocketConfig() }
                val selectedServerConfig = remember(serverLibrary) {
                    serverLibrary.selectedServerId
                        ?.let { selectedId -> serverLibrary.servers.firstOrNull { it.id == selectedId } }
                        ?.decode()
                        ?.mapCatching(LocalProxyXrayConfigFactory::build)
                }

                Scaffold(
                    topBar = {
                        if (currentSection != DesktopSection.Proxy) TopAppBar(title = { Text("SKIPI") })
                    },
                    bottomBar = {
                        DesktopBottomNavigation(
                            selected = currentSection,
                            onSelect = { currentSection = it },
                        )
                    },
                ) { contentPadding ->
                    when (currentSection) {
                        DesktopSection.Proxy -> DesktopProxyHome(
                            serverLibrary = serverLibrary,
                            subscriptionLibrary = subscriptionLibrary,
                            subscriptionUpdate = subscriptionUpdate,
                            serverLink = serverLink,
                            subscriptionUrl = subscriptionUrl,
                            running = xrayProcessState.isRunning,
                            canToggleTunnel = selectedServerConfig?.isSuccess == true,
                            tunnelMessage = xrayProcessMessage,
                            serverMessage = serverLibraryMessage,
                            subscriptionMessage = subscriptionMessage,
                            onServerLinkChange = { serverLink = it },
                            onSubscriptionUrlChange = { url ->
                                subscriptionUrl = url
                                subscriptionUpdate = null
                                subscriptionMessage = ""
                            },
                            onToggleTunnel = {
                                if (xrayProcessState.isRunning) {
                                    xrayController.stop().onSuccess { state ->
                                        xrayProcessState = state
                                        xrayProcessMessage = "Локальный туннель Xray остановлен."
                                    }.onFailure { error ->
                                        xrayProcessMessage = "Не удалось остановить Xray: ${error.message.orEmpty()}"
                                    }
                                } else {
                                    selectedServerConfig?.getOrNull()?.let { config ->
                                        xrayController.start(config).onSuccess { state ->
                                            xrayProcessState = state
                                            xrayProcessMessage = "Туннель запущен, PID ${state.pid}."
                                        }.onFailure { error ->
                                            xrayProcessMessage = "Не удалось запустить Xray: ${error.message.orEmpty()}"
                                        }
                                    }
                                }
                            },
                            onSelectServer = { serverId ->
                                runCatching { DesktopServerLibraries.select(serverLibrary, serverId) }
                                    .onSuccess { updated ->
                                        DesktopServerLibraries.saveDefault(updated).onSuccess {
                                            serverLibrary = updated
                                            serverLibraryMessage = "Сервер выбран."
                                        }.onFailure { error ->
                                            serverLibraryMessage = "Не удалось сохранить выбор: ${error.message.orEmpty()}"
                                        }
                                    }
                            },
                            onDeleteServer = { serverId ->
                                val updated = DesktopServerLibraries.remove(serverLibrary, serverId)
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLibraryMessage = "Сервер удалён."
                                }.onFailure { error ->
                                    serverLibraryMessage = "Не удалось удалить сервер: ${error.message.orEmpty()}"
                                }
                            },
                            onAddServer = { server ->
                                val updated = DesktopServerLibraries.add(serverLibrary, server)
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLink = ""
                                    serverLibraryMessage = "Сервер добавлен и выбран."
                                }.onFailure { error ->
                                    serverLibraryMessage = "Не удалось сохранить сервер: ${error.message.orEmpty()}"
                                }
                            },
                            onUpdateSubscription = {
                                val requestedUrl = subscriptionUrl.trim()
                                subscriptionScope.launch {
                                    subscriptionMessage = "Обновление подписки…"
                                    subscriptionUpdate = null
                                    runCatching {
                                        withContext(Dispatchers.IO) { subscriptionFetcher.fetchAndImport(requestedUrl) }
                                    }.onSuccess { update ->
                                        subscriptionUpdate = update
                                        runCatching {
                                            DesktopSubscriptionLibraries.addOrReplace(
                                                library = subscriptionLibrary,
                                                url = requestedUrl,
                                                userAgent = DefaultDesktopSubscriptionUserAgent,
                                                name = update.metadata.profileTitle.orEmpty(),
                                            )
                                        }.onSuccess { updated ->
                                            DesktopSubscriptionLibraries.saveDefault(updated).onSuccess {
                                                subscriptionLibrary = updated
                                                subscriptionUrl = requestedUrl
                                                subscriptionMessage = "Подписка сохранена. Получено серверов: " +
                                                    "${update.importResult.servers.size}; отклонено: " +
                                                    "${update.importResult.rejectedUrlCount}."
                                            }.onFailure { error ->
                                                subscriptionMessage = "Список получен, но подписка не сохранена: " +
                                                    error.message.orEmpty()
                                            }
                                        }.onFailure { error ->
                                            subscriptionMessage = "Список получен, но ссылка некорректна: " +
                                                error.message.orEmpty()
                                        }
                                    }.onFailure { error ->
                                        subscriptionMessage = "Не удалось обновить подписку: ${error.message.orEmpty()}"
                                    }
                                }
                            },
                            onImportSubscriptionServers = {
                                val imported = subscriptionUpdate?.importResult?.servers.orEmpty()
                                var updated = serverLibrary
                                imported.forEach { server -> updated = DesktopServerLibraries.add(updated, server) }
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLibraryMessage = "Добавлено серверов: ${imported.size}."
                                }.onFailure { error ->
                                    serverLibraryMessage = "Не удалось сохранить серверы: ${error.message.orEmpty()}"
                                }
                            },
                            onSaveSubscription = saveSubscription@{
                                val updated = runCatching {
                                    DesktopSubscriptionLibraries.addOrReplace(
                                        library = subscriptionLibrary,
                                        url = subscriptionUrl,
                                        userAgent = DefaultDesktopSubscriptionUserAgent,
                                        name = subscriptionUpdate?.metadata?.profileTitle.orEmpty(),
                                    )
                                }.getOrElse { error ->
                                    subscriptionMessage = "Некорректная подписка: ${error.message.orEmpty()}"
                                    return@saveSubscription false
                                }
                                val saveResult = DesktopSubscriptionLibraries.saveDefault(updated)
                                saveResult.onSuccess {
                                    subscriptionLibrary = updated
                                    subscriptionUrl = subscriptionUrl.trim()
                                    subscriptionMessage = "Подписка сохранена."
                                }.onFailure { error ->
                                    subscriptionMessage = "Не удалось сохранить подписку: ${error.message.orEmpty()}"
                                }
                                saveResult.isSuccess
                            },
                            onDeleteSubscription = { subscriptionId ->
                                val updated = DesktopSubscriptionLibraries.remove(subscriptionLibrary, subscriptionId)
                                DesktopSubscriptionLibraries.saveDefault(updated).onSuccess {
                                    subscriptionLibrary = updated
                                    subscriptionUrl = ""
                                    subscriptionMessage = "Подписка удалена."
                                }.onFailure { error ->
                                    subscriptionMessage = "Не удалось удалить подписку: ${error.message.orEmpty()}"
                                }
                            },
                            contentPadding = contentPadding,
                        )

                        DesktopSection.Configs -> DesktopConfigsPage(
                            profileText = profileText,
                            profilePath = profilePath,
                            configLibrary = configLibrary,
                            fileMessage = fileMessage,
                            ruleCount = profile.rules.size,
                            proxyGroupCount = profile.proxyGroups.size,
                            diagnosticCount = profile.diagnostics.size,
                            onProfileTextChange = { profileText = it },
                            onProfilePathChange = { profilePath = it },
                            onLibraryChange = { configLibrary = it },
                            onMessageChange = { fileMessage = it },
                            modifier = Modifier.padding(contentPadding),
                        )

                        DesktopSection.Settings -> DesktopSettingsPage(modifier = Modifier.padding(contentPadding))
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopConfigsPage(
    profileText: String,
    profilePath: Path?,
    configLibrary: DesktopConfigLibrary,
    fileMessage: String,
    ruleCount: Int,
    proxyGroupCount: Int,
    diagnosticCount: Int,
    onProfileTextChange: (String) -> Unit,
    onProfilePathChange: (Path?) -> Unit,
    onLibraryChange: (DesktopConfigLibrary) -> Unit,
    onMessageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Конфиги", style = MaterialTheme.typography.headlineMedium)
        Text("Конфигурации трафика Shadowrocket / SKIPI")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                DesktopProfileFiles.chooseProfileToOpen()?.let { path ->
                    DesktopProfileFiles.read(path).onSuccess { content ->
                        onProfileTextChange(content)
                        onProfilePathChange(path)
                        onMessageChange("Открыт ${path.fileName}")
                    }.onFailure { error -> onMessageChange("Не удалось открыть профиль: ${error.message.orEmpty()}") }
                }
            }) { Text("Открыть .conf") }
            Button(enabled = profilePath != null, onClick = {
                profilePath?.let { path ->
                    DesktopProfileFiles.write(path, profileText).onSuccess { onMessageChange("Сохранён ${path.fileName}") }
                }
            }) { Text("Сохранить") }
            Button(onClick = {
                DesktopProfileFiles.chooseProfileToSave()?.let { path ->
                    DesktopProfileFiles.write(path, profileText).onSuccess {
                        onProfilePathChange(path)
                        onMessageChange("Сохранён ${path.fileName}")
                    }.onFailure { error -> onMessageChange("Не удалось сохранить профиль: ${error.message.orEmpty()}") }
                }
            }) { Text("Сохранить как…") }
        }
        Text(fileMessage)
        Button(onClick = {
            val name = profilePath?.fileName?.toString()?.substringBeforeLast('.') ?: "Config ${configLibrary.configs.size + 1}"
            runCatching { DesktopConfigLibraries.put(configLibrary, name, profileText) }.onSuccess { updated ->
                DesktopConfigLibraries.saveDefault(updated).onSuccess {
                    onLibraryChange(updated)
                    onMessageChange("Конфиг сохранён в библиотеку.")
                }
            }
        }) { Text("Сохранить в библиотеку") }
        configLibrary.configs.forEach { stored ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val updated = DesktopConfigLibraries.select(configLibrary, stored.id)
                    DesktopConfigLibraries.saveDefault(updated).onSuccess {
                        onLibraryChange(updated)
                        onProfileTextChange(stored.content)
                        onMessageChange("Выбран ${stored.name}.")
                    }
                }) { Text((if (stored.id == configLibrary.selectedConfigId) "✓ " else "") + stored.name) }
                Button(onClick = {
                    val updated = DesktopConfigLibraries.remove(configLibrary, stored.id)
                    DesktopConfigLibraries.saveDefault(updated).onSuccess { onLibraryChange(updated) }
                }) { Text("Удалить") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("Правил: $ruleCount")
            Text("Прокси-групп: $proxyGroupCount")
            Text("Диагностик: $diagnosticCount")
        }
        OutlinedTextField(
            value = profileText,
            onValueChange = onProfileTextChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = { Text("Профиль Shadowrocket / SKIPI") },
        )
    }
}

@Composable
private fun DesktopSettingsPage(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        SettingsCard("Локальный прокси", "SOCKS5 · 127.0.0.1:$DefaultLocalSocksPort")
        SettingsCard("Хранилище", "Серверы и подписки хранятся в каталоге SKIPI пользователя")
        SettingsCard("Ядро", "Общие модели и разбор ссылок: skipi-core\nТуннель Windows: встроенный Xray")
    }
}

@Composable
private fun SettingsCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(body)
        }
    }
}

@Composable
private fun DesktopBottomNavigation(
    selected: DesktopSection,
    onSelect: (DesktopSection) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().background(SkipiBackground).padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 980.dp).height(82.dp),
            color = SkipiCard,
            shape = RoundedCornerShape(38.dp),
            border = BorderStroke(1.dp, Color(0xFF35373E)),
            shadowElevation = 10.dp,
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                DesktopSection.entries.forEach { section ->
                    val isSelected = section == selected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(30.dp))
                            .background(if (isSelected) Color(0xFF747474) else Color.Transparent)
                            .clickable { onSelect(section) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            section.icon(),
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFFF4F4F6) else SkipiMuted,
                        )
                        Text(
                            section.title,
                            color = if (isSelected) Color(0xFFF4F4F6) else SkipiMuted,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

private enum class DesktopSection(val title: String) {
    Proxy("Прокси"),
    Configs("Конфиги"),
    Settings("Настройки");

    fun icon() = when (this) {
        Proxy -> Icons.AutoMirrored.Outlined.List
        Configs -> Icons.Outlined.Tune
        Settings -> Icons.Outlined.Hexagon
    }
}

private val SkipiBackground = Color(0xFF141519)
private val SkipiCard = Color(0xFF202126)
private val SkipiMuted = Color(0xFF9A9DA8)
private val SkipiDesktopColors = darkColorScheme(
    background = SkipiBackground,
    surface = SkipiCard,
    surfaceContainer = SkipiCard,
    primary = Color(0xFFF1F1F3),
    onPrimary = Color(0xFF202126),
    onBackground = Color(0xFFF1F1F3),
    onSurface = Color(0xFFF1F1F3),
    secondary = SkipiMuted,
)
