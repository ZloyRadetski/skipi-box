// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.dp
import app.skipi.ui.resourcefiles.ResourceManagementScreen
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.resources.ResourceFileKind
import features.resources.ResourceFileOperations
import features.resources.ResourceFileStatus
import features.resources.ResourceFileUpdateOptions
import features.resources.ResourceFileUpdateQueue
import features.resources.ResourceFileUpdateQueueState
import features.resources.ResourceFileUpdateRequest
import features.resources.ResourceFileUpdateResult
import features.resources.ResourceFileScope
import features.resources.ResourceFilesStatus
import features.resources.CustomResourceFileStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceManagementScreenRenderTest {
    @Test
    fun rendersNarrowAndWideResourceManagementScreens() {
        render("phone", width = 390, height = 844, wide = false)
        render("wide", width = 1100, height = 800, wide = true)
    }

    private fun render(name: String, width: Int, height: Int, wide: Boolean) {
        val customFiles = listOf(
            TrafficConfigCustomResourceFile(31, "Custom route list", "https://rules.example/custom.dat"),
            TrafficConfigCustomResourceFile(32, "Manual local list", ""),
        )
        val profile = TrafficConfigState(
            id = 17,
            name = "Profile with resources",
            rawConfig = "",
            resourceSettings = TrafficConfigResourceSettings(
                customFiles = customFiles,
                nextCustomFileId = 33,
                userAgent = "Render test agent",
                autoUpdate = true,
            ),
        ).withSkipiSettingsInRawConfig()
        val queue = FakeResourceFileUpdateQueue()
        val operations = FakeResourceFileOperations()
        val actionScope = CoroutineScope(SupervisorJob())
        val scene = ImageComposeScene(width = width, height = height)
        var rendered: Image? = null
        try {
            scene.setContent {
                ProvideSkipiTheme(
                    colors = resolveBaseAppearanceColors(isDark = false),
                    fontFamily = null,
                    fontSizeMode = null,
                    fontWeightMode = null,
                    visualProfile = SkipiVisualProfile.Desktop,
                ) {
                    ResourceManagementScreen(
                        profile = profile,
                        padding = PaddingValues(0.dp),
                        isWideScreen = wide,
                        queue = queue,
                        operations = operations,
                        updateOptionsForUserAgent = { userAgent -> ResourceFileUpdateOptions(userAgent = userAgent) },
                        userAgentPresets = listOf("Render test agent", "Second agent"),
                        deletionConfirmationEnabled = true,
                        actionScope = actionScope,
                        onBack = {},
                        updateSettings = {},
                        formatTimestamp = { timestamp -> if (timestamp > 0L) "Jan 1, 2026" else "-" },
                        onTip = {},
                        onError = {},
                    )
                }
            }

            val image = scene.render()
            rendered = image
            assertEquals(width, image.width)
            assertEquals(height, image.height)
            assertTrue(!image.isEmpty)
            val pixels = requireNotNull(image.peekPixels())
            try {
                val sampledColors = buildSet {
                    for (y in 0 until height step 24) {
                        for (x in 0 until width step 24) add(pixels.getColor(x, y))
                    }
                }
                assertTrue(sampledColors.size >= 3, "Resource screen should render visible controls and cards")
            } finally {
                pixels.close()
            }

            val encoded = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
            try {
                val png = encoded.bytes
                assertTrue(png.size > 1_024, "PNG should contain the screen, not an empty frame")
                val reportDir = File(System.getProperty("user.dir"), "build/reports/resource-management")
                assertTrue(reportDir.mkdirs() || reportDir.isDirectory)
                File(reportDir, "$name.png").writeBytes(png)
            } finally {
                encoded.close()
            }
        } finally {
            rendered?.close()
            scene.close()
            actionScope.cancel()
        }
    }

    private class FakeResourceFileUpdateQueue : ResourceFileUpdateQueue {
        override val state = MutableStateFlow(ResourceFileUpdateQueueState())
        override val results: Flow<ResourceFileUpdateResult> = MutableSharedFlow(extraBufferCapacity = 16)

        override fun enqueue(request: ResourceFileUpdateRequest): Boolean = true

        override fun cancelAll() = Unit
    }

    private class FakeResourceFileOperations : ResourceFileOperations {
        override suspend fun status(
            scope: ResourceFileScope,
            customResourceFiles: List<TrafficConfigCustomResourceFile>,
        ) = ResourceFilesStatus(
            geoIp = ResourceFileStatus(exists = true, sizeBytes = 2_097_152, updatedAtMillis = 1_782_864_000_000),
            customResourceFiles = customResourceFiles.map { file ->
                CustomResourceFileStatus(file, ResourceFileStatus(exists = true, sizeBytes = 8192, updatedAtMillis = 1_782_864_000_000))
            },
        )

        override suspend fun replace(
            scope: ResourceFileScope,
            kind: ResourceFileKind,
            customResourceFiles: List<TrafficConfigCustomResourceFile>,
        ): ResourceFilesStatus? = null

        override suspend fun replaceCustom(
            scope: ResourceFileScope,
            customFile: TrafficConfigCustomResourceFile,
            customResourceFiles: List<TrafficConfigCustomResourceFile>,
        ): ResourceFilesStatus? = null

        override suspend fun restoreBundled(
            scope: ResourceFileScope,
            kind: ResourceFileKind,
            customResourceFiles: List<TrafficConfigCustomResourceFile>,
        ): ResourceFilesStatus = ResourceFilesStatus()

        override suspend fun renameCustom(
            scope: ResourceFileScope,
            previousFile: TrafficConfigCustomResourceFile,
            customFile: TrafficConfigCustomResourceFile,
            customResourceFiles: List<TrafficConfigCustomResourceFile>,
        ): ResourceFilesStatus = ResourceFilesStatus()

        override suspend fun deleteCustom(
            scope: ResourceFileScope,
            customFile: TrafficConfigCustomResourceFile,
            customResourceFiles: List<TrafficConfigCustomResourceFile>,
        ): ResourceFilesStatus = ResourceFilesStatus()
    }
}
