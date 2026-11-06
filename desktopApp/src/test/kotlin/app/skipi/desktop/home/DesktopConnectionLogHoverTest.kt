// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeButtonSize
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomePageUiState
import app.skipi.app.home.ProxyHomeSortMode
import app.skipi.app.home.ProxyHomeUiState
import app.skipi.app.home.ProxyServerSummary
import app.skipi.ui.home.ProxyHomeScreenContent
import platform.TunnelPhase
import platform.TunnelSnapshot
import app.skipi.app.home.ConnectionLogLevel
import app.skipi.app.home.ConnectionLogMessage
import app.skipi.app.home.ConnectionLogSource
import app.skipi.ui.home.SkipiConnectionLogPanel
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Home log must stand still while the pointer is over it, and catch up once the pointer leaves. */
@OptIn(ExperimentalComposeUiApi::class)
class DesktopConnectionLogHoverTest {
    private val feed = DesktopLogFeed()
    private val log = DesktopConnectionLog(
        read = { Result.success("""{"seq":0,"entries":[]}""") },
        readMessages = feed::after,
    )
    private val scene = ImageComposeScene(width = 800, height = 600, density = Density(1f))

    init {
        scene.setContent {
            ProvideSkipiTheme(
                colors = resolveBaseAppearanceColors(isDark = true),
                fontFamily = null,
                fontSizeMode = null,
                fontWeightMode = null,
                visualProfile = SkipiVisualProfile.Desktop,
            ) {
                Box(Modifier.fillMaxSize().background(SkipiTheme.colors.background)) {
                    val state by log.state.collectAsState()
                    SkipiConnectionLogPanel(
                        state = state,
                        connected = true,
                        formatTime = { "00:00:00" },
                        onHeldChange = log::setHeld,
                        onCopy = {},
                        modifier = Modifier.size(400.dp, 400.dp),
                    )
                }
            }
        }
        settle()
    }

    private fun settle() = repeat(6) { scene.render().close() }

    private fun say(text: String) {
        feed.add(ConnectionLogMessage(timeMillis = System.currentTimeMillis(), level = ConnectionLogLevel.Error, source = ConnectionLogSource.App, text = text))
        log.poll()
        settle()
    }

    private fun pointerAt(at: Offset, type: PointerEventType = PointerEventType.Move) {
        scene.sendPointerEvent(type, at, buttons = PointerButtons())
        settle()
    }

    @Test
    fun theLogStandsStillUnderThePointerAndCatchesUpWhenItLeaves() {
        say("one")
        say("two")
        assertEquals(2, log.state.value.rows.size)

        pointerAt(Offset(200f, 200f))
        assertTrue(log.state.value.held, "the pointer is over the log, so it must be held")

        say("three")
        assertEquals(2, log.state.value.rows.size, "a held log must not take new lines")

        pointerAt(Offset(700f, 550f))
        assertFalse(log.state.value.held, "the pointer left, so the log is released")
        assertEquals(3, log.state.value.rows.size)
    }

    @Test
    fun theLogStaysHeldWhileTheWheelScrollsAndLinesKeepComing() {
        repeat(60) { say("line $it") }
        pointerAt(Offset(200f, 200f))
        assertTrue(log.state.value.held)
        val before = log.state.value.rows.map { it.id }

        repeat(5) {
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(200f, 200f), scrollDelta = Offset(0f, -3f))
            say("while scrolling $it")
        }
        assertTrue(log.state.value.held, "scrolling must keep the log held")
        assertEquals(before, log.state.value.rows.map { it.id }, "the rows must not change under the pointer")
    }

    @Test
    fun theLogOfTheWideHomeStandsStillUnderThePointer() {
        val server = ProxyServerSummary(
            id = "s1", title = "Server", address = "example.test:443", protocol = "VLESS", selected = true, groupId = "g",
        )
        val group = ProxyGroupSummary("g", "Group", 1, true)
        val state = ProxyHomeUiState(
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected), selectedServerId = "s1", selectedServer = server,
            selectedServerTitle = "Server", activeProfileName = null, canToggleTunnel = true, tunnelBusy = false,
            groups = listOf(group), selectedGroupId = "g", servers = listOf(server), subscription = null,
            pages = listOf(ProxyHomePageUiState(group = group, servers = listOf(server), subscription = null, subscriptionExpanded = false)),
            isTestingLatency = false, searchQuery = "", isSearchVisible = false, searchEnabled = false,
            subscriptionSwipeEnabled = false, displayOptions = ProxyHomeDisplayOptions(
                connectionMode = ProxyHomeConnectionMode.Classic,
                classicButtonSize = ProxyHomeButtonSize.entries.first(),
                pinConnectionPanel = false,
            ),
            sortMode = ProxyHomeSortMode.entries.first(), runtimeOutboundMetric = null, statusMessage = null,
            isStatusError = false, localError = null, availableActions = emptySet(), availableImportSources = emptySet(),
            availableCopyFormats = emptySet(), availableServerKinds = emptySet(), availableServerTools = emptySet(),
            busyActions = emptySet(), rejectedAction = null,
        )
        val wide = ImageComposeScene(width = 1400, height = 900, density = Density(1f))
        wide.setContent {
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
                    wideSidePanel = { panelModifier ->
                        val logState by log.state.collectAsState()
                        SkipiConnectionLogPanel(
                            state = logState,
                            connected = true,
                            formatTime = { "00:00:00" },
                            onHeldChange = log::setHeld,
                            onCopy = {},
                            modifier = panelModifier,
                        )
                    },
                )
            }
        }
        fun settleWide() = repeat(6) { wide.render().close() }
        settleWide()
        repeat(20) {
            feed.add(ConnectionLogMessage(timeMillis = 1L, level = ConnectionLogLevel.Error, source = ConnectionLogSource.App, text = "wide $it"))
        }
        log.poll()
        settleWide()
        assertEquals(20, log.state.value.rows.size)

        // The left column is 300+ dp wide and the log sits at its bottom.
        wide.sendPointerEvent(PointerEventType.Move, Offset(150f, 780f), buttons = PointerButtons())
        settleWide()
        assertTrue(log.state.value.held, "the pointer is over the log in the wide Home, so it must be held")
        wide.close()
    }
}
