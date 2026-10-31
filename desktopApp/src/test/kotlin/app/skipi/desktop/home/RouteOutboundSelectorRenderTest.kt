// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.dp
import app.skipi.app.proxy.ProxyServerRecord
import app.skipi.app.routing.RouteOutboundItem
import app.skipi.app.routing.RouteOutboundPickerGroup
import app.skipi.app.routing.RouteOutboundPickerProjection
import app.skipi.ui.routing.SkipiRouteOutboundSelectorScreen
import app.skipi.ui.routing.SkipiRoutingOutboundGroupDetails
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.proxy.server.model.VLESS
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

class RouteOutboundSelectorRenderTest {
    @Test
    fun rendersNarrowSelectedServerAndProviderMetadata() {
        assertRenderedScreen(
            name = "narrow-selected-server",
            width = 390,
            height = 844,
            isWideScreen = false,
            expandedGroupKey = ProviderGroupKey,
        )
    }

    @Test
    fun rendersWideExpandedEmptyProviderState() {
        assertRenderedScreen(
            name = "wide-empty-provider",
            width = 1100,
            height = 800,
            isWideScreen = true,
            expandedGroupKey = EmptyGroupKey,
        )
    }

    private fun assertRenderedScreen(
        name: String,
        width: Int,
        height: Int,
        isWideScreen: Boolean,
        expandedGroupKey: String,
    ) {
        val scene = ImageComposeScene(width = width, height = height)
        var renderedImage: Image? = null
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = false),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    SkipiRouteOutboundSelectorScreen(
                        projection = fixtureProjection(expandedGroupKey),
                        selectedTag = SelectedServerTag,
                        isPolicyMode = false,
                        padding = PaddingValues(0.dp),
                        isWideScreen = isWideScreen,
                        subscriptionDetailsByGroupId = mapOf(
                            ProviderGroupId to SkipiRoutingOutboundGroupDetails(
                                traffic = "Used 20 MB of 100 GB",
                                expiry = "Expires in 4 days",
                                trafficProgress = 0.2f,
                                announcement = "Maintenance window scheduled",
                                updatedLabel = "Updated yesterday",
                            ),
                        ),
                        onBack = {},
                        onConfirm = {},
                    )
                }
            }

            val image = scene.render()
            renderedImage = image
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            assertTrue(!image.isEmpty, "Rendered scene should have pixels")

            val pixels = requireNotNull(image.peekPixels()) { "Rendered scene should expose pixels" }
            try {
                val sampledColors = buildSet {
                    for (y in 0 until height step 24) {
                        for (x in 0 until width step 24) {
                            add(pixels.getColor(x, y))
                        }
                    }
                }
                assertTrue(sampledColors.size >= 3, "Rendered scene should contain visible page content")
            } finally {
                pixels.close()
            }

            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) {
                "Rendered scene should encode as PNG"
            }
            try {
                val png = encoded.bytes
                assertTrue(png.size > 1_024, "PNG should contain the rendered page, not an empty frame")
                assertTrue(PngSignature.contentEquals(png.copyOfRange(0, PngSignature.size)))

                val reportDirectory = File(System.getProperty("user.dir"), "build/reports/route-outbound-selector")
                assertTrue(reportDirectory.mkdirs() || reportDirectory.isDirectory)
                File(reportDirectory, "$name.png").writeBytes(png)
            } finally {
                encoded.close()
            }
        } finally {
            renderedImage?.close()
            scene.close()
        }
    }

    private fun fixtureProjection(expandedGroupKey: String) = RouteOutboundPickerProjection(
        groups = listOf(
            RouteOutboundPickerGroup(
                key = "routing-system-targets",
                title = "System targets",
                subscriptionGroupId = null,
                items = listOf(
                    RouteOutboundItem.Static(tag = "direct", title = "Direct"),
                    RouteOutboundItem.Static(tag = "block", title = "Block"),
                ),
            ),
            RouteOutboundPickerGroup(
                key = ProviderGroupKey,
                title = "North America",
                subscriptionGroupId = ProviderGroupId,
                items = listOf(
                    RouteOutboundItem.Server(
                        server = ProxyServerRecord(
                            id = SelectedServerTag.toInt(),
                            groupId = ProviderGroupId,
                            server = VLESS(
                                remarks = "🇳🇱 Amsterdam VLESS",
                                id = "render-smoke-user",
                                server = "127.0.0.1",
                                port = "443",
                                encryption = "none",
                            ),
                        ),
                        tag = SelectedServerTag,
                    ),
                ),
            ),
            RouteOutboundPickerGroup(
                key = EmptyGroupKey,
                title = "Empty provider",
                subscriptionGroupId = EmptyGroupId,
                items = emptyList(),
            ),
        ),
        initiallyExpandedGroupKey = expandedGroupKey,
    )

    private companion object {
        const val SelectedServerTag = "42"
        const val ProviderGroupId = 310
        const val EmptyGroupId = 311
        const val ProviderGroupKey = "routing-subscription-$ProviderGroupId"
        const val EmptyGroupKey = "routing-subscription-$EmptyGroupId"
        val PngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
