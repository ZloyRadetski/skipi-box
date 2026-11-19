// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import java.io.File
import java.io.RandomAccessFile

internal fun readHevTunDiagnostics(logPath: String, maxChars: Int = 2048): String {
    if (logPath.isBlank()) return ""
    require(maxChars > 0)
    return runCatching {
        val file = File(logPath)
        if (!file.isFile) return ""
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            if (length <= 0L) return ""

            val maxBytes = (maxChars.toLong() * 4L + 4L).coerceAtMost(Int.MAX_VALUE.toLong())
            val start = (length - maxBytes).coerceAtLeast(0L)
            val byteCount = (length - start).toInt()
            val bytes = ByteArray(byteCount)
            input.seek(start)
            input.readFully(bytes)

            var tail = String(bytes, Charsets.UTF_8)
            if (start > 0L) {
                val firstLineEnd = tail.indexOf('\n')
                tail = if (firstLineEnd >= 0) tail.substring(firstLineEnd + 1) else ""
            }

            val safeLines = tail.lineSequence()
                .filterNot { line ->
                    line.contains("auth", ignoreCase = true) ||
                        line.contains("pass", ignoreCase = true) ||
                        line.contains("user:", ignoreCase = true)
                }
                .toList()
            val text = safeLines.joinToString("\n").trim()
            if (text.length <= maxChars) {
                text
            } else {
                val clipped = text.takeLast(maxChars)
                clipped.substringAfter('\n', clipped).trim()
            }
        }
    }.getOrDefault("")
}
