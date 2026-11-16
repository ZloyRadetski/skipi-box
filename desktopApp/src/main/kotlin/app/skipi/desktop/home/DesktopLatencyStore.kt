// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Keeps the last measured latency of every server across restarts, so the ping badges and the sort by
 * ping survive closing the app. One line per server: `id<TAB>ok<TAB>ms`, `id<TAB>timeout` or
 * `id<TAB>error<TAB>message`. A damaged line is skipped; a failed write leaves the previous file.
 */
internal class DesktopLatencyStore(private val directory: Path) {
    private val file: Path get() = directory.resolve(FileName)

    fun load(): Map<Int, DesktopServerLatencyResult> = runCatching {
        if (!Files.isRegularFile(file)) return emptyMap()
        buildMap {
            Files.readAllLines(file).forEach { line ->
                val parts = line.split('\t', limit = 3)
                val id = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val result = when (parts.getOrNull(1)) {
                    "ok" -> parts.getOrNull(2)?.toLongOrNull()?.let(DesktopServerLatencyResult::Success)
                    "timeout" -> DesktopServerLatencyResult.Timeout
                    "error" -> DesktopServerLatencyResult.Error(parts.getOrNull(2).orEmpty())
                    else -> null
                }
                if (result != null) put(id, result)
            }
        }
    }.getOrDefault(emptyMap())

    fun save(latencyByServerId: Map<Int, DesktopServerLatencyResult>): Boolean = runCatching {
        Files.createDirectories(directory)
        val lines = latencyByServerId.mapNotNull { (id, result) ->
            when (result) {
                is DesktopServerLatencyResult.Success -> "$id\tok\t${result.milliseconds}"
                DesktopServerLatencyResult.Timeout -> "$id\ttimeout"
                is DesktopServerLatencyResult.Error -> "$id\terror\t${result.message.replace('\n', ' ').replace('\t', ' ')}"
            }
        }
        val temp = directory.resolve("$FileName.tmp")
        Files.write(temp, lines)
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
        true
    }.getOrDefault(false)

    private companion object {
        const val FileName = "latency.txt"
    }
}
