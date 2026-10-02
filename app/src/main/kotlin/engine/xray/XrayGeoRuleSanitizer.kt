// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import engine.xray.XrayGeoRuleValidator
import features.logs.AndroidAppLogger
import features.routing.usecase.GeoDatParser
import java.io.File

internal object XrayGeoRuleSanitizer {
    private const val LogTag = "XrayGeoRuleSanitizer"

    fun isDomainRuleValid(rule: String, dataDir: String?): Boolean {
        if (dataDir.isNullOrBlank()) return true
        val trimmed = rule.trim()
        return XrayGeoRuleValidator.isDomainRuleValid(trimmed) { fileName, tag ->
            checkTagInDatFile(dataDir, fileName, tag, trimmed)
        }
    }

    fun isIpRuleValid(rule: String, dataDir: String?): Boolean {
        val trimmed = rule.trim()
        if (trimmed.isBlank()) return false
        if (dataDir.isNullOrBlank() && (trimmed.startsWith("geoip:", ignoreCase = true) || trimmed.startsWith("ext:", ignoreCase = true))) {
            return true
        }
        val resourceDirectory = dataDir
        val isValidAddress = XrayGeoRuleValidator.isIpRuleValid(trimmed) { fileName, tag ->
            resourceDirectory?.takeIf(String::isNotBlank)?.let { directory ->
                checkTagInDatFile(directory, fileName, tag, trimmed)
            } ?: false
        }
        if (!isValidAddress) {
            if (!trimmed.startsWith("geoip:", ignoreCase = true) && !trimmed.startsWith("ext:", ignoreCase = true)) {
                AndroidAppLogger.warn(LogTag, "Invalid IP/CIDR rule '$trimmed', skipping rule to prevent Xray crash")
            }
        }
        return isValidAddress
    }

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

    fun filterValidDomainRules(rules: List<String>, dataDir: String?): List<String> {
        if (dataDir.isNullOrBlank()) return rules
        return rules.filter { isDomainRuleValid(it, dataDir) }
    }

    fun filterValidIpRules(rules: List<String>, dataDir: String?): List<String> {
        if (dataDir.isNullOrBlank()) return rules
        return rules.filter { isIpRuleValid(it, dataDir) }
    }

}
