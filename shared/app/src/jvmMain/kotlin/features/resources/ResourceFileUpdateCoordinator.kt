// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResourceFileUpdateCoordinator(
    private val scope: CoroutineScope,
    /** Runs a request; it calls the second argument for each target as soon as that target's file is updated. */
    private val execute: suspend (ResourceFileUpdateRequest, (ResourceFileUpdateTarget) -> Unit) -> ResourceFilesStatus,
    private val cancelRunning: () -> Unit,
) : ResourceFileUpdateQueue {
    private val lock = Any()
    private val pending = ArrayDeque<ResourceFileUpdateQueueEntry>()
    private var running: ResourceFileUpdateQueueEntry? = null
    private var completionRevision = 0L
    private var finishedTargets: Set<ResourceFileUpdateTarget> = emptySet()
    private var nextEntryId = 1L
    private var worker: Job? = null

    private val mutableState = MutableStateFlow(ResourceFileUpdateQueueState())
    override val state: StateFlow<ResourceFileUpdateQueueState> = mutableState.asStateFlow()

    private val mutableResults = MutableSharedFlow<ResourceFileUpdateResult>(extraBufferCapacity = 16)
    override val results: SharedFlow<ResourceFileUpdateResult> = mutableResults.asSharedFlow()

    override fun enqueue(request: ResourceFileUpdateRequest): Boolean = synchronized(lock) {
        if (request.targets.isEmpty()) return false
        if (request is ResourceFileUpdateRequest.All && mutableState.value.isBusy) return false
        val busyTargets = buildSet {
            running?.request?.targets?.let(::addAll)
            pending.forEach { entry -> addAll(entry.request.targets) }
        }
        if (request.targets.any(busyTargets::contains)) return false

        pending.addLast(
            ResourceFileUpdateQueueEntry(
                id = nextEntryId++,
                request = request,
            ),
        )
        publishStateLocked()
        if (worker?.isActive != true) {
            worker = scope.launch { drainQueue() }
        }
        true
    }

    override fun cancelAll() {
        val hasRunningRequest = synchronized(lock) {
            pending.clear()
            publishStateLocked()
            running != null
        }
        if (hasRunningRequest) {
            cancelRunning()
        }
    }

    private suspend fun drainQueue() {
        while (true) {
            val entry = synchronized(lock) {
                val next = pending.removeFirstOrNull()
                if (next == null) {
                    worker = null
                    publishStateLocked()
                    null
                } else {
                    running = next
                    finishedTargets = emptySet()
                    publishStateLocked()
                    next
                }
            } ?: return

            val result = try {
                execute(entry.request) { target ->
                    synchronized(lock) {
                        if (running?.id == entry.id) {
                            // The page reads the file status again when this set changes, so the card shows the fresh time.
                            finishedTargets = finishedTargets + target
                            publishStateLocked()
                        }
                    }
                }
                ResourceFileUpdateResult.Success(entry)
            } catch (error: CancellationException) {
                clearAfterScopeCancellation()
                throw error
            } catch (_: ResourceFileDownloadCancelledException) {
                ResourceFileUpdateResult.Cancelled(entry)
            } catch (error: Throwable) {
                ResourceFileUpdateResult.Failure(entry, error)
            }

            synchronized(lock) {
                running = null
                finishedTargets = emptySet()
                completionRevision += 1
                publishStateLocked()
            }
            mutableResults.emit(result)
        }
    }

    private fun clearAfterScopeCancellation() = synchronized(lock) {
        running = null
        finishedTargets = emptySet()
        pending.clear()
        worker = null
        publishStateLocked()
    }

    private fun publishStateLocked() {
        mutableState.value = ResourceFileUpdateQueueState(
            running = running,
            pending = pending.toList(),
            completionRevision = completionRevision,
            finishedTargets = finishedTargets,
        )
    }
}
