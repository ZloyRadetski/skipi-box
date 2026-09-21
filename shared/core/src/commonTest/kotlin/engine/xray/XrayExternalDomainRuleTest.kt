// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XrayExternalDomainRuleTest {
    @Test
    fun parsesOnlySafeExternalDomainRules() {
        assertEquals(
            XrayExternalDomainRule(fileName = "geosite.dat", tag = "cn"),
            "ext:geosite.dat:cn".toXrayExternalDomainRuleOrNull(),
        )
        assertTrue(isXrayExternalDomainRuleCandidate(" EXT:geosite.dat:cn "))
        assertTrue(isValidXrayExternalDomainRule("ext:geosite.dat:cn"))
        assertFalse(isValidXrayExternalDomainRule("ext:../geosite.dat:cn"))
        assertFalse(isValidXrayExternalDomainRule("ext:geo/site.dat:cn"))
        assertFalse(isValidXrayExternalDomainRule("ext:geosite.dat:cn:extra"))
        assertNull("domain:example.com".toXrayExternalDomainRuleOrNull())
    }
}
