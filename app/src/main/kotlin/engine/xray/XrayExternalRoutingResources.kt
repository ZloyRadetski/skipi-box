// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import java.io.File

/** Android filesystem validation around shared ext: rule parsing. */
internal fun AppState.validateXrayExternalRoutingResources(dataDir: String) {
    validateXrayExternalDomainResources(routeRules) { fileName ->
        val file = File(dataDir, fileName)
        file.isFile && file.length() > 0
    }
}
