// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import engine.hevtun.HevSocks5TunnelConfig
import engine.hevtun.writeConfigFile
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile

internal class HevTunRuntime(
    private val nativeGateway: HevTunNativeGateway = HevTunNative,
    private val readinessTimeoutMillis: Long = HevTunReadinessTimeoutMillis,
    private val readinessPollIntervalMillis: Long = HevTunReadinessPollIntervalMillis,
) {
    private var nativeStartRequested = false

    init {
        require(readinessTimeoutMillis > 0L)
        require(readinessPollIntervalMillis > 0L)
    }

    suspend fun start(config: HevSocks5TunnelConfig, tunFd: Int) {
        stop()
        config.writeConfigFile()
        check(nativeGateway.startService(config.configPath, tunFd)) {
            val diagnostics = readHevTunDiagnostics(config.logPath)
            if (diagnostics.isNotBlank()) {
                "Failed to start Hev TUN native service: $diagnostics"
            } else {
                "Failed to start Hev TUN native service"
            }
        }
        nativeStartRequested = true
        try {
            val ready = awaitHevTunReadiness(
                isRunning = nativeGateway::isRunning,
                isReady = nativeGateway::isReady,
                timeoutMillis = readinessTimeoutMillis,
                pollIntervalMillis = readinessPollIntervalMillis,
            )
            check(ready) {
                val diagnostics = readHevTunDiagnostics(config.logPath)
                if (diagnostics.isNotBlank()) {
                    "Hev TUN native service did not become ready: $diagnostics"
                } else {
                    "Hev TUN native service did not become ready (running=${nativeGateway.isRunning()})"
                }
            }
        } catch (error: Throwable) {
            runCatching { stop() }
            throw error
        }
    }

    fun stop() {
        if (!nativeStartRequested) return
        try {
            check(nativeGateway.stopService()) {
                "Failed to stop Hev TUN native service"
            }
        } finally {
            nativeStartRequested = false
        }
    }

    fun isRunning(): Boolean = nativeGateway.isRunning()

    fun getStats(): LongArray = nativeGateway.getStats()
}

internal const val HevTunReadinessTimeoutMillis = 5_000L
private const val HevTunReadinessPollIntervalMillis = 25L

internal suspend fun awaitHevTunReadiness(
    isRunning: () -> Boolean,
    isReady: () -> Boolean,
    timeoutMillis: Long = HevTunReadinessTimeoutMillis,
    pollIntervalMillis: Long = HevTunReadinessPollIntervalMillis,
): Boolean {
    require(timeoutMillis > 0L)
    require(pollIntervalMillis > 0L)
    return withTimeoutOrNull(timeoutMillis) {
        while (isRunning()) {
            currentCoroutineContext().ensureActive()
            if (isReady()) return@withTimeoutOrNull true
            delay(pollIntervalMillis)
        }
        false
    } ?: false
}

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
