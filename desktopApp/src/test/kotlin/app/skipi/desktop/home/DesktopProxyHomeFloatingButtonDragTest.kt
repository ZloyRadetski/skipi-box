// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeButtonPosition
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyServerSummary
import app.skipi.ui.home.ProxyHomeScreenContent
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopProxyHomeFloatingButtonDragTest {
    // The grip and the buttons of the toolbar in the bottom-end zone, and the toggle in the bottom-start one.
    private val bottomEndGrip = Offset(212f, 790f)
    private val bottomEndPing = Offset(258f, 790f)
    private val bottomEndToggle = Offset(318f, 790f)
    private val bottomStartToggle = Offset(154f, 790f)
    private val gripToToggle = Offset(106f, 0f)
    private val toggled = listOf<ProxyHomeEffect>(ProxyHomeEffect.ToggleTunnel)

    @Test
    fun aFingerSlidingOverTheButtonsNeverMovesTheCapsule() = onHome { home ->
        home.swipe(from = bottomEndToggle, to = Offset(120f, 400f))
        home.swipe(from = bottomEndPing, to = Offset(60f, 400f))

        assertEquals(emptyList(), home.positions)
        assertEquals(0, home.vibrations())
        // Still where it was.
        home.effects.clear()
        home.click(bottomEndToggle)
        assertEquals(toggled, home.effects)
    }

    @Test
    fun holdingAButtonNoLongerPicksTheCapsuleUp() = onHome { home ->
        home.holdAndDrag(from = bottomEndToggle, to = Offset(120f, 400f))

        assertEquals(emptyList(), home.positions)
        assertEquals(0, home.vibrations())
    }

    @Test
    fun theGripDragsAtOnceAndNearAZoneTheCapsuleSticksToIt() = onHome { home ->
        home.swipe(from = bottomEndGrip, to = Offset(62f, 780f))

        // The middle zone passed on the way, the start zone reached, and the sticking.
        assertEquals(3, home.vibrations())
        assertEquals(listOf(ProxyHomeButtonPosition(0f, 1f)), home.positions)
        assertEquals(emptyList(), home.effects)
        home.click(bottomStartToggle)
        assertEquals(toggled, home.effects)
    }

    @Test
    fun droppedAnywhereElseTheCapsuleStaysAndWorksThere() = onHome { home ->
        val dropped = Offset(150f, 400f)

        home.dragHeld(from = bottomEndGrip, to = dropped)
        val vibrationsWhileHeld = home.vibrations()
        home.release(dropped)
        // Nothing stuck, so letting go added no vibration.
        assertEquals(vibrationsWhileHeld, home.vibrations())
        home.click(dropped + gripToToggle)

        val position = home.positions.single()
        assertTrue(position.x > 0.3f && position.x < 0.9f, position.toString())
        assertTrue(position.y > 0.3f && position.y < 0.7f, position.toString())
        assertEquals(toggled, home.effects)
    }

    @Test
    fun theCapsuleCanBeParkedAtTheTopOfTheScreen() = onHome { home ->
        home.swipe(from = bottomEndGrip, to = Offset(212f, 0f))

        assertEquals(listOf(ProxyHomeButtonPosition(1f, 0f)), home.positions)
        home.click(Offset(318f, 62f))
        assertEquals(toggled, home.effects)
    }

    @Test
    fun stickingBackToTheZoneItCameFromVibratesToo() = onHome { home ->
        home.swipe(from = bottomEndGrip, to = bottomEndGrip - Offset(30f, 0f))

        assertEquals(1, home.vibrations())
        assertEquals(emptyList(), home.positions)
    }

    @Test
    fun enteringAZoneVibratesWhileTheCapsuleIsStillHeld() = onHome { home ->
        val nearTheSideMiddle = Offset(212f, 400f)

        home.dragHeld(from = bottomEndGrip, to = nearTheSideMiddle)
        // The zone entered.
        assertEquals(1, home.vibrations())
        // Moving about inside the zone says nothing more.
        home.moveHeld(from = nearTheSideMiddle, to = nearTheSideMiddle + Offset(0f, 15f))
        assertEquals(1, home.vibrations())

        home.release(nearTheSideMiddle + Offset(0f, 15f))
        assertEquals(2, home.vibrations())
        assertEquals(listOf(ProxyHomeButtonPosition(1f, 0.5f)), home.positions)
    }

    @Test
    fun aZoneReachesFurtherThanFortyDp() = onHome { home ->
        home.swipe(from = bottomEndGrip, to = bottomEndGrip - Offset(0f, 48f))

        assertEquals(emptyList(), home.positions)
    }

    @Test
    fun takingHoldOfTheGripDoesNotVibrate() = onHome { home ->
        home.dragHeld(from = bottomEndGrip, to = bottomEndGrip - Offset(30f, 0f))

        assertEquals(0, home.vibrations())
    }

    @Test
    fun tappingTheGripDoesNothing() = onHome { home ->
        home.click(bottomEndGrip)

        assertEquals(emptyList(), home.positions)
        assertEquals(emptyList(), home.effects)
        assertEquals(0, home.vibrations())
    }

    @Test
    fun holdingThePingStillPingsEverythingAndDoesNotMoveTheCapsule() = onHome { home ->
        home.holdAndDrag(from = bottomEndPing, to = Offset(60f, 400f))

        assertEquals(emptyList(), home.positions)
        assertEquals(1, home.effects.size)
        assertTrue(home.effects.single() is ProxyHomeEffect.TestVisibleServers, home.effects.toString())
    }

    @Test
    fun theSavedPositionIsWhereTheCapsuleAppears() = onHome(ProxyHomeButtonPosition(0f, 1f)) { home ->
        home.click(bottomStartToggle)

        assertEquals(toggled, home.effects)
        assertEquals(emptyList(), home.positions)
    }

    @Test
    fun anOrdinaryClickDoesNotMoveTheCapsuleOrVibrate() = onHome { home ->
        home.click(bottomEndToggle)

        assertEquals(emptyList(), home.positions)
        assertEquals(0, home.vibrations())
        assertEquals(toggled, home.effects)
    }

    private class Home(
        private val scene: ImageComposeScene,
        val effects: MutableList<ProxyHomeEffect>,
        val positions: List<ProxyHomeButtonPosition>,
        val vibrations: () -> Int,
    ) {
        private var time = 0L

        private fun frame() {
            time += 16_000_000L
            scene.render(time).close()
        }

        /** Frames let the snap animation finish before the next gesture. */
        fun settle() = repeat(90) { frame() }

        private fun send(type: PointerEventType, position: Offset, pressed: Boolean) {
            scene.sendPointerEvent(
                eventType = type,
                position = position,
                buttons = PointerButtons(isPrimaryPressed = pressed),
                button = PointerButton.Primary.takeIf { type != PointerEventType.Move },
            )
        }

        fun click(position: Offset) {
            send(PointerEventType.Move, position, pressed = false)
            send(PointerEventType.Press, position, pressed = true)
            send(PointerEventType.Release, position, pressed = false)
            settle()
        }

        private fun press(position: Offset) {
            send(PointerEventType.Move, position, pressed = false)
            send(PointerEventType.Press, position, pressed = true)
        }

        /** The long press is timed by the clock, not by frames. */
        private fun hold() {
            Thread.sleep(900)
            frame()
        }

        fun moveHeld(from: Offset, to: Offset) {
            for (step in 1..10) {
                send(PointerEventType.Move, from + (to - from) * (step / 10f), pressed = true)
                frame()
            }
        }

        fun release(position: Offset) {
            send(PointerEventType.Release, position, pressed = false)
            settle()
        }

        fun dragHeld(from: Offset, to: Offset) {
            press(from)
            moveHeld(from, to)
        }

        private fun moveAndRelease(from: Offset, to: Offset) {
            moveHeld(from, to)
            release(to)
        }

        fun swipe(from: Offset, to: Offset) {
            press(from)
            moveAndRelease(from, to)
        }

        fun holdAndDrag(from: Offset, to: Offset) {
            press(from)
            hold()
            moveAndRelease(from, to)
        }
    }

    private fun onHome(
        position: ProxyHomeButtonPosition = ProxyHomeButtonPosition.BottomEnd,
        block: (Home) -> Unit,
    ) {
        val effects = mutableListOf<ProxyHomeEffect>()
        val positions = mutableListOf<ProxyHomeButtonPosition>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ProxyHomeStore(
            initialInput = ProxyHomeInput(
                tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
                selectedServerId = "selected",
                servers = listOf(
                    ProxyServerSummary(
                        id = "selected",
                        title = "Selected server",
                        address = "selected.example:443",
                        protocol = "vless",
                    ),
                ),
                displayOptions = ProxyHomeDisplayOptions(classicFloatingPowerButton = true, floatingButtonPosition = position),
                canToggleTunnel = true,
                availableActions = setOf(
                    ProxyHomeActionId.ToggleTunnel,
                    ProxyHomeActionId.TestConnection,
                    ProxyHomeActionId.TestVisibleServers,
                ),
            ),
            scope = scope,
            effectHandler = ProxyHomeEffectHandler { effect ->
                effects += effect
                Result.success(Unit)
            },
        )
        var vibrations = 0
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                vibrations++
            }
        }
        val scene = ImageComposeScene(width = 390, height = 844)
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = false),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                        ProxyHomeScreenContent(
                            state = store.uiState.value,
                            onAction = store::dispatch,
                            onFloatingButtonPositionChange = { positions += it },
                        )
                    }
                }
            }
            val home = Home(scene, effects, positions) { vibrations }
            home.settle()
            block(home)
        } finally {
            scene.close()
            scope.cancel()
        }
    }
}
