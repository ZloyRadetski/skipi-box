// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import app.CustomResourceFileState
import app.sanitizeCustomResourceFileName
import features.config.TrafficConfigState
import features.config.TrafficConfigAndroidSettings
import features.config.TrafficConfigNetworkActivation
import features.config.TrafficConfigResourceSettings
import features.logs.AndroidAppLogger
import features.networkautomation.model.NetworkAutomationRule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val appStateJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

internal object StringListJson {
    fun encode(values: List<String>): String {
        return appStateJson.encodeToString(values)
    }

    fun decode(payload: String): List<String> {
        return runCatching {
            appStateJson.decodeFromString<List<String>>(payload)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to decode persisted string list", error)
        }.getOrDefault(emptyList())
    }

    private const val LogTag = "AppStateJson"
}

internal object CustomResourceFileListJson {
    fun encode(values: List<CustomResourceFileState>): String {
        return appStateJson.encodeToString(values.map(PersistedCustomResourceFile::from))
    }

    fun decode(payload: String): List<CustomResourceFileState> {
        return runCatching {
            appStateJson.decodeFromString<List<PersistedCustomResourceFile>>(payload)
                .mapNotNull(PersistedCustomResourceFile::toStateOrNull)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to decode persisted custom resource files", error)
        }.getOrDefault(emptyList())
    }

    private const val LogTag = "AppStateJson"
}

internal object TrafficConfigListJson {
    fun encode(values: List<TrafficConfigState>): String {
        return appStateJson.encodeToString(values.map(PersistedTrafficConfig::from))
    }

    fun decode(payload: String): List<TrafficConfigState> {
        return runCatching {
            appStateJson.decodeFromString<List<PersistedTrafficConfig>>(payload)
                .mapNotNull(PersistedTrafficConfig::toStateOrNull)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to decode persisted traffic configs", error)
        }.getOrDefault(emptyList())
    }

    private const val LogTag = "AppStateJson"
}

internal object NetworkAutomationRuleListJson {
    fun encode(values: List<NetworkAutomationRule>): String {
        return appStateJson.encodeToString(values)
    }

    fun decode(payload: String): List<NetworkAutomationRule> {
        return runCatching {
            appStateJson.decodeFromString<List<NetworkAutomationRule>>(payload)
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to decode persisted network automation rules", error)
        }.getOrDefault(emptyList())
    }

    private const val LogTag = "AppStateJson"
}

@Serializable
private data class PersistedCustomResourceFile(
    val id: Int,
    val name: String,
    val url: String,
) {
    fun toStateOrNull(): CustomResourceFileState? {
        val fileName = sanitizeCustomResourceFileName(name, fallback = "")
        val updateUrl = url.trim()
        if (id <= 0 || fileName.isBlank()) return null
        return CustomResourceFileState(
            id = id,
            name = fileName,
            url = updateUrl,
        )
    }

    companion object {
        fun from(state: CustomResourceFileState): PersistedCustomResourceFile {
            return PersistedCustomResourceFile(
                id = state.id,
                name = state.name,
                url = state.url,
            )
        }
    }
}

@Serializable
private data class PersistedTrafficConfig(
    val id: Int,
    val name: String,
    val rawConfig: String,
    val sourceUrl: String,
    val updateLocked: Boolean,
    val lastUpdatedAtMillis: Long,
    val autoUpdate: Boolean = false,
    val updateInterval: String = "",
    val proxyAppListMode: Int,
    val proxyAppListSelectedApps: List<String>,
    val androidSettings: TrafficConfigAndroidSettings = TrafficConfigAndroidSettings(),
    val networkActivation: TrafficConfigNetworkActivation = TrafficConfigNetworkActivation(),
    val resourceSettings: TrafficConfigResourceSettings = TrafficConfigResourceSettings(),
) {
    fun toStateOrNull(): TrafficConfigState? {
        if (id <= 0 || name.isBlank() || rawConfig.isBlank()) return null
        return TrafficConfigState(
            id = id,
            name = name.trim(),
            rawConfig = rawConfig,
            sourceUrl = sourceUrl.trim(),
            updateLocked = updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis.coerceAtLeast(0L),
            autoUpdate = autoUpdate,
            updateInterval = updateInterval.trim(),
            proxyAppListMode = proxyAppListMode,
            proxyAppListSelectedApps = proxyAppListSelectedApps.distinct(),
            androidSettings = androidSettings,
            networkActivation = networkActivation,
            resourceSettings = resourceSettings,
        )
    }

    companion object {
        fun from(state: TrafficConfigState): PersistedTrafficConfig {
            return PersistedTrafficConfig(
                id = state.id,
                name = state.name,
                rawConfig = state.rawConfig,
                sourceUrl = state.sourceUrl,
                updateLocked = state.updateLocked,
                lastUpdatedAtMillis = state.lastUpdatedAtMillis,
                autoUpdate = state.autoUpdate,
                updateInterval = state.updateInterval,
                proxyAppListMode = state.proxyAppListMode,
                proxyAppListSelectedApps = state.proxyAppListSelectedApps,
                androidSettings = state.androidSettings,
                networkActivation = state.networkActivation,
                resourceSettings = state.resourceSettings,
            )
        }
    }
}
