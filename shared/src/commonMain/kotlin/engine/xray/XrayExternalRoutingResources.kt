// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.AppState
import features.routing.model.RouteRule
import java.io.File

private const val XrayExternalDomainPrefix = "ext:"

fun isXrayExternalDomainRuleCandidate(value: String): Boolean {
    return value.trim().startsWith(XrayExternalDomainPrefix, ignoreCase = true)
}

fun isValidXrayExternalDomainRule(value: String): Boolean {
    return value.toXrayExternalDomainRuleOrNull() != null
}

fun AppState.validateXrayExternalRoutingResources(dataDir: String) {
    val domainRules = routeRules
        .asSequence()
        .filter(RouteRule::enabled)
        .flatMap { rule -> rule.domain }
        .map(String::trim)
        .filter(::isXrayExternalDomainRuleCandidate)
        .distinct()
        .toList()

    val invalidRules = domainRules
        .filterNot(::isValidXrayExternalDomainRule)
    if (invalidRules.isNotEmpty()) {
        error("Invalid external routing domain rule: ${invalidRules.joinToString()}")
    }

    val missingFileNames = domainRules
        .mapNotNull { rule -> rule.toXrayExternalDomainRuleOrNull()?.fileName }
        .distinct()
        .filterNot { fileName ->
            val file = File(dataDir, fileName)
            file.isFile && file.length() > 0
        }
    if (missingFileNames.isNotEmpty()) {
        error("Missing external routing resource file: ${missingFileNames.joinToString()}")
    }
}

private data class XrayExternalDomainRule(
    val fileName: String,
    val tag: String,
)

private fun String.toXrayExternalDomainRuleOrNull(): XrayExternalDomainRule? {
    val value = trim()
    if (!value.startsWith(XrayExternalDomainPrefix)) return null

    val firstSeparator = value.indexOf(':')
    val secondSeparator = value.indexOf(':', startIndex = firstSeparator + 1)
    if (secondSeparator < 0 || secondSeparator == value.lastIndex) return null
    if (value.indexOf(':', startIndex = secondSeparator + 1) >= 0) return null

    val fileName = value.substring(firstSeparator + 1, secondSeparator)
    val tag = value.substring(secondSeparator + 1)
    if (!fileName.isPlainResourceFileName()) return null
    if (!tag.isPlainExternalTag()) return null

    return XrayExternalDomainRule(fileName = fileName, tag = tag)
}

private fun String.isPlainResourceFileName(): Boolean {
    return isNotBlank() &&
        this != "." &&
        this != ".." &&
        all { char ->
            char.code >= 32 &&
                char != ':' &&
                char != '/' &&
                char != '\\' &&
                !char.isWhitespace()
        }
}

private fun String.isPlainExternalTag(): Boolean {
    return isNotBlank() && none(Char::isWhitespace)
}
