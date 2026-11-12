package features.routing

import app.AppState
import app.DefaultRouteOutboundTag
import app.ProxyServerState
import app.SubscriptionGroupState
import app.skipi.app.routing.RouteOutboundItem
import app.skipi.app.routing.RouteOutboundPickerGroup
import app.skipi.app.routing.RouteOutboundPickerLabels
import features.config.TrafficConfigState
import features.proxy.server.list.AutoBalancerGroupId
import features.proxy.server.model.Custom
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.subscription.DefaultSubscriptionGroupId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Draft only: publish into app/src/test after the root opens the source-set gate.
 * This test intentionally calls the extracted production projection helper; it
 * contains no second implementation of the selector grouping algorithm.
 */
class RouteOutboundSelectorProjectionAndroidOracleTest {

    @Test
    fun profilePolicyProjectionKeepsAndroidOrderEmptyGroupsAndPolicyTags() {
        val state = stateWithCatalog(
            currentConfig = TrafficConfigState(
                id = 42,
                name = "Profile",
                rawConfig = """
                    [Proxy Group]
                    Preferred = select, Manual Node, Provider Node
                """.trimIndent(),
            ),
        )

        val result = projectRouteOutboundPicker(
            appState = state,
            selectedTag = "Provider Node",
            trafficConfigId = 42,
            labels = labels,
        )

        assertEquals(
            listOf(
                "routing-system-targets",
                "routing-auto-balancers",
                "routing-manual-servers",
                "routing-subscription-7",
                "routing-subscription-8",
            ),
            result.groups.map { it.key },
        )
        assertEquals(listOf("PROXY", "DIRECT", "REJECT"), result.groups[0].tags())
        // The global proxy groups and the profile's own are one group.
        assertEquals(listOf("Balance", "Preferred"), result.groups[1].tags())
        assertEquals(listOf("Provider Node"), result.groups[3].tags())
        assertTrue("empty subscription groups remain present", result.groups[4].items.isEmpty())
        assertEquals(listOf("Manual Node"), result.groups[2].tags())
        assertEquals("select", (result.groups[1].items.last() as RouteOutboundItem.Static).summary)
        assertEquals("routing-subscription-7", result.initiallyExpandedGroupKey)
    }

    @Test
    fun profilePolicyUnknownCaseVariantIsPreservedAsUnknownAndExpandsSystemGroup() {
        val state = stateWithCatalog(
            currentConfig = TrafficConfigState(
                id = 42,
                name = "Profile",
                rawConfig = """
                    [Proxy Group]
                    Preferred = select, DIRECT
                """.trimIndent(),
            ),
        )

        val result = projectRouteOutboundPicker(
            appState = state,
            selectedTag = "preferred",
            trafficConfigId = 42,
            labels = labels,
        )

        assertEquals(listOf("PROXY", "DIRECT", "REJECT", "preferred"), result.groups[0].tags())
        assertEquals(listOf("Balance", "Preferred"), result.groups[1].tags())
        assertEquals("routing-system-targets", result.initiallyExpandedGroupKey)
    }

    @Test
    fun globalProjectionUsesNumericServerTagsAndOmitsProfileOnlyTargets() {
        val state = stateWithCatalog(
            currentConfig = TrafficConfigState(
                id = 42,
                name = "Profile",
                rawConfig = """
                    [Proxy Group]
                    Preferred = select, DIRECT
                """.trimIndent(),
            ),
        )

        val result = projectRouteOutboundPicker(
            appState = state,
            selectedTag = "12",
            trafficConfigId = null,
            labels = labels,
        )

        assertEquals(
            listOf(
                "routing-system-targets",
                "routing-auto-balancers",
                "routing-manual-servers",
                "routing-subscription-7",
                "routing-subscription-8",
            ),
            result.groups.map { it.key },
        )
        assertEquals(listOf(DefaultRouteOutboundTag, "direct", "block"), result.groups[0].tags())
        assertEquals(listOf("12"), result.groups[3].tags())
        assertTrue(result.groups[4].items.isEmpty())
        assertFalse(result.groups.any { it.key == "routing-config-targets" })
        assertEquals("routing-subscription-7", result.initiallyExpandedGroupKey)
    }

    @Test
    fun missingExplicitProfileIdStillUsesPolicyTargets() {
        val currentConfig = TrafficConfigState(
            id = 42,
            name = "Profile",
            rawConfig = "",
        )

        val result = projectRouteOutboundPicker(
            appState = stateWithCatalog(currentConfig),
            selectedTag = "Provider Node",
            trafficConfigId = 404,
            labels = labels,
        )

        assertEquals(listOf("PROXY", "DIRECT", "REJECT"), result.groups[0].tags())
        assertEquals(listOf("Provider Node"), result.groups[3].tags())
        assertEquals(listOf("Balance"), result.groups[1].tags())
        assertFalse(result.groups.any { it.key == "routing-config-targets" })
        assertEquals("routing-subscription-7", result.initiallyExpandedGroupKey)
    }

    @Test
    fun profileModeFiltersUnsupportedCustomAndBlankRemarksButGlobalModeKeepsBlankServer() {
        val profile = TrafficConfigState(id = 42, name = "Profile", rawConfig = "")
        val state = stateWithCatalog(profile).copy(
            proxyServers = listOf(
                ProxyServerState(
                    id = 70,
                    groupId = 7,
                    server = Custom(
                        remarks = "Runnable Custom",
                        configJson = """{"outbounds":[{"tag":"proxy","protocol":"socks","settings":{}}]}""",
                    ),
                ),
                ProxyServerState(
                    id = 71,
                    groupId = 7,
                    server = Custom(
                        remarks = "Unsupported Custom",
                        configJson = """{"outbounds":[{"tag":"direct","protocol":"freedom","settings":{}}]}""",
                    ),
                ),
                ProxyServerState(
                    id = 72,
                    groupId = 7,
                    server = VLESS(remarks = " \t ", id = "blank", server = "blank.example", port = "443"),
                ),
            ),
        )

        val profileResult = projectRouteOutboundPicker(
            appState = state,
            selectedTag = "Runnable Custom",
            trafficConfigId = profile.id,
            labels = labels,
        )
        val globalResult = projectRouteOutboundPicker(
            appState = state,
            selectedTag = "70",
            trafficConfigId = null,
            labels = labels,
        )

        assertEquals(listOf("Runnable Custom"), profileResult.groups[3].tags())
        assertEquals(listOf("70", "72"), globalResult.groups[3].tags())
    }

    @Test
    fun profileSystemTargetMatchingIgnoresCaseAndConfigGroupExpandsOnCurrentSelection() {
        val profile = TrafficConfigState(
            id = 42,
            name = "Profile",
            rawConfig = """
                [Proxy Group]
                Preferred = select, DIRECT
            """.trimIndent(),
        )
        val state = stateWithCatalog(profile)

        val direct = projectRouteOutboundPicker(
            appState = state,
            selectedTag = "direct",
            trafficConfigId = profile.id,
            labels = labels,
        )
        val selectedGroup = projectRouteOutboundPicker(
            appState = state,
            selectedTag = "Preferred",
            trafficConfigId = profile.id,
            labels = labels,
        )

        assertEquals(listOf("PROXY", "DIRECT", "REJECT"), direct.groups[0].tags())
        assertEquals("routing-system-targets", direct.initiallyExpandedGroupKey)
        assertEquals(listOf("Balance", "Preferred"), selectedGroup.groups[1].tags())
        assertEquals("routing-auto-balancers", selectedGroup.initiallyExpandedGroupKey)
    }

    @Test
    fun knownServerOutsideRenderedGroupsIsNotAddedAsSystemOption() {
        val profile = TrafficConfigState(id = 42, name = "Profile", rawConfig = "")
        val state = stateWithCatalog(profile).copy(
            proxyServers = listOf(
                ProxyServerState(
                    id = 44,
                    groupId = 404,
                    server = VLESS(remarks = "Unrendered server", id = "orphan", server = "orphan.example", port = "443"),
                ),
            ),
        )

        val result = projectRouteOutboundPicker(
            appState = state,
            selectedTag = "44",
            trafficConfigId = null,
            labels = labels,
        )

        assertEquals(listOf(DefaultRouteOutboundTag, "direct", "block"), result.groups[0].tags())
        assertTrue(result.groups.none { group -> group.items.any { it is RouteOutboundItem.Server } })
        assertEquals("routing-system-targets", result.initiallyExpandedGroupKey)
    }

    private fun RouteOutboundPickerGroup.tags(): List<String> = items.map { item ->
        when (item) {
            is RouteOutboundItem.Static -> item.tag
            is RouteOutboundItem.Server -> item.tag
        }
    }

    private fun stateWithCatalog(currentConfig: TrafficConfigState): AppState = AppState(
        subscriptionGroups = listOf(
            subscriptionGroup(DefaultSubscriptionGroupId, "Manual"),
            subscriptionGroup(7, "Provider"),
            subscriptionGroup(8, "Empty provider"),
        ),
        proxyServers = listOf(
            ProxyServerState(
                id = 12,
                groupId = 7,
                server = VLESS(remarks = " Provider Node ", id = "provider", server = "provider.example", port = "443"),
            ),
            ProxyServerState(
                id = 21,
                groupId = DefaultSubscriptionGroupId,
                server = VLESS(remarks = "Manual Node", id = "manual", server = "manual.example", port = "443"),
            ),
            ProxyServerState(
                id = 31,
                groupId = AutoBalancerGroupId,
                server = StrategyGroup(remarks = "Balance"),
            ),
            ProxyServerState(
                id = 32,
                groupId = AutoBalancerGroupId,
                server = StrategyGroup(remarks = "Config-owned", sourceTrafficConfigId = currentConfig.id),
            ),
        ),
        trafficConfigs = listOf(
            currentConfig,
            TrafficConfigState(id = 99, name = "Other profile", rawConfig = ""),
        ),
    )

    private fun subscriptionGroup(id: Int, name: String) = SubscriptionGroupState(
        id = id,
        name = name,
        url = "",
        userAgent = "",
        updateInterval = "",
        enabled = true,
    )

    private val labels = RouteOutboundPickerLabels(
        defaultGroupName = "Manual",
        autoBalancerName = "Auto balancers",
        systemTargetsName = "System targets",
        proxyLabel = "Proxy",
        directLabel = "Direct",
        blockLabel = "Block",
    )
}
