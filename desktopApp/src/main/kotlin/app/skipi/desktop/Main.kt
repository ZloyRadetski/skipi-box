// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import features.config.analyzeShadowrocketConfig
import features.config.defaultShadowrocketConfig
import features.proxy.server.model.ProxyServer
import features.proxy.server.model.getTransportDisplay
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
        onCloseRequest = {
            xrayController.stop()
            exitApplication()
        },
        title = "SKIPI",
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                val subscriptionScope = rememberCoroutineScope()
                val subscriptionFetcher = remember { DesktopSubscriptionFetcher() }
                var profileText by remember { mutableStateOf(defaultShadowrocketConfig()) }
                var profilePath by remember { mutableStateOf<Path?>(null) }
                var configLibrary by remember { mutableStateOf(DesktopConfigLibraries.loadDefault().getOrElse { DesktopConfigLibrary() }) }
                var fileMessage by remember { mutableStateOf("Create or open a .conf profile to begin.") }
                var serverLink by remember { mutableStateOf("") }
                var subscriptionUrl by remember { mutableStateOf("") }
                var subscriptionUpdate by remember { mutableStateOf<DesktopSubscriptionUpdate?>(null) }
                var subscriptionMessage by remember { mutableStateOf("") }
                var subscriptionLibrary by remember {
                    mutableStateOf(DesktopSubscriptionLibraries.loadDefault().getOrElse { DesktopSubscriptionLibrary() })
                }
                var subscriptionLibraryMessage by remember {
                    mutableStateOf("Saved subscriptions: ${DesktopSubscriptionLibraries.defaultPath()}")
                }
                var currentSection by remember { mutableStateOf(DesktopSection.Proxy) }
                var serverLibrary by remember {
                    mutableStateOf(DesktopServerLibraries.loadDefault().getOrElse { DesktopServerLibrary() })
                }
                var serverLibraryMessage by remember {
                    mutableStateOf("Desktop server library: ${DesktopServerLibraries.defaultPath()}")
                }
                var xrayProcessState by remember { mutableStateOf(xrayController.state()) }
                var xrayProcessMessage by remember { mutableStateOf("") }
                val profile by remember(profileText) { mutableStateOf(profileText.analyzeShadowrocketConfig()) }
                val serverPreview = remember(serverLink) {
                    serverLink.trim().takeIf(String::isNotEmpty)?.let { link ->
                        runCatching { ProxyServer.parse(link) }
                    }
                }
                val selectedServerConfig = remember(serverLibrary) {
                    serverLibrary.selectedServerId
                        ?.let { selectedId -> serverLibrary.servers.firstOrNull { it.id == selectedId } }
                        ?.decode()
                        ?.mapCatching(LocalProxyXrayConfigFactory::build)
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("SKIPI") }) },
                    bottomBar = {
                        NavigationBar {
                            DesktopSection.entries.forEach { section ->
                                NavigationBarItem(
                                    selected = currentSection == section,
                                    onClick = { currentSection = section },
                                    icon = { Text(section.symbol) },
                                    label = { Text(section.title) },
                                )
                            }
                        }
                    },
                ) { contentPadding ->
                    if (currentSection == DesktopSection.Configs) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("Configs", style = MaterialTheme.typography.headlineMedium)
                            Text("Shadowrocket / SKIPI traffic configurations")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    DesktopProfileFiles.chooseProfileToOpen()?.let { path ->
                                        DesktopProfileFiles.read(path).onSuccess { content ->
                                            profileText = content; profilePath = path; fileMessage = "Opened ${path.fileName}"
                                        }.onFailure { error -> fileMessage = "Could not open profile: ${error.message.orEmpty()}" }
                                    }
                                }) { Text("Open .conf") }
                                Button(enabled = profilePath != null, onClick = {
                                    profilePath?.let { path -> DesktopProfileFiles.write(path, profileText).onSuccess { fileMessage = "Saved ${path.fileName}" } }
                                }) { Text("Save") }
                                Button(onClick = {
                                    DesktopProfileFiles.chooseProfileToSave()?.let { path ->
                                        DesktopProfileFiles.write(path, profileText).onSuccess {
                                            profilePath = path; fileMessage = "Saved ${path.fileName}"
                                        }.onFailure { error -> fileMessage = "Could not save profile: ${error.message.orEmpty()}" }
                                    }
                                }) { Text("Save as…") }
                            }
                            Text(fileMessage)
                            Button(onClick = {
                                val name = profilePath?.fileName?.toString()?.substringBeforeLast('.') ?: "Config ${configLibrary.configs.size + 1}"
                                runCatching { DesktopConfigLibraries.put(configLibrary, name, profileText) }.onSuccess { updated ->
                                    DesktopConfigLibraries.saveDefault(updated).onSuccess { configLibrary = updated; fileMessage = "Config saved to library." }
                                }
                            }) { Text("Save to library") }
                            configLibrary.configs.forEach { stored ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        val updated = DesktopConfigLibraries.select(configLibrary, stored.id)
                                        DesktopConfigLibraries.saveDefault(updated).onSuccess {
                                            configLibrary = updated; profileText = stored.content; fileMessage = "Selected ${stored.name}."
                                        }
                                    }) { Text((if (stored.id == configLibrary.selectedConfigId) "✓ " else "") + stored.name) }
                                    Button(onClick = {
                                        val updated = DesktopConfigLibraries.remove(configLibrary, stored.id)
                                        DesktopConfigLibraries.saveDefault(updated).onSuccess { configLibrary = updated }
                                    }) { Text("Delete") }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                Text("Rules: ${profile.rules.size}")
                                Text("Proxy groups: ${profile.proxyGroups.size}")
                                Text("Diagnostics: ${profile.diagnostics.size}")
                            }
                            OutlinedTextField(value = profileText, onValueChange = { profileText = it }, modifier = Modifier.fillMaxWidth().weight(1f), label = { Text("Shadowrocket / SKIPI profile") })
                        }
                    } else if (currentSection == DesktopSection.Settings) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("Settings", style = MaterialTheme.typography.headlineMedium)
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Local proxy", style = MaterialTheme.typography.titleLarge)
                                    Text("SOCKS5 • 127.0.0.1:$DefaultLocalSocksPort")
                                    Text(if (xrayProcessState.isRunning) "Running" else "Stopped")
                                }
                            }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Storage", style = MaterialTheme.typography.titleLarge)
                                    Text("Servers: ${DesktopServerLibraries.defaultPath()}")
                                    Text("Subscriptions: ${DesktopSubscriptionLibraries.defaultPath()}")
                                }
                            }
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Core", style = MaterialTheme.typography.titleLarge)
                                    Text("Android: SKIPI Core")
                                    Text("Desktop: bundled Xray adapter")
                                }
                            }
                        }
                    } else Column(
                    modifier = Modifier.fillMaxSize().padding(contentPadding).verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("SKIPI Desktop", style = MaterialTheme.typography.headlineMedium)
                    Text("Connection")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (xrayProcessState.isRunning) "Connected" else "Not connected",
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                when {
                                    selectedServerConfig == null -> "Choose a server to connect"
                                    selectedServerConfig.isFailure -> selectedServerConfig.exceptionOrNull()?.message.orEmpty()
                                    else -> "${serverLibrary.servers.size} servers available • local SOCKS 127.0.0.1:$DefaultLocalSocksPort"
                                },
                            )
                            if (selectedServerConfig?.isSuccess == true) {
                                Button(onClick = {
                                    if (xrayProcessState.isRunning) {
                                        xrayController.stop().onSuccess { state ->
                                            xrayProcessState = state
                                            xrayProcessMessage = "Local Xray tunnel stopped."
                                        }.onFailure { error ->
                                            xrayProcessMessage = "Could not stop Xray: ${error.message.orEmpty()}"
                                        }
                                    } else {
                                        val config = selectedServerConfig.getOrNull() ?: return@Button
                                        xrayController.start(config).onSuccess { state ->
                                            xrayProcessState = state
                                            xrayProcessMessage = "Local Xray tunnel started (PID ${state.pid})."
                                        }.onFailure { error ->
                                            xrayProcessMessage = "Could not start Xray: ${error.message.orEmpty()}"
                                        }
                                    }
                                }) {
                                    Text(if (xrayProcessState.isRunning) "Disconnect" else "Connect")
                                }
                            }
                            if (xrayProcessMessage.isNotBlank()) Text(xrayProcessMessage)
                        }
                    }
                    Text("Subscriptions", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = subscriptionUrl,
                        onValueChange = { subscriptionUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Subscription URL (HTTP/HTTPS)") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            subscriptionScope.launch {
                                subscriptionMessage = "Updating subscription…"
                                subscriptionUpdate = null
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        subscriptionFetcher.fetchAndImport(subscriptionUrl)
                                    }
                                }.onSuccess { update ->
                                    subscriptionUpdate = update
                                    subscriptionMessage = "Subscription contains ${update.importResult.servers.size} valid servers " +
                                        "(${update.importResult.rejectedUrlCount} rejected)."
                                }.onFailure { error ->
                                    subscriptionMessage = "Could not update subscription: ${error.message.orEmpty()}"
                                }
                            }
                        }) {
                            Text("Update subscription")
                        }
                        val importResult = subscriptionUpdate?.importResult
                        if (importResult?.servers?.isNotEmpty() == true) {
                            Button(onClick = {
                                var updated = serverLibrary
                                importResult.servers.forEach { server ->
                                    updated = DesktopServerLibraries.add(updated, server)
                                }
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLibraryMessage = "Added ${importResult.servers.size} subscription servers to the library."
                                }.onFailure { error ->
                                    serverLibraryMessage = "Could not save subscription servers: ${error.message.orEmpty()}"
                                }
                            }) {
                                Text("Add ${importResult.servers.size} servers")
                            }
                        }
                        if (subscriptionUpdate != null) {
                            Button(onClick = {
                                val updated = runCatching {
                                    DesktopSubscriptionLibraries.addOrReplace(
                                        library = subscriptionLibrary,
                                        url = subscriptionUrl,
                                        userAgent = DefaultDesktopSubscriptionUserAgent,
                                        name = subscriptionUpdate?.metadata?.profileTitle.orEmpty(),
                                    )
                                }
                                updated.onSuccess { library ->
                                    DesktopSubscriptionLibraries.saveDefault(library).onSuccess {
                                        subscriptionLibrary = library
                                        subscriptionLibraryMessage = "Subscription saved."
                                    }.onFailure { error ->
                                        subscriptionLibraryMessage = "Could not save subscription: ${error.message.orEmpty()}"
                                    }
                                }.onFailure { error ->
                                    subscriptionLibraryMessage = "Could not save subscription: ${error.message.orEmpty()}"
                                }
                            }) {
                                Text("Save subscription")
                            }
                        }
                    }
                    if (subscriptionMessage.isNotBlank()) Text(subscriptionMessage)
                    subscriptionUpdate?.metadata?.let { metadata ->
                        metadata.profileTitle?.let { Text("Subscription: $it") }
                        if (metadata.trafficTotalBytes >= 0L) {
                            Text(
                                "Traffic: ${metadata.trafficUploadBytes.coerceAtLeast(0L) + metadata.trafficDownloadBytes.coerceAtLeast(0L)}" +
                                    " / ${metadata.trafficTotalBytes} bytes",
                            )
                        }
                        metadata.announce?.let { Text("Announcement: $it") }
                    }
                    Text("Subscriptions: ${subscriptionLibrary.subscriptions.size}")
                    subscriptionLibrary.subscriptions.forEach { subscription ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                subscriptionUrl = subscription.url
                                subscriptionLibraryMessage = "Selected ${subscription.name.ifBlank { subscription.url }}."
                            }) { Text(subscription.name.ifBlank { subscription.url }) }
                            Button(onClick = {
                                val updated = DesktopSubscriptionLibraries.remove(subscriptionLibrary, subscription.id)
                                DesktopSubscriptionLibraries.saveDefault(updated).onSuccess {
                                    subscriptionLibrary = updated
                                    subscriptionLibraryMessage = "Subscription deleted."
                                }
                            }) { Text("Delete") }
                        }
                    }
                    Text(subscriptionLibraryMessage)

                    Text("Servers", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = serverLink,
                        onValueChange = { serverLink = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Server link preview (vless://, vmess://, ss://, …)") },
                        singleLine = true,
                    )
                    when {
                        serverPreview == null -> Text("Paste a server link to validate it with the shared core.")
                        serverPreview.isFailure -> Text(
                            "Unsupported or invalid server link: ${serverPreview.exceptionOrNull()?.message.orEmpty()}",
                        )
                        else -> {
                            val info = serverPreview.getOrThrow().getInfo()
                            val transport = serverPreview.getOrThrow().getTransportDisplay().orEmpty()
                            Text("${info.protocol}: ${info.remarks} — ${info.address}")
                            if (transport.isNotBlank()) Text("Transport: $transport")
                            Button(onClick = {
                                val updated = DesktopServerLibraries.add(serverLibrary, serverPreview.getOrThrow())
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLibraryMessage = "Added ${info.remarks} to the desktop library."
                                }.onFailure { error ->
                                    serverLibraryMessage = "Could not save the server library: ${error.message.orEmpty()}"
                                }
                            }) {
                                Text("Add to library")
                            }
                        }
                    }

                    Text("Saved servers: ${serverLibrary.servers.size}")
                    serverLibrary.servers.forEach { stored ->
                        val storedServer = stored.decode().getOrNull()
                        val info = storedServer?.getInfo()
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val updated = DesktopServerLibraries.select(serverLibrary, stored.id)
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLibraryMessage = "Selected ${info?.remarks ?: "server ${stored.id}"}."
                                }.onFailure { error ->
                                    serverLibraryMessage = "Could not save the server selection: ${error.message.orEmpty()}"
                                }
                            }) {
                                val marker = if (stored.id == serverLibrary.selectedServerId) "✓ " else ""
                                Text(marker + (info?.let { "${it.remarks} — ${it.address}" } ?: "Unreadable saved server ${stored.id}"))
                            }
                            Button(onClick = {
                                val updated = DesktopServerLibraries.remove(serverLibrary, stored.id)
                                DesktopServerLibraries.saveDefault(updated).onSuccess {
                                    serverLibrary = updated
                                    serverLibraryMessage = "Server deleted."
                                }
                            }) { Text("Delete") }
                        }
                    }
                    Text(serverLibraryMessage)
                    when {
                        selectedServerConfig == null -> Text("Select a saved server to prepare a local tunnel configuration.")
                        selectedServerConfig.isSuccess -> Text(
                            "Selected server is ready for a local SOCKS tunnel at 127.0.0.1:$DefaultLocalSocksPort.",
                        )
                        else -> Text(
                            "Selected server cannot start yet: ${selectedServerConfig.exceptionOrNull()?.message.orEmpty()}",
                        )
                    }
                }
                }
            }
        }
    }
}

private enum class DesktopSection(val title: String, val symbol: String) {
    Proxy("Proxy", "●"),
    Configs("Configs", "◇"),
    Settings("Settings", "⚙"),
}
