// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CoreMemoryStats(
    val allocBytes: Long = 0L,
    val allocMb: String = "",
    val sysBytes: Long = 0L,
    val sysMb: String = "",
    val heapInuse: Long = 0L,
    val heapIdle: Long = 0L,
    val heapReleased: Long = 0L,
    val numGoroutines: Int = 0,
    val numGc: Long = 0L,
)

private val memoryJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun parseCoreMemoryStats(raw: String): CoreMemoryStats? {
    if (raw.isBlank()) return null
    return runCatching { memoryJson.decodeFromString<CoreMemoryStats>(raw) }.getOrNull()
}

/**
 * The core's own memory as the home tile shows it: what the Go runtime holds from the OS (everything it
 * mapped minus the heap it gave back), or null when the core gave no figure. Live objects alone leave out
 * free heap not yet returned, goroutine stacks and runtime metadata, and jump with every GC. A core that
 * reports no usable total falls back to its live heap.
 */
fun coreMemoryKilobytes(raw: String): Long? {
    val stats = parseCoreMemoryStats(raw) ?: return null
    val held = stats.sysBytes - stats.heapReleased
    val bytes = if (held > stats.allocBytes) held else stats.allocBytes
    return bytes.takeIf { it > 0L }?.div(1_024L)
}
