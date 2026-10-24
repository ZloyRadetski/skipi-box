// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.StrategyGroupDisplayMode

/** Stable source-derived fixture shared by common grouping and Home projection contract tests. */
internal object ProxyServerGroupingAndroidContractFixture {
    val request = ProxyServerGroupingRequest(
        groups = listOf(
            group(id = 1, name = "Manual", enabled = true, builtIn = true),
            group(id = 8, name = "Later subscription", enabled = true),
            group(id = 3, name = "Disabled subscription", enabled = false),
            group(id = 4, name = "Empty subscription", enabled = true),
            group(id = 2, name = "Earlier subscription", enabled = true),
        ),
        servers = listOf(
            server(id = 10, groupId = 1, remarks = "🇺🇸 Manual node"),
            server(id = 20, groupId = 8, remarks = "Later subscription node"),
            server(id = 21, groupId = 2, remarks = "Earlier subscription node"),
            server(id = 30, groupId = 3, remarks = "Disabled node"),
            server(
                id = 40,
                groupId = -2,
                remarks = "User strategy",
                isStrategyGroup = true,
                displayMode = StrategyGroupDisplayMode.NEVER,
            ),
            server(
                id = 50,
                groupId = -2,
                remarks = "Always config",
                isStrategyGroup = true,
                sourceTrafficConfigId = 9,
                displayMode = StrategyGroupDisplayMode.ALWAYS,
            ),
            server(
                id = 51,
                groupId = -2,
                remarks = "Never config",
                isStrategyGroup = true,
                sourceTrafficConfigId = 7,
                displayMode = StrategyGroupDisplayMode.NEVER,
            ),
            server(
                id = 52,
                groupId = -2,
                remarks = "Active config",
                isStrategyGroup = true,
                sourceTrafficConfigId = 7,
                displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
            ),
            server(
                id = 53,
                groupId = -2,
                remarks = "Inactive config",
                isStrategyGroup = true,
                sourceTrafficConfigId = 9,
                displayMode = StrategyGroupDisplayMode.ACTIVE_CONFIG,
            ),
            server(id = 54, groupId = -2, remarks = "Non-strategy Auto row"),
        ),
        activeTrafficConfigId = 7,
        selectedGroupId = 0,
        searchQuery = "",
        enableAllGroup = true,
        allGroupName = "All",
        defaultGroupName = "Default group",
        autoBalancerGroupName = "Auto balancers",
        defaultGroupId = 1,
        autoBalancerGroupId = -2,
        allGroupId = 0,
    )

    private fun group(id: Int, name: String, enabled: Boolean, builtIn: Boolean = false) =
        ProxyServerGroupInput(id = id, name = name, enabled = enabled, builtIn = builtIn)

    private fun server(
        id: Int,
        groupId: Int,
        remarks: String,
        isStrategyGroup: Boolean = false,
        sourceTrafficConfigId: Int? = null,
        displayMode: String = StrategyGroupDisplayMode.ALWAYS,
    ) = ProxyServerGroupServerInput(
        id = id,
        groupId = groupId,
        remarks = remarks,
        isStrategyGroup = isStrategyGroup,
        sourceTrafficConfigId = sourceTrafficConfigId,
        displayMode = displayMode,
    )
}
