// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.logs.AndroidAppLogger
import features.routing.usecase.GeoDatParser
import java.io.File

internal object XrayGeoRuleSanitizer {
    private const val LogTag = "XrayGeoRuleSanitizer"

    fun isDomainRuleValid(rule: String, dataDir: String?): Boolean = validator(dataDir).isDomainRuleValid(rule)

    fun isIpRuleValid(rule: String, dataDir: String?): Boolean = validator(dataDir).isIpRuleValid(rule)

    private fun checkTagInDatFile(dataDir: String, fileName: String, tag: String, originalRule: String): Boolean {
        if (tag.isBlank()) return false
        val datFile = File(dataDir, fileName)
        if (!datFile.isFile || datFile.length() <= 0) {
            AndroidAppLogger.warn(LogTag, "Resource file '$fileName' not found or empty in '$dataDir', skipping rule '$originalRule' to prevent Xray crash")
            return false
        }
        val tags = GeoDatParser.parseTags(datFile)
        val exists = tags.any { it.equals(tag, ignoreCase = true) }
        if (!exists) {
            AndroidAppLogger.warn(LogTag, "Geo tag '$tag' not found in '$fileName', skipping rule '$originalRule' to prevent Xray crash")
            return false
        }
        return true
    }

    fun filterValidDomainRules(rules: List<String>, dataDir: String?): List<String> =
        validator(dataDir).filterValidDomainRules(rules)

    fun filterValidIpRules(rules: List<String>, dataDir: String?): List<String> =
        validator(dataDir).filterValidIpRules(rules)

    private fun validator(dataDir: String?): XrayResourceRoutingRuleValidator {
        val resourceDirectory = dataDir?.takeIf(String::isNotBlank)
        val hasResourceTag: ((String, String, String) -> Boolean)? = resourceDirectory?.let { directory ->
            { fileName, tag, originalRule -> checkTagInDatFile(directory, fileName, tag, originalRule) }
        }
        return XrayResourceRoutingRuleValidator(
            hasResourceTag = hasResourceTag,
            onInvalidIpRule = { rule ->
                AndroidAppLogger.warn(LogTag, "Invalid IP/CIDR rule '$rule', skipping rule to prevent Xray crash")
            },
        )
    }
}
