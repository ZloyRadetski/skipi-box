// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.resources.ResourceFileKind
import features.resources.ResourceFileScope
import features.resources.ResourceFileUpdateDownload
import features.resources.ResourceFileUpdateDownloadPlan
import features.resources.ResourceFilesStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParallelResourceFileDownloadTest {
    @Test
    fun filesRunSideBySideAndEachIsReportedAsSoonAsItIsDone() {
        val downloads = listOf(
            ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoIp, "https://example.test/geoip"),
            ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoSite, "https://example.test/geosite"),
        )
        // Both downloads wait for each other: this only finishes when they really run at the same time.
        val bothRunning = CountDownLatch(2)
        val releaseSecond = CountDownLatch(1)
        val firstReportedBeforeSecondEnded = CopyOnWriteArrayList<Boolean>()
        val reported = CopyOnWriteArrayList<ResourceFileUpdateDownload>()

        executeResourceFileUpdateBatch(
            plan = ResourceFileUpdateDownloadPlan(
                scope = ResourceFileScope(trafficConfigId = 1, resourceFileSource = 0),
                downloads = downloads,
                markResourceFileSourceAfterSuccess = false,
            ),
            currentStatus = { ResourceFilesStatus() },
            prepareSession = {
                ResourceFileUpdateBatchSession(
                    updateFile = { _, index, _ ->
                        bothRunning.countDown()
                        assertTrue(bothRunning.await(5, TimeUnit.SECONDS), "the files did not run in parallel")
                        if (index == 1) {
                            assertTrue(releaseSecond.await(5, TimeUnit.SECONDS))
                        }
                    },
                    notifyComplete = {},
                    reportFailure = { throw it },
                )
            },
            markSource = {},
            mapCancellationError = { it },
            runTasks = parallelResourceFileTaskRunner(),
            onFileUpdated = { download ->
                reported += download
                if (download === downloads[0]) {
                    firstReportedBeforeSecondEnded += (reported.size == 1)
                    releaseSecond.countDown()
                }
            },
        )

        assertEquals(listOf(true), firstReportedBeforeSecondEnded)
        assertEquals(downloads, reported.sortedBy { downloads.indexOf(it) })
    }

    @Test
    fun aFailingFileDoesNotHideTheOthersThatAreAlreadyRunning() {
        val downloads = listOf(
            ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoIp, "https://example.test/geoip"),
            ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoSite, "https://example.test/geosite"),
        )
        val bothRunning = CountDownLatch(2)
        val reported = CopyOnWriteArrayList<ResourceFileUpdateDownload>()

        assertFailsWith<ResourceFileDownloadFailedException> {
            executeResourceFileUpdateBatch(
                plan = ResourceFileUpdateDownloadPlan(
                    scope = ResourceFileScope(trafficConfigId = 1, resourceFileSource = 0),
                    downloads = downloads,
                    markResourceFileSourceAfterSuccess = false,
                ),
                currentStatus = { ResourceFilesStatus() },
                prepareSession = {
                    ResourceFileUpdateBatchSession(
                        updateFile = { _, index, _ ->
                            bothRunning.countDown()
                            bothRunning.await(5, TimeUnit.SECONDS)
                            if (index == 0) error("connection reset")
                        },
                        notifyComplete = {},
                        reportFailure = {},
                    )
                },
                markSource = {},
                mapCancellationError = { it },
                runTasks = parallelResourceFileTaskRunner(),
                onFileUpdated = { reported += it },
            )
        }

        assertEquals(listOf<ResourceFileUpdateDownload>(downloads[1]), reported.toList())
    }

    @Test
    fun batchProgressIsTheMeanOfTheFilesAndNamesTheFirstUnfinishedOne() {
        val progress = ResourceFileBatchProgress(listOf("geoip.dat", "geosite.dat"))

        assertNull(progress.snapshot().percent)

        progress.report(0, 50, 100)
        assertEquals(25, progress.snapshot().percent)

        progress.report(1, 100, 100)
        assertEquals(75, progress.snapshot().percent)
        assertEquals("geoip.dat", progress.snapshot().fileName)

        progress.finish(0)
        assertEquals(100, progress.snapshot().percent)
        assertEquals("geosite.dat", progress.snapshot().fileName)
    }
}
