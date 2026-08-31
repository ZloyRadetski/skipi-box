// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            DesktopProfileFiles.chooseProfileToOpen()?.let { path ->
                                DesktopProfileFiles.read(path).onSuccess { content ->
                                    profileText = content
                                    profilePath = path
                                    fileMessage = "Opened ${path.fileName}"
                                }.onFailure { error ->
                                    fileMessage = "Could not open profile: ${error.message.orEmpty()}"
                                }
                            }
                        }) {
                            Text("Open .conf")
                        }
                        Button(
                            enabled = profilePath != null,
                            onClick = {
                                val path = profilePath ?: return@Button
                                DesktopProfileFiles.write(path, profileText).onSuccess {
                                    fileMessage = "Saved ${path.fileName}"
                                }.onFailure { error ->
                                    fileMessage = "Could not save profile: ${error.message.orEmpty()}"
                                }
                            },
                        ) {
                            Text("Save")
                        }
                        Button(onClick = {
                            DesktopProfileFiles.chooseProfileToSave()?.let { path ->
                                DesktopProfileFiles.write(path, profileText).onSuccess {
                                    profilePath = path
                                    fileMessage = "Saved ${path.fileName}"
                                }.onFailure { error ->
                                    fileMessage = "Could not save profile: ${error.message.orEmpty()}"
                                }
                            }
                        }) {
                            Text("Save as…")
                        }
                    }
                    Text(fileMessage)

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text("Rules: ${profile.rules.size}")
                        Text("Proxy groups: ${profile.proxyGroups.size}")
                        Text("Diagnostics: ${profile.diagnostics.size}")
                    }

                    Text("Servers", style = MaterialTheme.typography.titleLarge)
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
                    subscriptionLibrary.subscriptions.take(3).forEach { subscription ->
                        Button(onClick = {
                            subscriptionUrl = subscription.url
                            subscriptionLibraryMessage = "Selected ${subscription.name.ifBlank { subscription.url }}."
                        }) {
                            Text(subscription.name.ifBlank { subscription.url })
                        }
                    }
                    if (subscriptionLibrary.subscriptions.size > 3) Text("Showing the first 3 subscriptions.")
                    Text(subscriptionLibraryMessage)

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
                    serverLibrary.servers.take(3).forEach { stored ->
                        val storedServer = stored.decode().getOrNull()
                        val info = storedServer?.getInfo()
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
                    }
                    if (serverLibrary.servers.size > 3) Text("Showing the first 3 saved servers.")
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
                    Text("Profile settings", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = profileText,
                        onValueChange = { profileText = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        label = { Text("Shadowrocket / SKIPI profile") },
                    )
                }
            }
        }
    }
}
