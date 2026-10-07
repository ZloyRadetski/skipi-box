// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import app.skipi.app.config.withTrafficConfigBasics
import features.config.ConfigProfile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.analyzeShadowrocketConfig
import features.config.ShadowrocketConfigDiagnosticSeverity

/** Result of preparing one Desktop profile-editor save for the existing library adapter. */
internal sealed interface DesktopConfigEditorSaveResult {
    data class Saved(val library: DesktopConfigLibrary) : DesktopConfigEditorSaveResult
    data class Rejected(val message: String) : DesktopConfigEditorSaveResult
}

/**
 * Applies the profile editor's basics through the shared typed document writer, validates the
 * canonical content, then delegates identity, selection, and collection behavior to the existing
 * config-library operations.
 */
internal fun applyDesktopConfigEditorSave(
    library: DesktopConfigLibrary,
    id: Int?,
    lastUpdatedAtMillis: Long,
    name: String,
    content: String,
    sourceUrl: String,
    updateLocked: Boolean,
    resourceUserAgentFallback: String = versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME),
): DesktopConfigEditorSaveResult {
    val profile = ConfigProfile(
        id = id ?: 0,
        name = name,
        content = content.trimEnd().plus("\n"),
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        lastUpdatedAtMillis = lastUpdatedAtMillis,
    ).withTrafficConfigBasics(
        name = name,
        sourceUrl = sourceUrl,
        updateLocked = updateLocked,
        seed = TrafficConfigState(
            id = id ?: 0,
            name = name,
            rawConfig = content,
            resourceSettings = TrafficConfigResourceSettings(userAgent = resourceUserAgentFallback),
        ),
    )

    val validationError = profile.content.analyzeShadowrocketConfig().diagnostics
        .firstOrNull { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }
    if (validationError != null) {
        return DesktopConfigEditorSaveResult.Rejected(validationError.message)
    }

    val updatedLibrary = if (id == null) {
        DesktopConfigLibraries.put(
            library = library,
            name = profile.name,
            content = profile.content,
            sourceUrl = profile.sourceUrl,
            updateLocked = profile.updateLocked,
        )
    } else {
        DesktopConfigLibraries.update(
            library = library,
            id = id,
            name = profile.name,
            content = profile.content,
            sourceUrl = profile.sourceUrl,
            updateLocked = profile.updateLocked,
            lastUpdatedAtMillis = lastUpdatedAtMillis,
        )
    }

    return DesktopConfigEditorSaveResult.Saved(updatedLibrary)
}

internal fun versionedDesktopProfileResourceUserAgent(versionName: String): String =
    "Skipi/$versionName/Desktop"
