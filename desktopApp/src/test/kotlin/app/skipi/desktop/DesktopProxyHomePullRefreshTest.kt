// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import app.skipi.app.home.ProxyGroupSummary
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyServerSummary
import app.skipi.app.home.ProxySubscriptionSummary
import app.skipi.ui.home.ProxyHomeScreenContent
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.jetbrains.skia.Image
import platform.TunnelPhase
import platform.TunnelSnapshot
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse

class DesktopProxyHomePullRefreshTest {
    @Test
    fun draggingDownFromTheTopOfTheListDoesNotPullTheListDownOnDesktop() {
        val before = renderBeforeAndAfterPullDrag(SkipiVisualProfile.Desktop)

        assertContentEquals(before.first, before.second)
    }

    @Test
    fun touchProfileStillPullsTheListDownSoTheCheckDetectsTheShift() {
        val shifted = renderBeforeAndAfterPullDrag(SkipiVisualProfile.Android)

        assertFalse(shifted.first.contentEquals(shifted.second))
    }

    private fun renderBeforeAndAfterPullDrag(profile: SkipiVisualProfile): Pair<ByteArray, ByteArray> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ProxyHomeStore(
            initialInput = subscriptionInput(),
            scope = scope,
            effectHandler = ProxyHomeEffectHandler { Result.success(Unit) },
        )
        val scene = ImageComposeScene(width = 390, height = 844)
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = false),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = profile,
                ) {
                    ProxyHomeScreenContent(
                        state = store.uiState.value,
                        onAction = store::dispatch,
                    )
                }
            }
            val before = scene.renderBytes()
            // A downward drag from the top of the list is the gesture that pulls the list down.
            val start = Offset(195f, 300f)
            var nanoTime = 0L
            scene.sendPointerEvent(
                eventType = PointerEventType.Press,
                position = start,
                buttons = PointerButtons(isPrimaryPressed = true),
                type = PointerType.Touch,
            )
            for (step in 1..12) {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Move,
                    position = start + Offset(0f, step * 20f),
                    buttons = PointerButtons(isPrimaryPressed = true),
                    type = PointerType.Touch,
                )
                nanoTime += 16_000_000L
                scene.render(nanoTime).close()
            }
            return before to scene.renderBytes()
        } finally {
            scene.close()
            scope.cancel()
        }
    }

    private fun ImageComposeScene.renderBytes(): ByteArray {
        val image: Image = render()
        try {
            val pixels = image.peekPixels() ?: error("Rendered image has no readable pixels")
            return requireNotNull(pixels.buffer.bytes)
        } finally {
            image.close()
        }
    }

    private fun subscriptionInput(): ProxyHomeInput {
        val subscriptionId = "subscription:7"
        val subscription = ProxySubscriptionSummary(
            id = subscriptionId,
            title = "Work subscription",
            serverCount = 2,
            enabled = true,
            refreshing = false,
        )
        return ProxyHomeInput(
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Disconnected),
            servers = listOf(
                ProxyServerSummary(
                    id = "first",
                    title = "First server",
                    address = "first.example:443",
                    protocol = "vless",
                    groupId = subscriptionId,
                ),
                ProxyServerSummary(
                    id = "second",
                    title = "Second server",
                    address = "second.example:443",
                    protocol = "vmess",
                    groupId = subscriptionId,
                ),
            ),
            groups = listOf(
                ProxyGroupSummary(
                    id = subscriptionId,
                    title = subscription.title,
                    serverCount = 2,
                    enabled = true,
                    serverIds = setOf("first", "second"),
                    subscription = subscription,
                    kind = ProxyHomeGroupKind.Subscription,
                ),
            ),
            availableActions = setOf(ProxyHomeActionId.RefreshAllSubscriptions),
        )
    }
}
