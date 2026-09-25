// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.SkipiProfileMetadata
import features.config.readSkipiProfileMetadata
import features.config.withSkipiProfileMetadata

/** Compatibility facade for Desktop callers; parsing and writing are shared. */
internal typealias DesktopSkipiProfileMetadata = SkipiProfileMetadata

internal fun String.readDesktopSkipiProfileMetadata(): DesktopSkipiProfileMetadata =
    readSkipiProfileMetadata()

internal fun String.withDesktopSkipiProfileMetadata(
    name: String,
    sourceUrl: String,
    updateLocked: Boolean,
): String = withSkipiProfileMetadata(name, sourceUrl, updateLocked)
