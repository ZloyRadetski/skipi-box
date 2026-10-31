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

/** The core's own memory as the home tile shows it: its live Go heap, or null when the core gave no figure. */
fun coreMemoryKilobytes(raw: String): Long? =
    parseCoreMemoryStats(raw)?.allocBytes?.takeIf { it > 0L }?.div(1_024L)
