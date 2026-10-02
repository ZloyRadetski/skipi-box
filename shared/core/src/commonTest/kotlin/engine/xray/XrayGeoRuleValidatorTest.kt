// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayGeoRuleValidatorTest {
    @Test
    fun validatesStandardAndCustomGeoSiteReferencesThroughHostLookup() {
        val seen = mutableListOf<Pair<String, String>>()
        val lookup: (String, String) -> Boolean = { file, tag ->
            seen += file to tag
            tag == "google"
        }

        assertTrue(XrayGeoRuleValidator.isDomainRuleValid("geosite:google", lookup))
        assertFalse(XrayGeoRuleValidator.isDomainRuleValid("geosite:missing", lookup))
        assertTrue(XrayGeoRuleValidator.isDomainRuleValid("ext:custom:google", lookup))
        assertFalse(XrayGeoRuleValidator.isDomainRuleValid("ext:malformed", lookup))
        assertTrue(seen.contains("geosite.dat" to "google"))
        assertTrue(seen.contains("custom.dat" to "google"))
    }

    @Test
    fun validatesIpCidrsAndGeoIpReferences() {
        var lookupCount = 0
        val lookup: (String, String) -> Boolean = { _, _ -> lookupCount++; true }
        assertTrue(XrayGeoRuleValidator.isIpRuleValid("192.168.1.0/24", lookup))
        assertFalse(XrayGeoRuleValidator.isIpRuleValid("not-an-ip", lookup))
        assertTrue(XrayGeoRuleValidator.isIpRuleValid("geoip:private", lookup))
        assertTrue(XrayGeoRuleValidator.isIpRuleValid("geoip:custom:ru", lookup))
        assertFalse(XrayGeoRuleValidator.isIpRuleValid("geoip:", lookup))
        assertEquals(1, lookupCount)
    }
}
