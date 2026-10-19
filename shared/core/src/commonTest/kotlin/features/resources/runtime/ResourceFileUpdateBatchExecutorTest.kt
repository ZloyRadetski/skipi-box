// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileDownloadCancelledException
import features.resources.ResourceFileKind
import features.resources.ResourceFileScope
import features.resources.ResourceFileUpdateDownload
import features.resources.ResourceFileUpdateDownloadPlan
import features.resources.ResourceFilesStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResourceFileUpdateBatchExecutorTest {
    @Test
    fun emptyPlanReadsStatusWithoutPreparingSessionOrTouchingOtherCallbacks() {
        val events = mutableListOf<String>()
        val expectedStatus = ResourceFilesStatus(geoIp = existingStatus())
        val result = executeResourceFileUpdateBatch(
            plan = plan(downloads = emptyList()),
            currentStatus = {
                events += "status"
                expectedStatus
            },
            prepareSession = {
                events += "prepare"
                error("empty batch must not prepare a session")
            },
            markSource = { source -> events += "mark:$source" },
            mapCancellationError = { error -> error },
        )

        assertEquals(expectedStatus, result)
        assertEquals(listOf("status"), events)
    }

    @Test
    fun preparationFailureEscapesBeforeBatchFailureReporting() {
        val preparationFailure = IllegalStateException("proxy setup failed")
        val events = mutableListOf<String>()
        val error = assertFailsWith<IllegalStateException> {
            executeResourceFileUpdateBatch(
                plan = plan(downloads = sampleDownloads()),
                currentStatus = {
                    events += "status"
                    ResourceFilesStatus()
                },
                prepareSession = {
                    events += "prepare"
                    throw preparationFailure
                },
                markSource = { source -> events += "mark:$source" },
                mapCancellationError = { cancellation -> cancellation },
            )
        }

        assertSame(preparationFailure, error)
        assertEquals(listOf("prepare"), events)
    }

    @Test
    fun successfulBatchUpdatesInOrderThenReadsStatusMarksRawSourceAndNotifies() {
        val events = mutableListOf<String>()
        val expectedStatus = ResourceFilesStatus(geoIp = existingStatus(sizeBytes = 4))
        val downloads = sampleDownloads()
        val seenDownloads = mutableListOf<ResourceFileUpdateDownload>()
        val result = executeResourceFileUpdateBatch(
            plan = plan(downloads = downloads, source = 999),
            currentStatus = {
                events += "status"
                expectedStatus
            },
            prepareSession = {
                events += "prepare"
                ResourceFileUpdateBatchSession(
                    updateFile = { download, index, count ->
                        seenDownloads += download
                        events += "update:$index/$count:${downloadName(download)}"
                    },
                    notifyComplete = { events += "complete" },
                    reportFailure = { error -> events += "failure:${error.message}" },
                )
            },
            markSource = { source -> events += "mark:$source" },
            mapCancellationError = { cancellation -> cancellation },
        )

        assertEquals(expectedStatus, result)
        assertEquals(downloads, seenDownloads)
        assertEquals(
            listOf(
                "prepare",
                "update:0/3:${ResourceFileKind.GeoIp.displayName}",
                "update:1/3:${ResourceFileKind.GeoSite.displayName}",
                "update:2/3:custom-route.dat",
                "status",
                "mark:999",
                "complete",
            ),
            events,
        )
    }

    @Test
    fun successfulSingleFilePlanDoesNotMarkSourceButStillNotifiesCompletion() {
        val events = mutableListOf<String>()
        val singlePlan = plan(
            downloads = listOf<ResourceFileUpdateDownload>(sampleDownloads().first()),
            source = 17,
            markResourceFileSourceAfterSuccess = false,
        )

        executeResourceFileUpdateBatch(
            plan = singlePlan,
            currentStatus = {
                events += "status"
                ResourceFilesStatus()
            },
            prepareSession = {
                ResourceFileUpdateBatchSession(
                    updateFile = { _, index, count -> events += "update:$index/$count" },
                    notifyComplete = { events += "complete" },
                    reportFailure = { error -> events += "failure:${error.message}" },
                )
            },
            markSource = { source -> events += "mark:$source" },
            mapCancellationError = { cancellation -> cancellation },
        )

        assertEquals(listOf("update:0/1", "status", "complete"), events)
    }

    @Test
    fun fileFailureIsWrappedWithDisplayNameAndStopsTheBatch() {
        val cause = IllegalStateException("connection reset")
        val events = mutableListOf<String>()
        var reported: Throwable? = null
        val error = assertFailsWith<ResourceFileDownloadFailedException> {
            executeResourceFileUpdateBatch(
                plan = plan(downloads = sampleDownloads()),
                currentStatus = {
                    events += "status"
                    ResourceFilesStatus()
                },
                prepareSession = {
                    ResourceFileUpdateBatchSession(
                        updateFile = { _, index, _ ->
                            events += "update:$index"
                            if (index == 1) throw cause
                        },
                        notifyComplete = { events += "complete" },
                        reportFailure = { failure ->
                            events += "report"
                            reported = failure
                        },
                    )
                },
                markSource = { source -> events += "mark:$source" },
                mapCancellationError = { cancellation ->
                    events += "map-cancellation"
                    cancellation
                },
            )
        }

        assertEquals("${ResourceFileKind.GeoSite.displayName}: connection reset", error.message)
        assertSame(cause, error.cause)
        assertSame(error, reported)
        assertEquals(listOf("update:0", "update:1", "report"), events)
    }

    @Test
    fun cancellationIsReportedThenMappedWithoutWrappingOrContinuing() {
        val rawCancellation = ResourceFileDownloadCancelledException("cancelled")
        val mappedCancellation = ResourceFileDownloadCancelledException("localized cancellation")
        val events = mutableListOf<String>()
        var reported: Throwable? = null
        var mappedInput: ResourceFileDownloadCancelledException? = null
        val error = assertFailsWith<ResourceFileDownloadCancelledException> {
            executeResourceFileUpdateBatch(
                plan = plan(downloads = sampleDownloads(), source = 999),
                currentStatus = {
                    events += "status"
                    ResourceFilesStatus()
                },
                prepareSession = {
                    ResourceFileUpdateBatchSession(
                        updateFile = { _, index, _ ->
                            events += "update:$index"
                            if (index == 1) throw rawCancellation
                        },
                        notifyComplete = { events += "complete" },
                        reportFailure = { failure ->
                            events += "report"
                            reported = failure
                        },
                    )
                },
                markSource = { source -> events += "mark:$source" },
                mapCancellationError = { cancellation ->
                    events += "map"
                    mappedInput = cancellation
                    mappedCancellation
                },
            )
        }

        assertSame(rawCancellation, reported)
        assertSame(rawCancellation, mappedInput)
        assertSame(mappedCancellation, error)
        assertEquals("localized cancellation", error.message)
        assertEquals(listOf("update:0", "update:1", "report", "map"), events)
    }

    @Test
    fun statusFailureIsReportedUnwrappedAndPreventsMarkAndCompletion() {
        val statusFailure = IllegalStateException("status read failed")
        val events = mutableListOf<String>()
        var reported: Throwable? = null
        val error = assertFailsWith<IllegalStateException> {
            executeResourceFileUpdateBatch(
                plan = plan(downloads = sampleDownloads()),
                currentStatus = {
                    events += "status"
                    throw statusFailure
                },
                prepareSession = {
                    ResourceFileUpdateBatchSession(
                        updateFile = { _, index, _ -> events += "update:$index" },
                        notifyComplete = { events += "complete" },
                        reportFailure = { failure ->
                            events += "report"
                            reported = failure
                        },
                    )
                },
                markSource = { source -> events += "mark:$source" },
                mapCancellationError = { cancellation -> cancellation },
            )
        }

        assertSame(statusFailure, error)
        assertSame(statusFailure, reported)
        assertEquals(
            listOf("update:0", "update:1", "update:2", "status", "report"),
            events,
        )
    }

    @Test
    fun sourceMarkerFailureEscapesAfterStatusWithoutFailureCallbackOrCompletion() {
        val markerFailure = IllegalStateException("marker write failed")
        val events = mutableListOf<String>()
        val error = assertFailsWith<IllegalStateException> {
            executeResourceFileUpdateBatch(
                plan = plan(downloads = sampleDownloads(), source = 999),
                currentStatus = {
                    events += "status"
                    ResourceFilesStatus()
                },
                prepareSession = {
                    ResourceFileUpdateBatchSession(
                        updateFile = { _, index, _ -> events += "update:$index" },
                        notifyComplete = { events += "complete" },
                        reportFailure = { failure -> events += "report:${failure.message}" },
                    )
                },
                markSource = { source ->
                    events += "mark:$source"
                    throw markerFailure
                },
                mapCancellationError = { cancellation -> cancellation },
            )
        }

        assertSame(markerFailure, error)
        assertEquals(
            listOf("update:0", "update:1", "update:2", "status", "mark:999"),
            events,
        )
    }

    @Test
    fun completionNotificationFailureIsSwallowedAfterSuccessfulMark() {
        val notificationFailure = IllegalStateException("notification unavailable")
        val events = mutableListOf<String>()
        val expectedStatus = ResourceFilesStatus(geoSite = existingStatus(sizeBytes = 8))
        val result = executeResourceFileUpdateBatch(
            plan = plan(downloads = sampleDownloads(), source = 999),
            currentStatus = {
                events += "status"
                expectedStatus
            },
            prepareSession = {
                ResourceFileUpdateBatchSession(
                    updateFile = { _, index, _ -> events += "update:$index" },
                    notifyComplete = {
                        events += "complete"
                        throw notificationFailure
                    },
                    reportFailure = { failure -> events += "report:${failure.message}" },
                )
            },
            markSource = { source -> events += "mark:$source" },
            mapCancellationError = { cancellation -> cancellation },
        )

        assertEquals(expectedStatus, result)
        assertEquals(
            listOf("update:0", "update:1", "update:2", "status", "mark:999", "complete"),
            events,
        )
    }

    @Test
    fun failureReporterExceptionIsNotSwallowed() {
        val downloadCause = IllegalStateException("download failed")
        val reporterFailure = IllegalArgumentException("reporter failed")
        val events = mutableListOf<String>()
        var reported: Throwable? = null
        val error = assertFailsWith<IllegalArgumentException> {
            executeResourceFileUpdateBatch(
                plan = plan(downloads = sampleDownloads()),
                currentStatus = { ResourceFilesStatus() },
                prepareSession = {
                    ResourceFileUpdateBatchSession(
                        updateFile = { _, index, _ ->
                            events += "update:$index"
                            if (index == 0) throw downloadCause
                        },
                        notifyComplete = { events += "complete" },
                        reportFailure = { failure ->
                            events += "report"
                            reported = failure
                            throw reporterFailure
                        },
                    )
                },
                markSource = { source -> events += "mark:$source" },
                mapCancellationError = { cancellation -> cancellation },
            )
        }

        assertSame(reporterFailure, error)
        assertTrue(reported is ResourceFileDownloadFailedException)
        assertSame(downloadCause, reported?.cause)
        assertEquals(listOf("update:0", "report"), events)
    }

    private fun sampleDownloads(): List<ResourceFileUpdateDownload> = listOf(
        ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoIp, "https://example.test/geoip"),
        ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoSite, "https://example.test/geosite"),
        ResourceFileUpdateDownload.Custom(
            TrafficConfigCustomResourceFile(
                id = 7,
                name = "custom-route.dat",
                url = "https://example.test/custom-route",
            ),
            "https://example.test/custom-route",
        ),
    )

    private fun plan(
        downloads: List<ResourceFileUpdateDownload>,
        source: Int = 999,
        markResourceFileSourceAfterSuccess: Boolean = true,
    ) = ResourceFileUpdateDownloadPlan(
        scope = ResourceFileScope(trafficConfigId = 42, resourceFileSource = source),
        downloads = downloads,
        markResourceFileSourceAfterSuccess = markResourceFileSourceAfterSuccess,
    )

    private fun downloadName(download: ResourceFileUpdateDownload): String = when (download) {
        is ResourceFileUpdateDownload.BuiltIn -> download.kind.displayName
        is ResourceFileUpdateDownload.Custom -> download.file.name
    }

    private fun existingStatus(sizeBytes: Long = 4) = features.resources.ResourceFileStatus(
        exists = true,
        sizeBytes = sizeBytes,
        updatedAtMillis = 123,
    )
}
