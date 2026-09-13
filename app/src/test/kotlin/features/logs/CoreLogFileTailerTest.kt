// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class CoreLogFileTailerTest {

    @Test
    fun startup_mode_reads_lines_written_before_the_tailer_coroutine_runs() = runBlocking {
        val logFile = File.createTempFile("skipi-core-log", ".log")
        val repository = InMemoryCoreLogRepository()
        val tailer = CoreLogFileTailer(
            logFiles = listOf(CoreLogFile(path = logFile.absolutePath, defaultLevel = "error")),
            repository = repository,
            startAtEnd = false,
        )
        try {
            logFile.writeText("2026/09/11 20:40:05 [Info] first DNS event\n")
            tailer.start()

            val entries = withTimeout(2_000) {
                repository.entries.first { current -> current.isNotEmpty() }
            }
            assertTrue(entries.single().message.endsWith("[Info] first DNS event"))
        } finally {
            tailer.stop()
            logFile.delete()
        }
    }
}
