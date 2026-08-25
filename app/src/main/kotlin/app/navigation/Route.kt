// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.navigation

import androidx.navigation3.runtime.NavKey
import features.proxy.server.model.ProxyServer
import kotlinx.serialization.Serializable

/**
 * Type-safe navigation keys for Navigation3.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object Onboarding : Route

    @Serializable
    data object SettingsAppearance : Route

    @Serializable
    data object SettingsVpn : Route

    @Serializable
    data object SettingsNetworkAutomation : Route

    @Serializable
    data object SettingsSubscriptions : Route

    @Serializable
    data object SettingsIntegration : Route

    @Serializable
    data object SettingsLogs : Route

    @Serializable
    data object SettingsBackupReset : Route

    @Serializable
    data object About : Route

    @Serializable
    data object LocalProxySettings : Route

    @Serializable
    data object SubscriptionPingSettings : Route

    @Serializable
    data object License : Route

    @Serializable
    data object CoreLogs : Route

    @Serializable
    data object AccessLogs : Route

    /** Full-screen connection speed test (ping/jitter/download/upload). */
    @Serializable
    data object SpeedTest : Route

    /** Full-screen DNS leak test showing which resolvers see the queries. */
    @Serializable
    data object DnsLeakTest : Route

    /** Full-screen IP address info showing public IP, location, ISP and network. */
    @Serializable
    data object IpInfo : Route

    @Serializable
    data object LogcatLogs : Route

    @Serializable
    data class ResourceManagement(
        val trafficConfigId: Int,
    ) : Route

    @Serializable
    data class TrafficConfigEditor(
        val trafficConfigId: Int,
    ) : Route

    @Serializable
    data class TrafficConfigRawEditor(
        val trafficConfigId: Int,
    ) : Route

    @Serializable
    data class TrafficConfigSection(
        val trafficConfigId: Int,
        val section: TrafficConfigEditorSection,
    ) : Route

    /** Full-screen visual editor for one [Rule] entry in a traffic config. */
    @Serializable
    data class TrafficConfigRuleEditor(
        val trafficConfigId: Int,
        val ruleLineNumber: Int? = null,
    ) : Route

    @Serializable
    data object SubscriptionGroupList : Route

    @Serializable
    data class ProxyAppList(
        val trafficConfigId: Int,
    ) : Route

    /** Full-screen editor for application-wide subscription request identifiers. */
    @Serializable
    data object SubscriptionUserAgents : Route

    /** Reference for the externally handled skipi:// links. */
    @Serializable
    data object SkipiUrlSchemes : Route

    @Serializable
    data class ProxyServerEditor(
        val ps: ProxyServer<*>,
        val serverId: Int? = null,
        val groupId: Int? = null,
        val returnGroupId: Int? = null,
        val resultKey: String? = null,
    ) : Route

    /** Compact, grouped member picker used by custom strategy groups. */
    @Serializable
    data class StrategyGroupMemberSelector(
        val selectedServerIds: List<Int>,
        val excludedServerId: Int? = null,
        val resultKey: String,
        /**
         * Shadowrocket [Proxy Group] stores members by their remarks, so a
         * server without a name cannot be represented there.
         */
        val requireServerRemarks: Boolean = false,
    ) : Route

    /** Full-screen single-target picker used by a routing rule's Send through field. */
    @Serializable
    data class RouteOutboundSelector(
        val selectedTag: String,
        val resultKey: String,
        /**
         * When present, the picker returns a Shadowrocket policy value for this
         * config (for example a server remark or a [Proxy Group] name), rather
         * than an internal Xray outbound tag.
         */
        val trafficConfigId: Int? = null,
    ) : Route

    /** Full-screen editor for one application-wide routing rule. */
    @Serializable
    data class RoutingRuleEditor(
        val ruleId: Int? = null,
    ) : Route
}

@Serializable
enum class TrafficConfigEditorSection {
    General,
    Dns,
    Tunnel,
    Network,
    Routing,
    /** Full-screen editor for the standard Shadowrocket [Proxy Group] section. */
    ProxyGroups,
    /** Full-screen visual editor for the standard Shadowrocket [Rule] section. */
    RoutingRules,
}

data class ProxyServerEditResult(
    val serverId: Int,
    val server: ProxyServer<*>,
    val groupId: Int? = null,
    val returnGroupId: Int? = null,
)

data class StrategyGroupMemberSelectionResult(
    val serverIds: List<Int>,
)

data class RouteOutboundSelectionResult(
    val tag: String,
)
