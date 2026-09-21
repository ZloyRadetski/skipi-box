// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

data class XrayExternalDomainRule(
    val fileName: String,
    val tag: String,
)

fun isXrayExternalDomainRuleCandidate(value: String): Boolean =
    value.trim().startsWith(XrayExternalDomainPrefix, ignoreCase = true)

fun isValidXrayExternalDomainRule(value: String): Boolean =
    value.toXrayExternalDomainRuleOrNull() != null

fun String.toXrayExternalDomainRuleOrNull(): XrayExternalDomainRule? {
    val value = trim()
    if (!value.startsWith(XrayExternalDomainPrefix, ignoreCase = true)) return null

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

private fun String.isPlainResourceFileName(): Boolean =
    isNotBlank() &&
        this != "." &&
        this != ".." &&
        all { char ->
            char.code >= 32 &&
                char != ':' &&
                char != '/' &&
                char != '\\' &&
                !char.isWhitespace()
        }

private fun String.isPlainExternalTag(): Boolean = isNotBlank() && none(Char::isWhitespace)

private const val XrayExternalDomainPrefix = "ext:"
