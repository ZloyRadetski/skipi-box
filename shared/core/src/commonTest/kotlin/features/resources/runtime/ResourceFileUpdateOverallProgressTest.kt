// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResourceFileUpdateOverallProgressTest {

    @Test
    fun returnsNullWhenFileCountOrContentLengthIsNonPositive() {
        assertNull(resourceFileUpdateOverallProgress(fileIndex = 0, fileCount = 1, downloadedBytes = 0, totalBytes = 0))
        assertNull(resourceFileUpdateOverallProgress(fileIndex = 0, fileCount = 1, downloadedBytes = 0, totalBytes = -1))
        assertNull(resourceFileUpdateOverallProgress(fileIndex = 0, fileCount = 0, downloadedBytes = 1, totalBytes = 2))
        assertNull(resourceFileUpdateOverallProgress(fileIndex = 0, fileCount = -1, downloadedBytes = 1, totalBytes = 2))
    }

    @Test
    fun clampsNegativeFileIndexAndDownloadedBytesToZero() {
        assertEquals(
            25,
            resourceFileUpdateOverallProgress(fileIndex = -1, fileCount = 2, downloadedBytes = 5, totalBytes = 10),
        )
        assertEquals(
            0,
            resourceFileUpdateOverallProgress(fileIndex = -1, fileCount = 2, downloadedBytes = -5, totalBytes = 10),
        )
    }

    @Test
    fun combinesCompletedFilesWithCurrentFileProgressAndTruncatesFractionalPercent() {
        assertEquals(
            75,
            resourceFileUpdateOverallProgress(fileIndex = 1, fileCount = 2, downloadedBytes = 5, totalBytes = 10),
        )
        assertEquals(
            37,
            resourceFileUpdateOverallProgress(fileIndex = 1, fileCount = 4, downloadedBytes = 1, totalBytes = 2),
        )
    }

    @Test
    fun capsProgressAtOneHundredWhenIndexOrCurrentFileExceedsItsBounds() {
        assertEquals(
            100,
            resourceFileUpdateOverallProgress(fileIndex = 2, fileCount = 2, downloadedBytes = 0, totalBytes = 10),
        )
        assertEquals(
            50,
            resourceFileUpdateOverallProgress(fileIndex = 0, fileCount = 2, downloadedBytes = 20, totalBytes = 10),
        )
    }
}
