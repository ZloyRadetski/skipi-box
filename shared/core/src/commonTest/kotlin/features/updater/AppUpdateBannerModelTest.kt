// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdateBannerModelTest {
    private val update = AppUpdateInfo(
        versionName = "1.2.3",
        versionCode = 123,
        releaseTitle = "Release",
        changelog = "Changes",
        downloadUrl = "https://example.invalid/SKIPI.apk",
        assetName = "SKIPI.apk",
        apkSizeBytes = 5L * 1024 * 1024 + 512 * 1024,
        publishedAt = "",
    )

    @Test
    fun noBannerWithoutAnUpdateOrAfterItsVersionWasDismissed() {
        assertNull(AppUpdateState().bannerModel())
        assertNull(AppUpdateState(availableUpdate = update, dismissedVersion = "1.2.3").bannerModel())
        assertEquals("1.2.3", AppUpdateState(availableUpdate = update, dismissedVersion = "1.2.2").bannerModel()?.versionName)
    }

    @Test
    fun idleBannerOffersInstallWithSizeAndChangelog() {
        val banner = AppUpdateState(availableUpdate = update).bannerModel()!!

        assertFalse(banner.isDownloading)
        assertFalse(banner.isReadyToInstall)
        assertTrue(banner.actionEnabled)
        assertEquals("5.5 MB", banner.sizeText)
        assertTrue(banner.hasChangelog)
        assertNull(banner.progress)
    }

    @Test
    fun downloadingBannerShowsClampedProgressAndDisablesTheAction() {
        val state = AppUpdateState(
            availableUpdate = update,
            status = AppUpdateDownloadStatus.DOWNLOADING,
            downloadedBytes = 50,
            totalBytes = 200,
        )

        val banner = state.bannerModel()!!

        assertTrue(banner.isDownloading)
        assertFalse(banner.actionEnabled)
        assertEquals(0.25f, banner.progress)
        assertEquals(25, banner.progressPercent)
        assertEquals(1f, state.copy(downloadedBytes = 900).bannerModel()!!.progress)
        assertEquals(0f, state.copy(totalBytes = 0).bannerModel()!!.progress)
        assertNull(state.copy(totalBytes = 0).bannerModel()!!.progressPercent)
    }

    @Test
    fun readyAndFailedStatesAreReflected() {
        val ready = AppUpdateState(availableUpdate = update, status = AppUpdateDownloadStatus.READY_TO_INSTALL).bannerModel()!!
        val failed = AppUpdateState(
            availableUpdate = update,
            status = AppUpdateDownloadStatus.FAILED,
            error = "HTTP 500",
        ).bannerModel()!!
        val blankError = AppUpdateState(
            availableUpdate = update,
            status = AppUpdateDownloadStatus.FAILED,
            error = "  ",
        ).bannerModel()!!

        assertTrue(ready.isReadyToInstall)
        assertEquals("HTTP 500", failed.errorMessage)
        assertNull(blankError.errorMessage)
    }

    @Test
    fun unknownSizeHasNoSizeTextAndBlankNotesHideTheChangelogAction() {
        val banner = AppUpdateState(
            availableUpdate = update.copy(apkSizeBytes = 0, changelog = " "),
        ).bannerModel()!!

        assertNull(banner.sizeText)
        assertFalse(banner.hasChangelog)
    }
}
