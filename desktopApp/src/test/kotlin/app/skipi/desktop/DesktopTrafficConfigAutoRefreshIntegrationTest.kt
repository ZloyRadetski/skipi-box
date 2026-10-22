// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.ProjectInfo
import app.skipi.app.config.TrafficConfigAutoUpdateExecutionResult
import app.skipi.app.config.toTrafficConfigState
import app.skipi.app.proxy.DefaultAutoBalancerGroupId
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import features.config.ConfigProfile
import features.config.analyzeShadowrocketConfig
import features.config.runtime.TrafficConfigRefreshTarget
import features.proxy.server.model.StrategyGroup
import features.proxy.server.model.VLESS
import features.proxy.server.model.encodePersistedProxyServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopTrafficConfigAutoRefreshIntegrationTest {
    @Test
    fun due_profile_fetch_persists_response_and_reconciles_its_generated_group() = runBlocking {
        withTemporaryRoot { root ->
            withHttpServer { server ->
                val profileId = 7
                val profileUrl = "http://127.0.0.1:${server.address.port}/profile"
                val body = fetchedProfileDocument
                val receivedUserAgent = AtomicReference<String?>()
                server.createContext("/profile") { exchange ->
                    receivedUserAgent.set(exchange.requestHeaders.getFirst("User-Agent"))
                    exchange.respond(body)
                }
                server.start()

                val sibling = ConfigProfile(
                    id = 13,
                    name = "Sibling",
                    content = profileDocument(
                        name = "Sibling",
                        sourceUrl = "https://sibling.example/profile",
                        autoUpdate = false,
                        updateInterval = "24",
                        resourceAutoUpdate = false,
                        resourceUpdateInterval = "24",
                    ),
                    sourceUrl = "https://sibling.example/profile",
                    lastUpdatedAtMillis = 77L,
                )
                val initialProfile = ConfigProfile(
                    id = profileId,
                    name = "Saved profile name",
                    content = profileDocument(
                        name = "Saved profile name",
                        sourceUrl = profileUrl,
                        autoUpdate = true,
                        updateInterval = "6",
                        resourceAutoUpdate = false,
                        resourceUpdateInterval = "12",
                    ),
                    sourceUrl = profileUrl,
                    lastUpdatedAtMillis = 41L,
                )
                val initialConfigs = DesktopConfigLibrary(
                    selectedConfigId = profileId,
                    configs = listOf(initialProfile, sibling),
                    nextConfigId = 14,
                )
                val configPath = root.resolve("configs.json")
                DesktopConfigLibraries.save(configPath, initialConfigs).getOrThrow()
                val readConfigs = { DesktopConfigLibraries.load(configPath).getOrThrow() }
                val configRepository = DesktopTrafficConfigRepository(
                    initialLibrary = readConfigs(),
                    readLibrary = readConfigs,
                    saveLibrary = { updated -> DesktopConfigLibraries.save(configPath, updated) },
                    publishLibrary = {},
                )

                val firstNode = storedServer(
                    id = 10,
                    server = VLESS(remarks = "Node A", id = "node-a", server = "a.example", port = "443"),
                )
                val secondNode = storedServer(
                    id = 11,
                    server = VLESS(remarks = "Node B", id = "node-b", server = "b.example", port = "443"),
                )
                val initialServers = DesktopServerLibrary(
                    selectedServerId = secondNode.id,
                    servers = listOf(firstNode, secondNode),
                    nextServerId = 12,
                )
                val serverPath = root.resolve("servers.json")
                DesktopServerLibraries.save(serverPath, initialServers).getOrThrow()
                val readServers = { DesktopServerLibraries.load(serverPath).getOrThrow() }
                val serverRepository = DesktopProxyServerRepository(
                    initialLibrary = readServers(),
                    readLibrary = readServers,
                    saveLibrary = { updated -> DesktopServerLibraries.save(serverPath, updated) },
                    publishLibrary = {},
                )
                val reconciler = DesktopTrafficConfigProxyGroupReconciler(
                    serverRepository = serverRepository,
                    readConfigLibrary = readConfigs,
                    stripLeadingCountryFlag = { it },
                )

                val profileFallback = versionedDesktopProfileResourceUserAgent(ProjectInfo.VERSION_NAME)
                val profileFetcher = DesktopTrafficConfigProfileFetcher(
                    fetcher = DesktopSubscriptionFetcher(),
                    readSettings = { DesktopAppSettings(sendDeviceHeaders = false) },
                    activeProxy = { null },
                )
                val refreshedAtMillis = 1_700_000_000_000L
                val refresh = DesktopTrafficConfigAutoRefresh(
                    readLibrary = readConfigs,
                    repository = configRepository,
                    fetch = profileFetcher::fetch,
                    reconcile = reconciler::reconcile,
                    nowMillis = { refreshedAtMillis },
                    resourceUserAgentFallback = profileFallback,
                )
                val readTargets = {
                    readConfigs().configs.map { profile ->
                        val state = profile.toTrafficConfigState(profileFallback)
                        TrafficConfigRefreshTarget(
                            id = state.id,
                            sourceUrl = state.sourceUrl,
                            autoUpdate = state.autoUpdate,
                            updateLocked = state.updateLocked,
                            updateInterval = state.updateInterval,
                            resourceAutoUpdate = state.resourceSettings.autoUpdate,
                            resourceUpdateInterval = state.resourceSettings.updateInterval,
                        )
                    }
                }
                val executions = mutableListOf<Int>()
                val refreshResults = mutableListOf<TrafficConfigAutoUpdateExecutionResult>()
                val updater = DesktopTrafficConfigAutoUpdater(
                    readLatestTargets = readTargets,
                    isNetworkConnected = { true },
                    updateAutomatically = { requestedId ->
                        executions += requestedId
                        val result = refresh.run(requestedId)
                        refreshResults += result
                        check(result == TrafficConfigAutoUpdateExecutionResult.Success) {
                            "Profile refresh should complete successfully"
                        }
                        result
                    },
                    nowMillis = { refreshedAtMillis },
                )

                updater.observeTargets(readTargets())
                assertEquals(refreshedAtMillis, updater.nextWakeupAtMillis())
                updater.runDue()

                assertEquals(listOf(profileId), executions)
                assertEquals(listOf(TrafficConfigAutoUpdateExecutionResult.Success), refreshResults)
                assertEquals(profileFallback, receivedUserAgent.get())

                val savedConfigs = readConfigs()
                assertEquals(profileId, savedConfigs.selectedConfigId)
                assertEquals(listOf(profileId, sibling.id), savedConfigs.configs.map(ConfigProfile::id))
                assertEquals(sibling, savedConfigs.configs.single { it.id == sibling.id })
                val savedProfile = savedConfigs.configs.single { it.id == profileId }
                assertEquals("Provider response name", savedProfile.name)
                assertEquals(profileUrl, savedProfile.sourceUrl)
                assertEquals(refreshedAtMillis, savedProfile.lastUpdatedAtMillis)

                val savedProfileState = savedProfile.toTrafficConfigState(profileFallback)
                assertEquals("info", savedProfileState.rawConfig.analyzeShadowrocketConfig().general["loglevel"])
                assertTrue(savedProfileState.autoUpdate)
                assertEquals("6", savedProfileState.updateInterval)
                assertFalse(savedProfileState.updateLocked)
                assertEquals(false, savedProfileState.resourceSettings.autoUpdate)
                assertEquals("12", savedProfileState.resourceSettings.updateInterval)
                assertEquals(profileFallback, savedProfileState.resourceSettings.userAgent)

                val savedServers = readServers()
                assertEquals(13, savedServers.nextServerId)
                assertEquals(secondNode.id, savedServers.selectedServerId)
                assertEquals(listOf(firstNode.id, secondNode.id, 12), savedServers.servers.map(DesktopStoredProxyServer::id))
                val generatedRecord = savedServers.servers.single { it.id == 12 }
                assertEquals(DefaultAutoBalancerGroupId, generatedRecord.subscriptionId)
                val generatedGroup = assertIs<StrategyGroup>(generatedRecord.decode().getOrThrow())
                assertEquals("Refreshed policy", generatedGroup.remarks)
                assertEquals(listOf(firstNode.id, secondNode.id), generatedGroup.proxyServerIds)
                assertEquals(firstNode.id, generatedGroup.selectedMemberId)
                assertEquals(profileId, generatedGroup.sourceTrafficConfigId)
                assertEquals("Refreshed policy", generatedGroup.sourcePolicyGroupName)
                assertEquals("always", generatedGroup.displayMode)

                assertEquals(refreshedAtMillis + 6L * 60L * 60L * 1_000L, updater.nextWakeupAtMillis())
            }
        }
    }

    private fun profileDocument(
        name: String,
        sourceUrl: String,
        autoUpdate: Boolean,
        updateInterval: String,
        resourceAutoUpdate: Boolean,
        resourceUpdateInterval: String,
    ): String = """[General]
        loglevel = warning

        [SKIPI]
        profile-name = $name
        profile-update-url = $sourceUrl
        profile-update-locked = false
        profile-auto-update = $autoUpdate
        profile-update-interval = $updateInterval
        resource-auto-update = $resourceAutoUpdate
        resource-update-interval = $resourceUpdateInterval
    """.trimIndent()

    private fun storedServer(id: Int, server: VLESS) = DesktopStoredProxyServer(
        id = id,
        serverJson = server.encodePersistedProxyServer(),
    )

    private fun HttpExchange.respond(body: String) {
        val bytes = body.encodeToByteArray()
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { output -> output.write(bytes) }
    }

    private suspend fun <T> withTemporaryRoot(block: suspend (Path) -> T): T {
        val root = Files.createTempDirectory("desktop-profile-auto-refresh-")
        return try {
            block(root)
        } finally {
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder<Path>()).forEach(Files::deleteIfExists)
            }
        }
    }

    private suspend fun withHttpServer(block: suspend (HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            block(server)
        } finally {
            server.stop(0)
        }
    }

    private companion object {
        val fetchedProfileDocument = """[General]
            loglevel = info

            [SKIPI]
            profile-name = Provider response name
            profile-update-url = https://response.example/should-not-replace-captured-url
            profile-update-locked = true
            profile-auto-update = false
            profile-update-interval = 2
            resource-auto-update = true
            resource-update-interval = 3

            [Proxy Group]
            Refreshed policy = select, Node A, Node B, skipi-display=always
        """.trimIndent()
    }
}
