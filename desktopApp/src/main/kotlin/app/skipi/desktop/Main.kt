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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import features.config.analyzeShadowrocketConfig
import features.config.defaultShadowrocketConfig
import java.nio.file.Path

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SKIPI",
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                var profileText by remember { mutableStateOf(defaultShadowrocketConfig()) }
                var profilePath by remember { mutableStateOf<Path?>(null) }
                var fileMessage by remember { mutableStateOf("Create or open a .conf profile to begin.") }
                val profile by remember(profileText) { mutableStateOf(profileText.analyzeShadowrocketConfig()) }

                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("SKIPI Desktop", style = MaterialTheme.typography.headlineMedium)
                    Text("Portable profile editor. Tunnel integration is the next desktop milestone.")

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
