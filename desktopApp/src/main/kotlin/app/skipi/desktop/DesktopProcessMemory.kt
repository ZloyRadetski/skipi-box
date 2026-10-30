// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.util.concurrent.TimeUnit

/**
 * Resident memory of this process in kilobytes, or null where the platform gives no answer.
 * The Go core runs inside the app process, so this is the tunnel's memory footprint on Desktop.
 */
internal fun currentProcessResidentKb(): Long? = runCatching {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    when {
        "win" in os -> windowsWorkingSetKb()
        "linux" in os -> parseLinuxVmRssKb(java.io.File("/proc/self/status").readText())
        else -> psResidentKb()
    }
}.getOrNull()

internal fun parseLinuxVmRssKb(status: String): Long? =
    status.lineSequence()
        .firstOrNull { it.startsWith("VmRSS:") }
        ?.removePrefix("VmRSS:")
        ?.trim()
        ?.substringBefore(' ')
        ?.toLongOrNull()

internal fun parsePsRssKb(output: String): Long? = output.trim().toLongOrNull()

private fun psResidentKb(): Long? {
    val process = ProcessBuilder("ps", "-o", "rss=", "-p", ProcessHandle.current().pid().toString())
        .redirectErrorStream(true)
        .start()
    val text = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(2, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return null
    }
    return parsePsRssKb(text)
}

private const val ProcessMemoryCountersSize = 72L
private const val WorkingSetSizeOffset = 16L

private val windowsMemoryInfo: MethodHandle by lazy {
    val linker = Linker.nativeLinker()
    val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
    linker.downcallHandle(
        kernel32.find("K32GetProcessMemoryInfo").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
    )
}

private val windowsCurrentProcess: MethodHandle by lazy {
    val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
    Linker.nativeLinker().downcallHandle(
        kernel32.find("GetCurrentProcess").orElseThrow(),
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
}

private fun windowsWorkingSetKb(): Long? = Arena.ofConfined().use { arena ->
    val counters = arena.allocate(ProcessMemoryCountersSize)
    counters.set(ValueLayout.JAVA_INT, 0, ProcessMemoryCountersSize.toInt())
    val process = windowsCurrentProcess.invoke() as java.lang.foreign.MemorySegment
    val ok = windowsMemoryInfo.invoke(process, counters, ProcessMemoryCountersSize.toInt()) as Int
    if (ok == 0) null else counters.get(ValueLayout.JAVA_LONG, WorkingSetSizeOffset) / 1024L
}
