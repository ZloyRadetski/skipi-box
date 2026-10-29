// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateStateTest {
    private val update = AppUpdateInfo(
        versionName = "1.2.3",
        versionCode = 123,
        releaseTitle = "Release",
        changelog = "Changes",
        downloadUrl = "https://example.invalid/SKIPI.apk",
        assetName = "SKIPI.apk",
        apkSizeBytes = 10L,
        publishedAt = "2026-09-15T00:00:00Z",
    )

    @Test
    fun releaseIdentityIgnoresNotesButRejectsADifferentAsset() {
        assertTrue(update.isSameReleaseAs(update.copy(changelog = "Corrected notes")))
        assertFalse(update.isSameReleaseAs(update.copy(assetName = "SKIPI-arm64.apk")))
        assertFalse(update.isSameReleaseAs(update.copy(versionCode = 124, versionName = "1.2.4")))
    }

    @Test
    fun discoveredReleaseResetsDismissalAndTransfer() {
        val stale = AppUpdateState(
            dismissedVersion = "1.2.2",
            status = AppUpdateDownloadStatus.FAILED,
            downloadedBytes = 5,
            totalBytes = 9,
            apkFilePath = "/old.apk",
            error = "boom",
            isAutomatic = true,
        )

        assertEquals(AppUpdateState(availableUpdate = update), stale.withAvailableUpdate(update))
    }

    @Test
    fun queuedDownloadKeepsTheDismissalOnlyForTheSameRelease() {
        val dismissed = AppUpdateState(availableUpdate = update, dismissedVersion = "1.2.3")

        val sameRelease = dismissed.withQueuedDownload(update.copy(changelog = "x"), automatic = true)
        val otherRelease = dismissed.withQueuedDownload(update.copy(versionName = "1.2.4", versionCode = 124), automatic = false)

        assertEquals("1.2.3", sameRelease.dismissedVersion)
        assertEquals(AppUpdateDownloadStatus.QUEUED, sameRelease.status)
        assertEquals(10L, sameRelease.totalBytes)
        assertTrue(sameRelease.isAutomatic)
        assertEquals("", otherRelease.dismissedVersion)
        assertFalse(otherRelease.isAutomatic)
    }

    @Test
    fun transferMarkersOnlyApplyToTheCurrentRelease() {
        val state = AppUpdateState(availableUpdate = update, status = AppUpdateDownloadStatus.QUEUED)
        val stale = update.copy(versionName = "1.0.0", versionCode = 100)

        assertEquals(state, state.markDownloading(stale))
        assertEquals(state, state.markProgress(stale, 5, 10))
        assertEquals(state, state.markReady(stale, "/x.apk"))
        assertEquals(state, state.markFailed(stale, "no"))
        assertEquals(AppUpdateDownloadStatus.DOWNLOADING, state.markDownloading(update).status)
    }

    @Test
    fun progressClampsAndReadyKeepsTheLargerByteCount() {
        val state = AppUpdateState(availableUpdate = update, totalBytes = 10)

        val progressing = state.markProgress(update, downloadedBytes = -5, totalBytes = 10)
        val ready = state.copy(downloadedBytes = 3).markReady(update, "/ok.apk")

        assertEquals(0L, progressing.downloadedBytes)
        assertEquals(AppUpdateDownloadStatus.READY_TO_INSTALL, ready.status)
        assertEquals(10L, ready.downloadedBytes)
        assertEquals("/ok.apk", ready.apkFilePath)
        assertNull(ready.error)
    }

    @Test
    fun failureKeepsTheReleaseAndRecordsTheMessage() {
        val failed = AppUpdateState(availableUpdate = update).markFailed(update, "HTTP 500")

        assertEquals(AppUpdateDownloadStatus.FAILED, failed.status)
        assertEquals("HTTP 500", failed.error)
        assertEquals(update, failed.availableUpdate)
    }

    @Test
    fun checkResultTransitionsDecideWhetherToNotify() {
        val none = AppUpdateState()

        val first = none.applyCheckedRelease(update)
        val repeated = first.state.applyCheckedRelease(update.copy(changelog = "Corrected"))
        val dismissedThenNew = first.state.copy(dismissedVersion = "1.2.3")
            .applyCheckedRelease(update.copy(versionName = "1.2.4", versionCode = 124))
        val dismissedSame = AppUpdateState(availableUpdate = update, dismissedVersion = "1.2.3")
            .applyCheckedRelease(update.copy(versionName = "1.2.3", assetName = "other.apk"))
        val upToDate = first.state.applyCheckedRelease(null)

        assertTrue(first.notify)
        assertFalse(repeated.notify)
        assertEquals("Corrected", repeated.state.availableUpdate?.changelog)
        assertTrue(dismissedThenNew.notify)
        // A different asset of a dismissed version is a new release: the dismissal resets, so it notifies.
        assertTrue(dismissedSame.notify)
        assertEquals("", dismissedSame.state.dismissedVersion)
        assertEquals(AppUpdateState(), upToDate.state)
        assertFalse(upToDate.notify)
    }

    @Test
    fun restoreResumesQueuedTransfersAndResetsAMissingApk() {
        val queued = AppUpdateState(availableUpdate = update, status = AppUpdateDownloadStatus.DOWNLOADING, isAutomatic = true)
        val ready = AppUpdateState(availableUpdate = update, status = AppUpdateDownloadStatus.READY_TO_INSTALL, apkFilePath = "/a.apk")

        assertEquals(AppUpdateRestoreAction.Resume(automatic = true), queued.restoreAction(apkFileExists = false))
        assertEquals(AppUpdateRestoreAction.None, ready.restoreAction(apkFileExists = true))
        assertEquals(AppUpdateRestoreAction.ResetToAvailable, ready.restoreAction(apkFileExists = false))
        assertEquals(AppUpdateRestoreAction.None, AppUpdateState().restoreAction(apkFileExists = false))
        assertEquals(
            AppUpdateRestoreAction.None,
            AppUpdateState(availableUpdate = update, status = AppUpdateDownloadStatus.FAILED).restoreAction(false),
        )
    }
}
