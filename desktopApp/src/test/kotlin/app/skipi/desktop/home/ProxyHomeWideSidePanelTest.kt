// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomePageUiState
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.ProxyServerSummary
import app.skipi.ui.home.ProxyHomeScreenContent
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.TunnelPhase
import platform.TunnelSnapshot

class ProxyHomeWideSidePanelTest {
    private fun state(mode: ProxyHomeConnectionMode = ProxyHomeConnectionMode.Classic): ProxyHomeUiState {
        val server = ProxyServerSummary(
            id = "s1", title = "Server", address = "example.test:443", protocol = "VLESS", selected = true, groupId = "g",
        )
        val group = ProxyGroupSummary("g", "Group", 1, true)
        return ProxyHomeUiState(
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected), selectedServerId = "s1", selectedServer = server,
            selectedServerTitle = "Server", activeProfileName = null, canToggleTunnel = true, tunnelBusy = false,
            groups = listOf(group), selectedGroupId = "g", servers = listOf(server), subscription = null,
            pages = listOf(ProxyHomePageUiState(group = group, servers = listOf(server), subscription = null, subscriptionExpanded = false)),
            isTestingLatency = false, searchQuery = "", isSearchVisible = false, searchEnabled = false,
            subscriptionSwipeEnabled = false, displayOptions = ProxyHomeDisplayOptions(connectionMode = mode),
            sortMode = ProxyHomeSortMode.entries.first(), runtimeOutboundMetric = null, statusMessage = null,
            isStatusError = false, localError = null, availableActions = emptySet(), availableImportSources = emptySet(),
            availableCopyFormats = emptySet(), availableServerKinds = emptySet(), availableServerTools = emptySet(),
            busyActions = emptySet(), rejectedAction = null,
        )
    }

    private fun panelBounds(width: Int, height: Int, state: ProxyHomeUiState): Rect? {
        var bounds: Rect? = null
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f))
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    ProxyHomeScreenContent(
                        state = state,
                        onAction = {},
                        wideSidePanel = { modifier ->
                            Box(modifier.onGloballyPositioned { bounds = it.boundsInRoot() })
                        },
                    )
                }
            }
            scene.render(0L).close()
            scene.render(100_000_000L).close()
        } finally {
            scene.close()
        }
        return bounds
    }

    @Test
    fun aWideWindowGivesThePanelTheRestOfTheLeftColumn() {
        val short = assertNotNull(panelBounds(1280, 800, state()))
        val tall = assertNotNull(panelBounds(1280, 1000, state()))

        assertEquals(300f, short.width)
        assertTrue(short.left < 640f, "the panel must sit in the left column")
        assertTrue(short.height >= 100f, "only ${short.height} px left for the panel")
        assertEquals(200f, tall.height - short.height, "the panel must take all the extra height")
    }

    @Test
    fun aOneColumnWindowHasNoSidePanel() {
        assertNull(panelBounds(500, 800, state()))
    }

    @Test
    fun theLegacyLayoutHasNoLeftColumnForIt() {
        assertNull(panelBounds(1280, 800, state(ProxyHomeConnectionMode.Legacy)))
    }
}
