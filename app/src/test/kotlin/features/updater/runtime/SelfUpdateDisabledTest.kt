// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.AppState
import app.skipi.app.store.InMemoryAppStateStore
import features.updater.AppUpdateChannel
import features.updater.AppUpdateDownloadStatus
import features.updater.AppUpdateInfo
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A build without self-update (F-Droid) must never ask for, download or keep an update. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SelfUpdateDisabledTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val update = AppUpdateInfo(
        versionName = "9.9.9", versionCode = 9999, releaseTitle = "r", changelog = "",
        downloadUrl = "https://example.invalid/a.apk", assetName = "a.apk", apkSizeBytes = 10L, publishedAt = "",
    )

    @Test
    fun aReleaseCheckAnswersUpToDateWithoutAskingTheServer() = runBlocking {
        val store = InMemoryAppStateStore()
        val coordinator = AppUpdateCheckCoordinator(context, store, enabled = false)

        assertEquals(AppUpdateCheckOutcome.UpToDate, coordinator.checkLatestRelease())
        assertTrue(AppUpdateCheckStore(context, AppUpdateChannel.Stable).isDue(), "no check may be recorded")
        assertEquals(0, store.persistedUpdateCount)
    }

    @Test
    fun aDownloadRequestIsIgnored() {
        val store = InMemoryAppStateStore(AppState(availableAppUpdate = update))
        val coordinator = AppUpdateDownloadCoordinator(context, store, enabled = false)

        coordinator.enqueue(update, automatic = false)
        coordinator.restorePendingDownload()

        assertEquals(AppUpdateDownloadStatus.IDLE, store.currentState.appUpdateDownloadStatus)
        assertEquals(0, store.persistedUpdateCount)
    }

    @Test
    fun whatAnEarlierBuildLeftBehindIsCancelledAndForgotten() {
        val store = InMemoryAppStateStore(
            AppState(
                availableAppUpdate = update,
                appUpdateDownloadStatus = AppUpdateDownloadStatus.READY_TO_INSTALL,
                appUpdateApkFilePath = "/data/updates/a.apk",
            ),
        )
        var cancelled = 0

        retireSelfUpdate(store) { cancelled++ }

        assertEquals(1, cancelled)
        assertEquals(null, store.currentState.availableAppUpdate)
        assertEquals(null, store.currentState.appUpdateApkFilePath)
        assertEquals(AppUpdateDownloadStatus.IDLE, store.currentState.appUpdateDownloadStatus)
    }
}
