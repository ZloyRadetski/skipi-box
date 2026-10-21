// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.subscription

import app.SubscriptionGroupState
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionProviderTrafficPresentationAndroidOracleTest {
    @Test
    fun unknownTrafficMetadataHasNoSummaryOrProgress() {
        val group = group(upload = -1L, download = -1L, total = -1L)

        assertNull(group.subscriptionTrafficSummary())
        assertNull(group.subscriptionTrafficProgress())
    }

    @Test
    fun summaryFormatsBytesAndFractionalKilobytesWithTheDefaultLocaleAndRestoresIt() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)

            assertEquals(
                "1.0 B / 2.0 B",
                group(upload = 1L, download = 0L, total = 2L).subscriptionTrafficSummary(),
            )
            assertEquals(
                "1.50 KB / 2.00 KB",
                group(upload = 512L, download = 1_024L, total = 2_048L).subscriptionTrafficSummary(),
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun usedTrafficSaturatesAtLongMaxValueInsteadOfWrapping() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            val group = group(upload = Long.MAX_VALUE, download = 1L, total = Long.MAX_VALUE)

            assertEquals("8388608.00 TB / ∞", group.subscriptionTrafficSummary())
            assertNull(group.subscriptionTrafficProgress())
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun unlimitedThresholdIsExclusiveAndFiniteProgressIsClamped() {
        val unlimitedThreshold = Long.MAX_VALUE / 2L
        val atThreshold = group(upload = 1L, download = 0L, total = unlimitedThreshold)
        val belowThreshold = group(upload = 1L, download = 0L, total = unlimitedThreshold - 1L)
        val overQuota = group(upload = 1_000L, download = 500L, total = 1_000L)
        val negativeUsage = group(upload = -1L, download = -2L, total = 100L)

        assertEquals("1.0 B / ∞", atThreshold.subscriptionTrafficSummary())
        assertNull(atThreshold.subscriptionTrafficProgress())
        assertEquals(1f, overQuota.subscriptionTrafficProgress()!!, 0f)
        assertEquals(0f, negativeUsage.subscriptionTrafficProgress()!!, 0f)
        assertNotNull(belowThreshold.subscriptionTrafficProgress())
    }

    private fun group(upload: Long, download: Long, total: Long) = SubscriptionGroupState(
        id = 1,
        name = "Provider",
        url = "https://provider.example/sub",
        userAgent = "test-agent",
        updateInterval = "24",
        enabled = true,
        trafficUploadBytes = upload,
        trafficDownloadBytes = download,
        trafficTotalBytes = total,
    )
}
