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
    /**
     * Whether the host's bundled resources can serve a value at all, whatever lookup is available
     * (Desktop cannot serve `geoip:asNNN`). A value they cannot serve is reported as unapplied, never an error.
     */
    private val canServe: (rule: String) -> Boolean = { true },
) : XrayRoutingRuleValidator {
    override fun isDomainRuleValid(rule: String): Boolean {
        if (!canServe(rule.trim())) return false
        val resourceLookup = hasResourceTag ?: return true
        val trimmed = rule.trim()
        return XrayGeoRuleValidator.isDomainRuleValid(trimmed) { fileName, tag ->
            resourceLookup(fileName, tag, trimmed)
        }
    }

    override fun isIpRuleValid(rule: String): Boolean {
        val trimmed = rule.trim()
        if (trimmed.isBlank() || !canServe(trimmed)) return false
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
        if (hasResourceTag == null) return rules.servable()
        return rules.filter(::isDomainRuleValid)
    }

    override fun filterValidIpRules(rules: List<String>): List<String> {
        if (hasResourceTag == null) return rules.servable()
        return rules.filter(::isIpRuleValid)
    }

    override fun classifyDomainRules(rules: List<String>): XrayRuleClassification {
        val (valid, invalid) = rules.partition(::isDomainRuleValid)
        return XrayRuleClassification(kept = if (hasResourceTag == null) rules.servable() else valid, unapplied = invalid)
    }

    override fun classifyIpRules(rules: List<String>): XrayRuleClassification {
        val (valid, invalid) = rules.partition(::isIpRuleValid)
        return XrayRuleClassification(kept = if (hasResourceTag == null) rules.servable() else valid, unapplied = invalid)
    }

    /** The list itself when the host serves all of it, so the common case allocates nothing. */
    private fun List<String>.servable(): List<String> = if (all(canServe)) this else filter(canServe)

    private fun isResourceRule(rule: String): Boolean =
        rule.startsWith("geoip:", ignoreCase = true) || rule.startsWith("ext:", ignoreCase = true)
}
