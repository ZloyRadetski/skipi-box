// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.server.editor

import app.skipi.ui.subscription.SubscriptionGroupUiState
import features.subscription.DefaultSubscriptionGroupId
import kotlin.test.Test
import kotlin.test.assertEquals

class ProxyServerEditorGroupOptionsProjectionTest {
    @Test
    fun includesAllFirstThenEligibleGroupsInSourceOrder() {
        val groups = listOf(
            group(id = 15, name = "Disabled group", enabled = false),
            group(id = 27, name = "Enabled group", enabled = true, profileTitle = "Remote profile title"),
            group(id = 38, name = "Built-in group", enabled = false, builtIn = true),
            group(id = DefaultSubscriptionGroupId, name = "Manual servers", enabled = false),
        )

        assertEquals(
            listOf(
                ProxyServerEditorGroupOption(id = null, label = "All groups"),
                ProxyServerEditorGroupOption(id = 27, label = "Enabled group"),
                ProxyServerEditorGroupOption(id = 38, label = "Built-in group"),
                ProxyServerEditorGroupOption(id = DefaultSubscriptionGroupId, label = "Manual servers"),
            ),
            projectProxyServerEditorGroupOptions(
                groups = groups,
                allGroupsLabel = "All groups",
                defaultGroupName = "Default group",
            ),
        )
    }

    @Test
    fun usesDisplayNameAndFallsBackForBlankNamesInsteadOfProfileTitle() {
        val groups = listOf(
            group(id = 7, name = "Visible name", enabled = true, profileTitle = "Metadata title"),
            group(id = 9, name = "", enabled = true, profileTitle = "Profile title must not be used"),
        )

        assertEquals(
            listOf(
                ProxyServerEditorGroupOption(id = null, label = "All"),
                ProxyServerEditorGroupOption(id = 7, label = "Visible name"),
                ProxyServerEditorGroupOption(id = 9, label = "Fallback default"),
            ),
            projectProxyServerEditorGroupOptions(
                groups = groups,
                allGroupsLabel = "All",
                defaultGroupName = "Fallback default",
            ),
        )
    }

    @Test
    fun emptyGroupsStillOfferAll() {
        assertEquals(
            listOf(ProxyServerEditorGroupOption(id = null, label = "All")),
            projectProxyServerEditorGroupOptions(
                groups = emptyList(),
                allGroupsLabel = "All",
                defaultGroupName = "Default group",
            ),
        )
    }

    @Test
    fun customDefaultGroupIdIncludesDisabledManualGroupButNotDisabledSubscriptionOne() {
        val groups = listOf(
            group(id = 1, name = "Subscription one", enabled = false),
            group(id = -1, name = "Manual servers", enabled = false),
        )

        assertEquals(
            listOf(
                ProxyServerEditorGroupOption(id = null, label = "All"),
                ProxyServerEditorGroupOption(id = -1, label = "Manual servers"),
            ),
            projectProxyServerEditorGroupOptions(
                groups = groups,
                allGroupsLabel = "All",
                defaultGroupName = "Default group",
                defaultGroupId = -1,
            ),
        )
    }

    private fun group(
        id: Int,
        name: String,
        enabled: Boolean,
        builtIn: Boolean = false,
        profileTitle: String = "",
    ) = SubscriptionGroupUiState(
        id = id,
        name = name,
        url = "",
        userAgent = "",
        updateInterval = "",
        enabled = enabled,
        builtIn = builtIn,
        profileTitle = profileTitle,
    )
}
