// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.updater.runtime

import androidx.work.NetworkType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AppUpdateDownloadConstraintsTest {
    /**
     * Android calls a network connected only after its own connectivity probe passes. Where that probe is blocked,
     * a download the user asked for would stay queued at 0% for good, so it must not wait for the system's opinion.
     */
    @Test
    fun aDownloadTheUserAskedForStartsAtOnce() {
        assertEquals(NetworkType.NOT_REQUIRED, appUpdateDownloadConstraints(automatic = false).requiredNetworkType)
    }

    @Test
    fun anAutomaticDownloadStillWaitsForUnmeteredWifiAndACharger() {
        val constraints = appUpdateDownloadConstraints(automatic = true)

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresCharging())
    }
}
