// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import engine.network.isIpOrCidrAddress

/** Portable validation for GeoIP/GeoSite references; file access is supplied by the host. */
object XrayGeoRuleValidator {
    fun isDomainRuleValid(rule: String, hasResourceTag: (fileName: String, tag: String) -> Boolean): Boolean {
        val trimmed = rule.trim()
        if (trimmed.isBlank()) return false
        if (!trimmed.startsWith("geosite:", ignoreCase = true) && !trimmed.startsWith("ext:", ignoreCase = true)) return true
        val reference = parseReference(trimmed, "geosite:", "ext:") ?: return false
        // `geosite:google@cn` selects the entries of tag "google" carrying attribute "cn".
        val tag = reference.tag.withoutAttributeOrNull() ?: return false
        return hasResourceTag(reference.fileName, tag)
    }

    fun isIpRuleValid(rule: String, hasResourceTag: (fileName: String, tag: String) -> Boolean): Boolean {
        val trimmed = rule.trim()
        if (trimmed.isBlank()) return false
        if (!trimmed.startsWith("geoip:", ignoreCase = true) && !trimmed.startsWith("ext:", ignoreCase = true)) {
            return isIpOrCidrAddress(trimmed)
        }
        val reference = parseReference(trimmed, "geoip:", "ext:") ?: return false
        if (reference.tag.equals("private", ignoreCase = true) && trimmed.startsWith("geoip:", ignoreCase = true)) return true
        return hasResourceTag(reference.fileName, reference.tag)
    }

    private fun parseReference(rule: String, standardPrefix: String, externalPrefix: String): ResourceTagReference? {
        val isExternal = rule.startsWith(externalPrefix, ignoreCase = true)
        val prefix = if (isExternal) externalPrefix else standardPrefix
        if (!rule.startsWith(prefix, ignoreCase = true)) return null
        val payload = rule.substring(prefix.length).trim()
        // Only `ext:` names a file; `geosite:` / `geoip:` always read the bundled one, so a
        // crafted `geosite:../../x:y` cannot pick a path.
        val separator = payload.indexOf(':')
        if (!isExternal && separator >= 0) return null
        if (isExternal && separator <= 0) return null
        val file = if (isExternal) payload.substring(0, separator).trim() else null
        val tag = if (isExternal) payload.substring(separator + 1).trim() else payload
        if (tag.isBlank()) return null
        val fileName = if (file != null) {
            if (!file.isPlainResourceFileName()) return null
            val withExtension = if (file.endsWith(".dat", ignoreCase = true)) file else "$file.dat"
            if (!withExtension.isPlainResourceFileName()) return null
            withExtension
        } else if (standardPrefix.equals("geoip:", ignoreCase = true)) {
            DefaultGeoIpFileName
        } else {
            DefaultGeoSiteFileName
        }
        return ResourceTagReference(fileName, tag)
    }

    /** The tag before any `@attribute`; null when either side of the `@` is blank. */
    private fun String.withoutAttributeOrNull(): String? {
        val at = indexOf('@')
        if (at < 0) return this
        val base = substring(0, at).trim()
        val attribute = substring(at + 1).trim()
        return base.takeIf { it.isNotBlank() && attribute.isNotBlank() }
    }

    private data class ResourceTagReference(val fileName: String, val tag: String)

    const val DefaultGeoSiteFileName = "geosite.dat"
    const val DefaultGeoIpFileName = "geoip.dat"
}
