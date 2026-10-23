// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.routing.model.RouteRule

/** Validates enabled external domain-rule references through a host file-existence check. */
fun validateXrayExternalDomainResources(
    routeRules: List<RouteRule>,
    hasNonEmptyFile: (fileName: String) -> Boolean,
) {
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
        .filterNot { fileName -> hasNonEmptyFile(fileName) }
    if (missingFileNames.isNotEmpty()) {
        error("Missing external routing resource file: " + missingFileNames.joinToString())
    }
}
