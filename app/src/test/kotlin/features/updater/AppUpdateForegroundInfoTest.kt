// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AppUpdateForegroundInfoTest {
    @Test
    fun theDownloadWorkerStartsItsForegroundServiceWithTheDataSyncType() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val update = AppUpdateInfo(
            versionName = "0.5.1", versionCode = 600, releaseTitle = "r", changelog = "",
            downloadUrl = "https://example.invalid/a.apk", assetName = "a.apk", apkSizeBytes = 10L, publishedAt = "",
        )

        val info = AppUpdateNotifier.foregroundInfo(context, update, downloadedBytes = 1L, totalBytes = 10L)

        // Without a type the system refuses the service for an app targeting Android 14 or newer, and the app crashes at launch.
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
    }
}
