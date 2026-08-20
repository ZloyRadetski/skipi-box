// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogRetentionTest {

    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    @Test
    fun testCoreLogEntryIsOlderThanDays() {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24L * 60L * 60L * 1000L

        val recentTime = formatter.format(Date(now - 2 * 60 * 60 * 1000L)) // 2 hours ago
        val fiveDaysAgoTime = formatter.format(Date(now - 5 * oneDayMillis))
        val tenDaysAgoTime = formatter.format(Date(now - 10 * oneDayMillis))

        val recentEntry = CoreLogEntry(id = 1, time = recentTime, level = "info", message = "recent")
        val fiveDaysAgoEntry = CoreLogEntry(id = 2, time = fiveDaysAgoTime, level = "info", message = "5 days old")
        val tenDaysAgoEntry = CoreLogEntry(id = 3, time = tenDaysAgoTime, level = "info", message = "10 days old")

        // 7 days retention
        assertFalse(recentEntry.isOlderThanDays(7, now))
        assertFalse(fiveDaysAgoEntry.isOlderThanDays(7, now))
        assertTrue(tenDaysAgoEntry.isOlderThanDays(7, now))

        // 3 days retention
        assertFalse(recentEntry.isOlderThanDays(3, now))
        assertTrue(fiveDaysAgoEntry.isOlderThanDays(3, now))
        assertTrue(tenDaysAgoEntry.isOlderThanDays(3, now))

        // Unlimited retention (0 days)
        assertFalse(recentEntry.isOlderThanDays(0, now))
        assertFalse(fiveDaysAgoEntry.isOlderThanDays(0, now))
        assertFalse(tenDaysAgoEntry.isOlderThanDays(0, now))
    }

    @Test
    fun testInMemoryRepositoryPruneOlderThanDays() {
        val now = System.currentTimeMillis()
        val oneDayMillis = 24L * 60L * 60L * 1000L

        val repo = InMemoryCoreLogRepository()
        repo.append(level = "info", message = "old log", time = formatter.format(Date(now - 10 * oneDayMillis)))
        repo.append(level = "info", message = "medium log", time = formatter.format(Date(now - 4 * oneDayMillis)))
        repo.append(level = "info", message = "new log", time = formatter.format(Date(now - 1 * oneDayMillis)))

        assertEquals(3, repo.entries.value.size)

        repo.pruneOlderThanDays(7)
        assertEquals(2, repo.entries.value.size)
        assertEquals("medium log", repo.entries.value[0].message)
        assertEquals("new log", repo.entries.value[1].message)

        repo.pruneOlderThanDays(2)
        assertEquals(1, repo.entries.value.size)
        assertEquals("new log", repo.entries.value[0].message)
    }
}
