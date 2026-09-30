// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.list

import app.AppState
import app.ProxyServerState
import app.SubscriptionGroupState
import app.modes.ConnectionDisplayModeCompact
import app.modes.ProxyServerListLayoutMultiple
import app.modes.ProxyServerListSortDefault
import app.modes.ProxyServerListSortLatency
import app.modes.ProxyServerListSortName
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeCopyFormat
import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyHomeImportSource
import app.skipi.app.home.ProxyHomeServerKind
import app.skipi.app.home.ProxyHomeServerTool
import app.skipi.app.home.ProxyHomeStore
import app.toProxyServerListState
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.StrategyGroupConstants
import features.proxy.server.model.VLESS
import features.proxy.server.presentation.ProxyServerPresentationFormatter
import features.proxy.server.presentation.ProxyServerPresentationLabels
import features.subscription.DefaultSubscriptionGroupId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import platform.TunnelPhase
import platform.TunnelFailure
import platform.TunnelSnapshot

class ProxyHomeAndroidInputTest {
    @Test
    fun mapsAllGroupMembershipSortedServersAndSubscriptionMetadata() {
        val manualGroup = SubscriptionGroupState(
            id = DefaultSubscriptionGroupId,
            name = "Manual",
            url = "",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = true,
        )
        val subscriptionGroup = SubscriptionGroupState(
            id = 2,
            name = "Work",
            url = "https://example.com/sub",
            userAgent = "client",
            updateInterval = "12",
            enabled = true,
            lastUpdatedAtMillis = 1234L,
            profileTitle = "Work profile",
            announce = "Maintenance",
            announceUrl = "https://example.com/notice",
            supportEmail = "help@example.com",
            profileWebPageUrl = "https://example.com/profile",
            trafficUploadBytes = 7L,
            trafficDownloadBytes = 11L,
            trafficTotalBytes = 100L,
            trafficExpireAtSeconds = 456L,
        )
        val alpha = ProxyServerState(
            id = 10,
            server = VLESS(remarks = "🇺🇸 Alpha", id = "a", server = "alpha.example", port = "443"),
            groupId = DefaultSubscriptionGroupId,
            latency = "19 ms",
        )
        val zulu = ProxyServerState(
            id = 20,
            server = VLESS(remarks = "Zulu", id = "z", server = "zulu.example", port = "8443"),
            groupId = subscriptionGroup.id,
            latency = "Timeout",
        )
        val state = AppState(
            subscriptionGroups = listOf(manualGroup, subscriptionGroup),
            proxyServers = listOf(zulu, alpha),
            selectedProxyServerId = alpha.id,
            enableAllProxyGroup = true,
            proxyServerListSort = ProxyServerListSortName,
            showServerSearch = true,
            enableSubscriptionSwipe = false,
        )
        val groups = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = AllProxyGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )

        val input = state.toProxyHomeInput(
            groupState = groups,
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
            tunnelBusy = false,
            isTestingLatency = true,
            refreshingSubscriptionGroupIds = setOf(subscriptionGroup.id),
            pingingSubscriptionGroupIds = setOf(subscriptionGroup.id),
        )

        // Legacy name sorting compares raw remarks, including the flag prefix.
        assertEquals(listOf("20", "10"), input.servers.map { it.id })
        val alphaSummary = input.servers.first { it.id == "10" }
        val zuluSummary = input.servers.first { it.id == "20" }
        assertEquals("Alpha", alphaSummary.title)
        assertEquals("🇺🇸", alphaSummary.flag)
        assertEquals("🇺🇸 Alpha", alphaSummary.sortKey)
        assertEquals("Zulu", zuluSummary.sortKey)
        assertEquals(19L, alphaSummary.latencyMs)
        assertTrue(zuluSummary.latencyError)
        assertEquals(setOf("10", "20"), input.groups.first { it.id == AllProxyGroupId.toString() }.serverIds)
        assertEquals(setOf("20"), input.groups.first { it.id == "2" }.serverIds)
        assertEquals("Alpha", input.selectedServerTitle)
        assertTrue(input.canToggleTunnel)
        assertTrue(input.isTestingLatency)
        assertTrue(input.searchEnabled)
        assertFalse(input.subscriptionSwipeEnabled)
        assertEquals("Default", input.activeProfileName)
        assertEquals(
            setOf(ProxyHomeImportSource.QrCode, ProxyHomeImportSource.Clipboard, ProxyHomeImportSource.File),
            input.availableImportSources,
        )
        assertEquals(
            setOf(ProxyHomeCopyFormat.Url, ProxyHomeCopyFormat.FullJson, ProxyHomeCopyFormat.QrCode),
            input.availableCopyFormats,
        )
        assertEquals(
            setOf(
                ProxyHomeServerKind.Http,
                ProxyHomeServerKind.Vmess,
                ProxyHomeServerKind.Vless,
                ProxyHomeServerKind.Trojan,
                ProxyHomeServerKind.Shadowsocks,
                ProxyHomeServerKind.Socks,
                ProxyHomeServerKind.Hysteria2,
                ProxyHomeServerKind.Wireguard,
                ProxyHomeServerKind.AmneziaWg,
                ProxyHomeServerKind.OlcRtc,
                ProxyHomeServerKind.StrategyGroup,
                ProxyHomeServerKind.ChainProxy,
                ProxyHomeServerKind.Custom,
            ),
            input.availableServerKinds,
        )
        assertEquals(
            setOf(
                ProxyHomeServerTool.RestartService,
                ProxyHomeServerTool.UpdateSubscriptions,
                ProxyHomeServerTool.DeleteDuplicateServers,
                ProxyHomeServerTool.DeleteInvalidServers,
                ProxyHomeServerTool.DeleteAllServers,
            ),
            input.availableServerTools,
        )

        val subscription = input.groups.first { it.id == "2" }.subscription
        assertNotNull(subscription)
        assertEquals(18L, subscription!!.usedBytes)
        assertEquals(100L, subscription.totalBytes)
        assertEquals("12", subscription.updateIntervalHours)
        assertEquals(456L, subscription.expireAtSeconds)
        assertEquals("Work profile", subscription.title)
        assertNull(subscription.description)
        assertEquals("Maintenance", subscription.announcement)
        assertEquals("https://example.com/notice", subscription.announcementUrl)
        assertEquals("mailto:help@example.com", subscription.supportUrl)
        assertEquals("https://example.com/profile", subscription.siteUrl)
        assertEquals(1234L, subscription.lastUpdatedAtMillis)
        assertTrue(subscription.refreshing)
        assertTrue(subscription.pinging)
        assertTrue(subscription.canCancelPing)
        assertTrue(subscription.enabled)
    }

    @Test
    fun mapsCompositeSummariesWithLocalizedSharedPresentationAndKeepsRegularAddresses() {
        val manualGroup = SubscriptionGroupState(
            id = DefaultSubscriptionGroupId,
            name = "Manual",
            url = "",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = true,
        )
        val subscriptionGroup = SubscriptionGroupState(
            id = 2,
            name = "Work",
            url = "https://example.com/sub",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = false,
        )
        val helsinki = ProxyServerState(
            id = 11,
            server = VLESS(remarks = "🇫🇮 Helsinki", id = "h", server = "helsinki.example", port = "443"),
            groupId = DefaultSubscriptionGroupId,
        )
        val london = ProxyServerState(
            id = 12,
            server = VLESS(remarks = "London", id = "l", server = "london.example", port = "8443"),
            groupId = subscriptionGroup.id,
        )
        val selectGroup = ProxyServerState(
            id = 20,
            server = StrategyGroup(
                remarks = "Chosen route",
                strategy = StrategyGroupConstants.TYPE_SELECT,
                proxyServerIds = listOf(11, 12),
                selectedMemberId = 12,
            ),
            groupId = DefaultSubscriptionGroupId,
        )
        val chain = ProxyServerState(
            id = 21,
            server = features.proxy.server.model.ChainProxy(
                remarks = "Two hops",
                proxyServerIds = listOf(11, 12),
            ),
            groupId = DefaultSubscriptionGroupId,
        )
        val state = AppState(
            subscriptionGroups = listOf(manualGroup, subscriptionGroup),
            proxyServers = listOf(helsinki, london, selectGroup, chain),
            selectedProxyServerId = helsinki.id,
            enableAllProxyGroup = true,
        )
        val groups = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = AllProxyGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )
        val formatter = ProxyServerListItemTextFormatter(
            ProxyServerPresentationFormatter(
                groupNames = groups.groupNames,
                labels = ProxyServerPresentationLabels(
                    unknownGroupName = "Unknown group",
                    allGroupsName = "All groups",
                    selectName = "Select",
                    leastPingName = "Least ping",
                    leastLoadName = "Least load",
                    randomName = "Random",
                    roundRobinName = "Round robin",
                    strategyGroupSummaryTemplate = "{strategy} · {group}",
                    strategyGroupSummaryWithFilterTemplate = "{strategy} · {group} · Filter: {filter}",
                    chainProxySummaryTemplate = "{count} hops",
                ),
            ),
        )

        val input = state.toProxyHomeInput(
            groupState = groups,
            tunnelSnapshot = TunnelSnapshot(),
            tunnelBusy = false,
            isTestingLatency = false,
            presentationFormatter = formatter,
        )

        assertEquals(helsinki.server.getInfo().address, input.servers.first { it.id == "11" }.address)
        assertEquals("Select: London (2)", input.servers.first { it.id == "20" }.address)
        assertEquals("🇫🇮 Helsinki -> London", input.servers.first { it.id == "21" }.address)
        assertEquals("Work", input.groups.first { it.id == "2" }.subscription?.title)
    }

    @Test
    fun disablesTunnelToggleWhenBusyOrWhenNoServerIsSelected() {
        val state = AppState(
            subscriptionGroups = listOf(
                SubscriptionGroupState(
                    id = DefaultSubscriptionGroupId,
                    name = "Manual",
                    url = "",
                    userAgent = "",
                    updateInterval = "",
                    enabled = true,
                    builtIn = true,
                ),
            ),
            proxyServers = emptyList(),
            selectedProxyServerId = 0,
        )
        val groups = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = DefaultSubscriptionGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )

        val idleInput = state.toProxyHomeInput(
            groupState = groups,
            tunnelSnapshot = TunnelSnapshot(),
            tunnelBusy = false,
            isTestingLatency = false,
        )
        val busyInput = state.toProxyHomeInput(
            groupState = groups,
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connecting),
            tunnelBusy = true,
            isTestingLatency = false,
        )

        assertFalse(idleInput.canToggleTunnel)
        assertFalse(busyInput.canToggleTunnel)
    }

    @Test
    fun disablesTunnelToggleWhenAndroidCannotReadTunnelStatus() {
        val server = ProxyServerState(
            id = 10,
            server = VLESS(remarks = "Node", id = "node", server = "node.example", port = "443"),
            groupId = DefaultSubscriptionGroupId,
        )
        val state = AppState(
            subscriptionGroups = listOf(
                SubscriptionGroupState(
                    id = DefaultSubscriptionGroupId,
                    name = "Manual",
                    url = "",
                    userAgent = "",
                    updateInterval = "",
                    enabled = true,
                    builtIn = true,
                ),
            ),
            proxyServers = listOf(server),
            selectedProxyServerId = server.id,
        )
        val groups = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = DefaultSubscriptionGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )

        val input = state.toProxyHomeInput(
            groupState = groups,
            tunnelSnapshot = TunnelSnapshot(
                phase = TunnelPhase.Failed,
                failure = TunnelFailure(
                    code = "android_vpn",
                    message = "VPN status unavailable",
                    recoverable = true,
                ),
            ),
            tunnelBusy = false,
            isTestingLatency = false,
        )

        assertFalse(input.canToggleTunnel)
        assertFalse(ProxyHomeActionId.ToggleTunnel in input.availableActions)
    }

    @Test
    fun selectedServerOutsideVisibleGroupsKeepsItsTitleAndCanReconnect() {
        val hiddenGroup = SubscriptionGroupState(
            id = 2,
            name = "Disabled subscription",
            url = "https://example.com/sub",
            userAgent = "",
            updateInterval = "",
            enabled = false,
        )
        val hiddenServer = ProxyServerState(
            id = 20,
            server = VLESS(remarks = "Hidden node", id = "hidden", server = "hidden.example", port = "443"),
            groupId = hiddenGroup.id,
        )
        val state = AppState(
            subscriptionGroups = listOf(
                SubscriptionGroupState(
                    id = DefaultSubscriptionGroupId,
                    name = "Manual",
                    url = "",
                    userAgent = "",
                    updateInterval = "",
                    enabled = true,
                    builtIn = true,
                ),
                hiddenGroup,
            ),
            proxyServers = listOf(hiddenServer),
            selectedProxyServerId = hiddenServer.id,
        )
        val groups = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = DefaultSubscriptionGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )
        val actualSnapshot = TunnelSnapshot(phase = TunnelPhase.Disconnected)

        val input = state.toProxyHomeInput(
            groupState = groups,
            tunnelSnapshot = actualSnapshot,
            tunnelBusy = false,
            isTestingLatency = false,
        )

        assertTrue(input.servers.isEmpty())
        assertEquals("Hidden node", input.selectedServerTitle)
        assertTrue(input.canToggleTunnel)
        assertEquals(actualSnapshot, input.tunnelSnapshot)
    }

    @Test
    fun mapsNondefaultDisplayOptionsAndAllGroupKindsAccurately() {
        val manualGroup = SubscriptionGroupState(
            id = DefaultSubscriptionGroupId,
            name = "Manual",
            url = "",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = true,
        )
        val subscriptionGroup = SubscriptionGroupState(
            id = 2,
            name = "Remote",
            url = "https://example.com/sub",
            userAgent = "",
            updateInterval = "24",
            enabled = true,
            builtIn = false,
        )
        val node1 = ProxyServerState(
            id = 1,
            server = VLESS(remarks = "Node 1", id = "n1", server = "n1.example", port = "443"),
            groupId = DefaultSubscriptionGroupId,
        )
        val node2 = ProxyServerState(
            id = 2,
            server = VLESS(remarks = "Node 2", id = "n2", server = "n2.example", port = "443"),
            groupId = subscriptionGroup.id,
        )
        val autoBalancer = ProxyServerState(
            id = 3,
            server = StrategyGroup(remarks = "Balancer 1"),
            groupId = AutoBalancerGroupId,
        )
        val state = AppState(
            subscriptionGroups = listOf(manualGroup, subscriptionGroup),
            proxyServers = listOf(node1, node2, autoBalancer),
            selectedProxyServerId = node1.id,
            connectionDisplayMode = ConnectionDisplayModeCompact,
            pinConnectionPanelOnHome = true,
            classicShowFloatingPowerButton = true,
            proxyServerListLayout = ProxyServerListLayoutMultiple,
            enableAllProxyGroup = true,
            showTunnelMemoryOnHome = true,
            showServerSearch = false,
            enableSubscriptionSwipe = false,
        )
        val groups = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = AllProxyGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )

        val input = state.toProxyHomeInput(
            groupState = groups,
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
            tunnelBusy = false,
            isTestingLatency = false,
        )

        assertEquals(ProxyHomeConnectionMode.Compact, input.displayOptions.connectionMode)
        assertTrue(input.displayOptions.pinConnectionPanel)
        assertTrue(input.displayOptions.classicFloatingPowerButton)
        assertEquals(3, input.displayOptions.requestedColumns)
        assertTrue(input.displayOptions.showAllGroup)
        assertTrue(input.displayOptions.showTunnelMemory)
        assertFalse(input.searchEnabled)
        assertFalse(input.subscriptionSwipeEnabled)

        val allGroupSummary = input.groups.first { it.id == AllProxyGroupId.toString() }
        assertEquals(ProxyHomeGroupKind.All, allGroupSummary.kind)
        assertNull(allGroupSummary.subscription)

        val autoBalancerSummary = input.groups.first { it.id == AutoBalancerGroupId.toString() }
        assertEquals(ProxyHomeGroupKind.AutoBalancer, autoBalancerSummary.kind)
        assertNull(autoBalancerSummary.subscription)

        val manualSummary = input.groups.first { it.id == DefaultSubscriptionGroupId.toString() }
        assertEquals(ProxyHomeGroupKind.Manual, manualSummary.kind)
        assertNull(manualSummary.subscription)

        val subscriptionSummary = input.groups.first { it.id == "2" }
        assertEquals(ProxyHomeGroupKind.Subscription, subscriptionSummary.kind)
        assertNotNull(subscriptionSummary.subscription)
    }

    @Test
    fun canonicalSortingIsIdenticalBeforeAndAfterUiMappingWithFlagStrippedDisplayTitles() {
        val manualGroup = SubscriptionGroupState(
            id = DefaultSubscriptionGroupId,
            name = "Manual",
            url = "",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = true,
        )
        val alpha = ProxyServerState(
            id = 10,
            server = VLESS(remarks = "🇺🇸 Alpha", id = "a", server = "alpha.example", port = "443"),
            groupId = DefaultSubscriptionGroupId,
        )
        val zulu = ProxyServerState(
            id = 20,
            server = VLESS(remarks = "Zulu", id = "z", server = "zulu.example", port = "8443"),
            groupId = DefaultSubscriptionGroupId,
        )
        val state = AppState(
            subscriptionGroups = listOf(manualGroup),
            proxyServers = listOf(alpha, zulu),
            selectedProxyServerId = alpha.id,
            proxyServerListSort = ProxyServerListSortName,
        )
        val groups = proxyServerListGroups(
            state = state.toProxyServerListState(),
            selectedGroupId = DefaultSubscriptionGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )

        val input = state.toProxyHomeInput(
            groupState = groups,
            tunnelSnapshot = TunnelSnapshot(),
            tunnelBusy = false,
            isTestingLatency = false,
        )

        // The adapter preserves host order; the shared store applies canonical name sorting.
        assertEquals(listOf("10", "20"), input.servers.map { it.id })

        val store = ProxyHomeStore(
            initialInput = input,
            scope = CoroutineScope(Dispatchers.Unconfined),
            effectHandler = { Result.success(Unit) },
        )

        // In store UI state, flag-stripped display title ("Alpha") does NOT reorder data
        val pageServers = store.uiState.value.pages.first().servers
        assertEquals(listOf("20", "10"), pageServers.map { it.id })
        assertEquals("Zulu", pageServers[0].title)
        assertEquals("Alpha", pageServers[1].title)
        assertEquals(listOf("20", "10"), store.uiState.value.servers.map { it.id })
    }

    @Test
    fun latencyAndDefaultSortingArePreservedThroughUiMapping() {
        val manualGroup = SubscriptionGroupState(
            id = DefaultSubscriptionGroupId,
            name = "Manual",
            url = "",
            userAgent = "",
            updateInterval = "",
            enabled = true,
            builtIn = true,
        )
        val slow = ProxyServerState(
            id = 1,
            server = VLESS(remarks = "Slow", id = "s", server = "slow.example", port = "443"),
            groupId = DefaultSubscriptionGroupId,
            latency = "500 ms",
        )
        val fast = ProxyServerState(
            id = 2,
            server = VLESS(remarks = "Fast", id = "f", server = "fast.example", port = "443"),
            groupId = DefaultSubscriptionGroupId,
            latency = "50 ms",
        )
        val unmeasured = ProxyServerState(
            id = 3,
            server = VLESS(remarks = "Unmeasured", id = "u", server = "u.example", port = "443"),
            groupId = DefaultSubscriptionGroupId,
            latency = "",
        )

        // The adapter preserves source order and the shared store applies the selected sort.
        val latencyState = AppState(
            subscriptionGroups = listOf(manualGroup),
            proxyServers = listOf(slow, fast, unmeasured),
            selectedProxyServerId = fast.id,
            proxyServerListSort = ProxyServerListSortLatency,
        )
        val latencyGroups = proxyServerListGroups(
            state = latencyState.toProxyServerListState(),
            selectedGroupId = DefaultSubscriptionGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )
        val latencyInput = latencyState.toProxyHomeInput(
            groupState = latencyGroups,
            tunnelSnapshot = TunnelSnapshot(),
            tunnelBusy = false,
            isTestingLatency = false,
        )
        assertEquals(listOf("1", "2", "3"), latencyInput.servers.map { it.id })

        val latencyStore = ProxyHomeStore(
            initialInput = latencyInput,
            scope = CoroutineScope(Dispatchers.Unconfined),
            effectHandler = { Result.success(Unit) },
        )
        assertEquals(listOf("2", "1", "3"), latencyStore.uiState.value.servers.map { it.id })

        // 2. Default sort: preserves proxyServers list order (1, 2, 3)
        val defaultState = AppState(
            subscriptionGroups = listOf(manualGroup),
            proxyServers = listOf(slow, fast, unmeasured),
            selectedProxyServerId = fast.id,
            proxyServerListSort = ProxyServerListSortDefault,
        )
        val defaultGroups = proxyServerListGroups(
            state = defaultState.toProxyServerListState(),
            selectedGroupId = DefaultSubscriptionGroupId,
            searchValue = "",
            allGroupName = "All",
            defaultGroupName = "Manual",
            autoBalancerGroupName = "Auto balancers",
        )
        val defaultInput = defaultState.toProxyHomeInput(
            groupState = defaultGroups,
            tunnelSnapshot = TunnelSnapshot(),
            tunnelBusy = false,
            isTestingLatency = false,
        )
        assertEquals(listOf("1", "2", "3"), defaultInput.servers.map { it.id })

        val defaultStore = ProxyHomeStore(
            initialInput = defaultInput,
            scope = CoroutineScope(Dispatchers.Unconfined),
            effectHandler = { Result.success(Unit) },
        )
        assertEquals(listOf("1", "2", "3"), defaultStore.uiState.value.servers.map { it.id })
    }
}
