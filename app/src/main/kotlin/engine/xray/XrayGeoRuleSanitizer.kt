// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.logs.AndroidAppLogger
import features.resources.ResourceFileGeoIpName
import features.resources.ResourceFileGeoSiteName
import features.routing.usecase.GeoDatParser
import java.io.File

internal object XrayGeoRuleSanitizer {
    private const val LogTag = "XrayGeoRuleSanitizer"

    fun isDomainRuleValid(rule: String, dataDir: String?): Boolean {
        if (dataDir.isNullOrBlank()) return true
        val trimmed = rule.trim()
        if (trimmed.isBlank()) return false

        if (trimmed.startsWith("geosite:", ignoreCase = true)) {
            val payload = trimmed.substring(8).trim()
            val colonIndex = payload.indexOf(':')
            val (fileName, tag) = if (colonIndex > 0) {
                val file = payload.substring(0, colonIndex).trim()
                val targetFile = if (file.endsWith(".dat", ignoreCase = true)) file else "$file.dat"
                targetFile to payload.substring(colonIndex + 1).trim()
            } else {
                ResourceFileGeoSiteName to payload
            }
            return checkTagInDatFile(dataDir, fileName, tag, trimmed)
        }

        if (trimmed.startsWith("ext:", ignoreCase = true)) {
            val payload = trimmed.substring(4).trim()
            val colonIndex = payload.indexOf(':')
            if (colonIndex <= 0) return false
            val file = payload.substring(0, colonIndex).trim()
            val targetFile = if (file.endsWith(".dat", ignoreCase = true)) file else "$file.dat"
            val tag = payload.substring(colonIndex + 1).trim()
            return checkTagInDatFile(dataDir, targetFile, tag, trimmed)
        }

        return true
    }

    fun isIpRuleValid(rule: String, dataDir: String?): Boolean {
        if (dataDir.isNullOrBlank()) return true
        val trimmed = rule.trim()
        if (trimmed.isBlank()) return false

        if (trimmed.startsWith("geoip:", ignoreCase = true)) {
            val payload = trimmed.substring(6).trim()
            val colonIndex = payload.indexOf(':')
            val (fileName, tag) = if (colonIndex > 0) {
                val file = payload.substring(0, colonIndex).trim()
                val targetFile = if (file.endsWith(".dat", ignoreCase = true)) file else "$file.dat"
                targetFile to payload.substring(colonIndex + 1).trim()
            } else {
                ResourceFileGeoIpName to payload
            }
            if (tag.equals("private", ignoreCase = true)) {
                return true
            }
            return checkTagInDatFile(dataDir, fileName, tag, trimmed)
        }

        if (trimmed.startsWith("ext:", ignoreCase = true)) {
            val payload = trimmed.substring(4).trim()
            val colonIndex = payload.indexOf(':')
            if (colonIndex <= 0) return false
            val file = payload.substring(0, colonIndex).trim()
            val targetFile = if (file.endsWith(".dat", ignoreCase = true)) file else "$file.dat"
            val tag = payload.substring(colonIndex + 1).trim()
            return checkTagInDatFile(dataDir, targetFile, tag, trimmed)
        }

        return true
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
