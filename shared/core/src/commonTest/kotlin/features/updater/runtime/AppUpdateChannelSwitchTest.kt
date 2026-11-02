// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import app.AppState
import features.updater.AppUpdateChannel
import features.updater.AppUpdateDownloadStatus
import features.updater.AppUpdateInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppUpdateChannelSwitchTest {
    private val update = AppUpdateInfo(
        versionName = "0.5.1-rc.2",
        versionCode = 590,
        releaseTitle = "rc2",
        changelog = "",
        downloadUrl = "https://example.invalid/u.apk",
        assetName = "SKIPI-universal.apk",
        apkSizeBytes = 10L,
        publishedAt = "2026-10-01T00:00:00Z",
    )

    @Test
    fun everyInstallStartsOnTheStableChannel() {
        assertEquals(AppUpdateChannel.Stable, AppState().appUpdateChannel)
    }

    @Test
    fun switchingChannelDropsTheOfferFoundOnTheOldOne() {
        val state = AppState(
            appUpdateChannel = AppUpdateChannel.Nightly,
            availableAppUpdate = update,
            dismissedUpdateVersion = "0.5.1-rc.1",
            appUpdateDownloadStatus = AppUpdateDownloadStatus.FAILED,
            appUpdateDownloadError = "offline",
        )

        val switched = state.withAppUpdateChannel(AppUpdateChannel.Stable)

        assertEquals(AppUpdateChannel.Stable, switched.appUpdateChannel)
        assertNull(switched.availableAppUpdate)
        assertEquals("", switched.dismissedUpdateVersion)
        assertEquals(AppUpdateDownloadStatus.IDLE, switched.appUpdateDownloadStatus)
        assertNull(switched.appUpdateDownloadError)
    }

    @Test
    fun choosingTheCurrentChannelAgainChangesNothing() {
        val state = AppState(appUpdateChannel = AppUpdateChannel.Nightly, availableAppUpdate = update)

        assertSame(state, state.withAppUpdateChannel(AppUpdateChannel.Nightly))
    }
}
