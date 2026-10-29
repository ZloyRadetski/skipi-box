// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Portable code logs through [AppLog]; each host plugs in its own destination. */
class AppLogTest {
    private class RecordingSink : AppLogSink {
        val entries = mutableListOf<Entry>()

        data class Entry(
            val level: AppLogLevel,
            val tag: String,
            val message: String,
            val error: Throwable?,
            val recordInApp: Boolean,
        )

        override fun log(level: AppLogLevel, tag: String, message: String, error: Throwable?, recordInApp: Boolean) {
            entries += Entry(level, tag, message, error, recordInApp)
        }
    }

    @AfterTest
    fun restoreDefaultSink() {
        AppLog.reset()
    }

    @Test
    fun everyLevelReachesTheInstalledSinkWithItsArguments() {
        val sink = RecordingSink()
        AppLog.install(sink)
        val failure = IllegalStateException("boom")

        AppLog.debug("A", "d")
        AppLog.info("B", "i")
        AppLog.warn("C", "w", failure)
        AppLog.error("D", "e", failure)

        assertEquals(
            listOf(
                RecordingSink.Entry(AppLogLevel.Debug, "A", "d", null, true),
                RecordingSink.Entry(AppLogLevel.Info, "B", "i", null, true),
                RecordingSink.Entry(AppLogLevel.Warning, "C", "w", failure, true),
                RecordingSink.Entry(AppLogLevel.Error, "D", "e", failure, true),
            ),
            sink.entries,
        )
    }

    @Test
    fun platformWarningsAreKeptOutOfTheInAppViewer() {
        val sink = RecordingSink()
        AppLog.install(sink)

        AppLog.platformWarn("Tag", "only for the platform log")

        val entry = sink.entries.single()
        assertEquals(AppLogLevel.Warning, entry.level)
        assertEquals(false, entry.recordInApp)
        assertNull(entry.error)
    }

    @Test
    fun logsBeforeAnyHostInstalledASinkAreHarmless() {
        AppLog.reset()

        AppLog.error("Early", "no sink yet", IllegalStateException("x"))
        AppLog.platformWarn("Early", "still fine")
    }

    @Test
    fun installingReplacesThePreviousSinkAndResetRestoresTheDefault() {
        val first = RecordingSink()
        val second = RecordingSink()
        AppLog.install(first)
        AppLog.install(second)

        AppLog.info("T", "m")

        assertTrue(first.entries.isEmpty())
        assertEquals(1, second.entries.size)
        AppLog.reset()
        assertSame(AppLog.defaultSink, AppLog.sink)
    }
}
