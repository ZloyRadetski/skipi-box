// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.config

import features.proxy.server.model.StrategyGroupDisplayMode

/**
 * A deliberately lossless, small parser for Shadowrocket's INI-like format.
 *
 * The original text remains the source of truth.  This model is used by the
 * Material editor for the sections SKIPI can apply and, importantly, leaves
 * unsupported blocks such as [MITM] and [Script] untouched on save.
 */
internal data class ShadowrocketConfigAnalysis(
    val sections: Map<String, List<String>>,
    val general: Map<String, String>,
    val rules: List<ShadowrocketRule>,
    val proxyGroups: List<ShadowrocketPolicyGroup>,
    val unsupportedSections: Set<String>,
    val diagnostics: List<ShadowrocketConfigDiagnostic>,
)

/**
 * A [Proxy Group] entry that selects existing SKIPI servers by their display
 * name.  Unlike Shadowrocket, SKIPI deliberately keeps endpoints in the
 * subscription/manual-server list rather than copying them into a profile.
 */
data class ShadowrocketPolicyGroup(
    val lineNumber: Int,
    val name: String,
    val type: String,
    val members: List<String>,
    val url: String = "",
    val intervalSeconds: Int? = null,
    val timeoutSeconds: Int? = null,
    /** SKIPI extension: display policy for proxy groups tab on home screen. */
    val displayMode: String = StrategyGroupDisplayMode.NEVER,
    val showInAutoBalancerList: Boolean = displayMode != StrategyGroupDisplayMode.NEVER,
    /** SKIPI extension: use the faster burst observatory for health checks. */
    val enableBurstProbe: Boolean = true,
    /** SKIPI extension: maximum latency difference allowed before switching. */
    val tolerance: String = "50ms",
    val raw: String = "",
) {
    val outboundTag: String
        get() = ShadowrocketPolicyGroupTagPrefix + name
}

internal const val ShadowrocketPolicyGroupTagPrefix = "shadowrocket-group:"

internal data class ShadowrocketRule(
    val lineNumber: Int,
    val type: String,
    val value: String,
    val policy: String,
    val option: String? = null,
    val raw: String,
) {
    val isFinal: Boolean
        get() = type.equals("FINAL", ignoreCase = true)
}

internal data class ShadowrocketConfigDiagnostic(
    val lineNumber: Int,
    val message: String,
    val severity: ShadowrocketConfigDiagnosticSeverity,
)

internal enum class ShadowrocketConfigDiagnosticSeverity {
    Warning,
    Error,
}

internal val ShadowrocketUnsupportedAndroidSections = setOf(
    "mitm",
    "script",
    "header rewrite",
)

private const val MaxAnalysisCacheEntries = 16
private val analysisCache = object : LinkedHashMap<Int, Pair<String, ShadowrocketConfigAnalysis>>(MaxAnalysisCacheEntries, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Pair<String, ShadowrocketConfigAnalysis>>?): Boolean {
        return size > MaxAnalysisCacheEntries
    }
}
private val analysisCacheLock = Any()

internal fun String.analyzeShadowrocketConfig(): ShadowrocketConfigAnalysis {
    val key = hashCode()
    synchronized(analysisCacheLock) {
        val cached = analysisCache[key]
        if (cached != null && cached.first == this) {
            return cached.second
        }
    }
    val analysis = parseShadowrocketConfig(this)
    synchronized(analysisCacheLock) {
        analysisCache[key] = this to analysis
    }
    return analysis
}

private fun parseShadowrocketConfig(rawText: String): ShadowrocketConfigAnalysis {
    val sections = linkedMapOf<String, MutableList<String>>()
    val general = linkedMapOf<String, String>()
    val rules = mutableListOf<ShadowrocketRule>()
    val proxyGroups = mutableListOf<ShadowrocketPolicyGroup>()
    val diagnostics = mutableListOf<ShadowrocketConfigDiagnostic>()
    var activeSection = ""

    rawText.lineSequence().forEachIndexed { index, line ->
        val lineNumber = index + 1
        val trimmed = line.trim()
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length > 2) {
            activeSection = trimmed.substring(1, trimmed.length - 1).trim()
            sections.getOrPut(activeSection.lowercase()) { mutableListOf() }
            return@forEachIndexed
        }

        if (activeSection.isBlank()) return@forEachIndexed
        sections.getOrPut(activeSection.lowercase()) { mutableListOf() }.add(line)
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
            return@forEachIndexed
        }

        when (activeSection.lowercase()) {
            "general" -> parseGeneralLine(trimmed)?.let { (key, value) ->
                general[key.lowercase()] = value
            } ?: diagnostics.add(
                ShadowrocketConfigDiagnostic(
                    lineNumber = lineNumber,
                    message = "Expected a Shadowrocket general setting: key = value",
                    severity = ShadowrocketConfigDiagnosticSeverity.Warning,
                ),
            )

            "rule" -> parseRuleLine(lineNumber, trimmed, line)?.let(rules::add)
                ?: diagnostics.add(
                    ShadowrocketConfigDiagnostic(
                        lineNumber = lineNumber,
                        message = "Rule could not be parsed and will be preserved as text",
                        severity = ShadowrocketConfigDiagnosticSeverity.Warning,
                ),
            )

            "proxy group" -> parseProxyGroupLine(lineNumber, trimmed, line)?.let(proxyGroups::add)
                ?: diagnostics.add(
                    ShadowrocketConfigDiagnostic(
                        lineNumber = lineNumber,
                        message = "Proxy Group could not be parsed and will be preserved as text",
                        severity = ShadowrocketConfigDiagnosticSeverity.Warning,
                    ),
                )
        }
    }

    val finalRules = rules.filter(ShadowrocketRule::isFinal)
    if (finalRules.size > 1) {
        finalRules.drop(1).forEach { rule ->
            diagnostics += ShadowrocketConfigDiagnostic(
                lineNumber = rule.lineNumber,
                message = "Only one FINAL rule is allowed",
                severity = ShadowrocketConfigDiagnosticSeverity.Error,
            )
        }
    }
    finalRules.firstOrNull()?.let { rule ->
        if (rules.lastOrNull() != rule) {
            diagnostics += ShadowrocketConfigDiagnostic(
                lineNumber = rule.lineNumber,
                message = "FINAL should be the last routing rule",
                severity = ShadowrocketConfigDiagnosticSeverity.Warning,
            )
        }
    }

    return ShadowrocketConfigAnalysis(
        sections = sections.mapValues { (_, lines) -> lines.toList() },
        general = general,
        rules = rules,
        proxyGroups = proxyGroups,
        unsupportedSections = sections.keys.intersect(ShadowrocketUnsupportedAndroidSections),
        diagnostics = diagnostics,
    )
}

/** Updates one [General] key while keeping every unrelated line and section intact. */
internal fun String.withShadowrocketGeneralValue(
    key: String,
    value: String,
): String {
    val targetKey = key.trim().lowercase()
    require(targetKey.isNotEmpty()) { "General key must not be blank" }
    val sourceLines = lines().toMutableList()
    var generalHeaderIndex = -1
    var sectionEndIndex = sourceLines.size
    var existingValueIndex = -1

    sourceLines.forEachIndexed { index, line ->
        val header = line.trim().takeIf { it.startsWith("[") && it.endsWith("]") }
            ?.substring(1, line.trim().length - 1)
            ?.trim()
            ?.lowercase()
        if (header == "general") {
            generalHeaderIndex = index
            return@forEachIndexed
        }
        if (generalHeaderIndex >= 0 && header != null && sectionEndIndex == sourceLines.size) {
            sectionEndIndex = index
        }
        if (generalHeaderIndex >= 0 && index > generalHeaderIndex && index < sectionEndIndex) {
            val parsed = parseGeneralLine(line.trim())
            if (parsed?.first?.lowercase() == targetKey) existingValueIndex = index
        }
    }

    val replacement = "$key = $value"
    when {
        existingValueIndex >= 0 -> sourceLines[existingValueIndex] = replacement
        generalHeaderIndex >= 0 -> sourceLines.add(sectionEndIndex, replacement)
        else -> {
            if (sourceLines.isNotEmpty() && sourceLines.last().isNotBlank()) sourceLines += ""
            sourceLines += "[General]"
            sourceLines += replacement
        }
    }
    return sourceLines.joinToString("\n").trimEnd() + "\n"
}

/** Replaces one complete INI section while preserving every other section verbatim. */
internal fun String.withShadowrocketSectionLines(
    sectionName: String,
    replacementLines: List<String>,
): String {
    val normalizedSectionName = sectionName.trim()
    require(normalizedSectionName.isNotEmpty()) { "Section name must not be blank" }
    val sourceLines = lines().toMutableList()
    val headerIndex = sourceLines.indexOfFirst { line ->
        line.trim().removeSurrounding("[", "]").equals(normalizedSectionName, ignoreCase = true)
    }
    if (headerIndex >= 0) {
        val nextHeaderIndex = sourceLines.drop(headerIndex + 1)
            .indexOfFirst { line ->
                val trimmed = line.trim()
                trimmed.startsWith("[") && trimmed.endsWith("]")
            }
            .let { offset -> if (offset < 0) sourceLines.size else headerIndex + 1 + offset }
        sourceLines.subList(headerIndex + 1, nextHeaderIndex).clear()
        sourceLines.addAll(headerIndex + 1, replacementLines)
    } else {
        if (sourceLines.isNotEmpty() && sourceLines.last().isNotBlank()) sourceLines += ""
        sourceLines += "[$normalizedSectionName]"
        sourceLines.addAll(replacementLines)
    }
    return sourceLines.joinToString("\n").trimEnd() + "\n"
}

/** Replaces exactly one parsed rule line and deliberately keeps surrounding comments untouched. */
internal fun String.withShadowrocketRuleLine(
    lineNumber: Int,
    replacement: String,
): String {
    val sourceLines = lines().toMutableList()
    val lineIndex = lineNumber - 1
    require(lineIndex in sourceLines.indices) { "Rule line $lineNumber is outside of the config" }
    sourceLines[lineIndex] = replacement.trim()
    return sourceLines.joinToString("\n").trimEnd() + "\n"
}

/** Removes exactly one parsed rule line and leaves all comments and other sections in place. */
internal fun String.withoutShadowrocketRuleLine(lineNumber: Int): String {
    val sourceLines = lines().toMutableList()
    val lineIndex = lineNumber - 1
    require(lineIndex in sourceLines.indices) { "Rule line $lineNumber is outside of the config" }
    sourceLines.removeAt(lineIndex)
    return sourceLines.joinToString("\n").trimEnd() + "\n"
}

/** Adds a rule immediately before FINAL, or at the end of [Rule] when FINAL has not been set yet. */
internal fun String.withShadowrocketRuleAdded(rawRule: String): String {
    val sourceLines = lines().toMutableList()
    val analysis = analyzeShadowrocketConfig()
    val finalRule = analysis.rules.firstOrNull(ShadowrocketRule::isFinal)
    if (finalRule != null) {
        sourceLines.add(finalRule.lineNumber - 1, rawRule.trim())
        return sourceLines.joinToString("\n").trimEnd() + "\n"
    }

    val ruleHeaderIndex = sourceLines.indexOfFirst { line ->
        line.trim().removeSurrounding("[", "]").equals("Rule", ignoreCase = true)
    }
    if (ruleHeaderIndex >= 0) {
        val insertionIndex = sourceLines
            .drop(ruleHeaderIndex + 1)
            .indexOfFirst { line ->
                val trimmed = line.trim()
                trimmed.startsWith("[") && trimmed.endsWith("]")
            }
            .let { nextSectionOffset ->
                if (nextSectionOffset < 0) sourceLines.size else ruleHeaderIndex + 1 + nextSectionOffset
            }
        sourceLines.add(insertionIndex, rawRule.trim())
    } else {
        if (sourceLines.isNotEmpty() && sourceLines.last().isNotBlank()) sourceLines += ""
        sourceLines += "[Rule]"
        sourceLines += rawRule.trim()
    }
    return sourceLines.joinToString("\n").trimEnd() + "\n"
}

/** Replaces or removes a [Proxy Group] line without touching comments or other sections. */
internal fun String.withShadowrocketProxyGroupLine(lineNumber: Int, replacement: String): String {
    return withShadowrocketRuleLine(lineNumber, replacement)
}

internal fun String.withoutShadowrocketProxyGroupLine(lineNumber: Int): String {
    return withoutShadowrocketRuleLine(lineNumber)
}

/** Adds a profile group at the end of [Proxy Group], creating that section if required. */
internal fun String.withShadowrocketProxyGroupAdded(rawGroup: String): String {
    val sourceLines = lines().toMutableList()
    val headerIndex = sourceLines.indexOfFirst { line ->
        line.trim().removeSurrounding("[", "]").equals("Proxy Group", ignoreCase = true)
    }
    if (headerIndex >= 0) {
        val nextSectionIndex = sourceLines
            .drop(headerIndex + 1)
            .indexOfFirst { line ->
                val trimmed = line.trim()
                trimmed.startsWith("[") && trimmed.endsWith("]")
            }
            .let { offset -> if (offset < 0) sourceLines.size else headerIndex + 1 + offset }
        sourceLines.add(nextSectionIndex, rawGroup.trim())
    } else {
        if (sourceLines.isNotEmpty() && sourceLines.last().isNotBlank()) sourceLines += ""
        sourceLines += "[Proxy Group]"
        sourceLines += rawGroup.trim()
    }
    return sourceLines.joinToString("\n").trimEnd() + "\n"
}

/** Swaps two parsed routing lines.  Comments stay at their original positions. */
internal fun String.withShadowrocketRulesSwapped(
    firstLineNumber: Int,
    secondLineNumber: Int,
): String {
    val sourceLines = lines().toMutableList()
    val first = firstLineNumber - 1
    val second = secondLineNumber - 1
    require(first in sourceLines.indices && second in sourceLines.indices) {
        "Rule line is outside of the config"
    }
    val previous = sourceLines[first]
    sourceLines[first] = sourceLines[second]
    sourceLines[second] = previous
    return sourceLines.joinToString("\n").trimEnd() + "\n"
}

/** Reorders normal rules in the [Rule] section. */
internal fun String.withShadowrocketRulesReordered(
    fromRuleIndex: Int,
    toRuleIndex: Int,
): String {
    val analysis = analyzeShadowrocketConfig()
    val normalRules = analysis.rules.filterNot(ShadowrocketRule::isFinal)
    if (fromRuleIndex !in normalRules.indices || toRuleIndex !in normalRules.indices || fromRuleIndex == toRuleIndex) {
        return this
    }
    val reordered = normalRules.toMutableList()
    val moved = reordered.removeAt(fromRuleIndex)
    reordered.add(toRuleIndex, moved)

    val sourceLines = lines().toMutableList()
    val lineIndices = normalRules.map { it.lineNumber - 1 }
    reordered.forEachIndexed { i, rule ->
        sourceLines[lineIndices[i]] = rule.raw
    }
    return sourceLines.joinToString("\n").trimEnd() + "\n"
}

internal fun ShadowrocketRule.toShadowrocketLine(
    type: String = this.type,
    value: String = this.value,
    policy: String = this.policy,
): String {
    return if (type.equals("FINAL", ignoreCase = true)) {
        "FINAL,${policy.trim()}"
    } else {
        listOf(type.trim().uppercase(), value.trim(), policy.trim())
            .plus(option?.takeIf(String::isNotBlank))
            .joinToString(",")
    }
}

internal fun defaultShadowrocketConfig(): String = """
    # SKIPI configuration
    [General]
    dns-server = 94.140.14.14,94.140.15.15
    ipv6 = false

    [Rule]
    FINAL,PROXY
""".trimIndent() + "\n"

private fun parseGeneralLine(line: String): Pair<String, String>? {
    val separatorIndex = line.indexOf('=')
    if (separatorIndex <= 0) return null
    val key = line.substring(0, separatorIndex).trim()
    val value = line.substring(separatorIndex + 1).trim()
    return key.takeIf(String::isNotEmpty)?.let { it to value }
}

private fun parseRuleLine(
    lineNumber: Int,
    line: String,
    raw: String,
): ShadowrocketRule? {
    val parts = line.split(',').map(String::trim)
    if (parts.size < 2) return null
    val type = parts.first().uppercase()
    if (type == "FINAL") {
        val policy = parts.getOrNull(1).orEmpty()
        return policy.takeIf(String::isNotEmpty)?.let {
            ShadowrocketRule(
                lineNumber = lineNumber,
                type = type,
                value = "",
                policy = it,
                option = parts.drop(2).joinToString(", ").takeIf(String::isNotBlank),
                raw = raw,
            )
        }
    }
    if (parts.size < 3) return null
    return ShadowrocketRule(
        lineNumber = lineNumber,
        type = type,
        value = parts[1],
        policy = parts[2],
        option = parts.drop(3).joinToString(", ").takeIf(String::isNotBlank),
        raw = raw,
    )
}

private fun parseProxyGroupLine(
    lineNumber: Int,
    line: String,
    raw: String,
): ShadowrocketPolicyGroup? {
    val separator = line.indexOf('=')
    if (separator <= 0) return null
    val name = line.substring(0, separator).trim()
    val values = line.substring(separator + 1)
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
    val type = values.firstOrNull().orEmpty()
    if (name.isBlank() || type.isBlank()) return null
    val options = values.drop(1).filter { value -> '=' in value }
        .associate { value ->
            value.substringBefore('=').trim().lowercase() to value.substringAfter('=').trim()
        }
    val displayOpt = options["skipi-display"]?.trim()?.lowercase()
    val showOnHomeLegacy = options["skipi-show-on-home"]
        ?.trim()
        ?.lowercase() in setOf("true", "yes", "1")
    val displayMode = when (displayOpt) {
        StrategyGroupDisplayMode.NEVER -> StrategyGroupDisplayMode.NEVER
        StrategyGroupDisplayMode.ALWAYS -> StrategyGroupDisplayMode.ALWAYS
        StrategyGroupDisplayMode.ACTIVE_CONFIG, "only_active_config", "active" -> StrategyGroupDisplayMode.ACTIVE_CONFIG
        else -> if (showOnHomeLegacy) StrategyGroupDisplayMode.ALWAYS else StrategyGroupDisplayMode.NEVER
    }
    val burstProbeEnabled = options["skipi-burst-probe"]
        ?.trim()
        ?.lowercase()
        ?.let { value -> value in setOf("true", "yes", "1") }
        ?: true
    val tolerance = options["skipi-tolerance"]
        ?.trim()
        ?.takeIf { value -> value.matches(Regex("\\d+ms")) }
        ?: "50ms"
    val timeoutSeconds = options["timeout"]?.toIntOrNull()?.takeIf { it in 1..30 }
        ?: options["skipi-timeout"]?.removeSuffix("s")?.toIntOrNull()?.takeIf { it in 1..30 }
    return ShadowrocketPolicyGroup(
        lineNumber = lineNumber,
        name = name,
        type = type.lowercase(),
        members = values.drop(1)
            .filterNot { value -> '=' in value }
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'").trim() }
            .filter(String::isNotEmpty),
        url = options["url"].orEmpty(),
        intervalSeconds = options["interval"]?.toIntOrNull()?.takeIf { it > 0 },
        timeoutSeconds = timeoutSeconds,
        displayMode = displayMode,
        enableBurstProbe = burstProbeEnabled,
        tolerance = tolerance,
        raw = raw,
    )
}
