// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerButtons
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import app.skipi.app.config.toTrafficConfigState
import app.skipi.ui.resourcefiles.ResourceManagementScreen
import app.skipi.ui.theme.ProvideSkipiTheme
import app.skipi.ui.theme.SkipiVisualProfile
import app.skipi.ui.theme.resolveBaseAppearanceColors
import features.config.ConfigProfile
import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.config.withSkipiSettingsInRawConfig
import features.resources.ResourceFileKind
import features.resources.ResourceFileScope
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileUpdateRequest
import features.resources.ResourceFileUpdateResult
import features.resources.runtime.ResourceFileDownloadCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Manual end-to-end sample: persist/reload a real Desktop profile, render the shared resource
 * screen, click its visible “Update resource files” row, and let the real Desktop service queue,
 * plan, transfer, and profile-scoped writer handle five loopback downloads.
 *
 * It intentionally uses the real Desktop queue, service, downloader, and writer.
 */
class DesktopResourceFileManualUpdateScreenIntegrationTest {
    @Test
    fun clickingTheRenderedUpdateSourceRunsThePersistedProfilePlanThroughDesktopServices(): Unit =
        withFreshCancellation {
            withTemporaryRoot { root ->
                withTemporaryUserHome(root) {
                    runBlocking {
                        val configPath = root.toPath().resolve("configs.json")
                        val settingsPath = root.toPath().resolve("settings.json")
                        val profileId = 42
                        val userAgent = "Captured sample resource agent"
                        val expectedPaths = listOf(
                            "/geoip",
                            "/geosite",
                            "/geoip-only-cn-private",
                            "/direct-cidr-ipv4",
                            "/direct-cidr-ipv6",
                        )
                        val expectedBodies = expectedPaths.mapIndexed { index, path ->
                            path to "sample-$index-resource-body".encodeToByteArray()
                        }.toMap()
                        val observedRequests = CopyOnWriteArrayList<Pair<String, String?>>()

                        withLocalHttpServer(
                            configure = { server ->
                                expectedPaths.forEach { expectedPath ->
                                    server.createContext(expectedPath) { exchange ->
                                        observedRequests += exchange.requestURI.path to
                                            exchange.requestHeaders.getFirst("User-Agent")
                                        respond(exchange, expectedBodies.getValue(expectedPath))
                                    }
                                }
                            },
                        ) { server ->
                            val base = "http://127.0.0.1:${server.address.port}"
                            val storedState = TrafficConfigState(
                                id = profileId,
                                name = "Manual update sample",
                                rawConfig = "[General]\nloglevel = warning\n",
                                resourceSettings = TrafficConfigResourceSettings(
                                    source = ResourceFileSourceCustom,
                                    customGeoIpUrl = "$base/geoip",
                                    customGeoSiteUrl = "$base/geosite",
                                    customGeoIpOnlyCnPrivateUrl = "$base/geoip-only-cn-private",
                                    customDirectCidrIpv4Url = "$base/direct-cidr-ipv4",
                                    customDirectCidrIpv6Url = "$base/direct-cidr-ipv6",
                                    userAgent = userAgent,
                                    autoUpdate = false,
                                    customFiles = emptyList<TrafficConfigCustomResourceFile>(),
                                ),
                            ).withSkipiSettingsInRawConfig()
                            val storedConfig = ConfigProfile(
                                id = profileId,
                                name = storedState.name,
                                content = storedState.rawConfig,
                            )
                            DesktopConfigLibraries.save(
                                configPath,
                                DesktopConfigLibrary(
                                    selectedConfigId = profileId,
                                    configs = listOf(storedConfig),
                                    nextConfigId = profileId + 1,
                                ),
                            ).getOrThrow()
                            DesktopSettingsLibraries.save(
                                settingsPath,
                                DesktopAppSettings(localProxyPortRaw = "18080"),
                            ).getOrThrow()

                            val loadedLibrary = DesktopConfigLibraries.load(configPath).getOrThrow()
                            val loadedProfile = requireNotNull(
                                loadedLibrary.configs.single().toTrafficConfigState("Desktop fallback agent"),
                            )
                            assertEquals(profileId, loadedLibrary.selectedConfigId)
                            assertEquals(userAgent, loadedProfile.resourceSettings.userAgent)
                            assertEquals(ResourceFileSourceCustom, loadedProfile.resourceSettings.source)

                            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                            val resourceFiles = DesktopProfileResourceFiles(File(root, "xray"))
                            val progressEvents = CopyOnWriteArrayList<Triple<String, Int?, Boolean>>()
                            val completionEvents = CopyOnWriteArrayList<String>()
                            val errors = CopyOnWriteArrayList<Throwable>()
                            val services = DesktopResourceFileServices(
                                scope = hostScope,
                                resourceFiles = resourceFiles,
                                readSettings = { DesktopSettingsLibraries.load(settingsPath).getOrThrow() },
                                readProfile = { requestedId ->
                                    DesktopConfigLibraries.load(configPath).getOrThrow().configs
                                        .singleOrNull { it.id == requestedId }
                                        ?.toTrafficConfigState("Desktop fallback agent")
                                },
                                activeProxy = { null },
                                prepareFeedback = {
                                    DesktopResourceFileUpdateFeedback(
                                        onProgress = { name, progress, force ->
                                            progressEvents += Triple(name, progress, force)
                                        },
                                        onComplete = { completionEvents += "complete" },
                                        onFailure = { error -> errors += error },
                                        mapCancellationError = { it },
                                    )
                                },
                                pickFile = { null },
                            )
                            val scene = ImageComposeScene(width = 1100, height = 800)
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
                                            profile = loadedProfile,
                                            padding = PaddingValues(0.dp),
                                            isWideScreen = true,
                                            queue = services.queue,
                                            operations = services.operations,
                                            updateOptionsForUserAgent = services::optionsForUserAgent,
                                            userAgentPresets = listOf(userAgent),
                                            deletionConfirmationEnabled = true,
                                            actionScope = hostScope,
                                            onBack = {},
                                            updateSettings = { transform ->
                                                val latestLibrary = DesktopConfigLibraries.load(configPath).getOrThrow()
                                                val currentStored = latestLibrary.configs.single { it.id == profileId }
                                                val current = currentStored.toTrafficConfigState("Desktop fallback agent")
                                                val updated = current.copy(
                                                    resourceSettings = transform(current.resourceSettings),
                                                ).withSkipiSettingsInRawConfig()
                                                DesktopConfigLibraries.save(
                                                    configPath,
                                                    latestLibrary.copy(
                                                        configs = latestLibrary.configs.map { stored ->
                                                            if (stored.id == profileId) {
                                                                stored.copy(content = updated.rawConfig)
                                                            } else {
                                                                stored
                                                            }
                                                        },
                                                    ),
                                                ).getOrThrow()
                                            },
                                            formatTimestamp = { timestamp ->
                                                if (timestamp > 0L) "Jan 1, 2026" else "-"
                                            },
                                            onTip = { completionEvents += it },
                                            onError = { error -> errors += error },
                                        )
                                    }
                                }

                                savePng(scene, File(root, "before-click.png"))
                                val resultReady = hostScope.async(start = CoroutineStart.UNDISPATCHED) {
                                    services.queue.results.first()
                                }

                                // Match the visible row in ResourceManagementScreenRenderTest's 1100x800
                                // image: x≈200,y≈236 is inside the “Update resource files” ArrowPreference.
                                val updateRow = Offset(200f, 236f)
                                scene.sendPointerEvent(
                                    eventType = PointerEventType.Move,
                                    position = updateRow,
                                    buttons = PointerButtons(),
                                )
                                scene.sendPointerEvent(
                                    eventType = PointerEventType.Press,
                                    position = updateRow,
                                    buttons = PointerButtons(isPrimaryPressed = true),
                                    button = PointerButton.Primary,
                                )
                                scene.sendPointerEvent(
                                    eventType = PointerEventType.Release,
                                    position = updateRow,
                                    buttons = PointerButtons(),
                                    button = PointerButton.Primary,
                                )
                                scene.render().close()

                                val result = withTimeout(30_000) { resultReady.await() }
                                val success = assertIs<ResourceFileUpdateResult.Success>(result)
                                val request = assertIs<ResourceFileUpdateRequest.All>(success.request)

                                assertEquals(ResourceFileScope(profileId, ResourceFileSourceCustom), request.scope)
                                assertEquals(userAgent, request.options.userAgent)
                                assertFalse(request.options.useRunningProxy)
                                assertFalse(request.options.showNotifications)
                                assertEquals(5, request.targets.size)
                                assertEquals(ResourceFileSourceCustom, request.source.id)
                                assertEquals(expectedPaths.map { "$base$it" }, listOf(
                                    request.source.geoIpUrl,
                                    request.source.geoSiteUrl,
                                    request.source.geoIpOnlyCnPrivateUrl,
                                    request.source.directCidrIpv4Url,
                                    request.source.directCidrIpv6Url,
                                ))
                                assertEquals(expectedPaths, observedRequests.map { (path, _) -> path })
                                assertEquals(List<String?>(5) { userAgent }, observedRequests.map { (_, agent) -> agent })
                                assertTrue("complete" in completionEvents)
                                assertTrue(request.customResourceFiles.isEmpty())
                                assertTrue(errors.isEmpty(), "real Desktop feedback should not report an error")
                                assertTrue(progressEvents.any { (_, _, forced) -> forced })

                                val scope = ResourceFileScope(profileId, ResourceFileSourceCustom)
                                val snapshot = resourceFiles.snapshot(scope)
                                val kinds = listOf(
                                    ResourceFileKind.GeoIp,
                                    ResourceFileKind.GeoSite,
                                    ResourceFileKind.GeoIpOnlyCnPrivate,
                                    ResourceFileKind.DirectCidrIpv4,
                                    ResourceFileKind.DirectCidrIpv6,
                                )
                                kinds.forEachIndexed { index, kind ->
                                    assertContentEquals(
                                        expectedBodies.getValue(expectedPaths[index]),
                                        snapshot.file(kind).readBytes(),
                                        "${kind.fileName} should contain the real loopback response",
                                    )
                                    assertEquals(File(root, "xray/profiles/$profileId"), snapshot.dataDir)
                                }
                                assertEquals(ResourceFileSourceCustom.toString(), File(snapshot.dataDir, ".resource-source").readText())
                                assertTrue(resourceFiles.snapshot(ResourceFileScope(profileId + 1, ResourceFileSourceCustom))
                                    .file(ResourceFileKind.GeoIp).let { !it.exists() },
                                )

                                // Allow the screen's completionRevision-keyed status effect to repaint,
                                // then save a second screenshot for a visual record of the actual UI flow.
                                repeat(4) {
                                    delay(50)
                                    scene.render().close()
                                }
                                savePng(scene, File(root, "after-completion.png"))
                                val reportDir = File(System.getProperty("user.dir"), "build/reports/resource-management-manual")
                                assertTrue(reportDir.mkdirs() || reportDir.isDirectory)
                                File(root, "before-click.png").copyTo(File(reportDir, "before-click.png"), overwrite = true)
                                File(root, "after-completion.png").copyTo(File(reportDir, "after-completion.png"), overwrite = true)
                            } finally {
                                scene.close()
                                hostScope.cancel()
                            }
                        }
                    }
                }
            }
        }

    private fun savePng(scene: ImageComposeScene, target: File) {
        val image = scene.render()
        try {
            val data = requireNotNull(image.encodeToData(EncodedImageFormat.PNG))
            try {
                target.writeBytes(data.bytes)
                assertTrue(target.length() > 1_024, "the rendered sample frame should contain the screen")
            } finally {
                data.close()
            }
        } finally {
            image.close()
        }
    }

    private inline fun <T> withLocalHttpServer(
        configure: (HttpServer) -> Unit,
        block: (HttpServer) -> T,
    ): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "desktop-resource-screen-http").apply { isDaemon = true }
        }
        configure(server)
        server.executor = executor
        server.start()
        return try {
            block(server)
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun respond(exchange: HttpExchange, body: ByteArray) {
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { output -> output.write(body) }
    }

    private inline fun <T> withTemporaryRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("skipi-resource-screen-manual-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private inline fun <T> withTemporaryUserHome(root: File, block: () -> T): T = synchronized(UserHomeLock) {
        val previousUserHome = System.getProperty("user.home")
        val temporaryUserHome = File(root, "home").apply { mkdirs() }
        System.setProperty("user.home", temporaryUserHome.absolutePath)
        try {
            block()
        } finally {
            if (previousUserHome == null) {
                System.clearProperty("user.home")
            } else {
                System.setProperty("user.home", previousUserHome)
            }
        }
    }

    private inline fun <T> withFreshCancellation(block: () -> T): T = synchronized(CancellationLock) {
        ResourceFileDownloadCancellation.begin()
        try {
            block()
        } finally {
            ResourceFileDownloadCancellation.cancel()
            ResourceFileDownloadCancellation.begin()
        }
    }

    private companion object {
        val CancellationLock = Any()
        val UserHomeLock = Any()
    }
}
