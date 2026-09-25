// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

/** Portable subset of the [SKIPI] profile metadata used by profile editors. */
data class SkipiProfileMetadata(
    val name: String? = null,
    val sourceUrl: String? = null,
    val updateLocked: Boolean? = null,
)

fun String.readSkipiProfileMetadata(): SkipiProfileMetadata {
    fun value(key: String): String? = shadowrocketSectionValue(SkipiSection, key)
        ?.trim()
        ?.takeIf(String::isNotBlank)
    return SkipiProfileMetadata(
        name = value(SkipiProfileName),
        sourceUrl = value(SkipiProfileUpdateUrl),
        updateLocked = shadowrocketSectionValue(SkipiSection, SkipiProfileUpdateLocked)
            ?.toSkipiConfigBoolean(false),
    )
}

fun String.withSkipiProfileMetadata(
    name: String,
    sourceUrl: String,
    updateLocked: Boolean,
): String = withShadowrocketSectionValue(SkipiSection, SkipiProfileName, name.trim())
    .withShadowrocketSectionValue(SkipiSection, SkipiProfileUpdateUrl, sourceUrl.trim())
    .withShadowrocketSectionValue(SkipiSection, SkipiProfileUpdateLocked, updateLocked.toString())
