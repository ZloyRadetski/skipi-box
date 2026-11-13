// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package data

import features.logs.AppLog
import features.logs.AppLogLevel
import features.logs.AppLogSink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallationUuidTest {
    private val warnings = mutableListOf<String>()

    @After
    fun tearDown() = AppLog.reset()

    private fun captureLogs() {
        AppLog.install(object : AppLogSink {
            override fun log(level: AppLogLevel, tag: String, message: String, error: Throwable?, recordInApp: Boolean) {
                if (level == AppLogLevel.Warning) warnings += message
            }
        })
    }

    @Test
    fun noStoredValue_generatesWithoutWarning() {
        captureLogs()
        val persisted = mutableListOf<String>()
        val uuid = resolveInstallationUuid(stored = null, decrypt = { null }, persistNew = { persisted += it }, generate = { "new" })
        assertEquals("new", uuid)
        assertEquals(listOf("new"), persisted)
        assertEquals(emptyList<String>(), warnings)
    }

    @Test
    fun undecryptableStoredValue_warnsOnceAndStillGeneratesNew() {
        captureLogs()
        val persisted = mutableListOf<String>()
        val uuid = resolveInstallationUuid(stored = "garbage", decrypt = { null }, persistNew = { persisted += it }, generate = { "new" })
        assertEquals("new", uuid)
        assertEquals(listOf("new"), persisted)
        assertEquals(1, warnings.size)
    }

    @Test
    fun decryptableStoredValue_isReusedSilently() {
        captureLogs()
        val uuid = resolveInstallationUuid(stored = "enc", decrypt = { "old" }, persistNew = { error("must not persist") }, generate = { "new" })
        assertEquals("old", uuid)
        assertEquals(emptyList<String>(), warnings)
    }
}
