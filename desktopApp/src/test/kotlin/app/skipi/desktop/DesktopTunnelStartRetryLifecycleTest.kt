// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.model.SubscriptionRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import platform.TunnelConnectRequest
import platform.TunnelConfiguration
import platform.TunnelOperationException
import platform.TunnelOperationStage
import platform.TunnelPhase

class DesktopTunnelStartRetryLifecycleTest {
    @Test
    fun dynamicBindFailureRebuildsFromOneSettingsSnapshotAndPublishesOnlyTheFinalEndpoint() = runBlocking {
        val runtime = DesktopLocalProxyRuntime()
        val capturedSettings = DesktopAppSettings(
            useSystemProxy = false,
            useWindowsSystemProxy = false,
            localProxyPort = 10_808,
            enableDynamicLocalProxyPort = true,
            enableLocalProxyAuth = true,
            localProxyUsername = " captured-user ",
            localProxyPassword = " captured-password ",
        )
        var latestSettings = capturedSettings
        val allocateCalls = mutableListOf<Set<Int>>()
        val allocatedPorts = ArrayDeque(listOf(20_480, 20_481))
        val startEvents = mutableListOf<String>()
        var running = false
        var startCalls = 0
        var rebuildCalls = 0

        fun buildPlan(
            settingsSnapshot: DesktopAppSettings,
            excludedPorts: Set<Int>,
        ): DesktopTunnelStartPlan {
            val options = runtime.prepareStart(
                settings = settingsSnapshot,
                excludedPorts = excludedPorts,
                allocatePort = { _, excluded ->
                    allocateCalls += excluded
                    allocatedPorts.removeFirst()
                },
            )
            return DesktopTunnelStartPlan(
                configuration = "socks-port=${options.socksPort}",
                dynamicPortEnabled = settingsSnapshot.enableDynamicLocalProxyPort,
                localProxyPort = options.socksPort,
                rebuild = { failedPorts ->
                    rebuildCalls += 1
                    buildPlan(settingsSnapshot, failedPorts)
                },
            )
        }

        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("start plan should be used")) },
            startPlanForProfile = {
                // The plan closes over the settings/auth snapshot taken before the first start.
                Result.success(buildPlan(latestSettings, emptySet()))
            },
            startCore = { request ->
                val port = request.configuration.substringAfter('=').toInt()
                startCalls += 1
                startEvents += "start:$port"
                if (startCalls == 1) {
                    latestSettings = capturedSettings.copy(
                        enableDynamicLocalProxyPort = false,
                        enableLocalProxyAuth = false,
                        localProxyUsername = "changed-user",
                        localProxyPassword = "changed-password",
                    )
                }
                val startResult = if (startCalls == 1) {
                    Result.failure(
                        IllegalStateException("Core startup failed", IllegalArgumentException("Address already in use")),
                    )
                } else {
                    running = true
                    Result.success(DesktopCoreState(isRunning = true, sessionId = 21L))
                }
                runtime.onCoreStarted(startResult).also {
                    if (startCalls == 1) assertNull(runtime.activeOptions(coreRunning = true))
                }
            },
            stopCore = {
                running = false
                runtime.onCoreStopped(Result.success(DesktopCoreState(isRunning = false)))
            },
            coreState = { DesktopCoreState(isRunning = running) },
            awaitSystemProxyEndpoint = {
                val options = runtime.activeOptions(coreRunning = running)
                startEvents += "ready:${options?.socksPort}"
                assertEquals(20_481, options?.socksPort)
                Result.success(Unit)
            },
            acquireSystemProxy = {
                val options = runtime.activeOptions(coreRunning = running)
                startEvents += "acquire:${options?.socksPort}"
                assertEquals(20_481, options?.socksPort)
                Result.success(Unit)
            },
            systemProxySupported = { true },
        )

        val result = controller.connect(TunnelConnectRequest("profile-1"))

        assertTrue(result.isSuccess)
        assertEquals(TunnelPhase.Connected, controller.snapshot().phase)
        assertEquals(2, startCalls)
        assertEquals(1, rebuildCalls)
        assertEquals(listOf(emptySet(), setOf(20_480)), allocateCalls)
        assertEquals(listOf("start:20480", "start:20481", "ready:20481", "acquire:20481"), startEvents)
        val publishedProxy = runtime.subscriptionFetchOptions(
            settings = latestSettings,
            subscription = subscription(updateViaProxy = true),
            coreRunning = running,
        ).proxy
        assertEquals(20_481, publishedProxy?.port)
        assertEquals("captured-user", publishedProxy?.username)
        assertEquals(" captured-password ", publishedProxy?.password)
    }

    @Test
    fun staticOrNonBindFailureAndSuccessfulNonrunningStartDoNotRebuild() = runBlocking {
        val staticBindFailure = runAttempt(
            dynamicPortEnabled = false,
            startResult = Result.failure(IllegalStateException("Address already in use")),
        )
        assertTrue(staticBindFailure.result.isFailure)
        assertEquals(1, staticBindFailure.startCalls)
        assertEquals(0, staticBindFailure.rebuildCalls)
        assertNull(staticBindFailure.activeOptions)

        val dynamicNonBindFailure = runAttempt(
            dynamicPortEnabled = true,
            startResult = Result.failure(IllegalStateException("invalid Xray configuration")),
        )
        assertTrue(dynamicNonBindFailure.result.isFailure)
        assertEquals(1, dynamicNonBindFailure.startCalls)
        assertEquals(0, dynamicNonBindFailure.rebuildCalls)
        assertNull(dynamicNonBindFailure.activeOptions)

        val successfulButStopped = runAttempt(
            dynamicPortEnabled = true,
            startResult = Result.success(DesktopCoreState(isRunning = false)),
        )
        assertTrue(successfulButStopped.result.isSuccess)
        assertEquals(1, successfulButStopped.startCalls)
        assertEquals(0, successfulButStopped.rebuildCalls)
        assertNull(successfulButStopped.activeOptions)
    }

    @Test
    fun preparedConfigurationDoesNotEnterTheDynamicRetryPath() = runBlocking {
        val result = runAttempt(
            dynamicPortEnabled = true,
            startResult = Result.failure(IllegalStateException("failed to listen TCP 127.0.0.1:20001")),
            requestConfiguration = TunnelConfiguration("prepared-config"),
        )

        assertTrue(result.result.isFailure)
        assertEquals(1, result.startCalls)
        assertEquals(0, result.planCalls)
        assertEquals(0, result.rebuildCalls)
        assertNull(result.activeOptions)
    }

    @Test
    fun secondBindFailureIsFinalAndRetryPlanFailureReportsPrepareConfigurationStage() = runBlocking {
        val exhausted = runDynamicFailureAttempts(rebuildFailure = null)
        assertTrue(exhausted.result.isFailure)
        assertEquals(2, exhausted.startCalls)
        assertEquals(1, exhausted.rebuildCalls)
        assertEquals(listOf(emptySet(), setOf(20_480)), exhausted.allocateCalls)
        assertNull(exhausted.activeOptions)
        val startError = exhausted.result.exceptionOrNull() as TunnelOperationException
        assertEquals(TunnelOperationStage.Start, startError.failure.stage)

        val rebuildFailure = IllegalStateException("could not rebuild selected profile")
        val failedRebuild = runDynamicFailureAttempts(rebuildFailure = rebuildFailure)
        assertTrue(failedRebuild.result.isFailure)
        assertEquals(1, failedRebuild.startCalls)
        assertEquals(1, failedRebuild.rebuildCalls)
        assertNull(failedRebuild.activeOptions)
        val prepareError = failedRebuild.result.exceptionOrNull() as TunnelOperationException
        assertEquals(TunnelOperationStage.PrepareConfiguration, prepareError.failure.stage)
        assertEquals(rebuildFailure.message, prepareError.failure.message)
    }

    private data class AttemptResult(
        val result: Result<Unit>,
        val startCalls: Int,
        val planCalls: Int,
        val rebuildCalls: Int,
        val activeOptions: platform.LocalProxyXrayConfigOptions?,
    )

    private suspend fun runAttempt(
        dynamicPortEnabled: Boolean,
        startResult: Result<DesktopCoreState>,
        requestConfiguration: TunnelConfiguration? = null,
    ): AttemptResult {
        val runtime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(
            useSystemProxy = false,
            useWindowsSystemProxy = false,
            enableDynamicLocalProxyPort = dynamicPortEnabled,
        )
        var nextPort = 20_480
        var startCalls = 0
        var planCalls = 0
        var rebuildCalls = 0
        fun buildPlan(excludedPorts: Set<Int>): DesktopTunnelStartPlan {
            val options = runtime.prepareStart(settings, excludedPorts) { _, _ -> nextPort++ }
            return DesktopTunnelStartPlan(
                configuration = "socks-port=${options.socksPort}",
                dynamicPortEnabled = settings.enableDynamicLocalProxyPort,
                localProxyPort = options.socksPort,
                rebuild = { failedPorts ->
                    rebuildCalls += 1
                    buildPlan(failedPorts)
                },
            )
        }
        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("plan path expected")) },
            startPlanForProfile = {
                planCalls += 1
                Result.success(buildPlan(emptySet()))
            },
            startCore = {
                startCalls += 1
                runtime.onCoreStarted(startResult)
            },
            stopCore = { runtime.onCoreStopped(Result.success(DesktopCoreState(isRunning = false))) },
            coreState = { DesktopCoreState(isRunning = startResult.getOrNull()?.isRunning == true) },
        )
        val result = controller.connect(
            TunnelConnectRequest(
                profileId = "profile-1",
                configuration = requestConfiguration,
            )
        )
        return AttemptResult(
            result = result,
            startCalls = startCalls,
            planCalls = planCalls,
            rebuildCalls = rebuildCalls,
            activeOptions = runtime.activeOptions(coreRunning = true),
        )
    }

    private data class DynamicFailureResult(
        val result: Result<Unit>,
        val startCalls: Int,
        val rebuildCalls: Int,
        val allocateCalls: List<Set<Int>>,
        val activeOptions: platform.LocalProxyXrayConfigOptions?,
    )

    private suspend fun runDynamicFailureAttempts(
        rebuildFailure: Throwable?,
    ): DynamicFailureResult {
        val runtime = DesktopLocalProxyRuntime()
        val settings = DesktopAppSettings(
            useSystemProxy = false,
            useWindowsSystemProxy = false,
            enableDynamicLocalProxyPort = true,
        )
        val allocateCalls = mutableListOf<Set<Int>>()
        val allocatedPorts = ArrayDeque(listOf(20_480, 20_481))
        var startCalls = 0
        var rebuildCalls = 0
        fun buildPlan(excludedPorts: Set<Int>): DesktopTunnelStartPlan {
            val options = runtime.prepareStart(
                settings = settings,
                excludedPorts = excludedPorts,
                allocatePort = { _, excluded ->
                    allocateCalls += excluded
                    allocatedPorts.removeFirst()
                },
            )
            return DesktopTunnelStartPlan(
                configuration = "socks-port=${options.socksPort}",
                dynamicPortEnabled = true,
                localProxyPort = options.socksPort,
                rebuild = { failedPorts ->
                    rebuildCalls += 1
                    if (rebuildFailure != null) throw rebuildFailure
                    buildPlan(failedPorts)
                },
            )
        }
        val controller = DesktopTunnelController(
            configForProfile = { Result.failure(AssertionError("plan path expected")) },
            startPlanForProfile = { Result.success(buildPlan(emptySet())) },
            startCore = {
                startCalls += 1
                runtime.onCoreStarted(
                    Result.failure(
                        IllegalStateException("Core start failed", IllegalStateException("failed to listen TCP 127.0.0.1:20001")),
                    ),
                )
            },
            stopCore = { runtime.onCoreStopped(Result.success(DesktopCoreState(isRunning = false))) },
            coreState = { DesktopCoreState(isRunning = false) },
        )

        val result = controller.connect(TunnelConnectRequest("profile-1"))
        return DynamicFailureResult(
            result = result,
            startCalls = startCalls,
            rebuildCalls = rebuildCalls,
            allocateCalls = allocateCalls,
            activeOptions = runtime.activeOptions(coreRunning = true),
        )
    }

    private fun subscription(updateViaProxy: Boolean) = SubscriptionRecord(
        id = 8,
        title = "Subscription",
        url = "https://sub.example/list",
        userAgent = "",
        updateInterval = "1d",
        hwid = "install-uuid",
        ageSecretKey = "",
        updateViaProxy = updateViaProxy,
        autoOverrideRules = false,
        builtIn = false,
        notifyOnExpiry = false,
        customExpiryReminders = null,
    )
}
