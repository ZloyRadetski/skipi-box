// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import app.skipi.app.home.ConnectionLogEntry
import app.skipi.app.home.ConnectionLogState
import app.skipi.ui.home.SkipiConnectionLogPanel
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SkipiConnectionLogPanelTest {
    private fun entries(count: Int, firstSeq: Long = 1) = (0 until count).map { index ->
        val seq = firstSeq + index
        ConnectionLogEntry(seq, seq * 1_000, "host$seq.test", 443, "tcp", if (seq % 2 == 0L) "direct" else "proxy")
    }

    private class Harness(state: ConnectionLogState, connected: Boolean = true) {
        var state by mutableStateOf(state)
        var connected by mutableStateOf(connected)
        val list = LazyListState()
        val held = mutableListOf<Boolean>()
        val copied = mutableListOf<String>()
        private var nanos = 0L
        val scene = ImageComposeScene(width = 300, height = 220, density = Density(1f))

        init {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = true),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        SkipiConnectionLogPanel(
                            state = this@Harness.state,
                            connected = this@Harness.connected,
                            formatTime = { "00:00:00" },
                            onHeldChange = { held += it },
                            onCopy = { copied += it },
                            modifier = Modifier.fillMaxSize(),
                            listState = list,
                        )
                    }
                }
            }
            settle()
        }

        fun settle() {
            repeat(6) {
                nanos += 100_000_000L
                scene.render(nanos).close()
            }
        }

        fun pixels(): ByteArray {
            nanos += 16_000_000L
            return scene.render(nanos).use { image ->
                checkNotNull(image.encodeToData()).bytes
            }
        }

        fun hover(y: Float = 100f) {
            scene.sendPointerEvent(PointerEventType.Enter, Offset(150f, y))
            scene.sendPointerEvent(PointerEventType.Move, Offset(150f, y))
            settle()
        }

        fun leave() {
            scene.sendPointerEvent(PointerEventType.Exit, Offset(-10f, -10f))
            settle()
        }

        fun wheel(lines: Float) {
            scene.sendPointerEvent(
                eventType = PointerEventType.Scroll,
                position = Offset(150f, 100f),
                scrollDelta = Offset(0f, lines),
            )
            settle()
        }

        fun click(y: Float) {
            val at = Offset(150f, y)
            scene.sendPointerEvent(PointerEventType.Move, at)
            scene.sendPointerEvent(PointerEventType.Press, at, buttons = PointerButtons(isPrimaryPressed = true))
            scene.sendPointerEvent(PointerEventType.Release, at)
            settle()
        }
    }

    private fun withHarness(harness: Harness, block: Harness.() -> Unit) {
        try {
            harness.block()
        } finally {
            harness.scene.close()
        }
    }

    @Test
    fun clickingARowCopiesThatRowsAddress() = withHarness(Harness(ConnectionLogState().append(entries(3)))) {
        // Rows are 20 px under a 20 px title, newest first: host3, host2, host1.
        click(50f)

        assertEquals(listOf("host2.test"), copied)
    }

    @Test
    fun theWheelScrollsBackThroughOlderRows() = withHarness(Harness(ConnectionLogState().append(entries(60)))) {
        hover()
        wheel(5f)

        assertTrue(list.firstVisibleItemIndex > 0, "the list did not move")
    }

    @Test
    fun pointerOverTheLogHoldsItAndLeavingReleasesItAndReturnsToTheNewest() =
        withHarness(Harness(ConnectionLogState().append(entries(60)))) {
            hover()
            assertEquals(true, held.lastOrNull())
            wheel(5f)

            leave()

            assertEquals(false, held.lastOrNull())
            assertEquals(0, list.firstVisibleItemIndex)
        }

    @Test
    fun aNewConnectionAppearsAtTheTopOfAnUnheldList() =
        withHarness(Harness(ConnectionLogState().append(entries(30)))) {
            state = state.append(entries(1, firstSeq = 31))
            settle()

            assertEquals(0, list.firstVisibleItemIndex)
            assertEquals(0, list.firstVisibleItemScrollOffset)
        }

    @Test
    fun aHeldListDoesNotMoveWhenRowsAreReleasedInBehindIt() =
        withHarness(Harness(ConnectionLogState().append(entries(60)).hold())) {
            hover()
            wheel(5f)
            val position = list.firstVisibleItemIndex
            assertTrue(position > 0)

            state = state.append(entries(3, firstSeq = 61))
            settle()

            assertEquals(position, list.firstVisibleItemIndex)
        }

    @Test
    fun nothingIsDrawnWhileDisconnected() {
        val blank = Harness(ConnectionLogState(), connected = false)
        val idle = Harness(ConnectionLogState(), connected = true)
        val busy = Harness(ConnectionLogState().append(entries(3)), connected = true)
        try {
            val blankPixels = blank.pixels()
            assertNotEquals(blankPixels.toList(), idle.pixels().toList(), "the waiting hint is missing")
            assertNotEquals(blankPixels.toList(), busy.pixels().toList(), "rows are missing")
            assertFalse(blank.list.isScrollInProgress)
        } finally {
            blank.scene.close()
            idle.scene.close()
            busy.scene.close()
        }
    }
}
