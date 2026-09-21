// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import features.routing.model.RouteRule
import java.io.File

/** Android filesystem validation around shared ext: rule parsing. */
internal fun AppState.validateXrayExternalRoutingResources(dataDir: String) {
    val domainRules = routeRules
        .asSequence()
        .filter(RouteRule::enabled)
        .flatMap { rule -> rule.domain }
        .map(String::trim)
        .filter(::isXrayExternalDomainRuleCandidate)
        .distinct()
        .toList()

    val invalidRules = domainRules.filterNot(::isValidXrayExternalDomainRule)
    if (invalidRules.isNotEmpty()) {
        error("Invalid external routing domain rule: " + invalidRules.joinToString())
    }

    val missingFileNames = domainRules
        .mapNotNull { rule -> rule.toXrayExternalDomainRuleOrNull()?.fileName }
        .distinct()
        .filterNot { fileName ->
            val file = File(dataDir, fileName)
            file.isFile && file.length() > 0
        }
    if (missingFileNames.isNotEmpty()) {
        error("Missing external routing resource file: " + missingFileNames.joinToString())
    }
}
