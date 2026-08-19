// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package desktop

import app.AppState
import data.AppStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DesktopSettingsStore : AppStateStore {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val configFile: File by lazy {
        val userHome = System.getProperty("user.home") ?: "."
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        val dir = when {
            os.contains("win") -> File(System.getenv("APPDATA") ?: userHome, "SKIPI")
            os.contains("mac") -> File(userHome, "Library/Application Support/SKIPI")
            else -> File(userHome, ".config/skipi")
        }
        dir.mkdirs()
        File(dir, "settings.json")
    }

    private val mutableState = MutableStateFlow(loadInitialState())
    override val state: StateFlow<AppState> = mutableState.asStateFlow()

    override fun update(transform: (AppState) -> AppState) {
        val next = transform(mutableState.value)
        mutableState.value = next
        scope.launch {
            save(next)
        }
    }

    override suspend fun resetToStockState() {
        val stock = AppState()
        mutableState.value = stock
        save(stock)
    }

    private fun loadInitialState(): AppState {
        return runCatching {
            if (configFile.exists()) {
                json.decodeFromString<AppState>(configFile.readText())
            } else {
                AppState().also(::save)
            }
        }.getOrDefault(AppState())
    }

    private fun save(appState: AppState) {
        runCatching {
            configFile.writeText(json.encodeToString(appState))
        }
    }
}
