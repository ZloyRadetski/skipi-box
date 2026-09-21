// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.SkipiProfileName
import features.config.SkipiProfileUpdateLocked
import features.config.SkipiProfileUpdateUrl
import features.config.SkipiSection
import features.config.shadowrocketSectionValue
import features.config.toSkipiConfigBoolean
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
    fun value(key: String): String? = shadowrocketSectionValue(SkipiSection, key)?.trim()?.takeIf(String::isNotBlank)
    return DesktopSkipiProfileMetadata(
        name = value(SkipiProfileName),
        sourceUrl = value(SkipiProfileUpdateUrl),
        updateLocked = shadowrocketSectionValue(SkipiSection, SkipiProfileUpdateLocked)
            ?.toSkipiConfigBoolean(false),
    )
}

internal fun String.withDesktopSkipiProfileMetadata(
    name: String,
    sourceUrl: String,
    updateLocked: Boolean,
): String {
    return withShadowrocketSectionValue(SkipiSection, SkipiProfileName, name.trim())
        .withShadowrocketSectionValue(SkipiSection, SkipiProfileUpdateUrl, sourceUrl.trim())
        .withShadowrocketSectionValue(SkipiSection, SkipiProfileUpdateLocked, updateLocked.toString())
}
