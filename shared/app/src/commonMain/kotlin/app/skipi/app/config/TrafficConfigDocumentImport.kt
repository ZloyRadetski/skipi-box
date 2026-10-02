// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.config

import features.config.ShadowrocketConfigDiagnosticSeverity
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.withSkipiSettingsInRawConfig
import features.config.withSkipiSettingsReadFromRawConfig

data class TrafficConfigDocumentImportResult(
    val trafficConfigs: List<TrafficConfigState>,
    val nextTrafficConfigId: Int,
    val activeTrafficConfigId: Int,
    val importedConfigId: Int,
    val created: Boolean,
)

/** Imports or refreshes a portable profile document without platform state or side effects. */
fun importTrafficConfigDocument(
    trafficConfigs: List<TrafficConfigState>,
    nextTrafficConfigId: Int,
    activeTrafficConfigId: Int,
    content: String,
    activate: Boolean,
    fallbackName: String = "Config",
    sourceUrl: String = "",
    newProfileResourceSettings: TrafficConfigResourceSettings = TrafficConfigResourceSettings(),
): TrafficConfigDocumentImportResult {
    val normalized = content.trimEnd() + "\n"
    require(normalized.isNotBlank()) { "Configuration is empty" }
    val analysis = normalized.analyzeShadowrocketConfig()
    require(analysis.diagnostics.none { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }) {
        analysis.diagnostics.first { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }.message
    }
    val configName = normalized.lineSequence()
        .firstOrNull { line -> line.trim().startsWith("#") && line.contains("name", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf(String::isNotBlank)

    val cleanSourceUrl = sourceUrl.trim()
    val existing = trafficConfigs.firstOrNull { config ->
        when {
            cleanSourceUrl.isNotBlank() && config.sourceUrl.isNotBlank() && config.sourceUrl.trim().equals(cleanSourceUrl, ignoreCase = true) -> true
            configName != null && config.name.trim().equals(configName, ignoreCase = true) -> true
            fallbackName.isNotBlank() && fallbackName != "Config" && config.name.trim().equals(fallbackName.trim(), ignoreCase = true) -> true
            else -> false
        }
    }
    if (existing != null) {
        val effectiveSourceUrl = if (cleanSourceUrl.isNotBlank()) cleanSourceUrl else existing.sourceUrl
        val updated = existing.copy(
            rawConfig = normalized,
            name = configName ?: existing.name,
            sourceUrl = effectiveSourceUrl,
        ).withSkipiSettingsReadFromRawConfig().let { parsed ->
            parsed.copy(
                sourceUrl = effectiveSourceUrl.ifBlank { parsed.sourceUrl },
                name = configName ?: existing.name,
            ).withSkipiSettingsInRawConfig()
        }
        return TrafficConfigDocumentImportResult(
            trafficConfigs = trafficConfigs.map { if (it.id == existing.id) updated else it },
            nextTrafficConfigId = nextTrafficConfigId,
            activeTrafficConfigId = activeTrafficConfigId,
            importedConfigId = existing.id,
            created = false,
        )
    }

    val configId = nextTrafficConfigId
    val name = configName ?: if (fallbackName.isNotBlank() && fallbackName != "Config") fallbackName else "$fallbackName $configId"
    val imported = TrafficConfigState(
        id = configId,
        name = name,
        rawConfig = normalized,
        sourceUrl = cleanSourceUrl,
        resourceSettings = newProfileResourceSettings,
    ).withSkipiSettingsReadFromRawConfig().let { parsed ->
        parsed.copy(sourceUrl = cleanSourceUrl.ifBlank { parsed.sourceUrl }).withSkipiSettingsInRawConfig()
    }
    return TrafficConfigDocumentImportResult(
        trafficConfigs = trafficConfigs + imported,
        nextTrafficConfigId = configId + 1,
        activeTrafficConfigId = if (activate) configId else activeTrafficConfigId,
        importedConfigId = configId,
        created = true,
    )
}
