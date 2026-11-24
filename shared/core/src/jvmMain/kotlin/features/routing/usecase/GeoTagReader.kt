// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.usecase

import java.io.File

/**
 * Reads the geo tags of `dataDir/<fileName>` for the routing suggestion resolvers.
 * Missing or empty files yield no tags instead of an error.
 */
fun geoTagReader(dataDir: File): (String) -> List<String> = { fileName ->
    val file = File(dataDir, fileName)
    if (file.isFile && file.length() > 0) GeoDatParser.parseTags(file) else emptyList()
}
