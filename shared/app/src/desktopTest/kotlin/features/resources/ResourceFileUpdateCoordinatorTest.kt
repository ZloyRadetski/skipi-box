// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResourceFileUpdateCoordinatorTest {
    @Test
    fun updatesRunSeriallyInFifoOrderAndRejectOverlappingTargetsAcrossProfiles() = runTest {
        val first = builtIn(scope(id = 7), ResourceFileKind.GeoIp)
        val sameTargetDifferentProfile = builtIn(scope(id = 8), ResourceFileKind.GeoIp)
        val second = builtIn(scope(id = 8), ResourceFileKind.GeoSite)
        val sameQueuedTargetDifferentProfile = builtIn(scope(id = 9), ResourceFileKind.GeoSite)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val executed = mutableListOf<ResourceFileUpdateRequest>()
        val results = mutableListOf<ResourceFileUpdateResult>()
        val coordinator = ResourceFileUpdateCoordinator(
            scope = this,
            execute = { request ->
                executed += request
                if (request === first) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                ResourceFilesStatus()
            },
            cancelRunning = {},
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.results.collect(results::add)
        }

        assertTrue(coordinator.enqueue(first))
        runCurrent()
        assertTrue(firstStarted.isCompleted)
        assertEquals(ResourceFileUpdateDisplayState.Running, coordinator.state.value.displayStateOf(
            ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.GeoIp),
        ))
        assertFalse(coordinator.enqueue(sameTargetDifferentProfile))
        assertTrue(coordinator.enqueue(second))
        assertFalse(coordinator.enqueue(sameQueuedTargetDifferentProfile))
        assertEquals(ResourceFileUpdateDisplayState.Queued, coordinator.state.value.displayStateOf(
            ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.GeoSite),
        ))
        assertEquals(ResourceFileUpdateDisplayState.Idle, coordinator.state.value.displayStateOf(
            ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.GeoIpOnlyCnPrivate),
        ))
        assertEquals(listOf<ResourceFileUpdateRequest>(first), executed)

        releaseFirst.complete(Unit)
        runCurrent()

        assertEquals(listOf<ResourceFileUpdateRequest>(first, second), executed)
        assertEquals(2, results.size)
        assertSame(first, assertIs<ResourceFileUpdateResult.Success>(results[0]).request)
        assertSame(second, assertIs<ResourceFileUpdateResult.Success>(results[1]).request)
        assertEquals(2L, coordinator.state.value.completionRevision)
        assertFalse(coordinator.state.value.isBusy)
    }

    @Test
    fun allRequestIsRejectedWheneverAnyRequestIsRunningOrQueued() = runTest {
        val running = builtIn(scope(id = 1), ResourceFileKind.GeoIp)
        val queued = builtIn(scope(id = 2), ResourceFileKind.GeoSite)
        val all = all(scope(id = 3))
        val runningStarted = CompletableDeferred<Unit>()
        val releaseRunning = CompletableDeferred<Unit>()
        val executed = mutableListOf<ResourceFileUpdateRequest>()
        val coordinator = ResourceFileUpdateCoordinator(
            scope = this,
            execute = { request ->
                executed += request
                if (request === running) {
                    runningStarted.complete(Unit)
                    releaseRunning.await()
                }
                ResourceFilesStatus()
            },
            cancelRunning = {},
        )

        assertTrue(coordinator.enqueue(running))
        runCurrent()
        assertTrue(runningStarted.isCompleted)
        assertFalse(coordinator.enqueue(all))
        assertTrue(coordinator.enqueue(queued))
        assertFalse(coordinator.enqueue(all))

        releaseRunning.complete(Unit)
        runCurrent()
        assertEquals(listOf<ResourceFileUpdateRequest>(running, queued), executed)
        assertTrue(coordinator.enqueue(all))
        runCurrent()
        assertSame(all, executed.last())
        assertFalse(coordinator.state.value.isBusy)
    }

    @Test
    fun allRequestIncludesFiveBuiltInsAndOnlyCustomFilesWithNonblankUrls() {
        val customFiles = listOf(
            TrafficConfigCustomResourceFile(id = 1, name = "custom-rules.dat", url = " https://files.example/custom.dat "),
            TrafficConfigCustomResourceFile(id = 2, name = "blank-url.dat", url = "  "),
            // The coordinator snapshots configured targets; the repository separately filters reserved names.
            TrafficConfigCustomResourceFile(id = 3, name = ResourceFileKind.GeoIp.fileName, url = "https://files.example/geoip"),
        )

        val request = all(scope(id = 7), customFiles)

        assertEquals(
            setOf(
                ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.GeoIp),
                ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.GeoSite),
                ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.GeoIpOnlyCnPrivate),
                ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.DirectCidrIpv4),
                ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.DirectCidrIpv6),
                ResourceFileUpdateTarget.Custom(1),
                ResourceFileUpdateTarget.Custom(3),
            ),
            request.targets,
        )
        assertFalse(ResourceFileUpdateTarget.BuiltIn(ResourceFileKind.XrayCore) in request.targets)
    }

    @Test
    fun requestKeepsItsCapturedScopeAndCopiesTheCustomFileList() = runTest {
        val originalScope = scope(id = 11, source = 4)
        val nextScope = scope(id = 12, source = 1)
        val firstCustom = TrafficConfigCustomResourceFile(id = 5, name = "before.dat", url = "https://files.example/5")
        val secondCustom = TrafficConfigCustomResourceFile(id = 6, name = "after.dat", url = "https://files.example/6")
        val mutableCustomFiles = mutableListOf(firstCustom)
        var currentScope = originalScope
        val options = ResourceFileUpdateOptions(userAgent = "captured-agent")
        val builtInRequest = ResourceFileUpdateRequest.BuiltIn(
            scope = currentScope,
            kind = ResourceFileKind.GeoSite,
            source = source(id = 4),
            options = options,
            customResourceFiles = mutableCustomFiles,
        )
        val customRequest = ResourceFileUpdateRequest.Custom(
            scope = currentScope,
            file = firstCustom,
            options = options,
            customResourceFiles = mutableCustomFiles,
        )
        val allRequest = ResourceFileUpdateRequest.All(
            scope = currentScope,
            source = source(id = 4),
            options = options,
            customResourceFiles = mutableCustomFiles,
        )
        currentScope = nextScope
        mutableCustomFiles += secondCustom
        var executed: ResourceFileUpdateRequest? = null
        val coordinator = ResourceFileUpdateCoordinator(
            scope = this,
            execute = { captured ->
                executed = captured
                ResourceFilesStatus()
            },
            cancelRunning = {},
        )

        listOf(
            Triple(builtInRequest.scope, builtInRequest.customResourceFiles, builtInRequest.options),
            Triple(customRequest.scope, customRequest.customResourceFiles, customRequest.options),
            Triple(allRequest.scope, allRequest.customResourceFiles, allRequest.options),
        ).forEach { (requestScope, customFiles, requestOptions) ->
            assertEquals(originalScope, requestScope)
            assertEquals(listOf(firstCustom), customFiles)
            assertEquals(options, requestOptions)
        }
        assertEquals(source(id = 4), builtInRequest.source)
        assertEquals(source(id = 4), allRequest.source)
        assertTrue(ResourceFileUpdateTarget.Custom(secondCustom.id) !in allRequest.targets)
        assertTrue(coordinator.enqueue(builtInRequest))
        runCurrent()
        assertSame(builtInRequest, executed)
        val executedRequest = assertIs<ResourceFileUpdateRequest.BuiltIn>(executed)
        assertEquals(originalScope, executedRequest.scope)
        assertEquals(listOf(firstCustom), executedRequest.customResourceFiles)
        assertEquals(nextScope, currentScope)
    }

    @Test
    fun ordinaryFailureEmitsFailureThenQueueContinuesWithTheNextRequest() = runTest {
        val first = builtIn(scope(id = 1), ResourceFileKind.GeoIp)
        val second = builtIn(scope(id = 2), ResourceFileKind.GeoSite)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val executed = mutableListOf<ResourceFileUpdateRequest>()
        val results = mutableListOf<ResourceFileUpdateResult>()
        val coordinator = ResourceFileUpdateCoordinator(
            scope = this,
            execute = { request ->
                executed += request
                if (request === first) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                    throw IllegalStateException("first update failed")
                }
                ResourceFilesStatus()
            },
            cancelRunning = {},
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.results.collect(results::add)
        }

        assertTrue(coordinator.enqueue(first))
        runCurrent()
        assertTrue(firstStarted.isCompleted)
        assertTrue(coordinator.enqueue(second))
        releaseFirst.complete(Unit)
        runCurrent()

        assertEquals(listOf<ResourceFileUpdateRequest>(first, second), executed)
        assertEquals(2, results.size)
        val failure = assertIs<ResourceFileUpdateResult.Failure>(results[0])
        assertSame(first, failure.request)
        assertEquals("first update failed", failure.error.message)
        assertSame(second, assertIs<ResourceFileUpdateResult.Success>(results[1]).request)
        assertEquals(2L, coordinator.state.value.completionRevision)
    }

    @Test
    fun downloadCancellationProducesCancelledResultAndContinuesQueue() = runTest {
        val cancelledRequest = builtIn(scope(id = 1), ResourceFileKind.GeoIp)
        val nextRequest = builtIn(scope(id = 2), ResourceFileKind.GeoSite)
        val executed = mutableListOf<ResourceFileUpdateRequest>()
        val results = mutableListOf<ResourceFileUpdateResult>()
        val coordinator = ResourceFileUpdateCoordinator(
            scope = this,
            execute = { request ->
                executed += request
                if (request === cancelledRequest) {
                    throw ResourceFileDownloadCancelledException()
                }
                ResourceFilesStatus()
            },
            cancelRunning = {},
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.results.collect(results::add)
        }

        assertTrue(coordinator.enqueue(cancelledRequest))
        assertTrue(coordinator.enqueue(nextRequest))
        runCurrent()

        assertEquals(listOf<ResourceFileUpdateRequest>(cancelledRequest, nextRequest), executed)
        assertEquals(2, results.size)
        assertSame(cancelledRequest, assertIs<ResourceFileUpdateResult.Cancelled>(results[0]).request)
        assertSame(nextRequest, assertIs<ResourceFileUpdateResult.Success>(results[1]).request)
        assertEquals(2L, coordinator.state.value.completionRevision)
    }

    @Test
    fun coroutineCancellationClearsQueueWithoutResultOrCompletionRevision() = runTest {
        val first = builtIn(scope(id = 1), ResourceFileKind.GeoIp)
        val pending = builtIn(scope(id = 2), ResourceFileKind.GeoSite)
        val recovery = builtIn(scope(id = 3), ResourceFileKind.DirectCidrIpv4)
        val firstStarted = CompletableDeferred<Unit>()
        val cancelFirst = CompletableDeferred<Unit>()
        val executed = mutableListOf<ResourceFileUpdateRequest>()
        val results = mutableListOf<ResourceFileUpdateResult>()
        val coordinator = ResourceFileUpdateCoordinator(
            scope = this,
            execute = { request ->
                executed += request
                if (request === first) {
                    firstStarted.complete(Unit)
                    cancelFirst.await()
                }
                ResourceFilesStatus()
            },
            cancelRunning = {},
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.results.collect(results::add)
        }

        assertTrue(coordinator.enqueue(first))
        runCurrent()
        assertTrue(firstStarted.isCompleted)
        assertTrue(coordinator.enqueue(pending))
        cancelFirst.completeExceptionally(CancellationException("test cancellation"))
        runCurrent()

        assertEquals(listOf<ResourceFileUpdateRequest>(first), executed)
        assertTrue(results.isEmpty())
        assertNull(coordinator.state.value.running)
        assertTrue(coordinator.state.value.pending.isEmpty())
        assertEquals(0L, coordinator.state.value.completionRevision)
        assertFalse(coordinator.state.value.isBusy)

        assertTrue(coordinator.enqueue(recovery))
        runCurrent()
        assertEquals(listOf<ResourceFileUpdateRequest>(first, recovery), executed)
        assertEquals(1L, coordinator.state.value.completionRevision)
        assertSame(recovery, assertIs<ResourceFileUpdateResult.Success>(results.single()).request)
    }

    @Test
    fun cancelAllClearsPendingWorkAndCallsTheRunningCancellationCallback() = runTest {
        val running = builtIn(scope(id = 1), ResourceFileKind.GeoIp)
        val pending = builtIn(scope(id = 2), ResourceFileKind.GeoSite)
        val started = CompletableDeferred<Unit>()
        val finishRunning = CompletableDeferred<Unit>()
        val executed = mutableListOf<ResourceFileUpdateRequest>()
        var cancellationCallbacks = 0
        val coordinator = ResourceFileUpdateCoordinator(
            scope = this,
            execute = { request ->
                executed += request
                if (request === running) {
                    started.complete(Unit)
                    finishRunning.await()
                }
                ResourceFilesStatus()
            },
            cancelRunning = {
                cancellationCallbacks += 1
                finishRunning.complete(Unit)
            },
        )

        assertTrue(coordinator.enqueue(running))
        runCurrent()
        assertTrue(started.isCompleted)
        assertTrue(coordinator.enqueue(pending))

        coordinator.cancelAll()

        assertEquals(1, cancellationCallbacks)
        assertSame(running, coordinator.state.value.running?.request)
        assertTrue(coordinator.state.value.pending.isEmpty())
        assertTrue(coordinator.state.value.isBusy)

        runCurrent()

        assertEquals(listOf<ResourceFileUpdateRequest>(running), executed)
        assertFalse(coordinator.state.value.isBusy)
        assertEquals(1L, coordinator.state.value.completionRevision)
    }

    private fun builtIn(
        scope: ResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ) = ResourceFileUpdateRequest.BuiltIn(
        scope = scope,
        kind = kind,
        source = source(scope.resourceFileSource),
        options = ResourceFileUpdateOptions(),
        customResourceFiles = customResourceFiles,
    )

    private fun all(
        scope: ResourceFileScope,
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ) = ResourceFileUpdateRequest.All(
        scope = scope,
        source = source(scope.resourceFileSource),
        options = ResourceFileUpdateOptions(),
        customResourceFiles = customResourceFiles,
    )

    private fun scope(id: Int, source: Int = 0) = ResourceFileScope(
        trafficConfigId = id,
        resourceFileSource = source,
    )

    private fun source(id: Int) = ResourceFileUpdateSource(
        id = id,
        geoIpUrl = "https://files.example/$id/geoip.dat",
        geoSiteUrl = "https://files.example/$id/geosite.dat",
        geoIpOnlyCnPrivateUrl = "https://files.example/$id/geoip-only-cn-private.dat",
        directCidrIpv4Url = "https://files.example/$id/direct-cidr-v4.txt",
        directCidrIpv6Url = "https://files.example/$id/direct-cidr-v6.txt",
    )
}
