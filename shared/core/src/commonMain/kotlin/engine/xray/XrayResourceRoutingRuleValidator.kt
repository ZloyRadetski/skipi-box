// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

/**
 * Validates global routing criteria through host-provided GeoIP/GeoSite resources.
 * A null lookup preserves Android's compatibility behavior when no data directory exists.
 */
class XrayResourceRoutingRuleValidator(
    private val hasResourceTag: ((fileName: String, tag: String, originalRule: String) -> Boolean)?,
    private val onInvalidIpRule: (String) -> Unit = {},
) : XrayRoutingRuleValidator {
    override fun isDomainRuleValid(rule: String): Boolean {
        val resourceLookup = hasResourceTag ?: return true
        val trimmed = rule.trim()
        return XrayGeoRuleValidator.isDomainRuleValid(trimmed) { fileName, tag ->
            resourceLookup(fileName, tag, trimmed)
        }
    }

    override fun isIpRuleValid(rule: String): Boolean {
        val trimmed = rule.trim()
        if (trimmed.isBlank()) return false
        if (hasResourceTag == null && isResourceRule(trimmed)) return true

        val isValid = XrayGeoRuleValidator.isIpRuleValid(trimmed) { fileName, tag ->
            hasResourceTag?.invoke(fileName, tag, trimmed) ?: false
        }
        if (!isValid && !isResourceRule(trimmed)) {
            onInvalidIpRule(trimmed)
        }
        return isValid
    }

    override fun filterValidDomainRules(rules: List<String>): List<String> {
        if (hasResourceTag == null) return rules
        return rules.filter(::isDomainRuleValid)
    }

    override fun filterValidIpRules(rules: List<String>): List<String> {
        if (hasResourceTag == null) return rules
        return rules.filter(::isIpRuleValid)
    }

    private fun isResourceRule(rule: String): Boolean =
        rule.startsWith("geoip:", ignoreCase = true) || rule.startsWith("ext:", ignoreCase = true)
}
