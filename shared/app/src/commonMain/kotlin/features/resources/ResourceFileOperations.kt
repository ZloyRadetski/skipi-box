// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile

/** Host-owned local resource-file operations used by the portable resource screen. */
interface ResourceFileOperations {
    suspend fun status(
        scope: ResourceFileScope,
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus

    suspend fun replace(
        scope: ResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus?

    suspend fun replaceCustom(
        scope: ResourceFileScope,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus?

    suspend fun restoreBundled(
        scope: ResourceFileScope,
        kind: ResourceFileKind,
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus

    suspend fun renameCustom(
        scope: ResourceFileScope,
        previousFile: TrafficConfigCustomResourceFile,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus

    suspend fun deleteCustom(
        scope: ResourceFileScope,
        customFile: TrafficConfigCustomResourceFile,
        customResourceFiles: List<TrafficConfigCustomResourceFile> = emptyList(),
    ): ResourceFilesStatus
}
