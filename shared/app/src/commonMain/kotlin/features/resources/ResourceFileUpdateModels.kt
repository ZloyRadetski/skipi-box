// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile

private val UpdateableResourceFileKinds = setOf(
    ResourceFileKind.GeoIp,
    ResourceFileKind.GeoSite,
    ResourceFileKind.GeoIpOnlyCnPrivate,
    ResourceFileKind.DirectCidrIpv4,
    ResourceFileKind.DirectCidrIpv6,
)

data class ResourceFileUpdateOptions(
    val useRunningProxy: Boolean = false,
    val fallbackProxyPort: Int? = null,
    val fallbackProxyUsername: String = "",
    val fallbackProxyPassword: String = "",
    val userAgent: String = "",
    /** Whether system notifications should be shown while files are updated. */
    val showNotifications: Boolean = true,
)

sealed interface ResourceFileUpdateTarget {
    data class BuiltIn(val kind: ResourceFileKind) : ResourceFileUpdateTarget

    data class Custom(val id: Int) : ResourceFileUpdateTarget
}

sealed interface ResourceFileUpdateRequest {
    val targets: Set<ResourceFileUpdateTarget>

    class BuiltIn(
        val scope: ResourceFileScope,
        val kind: ResourceFileKind,
        val source: ResourceFileUpdateSource,
        val options: ResourceFileUpdateOptions,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ) : ResourceFileUpdateRequest {
        val customResourceFiles = customResourceFiles.toList()
        override val targets = setOf(ResourceFileUpdateTarget.BuiltIn(kind))
    }

    class Custom(
        val scope: ResourceFileScope,
        val file: TrafficConfigCustomResourceFile,
        val options: ResourceFileUpdateOptions,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ) : ResourceFileUpdateRequest {
        val customResourceFiles = customResourceFiles.toList()
        override val targets = setOf(ResourceFileUpdateTarget.Custom(file.id))
    }

    class All(
        val scope: ResourceFileScope,
        val source: ResourceFileUpdateSource,
        val options: ResourceFileUpdateOptions,
        customResourceFiles: List<TrafficConfigCustomResourceFile>,
    ) : ResourceFileUpdateRequest {
        val customResourceFiles = customResourceFiles.toList()
        override val targets: Set<ResourceFileUpdateTarget> = buildSet {
            UpdateableResourceFileKinds.forEach { kind ->
                add(ResourceFileUpdateTarget.BuiltIn(kind))
            }
            this@All.customResourceFiles.forEach { customFile ->
                if (customFile.url.isNotBlank()) {
                    add(ResourceFileUpdateTarget.Custom(customFile.id))
                }
            }
        }
    }
}

data class ResourceFileUpdateQueueEntry(
    val id: Long,
    val request: ResourceFileUpdateRequest,
)

enum class ResourceFileUpdateDisplayState {
    Idle,
    Queued,
    Running,
}

data class ResourceFileUpdateQueueState(
    val running: ResourceFileUpdateQueueEntry? = null,
    val pending: List<ResourceFileUpdateQueueEntry> = emptyList(),
    val completionRevision: Long = 0,
)

val ResourceFileUpdateQueueState.isBusy: Boolean
    get() = running != null || pending.isNotEmpty()

fun ResourceFileUpdateQueueState.displayStateOf(
    target: ResourceFileUpdateTarget,
): ResourceFileUpdateDisplayState = when {
    target in running?.request?.targets.orEmpty() -> ResourceFileUpdateDisplayState.Running
    pending.any { entry -> target in entry.request.targets } -> ResourceFileUpdateDisplayState.Queued
    else -> ResourceFileUpdateDisplayState.Idle
}

sealed interface ResourceFileUpdateResult {
    val entry: ResourceFileUpdateQueueEntry
    val request: ResourceFileUpdateRequest
        get() = entry.request

    data class Success(
        override val entry: ResourceFileUpdateQueueEntry,
    ) : ResourceFileUpdateResult

    data class Failure(
        override val entry: ResourceFileUpdateQueueEntry,
        val error: Throwable,
    ) : ResourceFileUpdateResult

    data class Cancelled(
        override val entry: ResourceFileUpdateQueueEntry,
    ) : ResourceFileUpdateResult
}
