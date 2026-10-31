// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import app.skipi.app.home.ProxyHomeAction
import app.skipi.app.home.ProxyHomeActionId
import app.skipi.app.home.ProxyHomeConnectionMode
import app.skipi.app.home.ProxyHomeDisplayOptions
import app.skipi.app.home.ProxyHomeEffect
import app.skipi.app.home.ProxyHomeEffectHandler
import app.skipi.app.home.ProxyHomeGroupKind
import app.skipi.app.home.ProxyHomeInput
import app.skipi.app.home.ProxyHomeStore
import app.skipi.app.home.ProxyGroupSummary
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
import org.jetbrains.skia.EncodedImageFormat
import platform.TunnelPhase
import platform.TunnelSnapshot
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopProxyHomeLatencyFabTest {
    @Test
    fun connectedFloatingLatencyActionTestsTheConnectionOfTheSelectedServerEvenWhenSearchAndGroupHideIt() {
        val effects = clickConnectedLatencyFab(
            selectedServerId = "selected",
            prepareStore = { store ->
                store.dispatch(ProxyHomeAction.SelectGroup("visible-group"))
                store.dispatch(ProxyHomeAction.SetSearchQuery("Visible"))
            },
        )

        assertEquals(listOf(ProxyHomeEffect.TestConnection("selected")), effects)
    }

    @Test
    fun connectedFloatingLatencyActionDoesNothingWhenNoServerIsSelected() {
        val effects = clickConnectedLatencyFab(selectedServerId = null)

        assertEquals(emptyList(), effects)
    }

    @Test
    fun connectedFloatingLatencyActionMeasuresTheConnectionEvenWhenTheServerCannotBePingedOnItsOwn() {
        val effects = clickConnectedLatencyFab(
            selectedServerId = "selected",
            selectedServerCanTest = false,
        )

        assertEquals(listOf(ProxyHomeEffect.TestConnection("selected")), effects)
    }

    @Test
    fun repeatingTheFloatingLatencyActionStillMeasuresOnlyTheConnectionOfTheSelectedServer() {
        val effects = renderAndClick(
            input = connectedInput(selectedServerId = "selected"),
            position = Offset(258f, 790f),
            clicks = 3,
        )

        assertEquals(List(3) { ProxyHomeEffect.TestConnection("selected") }, effects)
    }

    @Test
    fun connectedFloatingLatencyActionCanTestSelectedServerWhenSearchLeavesNoVisibleRows() {
        val effects = clickConnectedLatencyFab(
            selectedServerId = "selected",
            prepareStore = { store -> store.dispatch(ProxyHomeAction.SetSearchQuery("No matches")) },
        )

        assertEquals(listOf(ProxyHomeEffect.TestConnection("selected")), effects)
    }

    @Test
    fun connectedSubscriptionHeaderLatencyActionStillTestsItsWholeSubscription() {
        val effects = renderAndClick(
            input = connectedSubscriptionInput(),
            position = Offset(334f, 293f),
            screenshotName = "subscription-card-before-click.png",
        )

        assertEquals(listOf(ProxyHomeEffect.PingSubscription("subscription:7")), effects)
    }

    @Test
    fun connectedFloatingLatencyActionStillCancelsAnActiveTest() {
        val effects = clickConnectedLatencyFab(
            selectedServerId = "selected",
            isTestingLatency = true,
        )

        assertEquals(listOf(ProxyHomeEffect.CancelLatencyTests), effects)
    }

    @Test
    fun classicConnectionWithoutFloatingPowerButtonHidesConnectedLatencyToolbar() {
        val effects = renderAndClick(
            input = connectedInput(
                selectedServerId = "selected",
                displayOptions = ProxyHomeDisplayOptions(classicFloatingPowerButton = false),
            ),
            position = Offset(318f, 790f),
            screenshotName = "classic-floating-off.png",
        )

        assertEquals(emptyList(), effects)
    }

    @Test
    fun classicConnectionWithFloatingPowerButtonShowsToolbarAndTestsTheConnection() {
        val effects = renderAndClick(
            input = connectedInput(
                selectedServerId = "selected",
                displayOptions = ProxyHomeDisplayOptions(classicFloatingPowerButton = true),
            ),
            position = Offset(258f, 790f),
            screenshotName = "classic-floating-on.png",
        )

        assertEquals(listOf(ProxyHomeEffect.TestConnection("selected")), effects)
    }

    @Test
    fun compactConnectionShowsToolbarEvenWhenClassicFloatingPowerButtonIsOff() {
        val effects = renderAndClick(
            input = connectedInput(
                selectedServerId = "selected",
                displayOptions = ProxyHomeDisplayOptions(
                    connectionMode = ProxyHomeConnectionMode.Compact,
                    classicFloatingPowerButton = false,
                ),
            ),
            position = Offset(258f, 790f),
            screenshotName = "compact-floating-off.png",
        )

        assertEquals(listOf(ProxyHomeEffect.TestConnection("selected")), effects)
    }

    @Test
    fun classicConnectionWithoutFloatingPowerButtonKeepsCardPowerControlUsable() {
        val effects = renderAndClick(
            input = connectedInput(
                selectedServerId = "selected",
                displayOptions = ProxyHomeDisplayOptions(classicFloatingPowerButton = false),
                canToggleTunnel = true,
            ),
            position = Offset(72f, 160f),
        )

        assertEquals(listOf(ProxyHomeEffect.ToggleTunnel), effects)
    }

    private fun clickConnectedLatencyFab(
        selectedServerId: String?,
        isTestingLatency: Boolean = false,
        selectedServerCanTest: Boolean = true,
        prepareStore: (ProxyHomeStore) -> Unit = {},
    ) = renderAndClick(
        input = connectedInput(selectedServerId, isTestingLatency, selectedServerCanTest),
        position = Offset(258f, 790f),
        prepareStore = prepareStore,
    )

    private fun renderAndClick(
        input: ProxyHomeInput,
        position: Offset,
        screenshotName: String? = null,
        clicks: Int = 1,
        prepareStore: (ProxyHomeStore) -> Unit = {},
    ): List<ProxyHomeEffect> {
        val effects = mutableListOf<ProxyHomeEffect>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = ProxyHomeStore(
            initialInput = input,
            scope = scope,
            effectHandler = ProxyHomeEffectHandler { effect ->
                effects += effect
                Result.success(Unit)
            },
        )
        val scene = ImageComposeScene(width = 390, height = 844)
        try {
            prepareStore(store)
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = false),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    ProxyHomeScreenContent(
                        state = store.uiState.value,
                        onAction = store::dispatch,
                    )
                }
            }
            val image = scene.render()
            try {
                if (screenshotName != null) saveRenderedImage(image, screenshotName)
            } finally {
                image.close()
            }

            // The connected layout places the ping control on the left of the floating toolbar.
            scene.sendPointerEvent(
                eventType = PointerEventType.Move,
                position = position,
                buttons = PointerButtons(),
            )
            repeat(clicks) {
                scene.sendPointerEvent(
                    eventType = PointerEventType.Press,
                    position = position,
                    buttons = PointerButtons(isPrimaryPressed = true),
                    button = PointerButton.Primary,
                )
                scene.sendPointerEvent(
                    eventType = PointerEventType.Release,
                    position = position,
                    buttons = PointerButtons(),
                    button = PointerButton.Primary,
                )
            }
            scene.render().close()
            return effects.toList()
        } finally {
            scene.close()
            scope.cancel()
        }
    }

    private fun saveRenderedImage(image: org.jetbrains.skia.Image, fileName: String) {
        val outputDirectory = File(System.getProperty("user.dir"), "build/reports/latency-fab-scope")
        check(outputDirectory.mkdirs() || outputDirectory.isDirectory)
        val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
        try {
            File(outputDirectory, fileName).writeBytes(encoded.bytes)
        } finally {
            encoded.close()
        }
    }

    private fun connectedInput(
        selectedServerId: String?,
        isTestingLatency: Boolean = false,
        selectedServerCanTest: Boolean = true,
        displayOptions: ProxyHomeDisplayOptions = ProxyHomeDisplayOptions(classicFloatingPowerButton = true),
        canToggleTunnel: Boolean = false,
    ) = ProxyHomeInput(
        tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
        selectedServerId = selectedServerId,
        canToggleTunnel = canToggleTunnel,
        servers = listOf(
            ProxyServerSummary(
                id = "selected",
                title = "Selected server",
                address = "selected.example:443",
                protocol = "vless",
                groupId = "selected-group",
                searchText = "Selected server",
                canTest = selectedServerCanTest,
            ),
            ProxyServerSummary(
                id = "visible",
                title = "Visible server",
                address = "visible.example:443",
                protocol = "vmess",
                groupId = "visible-group",
                searchText = "Visible server",
            ),
        ),
        groups = listOf(
            ProxyGroupSummary(
                id = "selected-group",
                title = "Selected group",
                serverCount = 1,
                enabled = true,
                serverIds = setOf("selected"),
                kind = ProxyHomeGroupKind.Manual,
            ),
            ProxyGroupSummary(
                id = "visible-group",
                title = "Visible group",
                serverCount = 1,
                enabled = true,
                serverIds = setOf("visible"),
                kind = ProxyHomeGroupKind.Manual,
            ),
        ),
        isTestingLatency = isTestingLatency,
        displayOptions = displayOptions,
        availableActions = setOf(
            ProxyHomeActionId.TestServer,
            ProxyHomeActionId.TestConnection,
            ProxyHomeActionId.TestVisibleServers,
            ProxyHomeActionId.TestGroup,
            ProxyHomeActionId.CancelLatencyTests,
        ) + if (canToggleTunnel) setOf(ProxyHomeActionId.ToggleTunnel) else emptySet(),
    )

    private fun connectedSubscriptionInput(): ProxyHomeInput {
        val subscriptionId = "subscription:7"
        val subscription = ProxySubscriptionSummary(
            id = subscriptionId,
            title = "Work subscription",
            serverCount = 2,
            enabled = true,
            refreshing = false,
        )
        return ProxyHomeInput(
            tunnelSnapshot = TunnelSnapshot(phase = TunnelPhase.Connected),
            selectedServerId = "selected",
            servers = listOf(
                ProxyServerSummary(
                    id = "selected",
                    title = "Selected server",
                    address = "selected.example:443",
                    protocol = "vless",
                    groupId = subscriptionId,
                ),
                ProxyServerSummary(
                    id = "other",
                    title = "Another subscription server",
                    address = "other.example:443",
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
                    serverIds = setOf("selected", "other"),
                    subscription = subscription,
                    kind = ProxyHomeGroupKind.Subscription,
                ),
            ),
            availableActions = setOf(ProxyHomeActionId.PingSubscription),
        )
    }
}
