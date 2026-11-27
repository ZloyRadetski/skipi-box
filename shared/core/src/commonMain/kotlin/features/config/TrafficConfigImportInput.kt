// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import utils.decodeUrlComponentPreservingPlus

/** What a text handed to config import stands for. */
sealed interface TrafficConfigImportInput {
    /** Whether the link that carried it asked to make the new profile the active one. */
    val activate: Boolean

    /** The config has to be downloaded from [url]. */
    data class Url(val url: String, override val activate: Boolean) : TrafficConfigImportInput

    /** The config text itself. */
    data class Document(val content: String, override val activate: Boolean) : TrafficConfigImportInput
}

/**
 * Reads a pasted text: a `skipi://conf` or `skipi://routing` link is decoded the same way as an opened one,
 * an http(s) address is to be downloaded and anything else is the document.
 *
 * @throws IllegalArgumentException for a SKIPI link that carries no config
 */
fun parseTrafficConfigImportInput(text: String): TrafficConfigImportInput {
    val trimmed = text.trim()
    var activate = false
    var content = trimmed
    if (trimmed.startsWith("skipi://", ignoreCase = true)) {
        val link = parseSkipiDeepLinkOrNull(trimmed) as? SkipiDeepLink.TrafficConfig
        requireNotNull(link) { "The SKIPI link does not carry a configuration" }
        activate = link.activate
        content = link.content
    }
    return if (content.isSingleHttpUrl()) {
        TrafficConfigImportInput.Url(content, activate)
    } else {
        TrafficConfigImportInput.Document(content, activate)
    }
}

/**
 * The text behind the payload of a config link or of a provider's routing header: percent-encoding is removed
 * (literal plus signs stay) and Base64 is decoded. A payload that is neither comes back as it was, trimmed.
 */
fun String.resolveSkipiConfigPayload(): String {
    val unescaped = trim().decodeUrlComponentPreservingPlus()
    return unescaped.decodeSkipiConfigPayloadOrNull() ?: unescaped.trim()
}

/** Whether the whole text is one http(s) address. */
fun String.isSingleHttpUrl(): Boolean =
    (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) &&
        none { it == '\n' || it == '\r' }

/**
 * Why this text cannot become a config, or null when it can. The parser ignores everything outside sections,
 * so a text without any (a link, an address, a web page) would be a profile with no effect.
 */
fun String.trafficConfigDocumentErrorOrNull(): String? {
    val analysis = analyzeShadowrocketConfig()
    analysis.diagnostics.firstOrNull { it.severity == ShadowrocketConfigDiagnosticSeverity.Error }
        ?.let { return it.message }
    return if (analysis.sections.isEmpty()) "Not a configuration: no [Section] was found" else null
}
