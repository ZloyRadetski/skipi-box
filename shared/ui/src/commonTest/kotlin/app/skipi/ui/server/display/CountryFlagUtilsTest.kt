// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.display

import features.proxy.server.model.StrategyGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountryFlagUtilsTest {

    private data class Candidate(
        override val id: Int,
        override val groupId: Int? = null,
        override val remarks: String = "",
        override val strategyGroup: StrategyGroup? = null,
    ) : StrategyGroupMemberCandidate

    @Test
    fun extractLeadingCountryFlag_extractsRegionalIndicators() {
        assertEquals("🇺🇸", CountryFlagUtils.extractLeadingCountryFlag("🇺🇸 US Server 01"))
        assertEquals("🇩🇪", CountryFlagUtils.extractLeadingCountryFlag("  🇩🇪 Germany Fast"))
        assertEquals("🇳🇱", CountryFlagUtils.extractLeadingCountryFlag("🇳🇱 Amsterdam"))
        assertEquals("🇷🇺", CountryFlagUtils.extractLeadingCountryFlag("🇷🇺 Moscow"))
    }

    @Test
    fun extractLeadingCountryFlag_returnsNullWhenNoFlag() {
        assertNull(CountryFlagUtils.extractLeadingCountryFlag("Fast Proxy"))
        assertNull(CountryFlagUtils.extractLeadingCountryFlag("127.0.0.1:8080"))
        assertNull(CountryFlagUtils.extractLeadingCountryFlag(""))
    }

    @Test
    fun stripLeadingCountryFlag_removesFlagAndTrims() {
        assertEquals("US Server 01", CountryFlagUtils.stripLeadingCountryFlag("🇺🇸 US Server 01"))
        assertEquals("Germany Fast", CountryFlagUtils.stripLeadingCountryFlag("🇩🇪   Germany Fast"))
        // If stripping leaves empty, returns original text
        assertEquals("🇳🇱", CountryFlagUtils.stripLeadingCountryFlag("🇳🇱"))
        assertEquals("No Flag Server", CountryFlagUtils.stripLeadingCountryFlag("No Flag Server"))
    }

    @Test
    fun strategyGroupContainsMember_explicitList() {
        val group = StrategyGroup().apply {
            proxyServerIds = listOf(10, 20)
        }
        val candidates = listOf(
            Candidate(id = 10, remarks = "Server 10"),
            Candidate(id = 20, remarks = "Server 20"),
            Candidate(id = 30, remarks = "Server 30"),
        )

        assertTrue(CountryFlagUtils.strategyGroupContainsMember(group, 10, candidates))
        assertTrue(CountryFlagUtils.strategyGroupContainsMember(group, 20, candidates))
        assertFalse(CountryFlagUtils.strategyGroupContainsMember(group, 30, candidates))
    }

    @Test
    fun strategyGroupContainsMember_filterMatching() {
        val group = StrategyGroup().apply {
            subscriptionGroupId = 1
            filter = "Germany|Netherlands"
        }
        val candidates = listOf(
            Candidate(id = 1, groupId = 1, remarks = "🇩🇪 Germany - 01"),
            Candidate(id = 2, groupId = 1, remarks = "🇳🇱 Netherlands - 01"),
            Candidate(id = 3, groupId = 1, remarks = "🇺🇸 USA - 01"),
            Candidate(id = 4, groupId = 2, remarks = "🇩🇪 Germany - 02 (Other Group)"),
        )

        assertTrue(CountryFlagUtils.strategyGroupContainsMember(group, 1, candidates))
        assertTrue(CountryFlagUtils.strategyGroupContainsMember(group, 2, candidates))
        assertFalse(CountryFlagUtils.strategyGroupContainsMember(group, 3, candidates))
        assertFalse(CountryFlagUtils.strategyGroupContainsMember(group, 4, candidates))
    }
}
