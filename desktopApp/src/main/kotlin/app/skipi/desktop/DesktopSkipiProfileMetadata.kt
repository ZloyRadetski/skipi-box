// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.shadowrocketSectionValue
import features.config.withShadowrocketSectionValue

/**
 * The portable slice of Android's `[SKIPI]` profile metadata that has desktop
 * behaviour today. Keeping it in the raw profile makes a `.conf` round-trip
 * between Android and Desktop without dropping the Android-only keys.
 */
internal data class DesktopSkipiProfileMetadata(
    val name: String? = null,
    val sourceUrl: String? = null,
    val updateLocked: Boolean? = null,
)

internal fun String.readDesktopSkipiProfileMetadata(): DesktopSkipiProfileMetadata {
    fun value(key: String): String? = shadowrocketSectionValue(DesktopSkipiSection, key)?.trim()?.takeIf(String::isNotBlank)
    return DesktopSkipiProfileMetadata(
        name = value(SkipiProfileName),
        sourceUrl = value(SkipiProfileUpdateUrl),
        updateLocked = shadowrocketSectionValue(DesktopSkipiSection, SkipiProfileUpdateLocked)
            ?.trim()
            ?.lowercase()
            ?.let { raw -> raw in setOf("true", "yes", "1") },
    )
}

internal fun String.withDesktopSkipiProfileMetadata(
    name: String,
    sourceUrl: String,
    updateLocked: Boolean,
): String {
    return withShadowrocketSectionValue(DesktopSkipiSection, SkipiProfileName, name.trim())
        .withShadowrocketSectionValue(DesktopSkipiSection, SkipiProfileUpdateUrl, sourceUrl.trim())
        .withShadowrocketSectionValue(DesktopSkipiSection, SkipiProfileUpdateLocked, updateLocked.toString())
}

private const val DesktopSkipiSection = "SKIPI"
private const val SkipiProfileName = "profile-name"
private const val SkipiProfileUpdateUrl = "profile-update-url"
private const val SkipiProfileUpdateLocked = "profile-update-locked"
