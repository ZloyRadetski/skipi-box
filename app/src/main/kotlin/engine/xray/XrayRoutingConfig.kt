// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

internal fun xrayRoutingRuleValidator(dataDir: String?): XrayRoutingRuleValidator = object : XrayRoutingRuleValidator {
    override fun isDomainRuleValid(rule: String): Boolean =
        XrayGeoRuleSanitizer.isDomainRuleValid(rule, dataDir)

    override fun isIpRuleValid(rule: String): Boolean =
        XrayGeoRuleSanitizer.isIpRuleValid(rule, dataDir)

    override fun filterValidDomainRules(rules: List<String>): List<String> =
        XrayGeoRuleSanitizer.filterValidDomainRules(rules, dataDir)

    override fun filterValidIpRules(rules: List<String>): List<String> =
        XrayGeoRuleSanitizer.filterValidIpRules(rules, dataDir)
}
