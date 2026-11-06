// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeButtonSize
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
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import platform.TunnelPhase
import platform.TunnelSnapshot

/** The aura of the connected power button must end in a circle, not in the edges of what holds it. */
class ProxyHomeHeroAuraTest {
    private fun state(
        size: ProxyHomeButtonSize,
        phase: TunnelPhase = TunnelPhase.Connected,
        pinned: Boolean = false,
    ): ProxyHomeUiState {
        val server = ProxyServerSummary(
            id = "s1", title = "Server", address = "example.test:443", protocol = "VLESS", selected = true, groupId = "g",
        )
        val group = ProxyGroupSummary("g", "Group", 1, true)
        return ProxyHomeUiState(
            tunnelSnapshot = TunnelSnapshot(phase = phase), selectedServerId = "s1", selectedServer = server,
            selectedServerTitle = "Server", activeProfileName = null, canToggleTunnel = true, tunnelBusy = false,
            groups = listOf(group), selectedGroupId = "g", servers = listOf(server), subscription = null,
            pages = listOf(ProxyHomePageUiState(group = group, servers = listOf(server), subscription = null, subscriptionExpanded = false)),
            isTestingLatency = false, searchQuery = "", isSearchVisible = false, searchEnabled = false,
            subscriptionSwipeEnabled = false, displayOptions = ProxyHomeDisplayOptions(
                connectionMode = ProxyHomeConnectionMode.Classic,
                classicButtonSize = size,
                pinConnectionPanel = pinned,
            ),
            sortMode = ProxyHomeSortMode.entries.first(), runtimeOutboundMetric = null, statusMessage = null,
            isStatusError = false, localError = null, availableActions = emptySet(), availableImportSources = emptySet(),
            availableCopyFormats = emptySet(), availableServerKinds = emptySet(), availableServerTools = emptySet(),
            busyActions = emptySet(), rejectedAction = null,
        )
    }


    private class Frames(state: ProxyHomeUiState, width: Int = 1280, height: Int = 800) {
        private var nanos = 0L
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f))

        init {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    ProxyHomeScreenContent(state = state, onAction = {})
                }
            }
        }

        fun at(millis: Long): Bitmap {
            val target = millis * 1_000_000L
            while (nanos < target) {
                nanos += 50_000_000L
                scene.render(nanos).close()
            }
            nanos += 1
            return scene.render(nanos).use { image -> Bitmap.makeFromImage(image) }
        }
    }

    private fun <T> Frames.use(block: Frames.() -> T): T = try {
        block()
    } finally {
        scene.close()
    }

    /** Where two frames of one screen differ: nothing but the waves moves, so this is the aura. */
    private fun changedArea(first: Bitmap, second: Bitmap, maxX: Int): IntArray {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in 0 until first.height) {
            for (x in 0 until minOf(first.width, maxX)) {
                val a = first.getColor(x, y)
                val b = second.getColor(x, y)
                val distance = abs((a shr 16 and 255) - (b shr 16 and 255)) +
                    abs((a shr 8 and 255) - (b shr 8 and 255)) +
                    abs((a and 255) - (b and 255))
                if (distance > 2) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return intArrayOf(left, top, right, bottom)
    }

    private fun assertAuraIsRound(size: ProxyHomeButtonSize, minDiameter: Int) = Frames(state(size)).use {
        // Both waves restart near 4.8 s, hidden behind the button; 900 ms later the first is past the ring.
        val hidden = at(4_850)
        val wave = at(5_750)

        val (left, top, right, bottom) = changedArea(hidden, wave, maxX = 420)
        val width = right - left
        val height = bottom - top
        assertTrue(height >= minDiameter, "the aura is only $height px tall")
        assertTrue(abs(width - height) <= 2, "the aura is $width x $height px, cut at $left, $top, $right, $bottom")
    }

    @Test
    fun theAuraOfTheLargestButtonIsRoundInAWideWindow() = assertAuraIsRound(ProxyHomeButtonSize.Giant, minDiameter = 340)

    @Test
    fun theAuraOfTheDefaultButtonIsRoundInAWideWindow() = assertAuraIsRound(ProxyHomeButtonSize.Large, minDiameter = 285)

    @Test
    fun theAuraDoesNotMoveTheButtonOrWhatIsUnderIt() {
        val frame = Frames(state(ProxyHomeButtonSize.Large, TunnelPhase.Disconnected)).use { at(500) }

        // The top of the ring, measured before the column gave the aura its room.
        val ringTop = (90 until frame.height).first { y -> frame.getColor(182, y) != frame.getColor(5, y) }
        assertEquals(98, ringTop)
    }

    @Test
    fun scrolledContentStaysOutOfTheHeader() {
        Frames(state(ProxyHomeButtonSize.Giant, TunnelPhase.Disconnected), height = 520).use {
            val resting = at(500)
            scene.sendPointerEvent(PointerEventType.Move, Offset(200f, 300f))
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(200f, 300f), scrollDelta = Offset(0f, 8f))
            val scrolled = at(1_500)

            assertNotEquals(
                (0 until 520).map { y -> resting.getColor(200, y) },
                (0 until 520).map { y -> scrolled.getColor(200, y) },
                "the left column did not scroll",
            )
            for (y in 0 until 98) {
                for (x in 0 until 420) {
                    assertEquals(resting.getColor(x, y), scrolled.getColor(x, y), "content reached the header at $x, $y")
                }
            }
        }
    }
}
