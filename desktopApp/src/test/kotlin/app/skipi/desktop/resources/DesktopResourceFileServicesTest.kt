// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.config.toTrafficConfigState
import features.config.ConfigProfile
import features.config.TrafficConfigCustomResourceFile
import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.CustomResourceFileStatus
import features.resources.ResourceFileDownloadCancelledException
import features.resources.ResourceFileKind
import features.resources.ResourceFileScope
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceV2FlyGithub
import features.resources.ResourceFileStatus
import features.resources.ResourceFileUpdateDisplayState
import features.resources.ResourceFileUpdateOptions
import features.resources.ResourceFileUpdateRequest
import features.resources.ResourceFileUpdateResult
import features.resources.ResourceFileUpdateSource
import features.resources.ResourceFileUpdateTarget
import features.resources.ResourceFilesStatus
import features.resources.isBusy
import features.resources.resourceFileSourceAssetDir
import features.resources.resourceFileUpdateSource
import features.resources.runtime.ResourceFileDownloadCancellation
import features.resources.runtime.openDecompressedStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Comparator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import platform.LocalProxyXrayConfigOptions
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/*
Expected production surface:
  DesktopResourceFileServices(scope, resourceFiles, readSettings, readProfile, activeProxy,
      prepareFeedback, pickFile, bundledUpdatedAtMillis)
  services.operations, services.queue, services.optionsForUserAgent(...),
  services.updateAutomatically(profileId), and
  services.synchronizeBundledFilesAfterPackageUpdate(profile?).
  DesktopProfileResourceFiles.prepare(scope, bundledUpdatedAtMillis, restoreAfterPackageUpdate)
  is used by the synchronization method so the null-profile fallback can retain scope (1, 0).
*/
class DesktopResourceFileServicesTest {

    @Test
    fun queuedManualRequestsUseCapturedProfilesAndRunFifoAfterLatestSelectionChanges() = withFreshCancellation {
        runBlocking {
            withXrayRoot { root ->
                val firstStarted = CountDownLatch(1)
                val releaseFirst = CountDownLatch(1)
                val observedPaths = CopyOnWriteArrayList<String>()
                val observedAgents = CopyOnWriteArrayList<String?>()
                val results = CopyOnWriteArrayList<ResourceFileUpdateResult>()
                val resultsReady = CountDownLatch(2)
                val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

                withHttpServer(
                    configure = { server ->
                        server.createContext("/first") { exchange ->
                            observedPaths += exchange.requestURI.path
                            observedAgents += exchange.requestHeaders.getFirst("User-Agent")
                            firstStarted.countDown()
                            check(releaseFirst.await(5, SECONDS)) { "first request was not released" }
                            respond(exchange, "first-body".encodeToByteArray())
                        }
                        server.createContext("/second") { exchange ->
                            observedPaths += exchange.requestURI.path
                            observedAgents += exchange.requestHeaders.getFirst("User-Agent")
                            respond(exchange, "second-body".encodeToByteArray())
                        }
                    },
                ) { server ->
                    try {
                        val profiles = mutableMapOf(
                            42 to profile(
                                id = 42,
                                userAgent = "captured profile 42 agent",
                                geoIpUrl = "http://127.0.0.1:${server.address.port}/first",
                            ),
                            43 to profile(
                                id = 43,
                                userAgent = "captured profile 43 agent",
                                geoSiteUrl = "http://127.0.0.1:${server.address.port}/second",
                            ),
                        )
                        val profileLookups = CopyOnWriteArrayList<Int>()
                        var latestSelectedProfileId = 42
                        var settings = DesktopAppSettings(localProxyPortRaw = "8123")
                        val services = services(
                            scope = hostScope,
                            root = root,
                            readSettings = { settings },
                            readProfile = { id -> profileLookups += id; profiles[id] },
                        )
                        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            services.queue.results.take(2).collect { result ->
                                results += result
                                resultsReady.countDown()
                            }
                        }

                        val capturedFirstProfile = profiles.getValue(42)
                        val firstRequest = capturedFirstProfile.toBuiltInRequest(
                            services = services,
                            kind = ResourceFileKind.GeoIp,
                        )
                        assertTrue(services.queue.enqueue(firstRequest))
                        assertTrue(firstStarted.await(5, SECONDS), "first request should enter the local server")

                        profiles[42] = capturedFirstProfile.copy(
                            resourceSettings = capturedFirstProfile.resourceSettings.copy(
                                userAgent = "newly edited agent",
                                customGeoIpUrl = "http://127.0.0.1:${server.address.port}/edited",
                            ),
                        )
                        latestSelectedProfileId = 43
                        settings = settings.copy(localProxyPortRaw = "9123")
                        val capturedSecondProfile = profiles.getValue(latestSelectedProfileId)
                        val secondRequest = capturedSecondProfile.toBuiltInRequest(
                            services = services,
                            kind = ResourceFileKind.GeoSite,
                        )
                        assertTrue(services.queue.enqueue(secondRequest))
                        releaseFirst.countDown()

                        assertTrue(resultsReady.await(10, SECONDS), "both queued updates should complete")

                        assertEquals(listOf("/first", "/second"), observedPaths.toList())
                        assertEquals(
                            listOf("captured profile 42 agent", "captured profile 43 agent"),
                            observedAgents.toList(),
                        )
                        assertEquals(8123, firstRequest.options.fallbackProxyPort)
                        assertEquals(9123, secondRequest.options.fallbackProxyPort)
                        assertEquals(0, profileLookups.size, "manual requests use their captured profile snapshot")
                        assertSame(firstRequest, assertIs<ResourceFileUpdateResult.Success>(results[0]).request)
                        assertSame(secondRequest, assertIs<ResourceFileUpdateResult.Success>(results[1]).request)
                        assertEquals(2L, services.queue.state.value.completionRevision)
                        assertFalse(services.queue.state.value.isBusy)
                        assertContentEquals(
                            "first-body".encodeToByteArray(),
                            File(root, "profiles/42/${ResourceFileKind.GeoIp.fileName}").readBytes(),
                        )
                        assertContentEquals(
                            "second-body".encodeToByteArray(),
                            File(root, "profiles/43/${ResourceFileKind.GeoSite.fileName}").readBytes(),
                        )
                        assertFalse(File(root, "profiles/42/${ResourceFileKind.GeoSite.fileName}").exists())
                        assertFalse(File(root, "profiles/43/${ResourceFileKind.GeoIp.fileName}").exists())
                    } finally {
                        releaseFirst.countDown()
                        collectorScope.cancel()
                        hostScope.cancel()
                    }
                }
            }
        }
    }

    @Test
    fun cancellingTheQueueDisconnectsTheRunningDownloadAndDropsPendingWork() = withFreshCancellation {
        runBlocking {
            withXrayRoot { root ->
                val bodyStarted = CountDownLatch(1)
                val requestedPaths = CopyOnWriteArrayList<String>()
                val results = CopyOnWriteArrayList<ResourceFileUpdateResult>()
                val resultReady = CountDownLatch(1)
                val reportedFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
                val mappedCancellation = AtomicInteger()
                val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val collectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

                withHttpServer(
                    configure = { server ->
                        server.createContext("/slow") { exchange ->
                            requestedPaths += exchange.requestURI.path
                            exchange.sendResponseHeaders(200, 0)
                            try {
                                exchange.responseBody.use { output ->
                                    val chunk = ByteArray(32 * 1024) { 7 }
                                    repeat(600) {
                                        output.write(chunk)
                                        output.flush()
                                        bodyStarted.countDown()
                                        Thread.sleep(5)
                                    }
                                }
                            } catch (_: IOException) {
                                // Disconnecting the real URLConnection is the cancellation signal under test.
                            } finally {
                                exchange.close()
                            }
                        }
                        server.createContext("/pending") { exchange ->
                            requestedPaths += exchange.requestURI.path
                            respond(exchange, "pending".encodeToByteArray())
                        }
                    },
                ) { server ->
                    try {
                        val services = services(
                            scope = hostScope,
                            root = root,
                            onFailure = reportedFailure::set,
                            mapCancellationError = { error ->
                                mappedCancellation.incrementAndGet()
                                ResourceFileDownloadCancelledException("localized cancellation")
                            },
                        )
                        collectorScope.launch(start = CoroutineStart.UNDISPATCHED) {
                            services.queue.results.take(1).collect { result ->
                                results += result
                                resultReady.countDown()
                            }
                        }
                        val running = request(
                            services,
                            profile(
                                id = 51,
                                geoIpUrl = "http://127.0.0.1:${server.address.port}/slow",
                            ),
                            ResourceFileKind.GeoIp,
                        )
                        val pending = request(
                            services,
                            profile(
                                id = 52,
                                geoSiteUrl = "http://127.0.0.1:${server.address.port}/pending",
                            ),
                            ResourceFileKind.GeoSite,
                        )

                        assertTrue(services.queue.enqueue(running))
                        assertTrue(bodyStarted.await(5, SECONDS), "slow response body should start")
                        assertTrue(services.queue.enqueue(pending))
                        services.queue.cancelAll()

                        assertTrue(resultReady.await(10, SECONDS), "running cancellation should finish")

                        assertSame(running, assertIs<ResourceFileUpdateResult.Cancelled>(results.single()).request)
                        assertTrue(services.queue.state.value.pending.isEmpty())
                        assertFalse(services.queue.state.value.isBusy)
                        assertEquals(1L, services.queue.state.value.completionRevision)
                        assertEquals(listOf("/slow"), requestedPaths.toList())
                        assertEquals("Resource file update cancelled", reportedFailure.get()?.message)
                        assertEquals(1, mappedCancellation.get())
                    } finally {
                        ResourceFileDownloadCancellation.cancel()
                        collectorScope.cancel()
                        hostScope.cancel()
                    }
                }
            }
        }
    }

    @Test
    fun automaticUpdateRechecksLatestProfileAndRunsOutsideTheManualQueue() = withFreshCancellation {
        runBlocking {
            withXrayRoot { root ->
                val requests = CopyOnWriteArrayList<Pair<String, String?>>()
                val profileLookups = CopyOnWriteArrayList<Int>()
                val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                withHttpServer(
                    configure = { server ->
                        server.createContext("/") { exchange ->
                            requests += exchange.requestURI.path to exchange.requestHeaders.getFirst("User-Agent")
                            respond(exchange, "automatic-body".encodeToByteArray())
                        }
                    },
                ) { server ->
                    try {
                        val profileId = 61
                        var latestProfile: TrafficConfigState? = profile(
                            id = profileId,
                            autoUpdate = false,
                            userAgent = "disabled profile agent",
                            urlPrefix = "http://127.0.0.1:${server.address.port}/disabled",
                        )
                        val services = services(
                            scope = hostScope,
                            root = root,
                            readProfile = { id ->
                                profileLookups += id
                                latestProfile?.takeIf { profile -> profile.id == id }
                            },
                        )

                        services.updateAutomatically(profileId)
                        assertTrue(requests.isEmpty(), "a disabled profile must not start an automatic update")

                        latestProfile = profile(
                            id = profileId,
                            autoUpdate = true,
                            userAgent = "latest profile agent",
                            urlPrefix = "http://127.0.0.1:${server.address.port}/latest",
                        )
                        services.updateAutomatically(profileId)

                        assertEquals(
                            listOf(
                                "/latest/geoip",
                                "/latest/geosite",
                                "/latest/geoip-only-cn-private",
                                "/latest/direct-cidr-ipv4",
                                "/latest/direct-cidr-ipv6",
                            ),
                            requests.map { (path, _) -> path },
                        )
                        assertEquals(
                            List(5) { "latest profile agent" },
                            requests.map { (_, userAgent) -> userAgent },
                        )

                        latestProfile = null
                        services.updateAutomatically(profileId)

                        assertEquals(listOf(profileId, profileId, profileId), profileLookups.toList())
                        assertEquals(0L, services.queue.state.value.completionRevision)
                        assertFalse(services.queue.state.value.isBusy)
                    } finally {
                        hostScope.cancel()
                    }
                }
            }
        }
    }

    @Test
    fun resourceSettingsPersistThroughTheLatestEditorStoreAndReload() = withXrayRoot { root ->
        val configPath = root.toPath().resolve("configs.json")
        val original = ConfigProfile(
            id = 71,
            name = "Profile 71",
            content = "[General]\nloglevel = warning\n",
        )
        val initial = DesktopConfigLibrary(
            selectedConfigId = original.id,
            configs = listOf(original),
            nextConfigId = 72,
        )
        DesktopConfigLibraries.save(configPath, initial).getOrThrow()
        val repository = DesktopTrafficConfigRepository(
            initialLibrary = initial,
            readLibrary = { DesktopConfigLibraries.load(configPath).getOrThrow() },
            saveLibrary = { DesktopConfigLibraries.save(configPath, it) },
            publishLibrary = {},
        )
        val profileStore = DesktopTrafficConfigEditorProfileStore(
            repository = repository,
            resourceUserAgentFallback = "Skipi/9.8.7/Desktop",
        )
        val expectedSettings = TrafficConfigResourceSettings(
            source = ResourceFileSourceCustom,
            customGeoIpUrl = "https://source.example/ip.dat",
            customGeoSiteUrl = "https://source.example/site.dat",
            customGeoIpOnlyCnPrivateUrl = "https://source.example/private.dat",
            customDirectCidrIpv4Url = "https://source.example/v4.txt",
            customDirectCidrIpv6Url = "https://source.example/v6.txt",
            customFiles = listOf(TrafficConfigCustomResourceFile(7, "user-rules.dat", "https://source.example/rules.dat")),
            nextCustomFileId = 8,
            userAgent = "explicit profile resource agent",
            autoUpdate = true,
            updateInterval = "96",
        )

        assertTrue(
            profileStore.update(original.id) { current ->
                current.copy(resourceSettings = expectedSettings)
            },
        )

        val reloaded = DesktopConfigLibraries.load(configPath).getOrThrow()
        val reloadedProfile = reloaded.configs.single().toTrafficConfigState("Skipi/9.8.7/Desktop")

        assertEquals(original.id, reloaded.selectedConfigId)
        assertEquals(72, reloaded.nextConfigId)
        assertEquals(expectedSettings, reloadedProfile.resourceSettings)
    }

    @Test
    fun packageUpdateSynchronizationRestoresOnlyOldProfileAssets() = runBlocking {
        withXrayRoot { root ->
            val resourceFiles = DesktopProfileResourceFiles(root)
            val profile = profile(id = 81, source = ResourceFileSourceV2FlyGithub)
            val scope = ResourceFileScope(profile.id, profile.resourceSettings.source)
            val siblingScope = scope.copy(trafficConfigId = 82)
            val snapshot = resourceFiles.snapshot(scope)
            val sibling = resourceFiles.snapshot(siblingScope)
            val bundleTimestamp = System.currentTimeMillis() + 60_000
            val oldGeoIp = snapshot.file(ResourceFileKind.GeoIp).apply {
                parentFile.mkdirs()
                writeText("old-profile-geoip")
                assertTrue(setLastModified(bundleTimestamp - 30_000), "old GeoIP timestamp should be set")
            }
            val newerGeoSite = snapshot.file(ResourceFileKind.GeoSite).apply {
                writeText("newer-user-geo-site")
                assertTrue(setLastModified(bundleTimestamp + 30_000), "newer GeoSite timestamp should be set")
            }
            val custom = TrafficConfigCustomResourceFile(1, "custom-user.dat", "")
            val customTarget = snapshot.file(custom).apply { writeText("custom-user-bytes") }
            val globalCore = snapshot.file(ResourceFileKind.XrayCore).apply {
                parentFile.mkdirs()
                writeText("global-core-bytes")
            }
            val siblingGeoIp = sibling.file(ResourceFileKind.GeoIp).apply {
                parentFile.mkdirs()
                writeText("sibling-profile-geoip")
            }
            snapshot.markResourceFileSource(scope.resourceFileSource)
            val assetPath = "${resourceFileSourceAssetDir(scope.resourceFileSource)}/${ResourceFileKind.GeoIp.fileName}.xz"
            val expectedGeoIp = requireNotNull(
                DesktopProfileResourceFiles::class.java.classLoader?.getResourceAsStream(assetPath),
            ).use { input -> openDecompressedStream(input, assetPath).use { it.readBytes() } }
            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            var timestampReads = 0
            try {
                val services = services(
                    scope = hostScope,
                    root = root,
                    resourceFiles = resourceFiles,
                    bundledUpdatedAtMillis = { timestampReads += 1; bundleTimestamp },
                )

                services.synchronizeBundledFilesAfterPackageUpdate(profile)

                assertEquals(1, timestampReads)
                assertContentEquals(expectedGeoIp, oldGeoIp.readBytes())
                assertEquals("newer-user-geo-site", newerGeoSite.readText())
                assertEquals("custom-user-bytes", customTarget.readText())
                assertEquals("global-core-bytes", globalCore.readText())
                assertEquals("sibling-profile-geoip", siblingGeoIp.readText())
                assertEquals(scope.resourceFileSource.toString(), File(snapshot.dataDir, ".resource-source").readText())
            } finally {
                hostScope.cancel()
            }
        }
    }

    @Test
    fun startupSynchronizationUsesProfileOneAndSourceZeroWhenThereIsNoProfile() = runBlocking {
        withXrayRoot { root ->
            val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            var timestampReads = 0
            try {
                val services = services(
                    scope = hostScope,
                    root = root,
                    bundledUpdatedAtMillis = { timestampReads += 1; 0L },
                )

                services.synchronizeBundledFilesAfterPackageUpdate(profile = null)

                assertEquals(1, timestampReads)
                assertEquals("0", File(root, "profiles/1/.resource-source").readText())
                assertTrue(File(root, "profiles/1/${ResourceFileKind.GeoIp.fileName}").isFile)
                assertTrue(File(root, ".profile-resources-migrated-v1").isFile)
            } finally {
                hostScope.cancel()
            }
        }
    }

    private fun services(
        scope: CoroutineScope,
        root: File,
        resourceFiles: DesktopProfileResourceFiles = DesktopProfileResourceFiles(root),
        readSettings: () -> DesktopAppSettings = { DesktopAppSettings() },
        readProfile: (Int) -> TrafficConfigState? = { null },
        activeProxy: () -> LocalProxyXrayConfigOptions? = { null },
        onFailure: (Throwable) -> Unit = {},
        mapCancellationError: (ResourceFileDownloadCancelledException) -> ResourceFileDownloadCancelledException = { it },
        bundledUpdatedAtMillis: () -> Long = { 0L },
    ) = DesktopResourceFileServices(
        scope = scope,
        resourceFiles = resourceFiles,
        readSettings = readSettings,
        readProfile = readProfile,
        activeProxy = activeProxy,
        prepareFeedback = {
            DesktopResourceFileUpdateFeedback(
                onProgress = { _, _, _ -> },
                onComplete = {},
                onFailure = onFailure,
                mapCancellationError = mapCancellationError,
            )
        },
        pickFile = { null },
        bundledUpdatedAtMillis = bundledUpdatedAtMillis,
    )

    private fun request(
        services: DesktopResourceFileServices,
        profile: TrafficConfigState,
        kind: ResourceFileKind,
    ): ResourceFileUpdateRequest.BuiltIn = profile.toBuiltInRequest(services, kind)

    private fun TrafficConfigState.toBuiltInRequest(
        services: DesktopResourceFileServices,
        kind: ResourceFileKind,
    ) = ResourceFileUpdateRequest.BuiltIn(
        scope = ResourceFileScope(id, resourceSettings.source),
        kind = kind,
        source = resourceSettings.resourceFileUpdateSource(),
        options = services.optionsForUserAgent(resourceSettings.userAgent),
        customResourceFiles = resourceSettings.customFiles,
    )

    private fun profile(
        id: Int,
        source: Int = ResourceFileSourceCustom,
        userAgent: String = "profile agent $id",
        autoUpdate: Boolean = true,
        urlPrefix: String = "https://unused.example/$id",
        geoIpUrl: String = "$urlPrefix/geoip",
        geoSiteUrl: String = "$urlPrefix/geosite",
    ) = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "",
        resourceSettings = TrafficConfigResourceSettings(
            source = source,
            customGeoIpUrl = geoIpUrl,
            customGeoSiteUrl = geoSiteUrl,
            customGeoIpOnlyCnPrivateUrl = "$urlPrefix/geoip-only-cn-private",
            customDirectCidrIpv4Url = "$urlPrefix/direct-cidr-ipv4",
            customDirectCidrIpv6Url = "$urlPrefix/direct-cidr-ipv6",
            userAgent = userAgent,
            autoUpdate = autoUpdate,
        ),
    )

    private inline fun <T> withHttpServer(
        configure: (HttpServer) -> Unit,
        block: (HttpServer) -> T,
    ): T {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "desktop-resource-services-http").apply { isDaemon = true }
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

    private inline fun <T> withXrayRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("skipi-desktop-resource-services-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
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
    }
}
