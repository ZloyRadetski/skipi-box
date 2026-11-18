// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.io.File
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory
import javax.management.ObjectName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ████████████████████████████████████████████████████████████████████████████████████████████████████████████████
// ██                                                                                                            ██
// ██   DEBUG MEMORY TRACE - MUST BE SWITCHED OFF BEFORE A RELEASE.                                              ██
// ██                                                                                                            ██
// ██   BEFORE SHIPPING A RELEASE:                                                                               ██
// ██     1. set DesktopMemoryTraceEnabled below to false;                                                       ██
// ██     2. delete "-XX:NativeMemoryTracking=summary" from jvmArgs in desktopApp/build.gradle.kts               ██
// ██        (it costs CPU and memory of its own);                                                               ██
// ██     3. delete "jdk.management" from modules(...) in the same file if nothing else needs it.                ██
// ██                                                                                                            ██
// ██   While it is on, the app writes memory-trace.log (JVM heap, native memory by category, threads, process    ██
// ██   working set and private bytes, the core's heap) into %LOCALAPPDATA%\SKIPI.                               ██
// ██                                                                                                            ██
// ████████████████████████████████████████████████████████████████████████████████████████████████████████████████
internal const val DesktopMemoryTraceEnabled = true

internal const val DesktopMemoryTraceIntervalMillis = 30_000L
internal const val DesktopMemoryTraceMaxFileBytes = 20L * 1_024L * 1_024L

/** Appends one block per [sample] to [file]; a section that throws is reported and does not stop the others. */
internal class DesktopMemoryTrace(
    private val file: File,
    private val sections: List<Pair<String, () -> String>>,
    private val now: () -> String,
    private val maxFileBytes: Long = DesktopMemoryTraceMaxFileBytes,
) {
    fun sample() {
        val block = buildString {
            append("===== ").append(now()).append(" =====\n")
            for ((name, read) in sections) {
                append('[').append(name).append("]\n")
                val body = runCatching(read).getOrElse { "failed: ${it::class.simpleName}: ${it.message}" }
                append(body.trimEnd()).append("\n\n")
            }
        }
        file.parentFile?.mkdirs()
        if (maxFileBytes > 0 && file.length() > maxFileBytes) file.delete()
        file.appendText(block)
    }
}

internal suspend fun runDesktopMemoryTrace(
    trace: DesktopMemoryTrace,
    intervalMillis: Long = DesktopMemoryTraceIntervalMillis,
) {
    delay(10_000L)
    while (true) {
        withContext(Dispatchers.IO) { runCatching { trace.sample() } }
        delay(intervalMillis)
    }
}

internal fun desktopMemoryTraceFile(): File {
    val base = System.getenv("LOCALAPPDATA")?.takeIf(String::isNotBlank)?.let { File(it, "SKIPI") }
        ?: File(System.getProperty("java.io.tmpdir"), "SKIPI")
    return File(base, "memory-trace.log")
}

internal fun desktopMemoryTraceSections(readCoreMemory: () -> String): List<Pair<String, () -> String>> = listOf(
    "process" to ::processMemorySection,
    "jvm" to ::jvmMemorySection,
    "address-space" to ::addressSpaceSection,
    "native-memory-tracking" to ::nativeMemoryTrackingSection,
    "threads" to ::threadsSection,
    "go-core" to readCoreMemory,
)

private fun mb(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_048_576.0)

private fun jvmMemorySection(): String = buildString {
    val memory = ManagementFactory.getMemoryMXBean()
    val heap = memory.heapMemoryUsage
    val nonHeap = memory.nonHeapMemoryUsage
    appendLine("heap used=${mb(heap.used)} committed=${mb(heap.committed)} max=${mb(heap.max)}")
    appendLine("non-heap used=${mb(nonHeap.used)} committed=${mb(nonHeap.committed)}")
    ManagementFactory.getMemoryPoolMXBeans().forEach { pool ->
        appendLine("pool ${pool.name}: used=${mb(pool.usage.used)} committed=${mb(pool.usage.committed)}")
    }
    ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java).forEach { pool ->
        appendLine("buffers ${pool.name}: count=${pool.count} used=${mb(pool.memoryUsed)} capacity=${mb(pool.totalCapacity)}")
    }
    val classes = ManagementFactory.getClassLoadingMXBean()
    appendLine("classes loaded=${classes.loadedClassCount} total=${classes.totalLoadedClassCount}")
    ManagementFactory.getGarbageCollectorMXBeans().forEach { gc ->
        appendLine("gc ${gc.name}: count=${gc.collectionCount} timeMs=${gc.collectionTime}")
    }
}

/** The JVM's own native memory by category; needs -XX:NativeMemoryTracking=summary and the jdk.management module. */
private fun nativeMemoryTrackingSection(): String {
    val server = ManagementFactory.getPlatformMBeanServer()
    val name = ObjectName("com.sun.management:type=DiagnosticCommand")
    return try {
        server.invoke(name, "vmNativeMemory", arrayOf<Any>(arrayOf("summary")), arrayOf("[Ljava.lang.String;")) as String
    } catch (error: Throwable) {
        // The bare exception has no message; show its causes and what the MBean offers, so the reason is visible.
        val causes = generateSequence(error) { it.cause }.joinToString(" <- ") { "${it::class.simpleName}: ${it.message}" }
        val operations = runCatching { server.getMBeanInfo(name).operations.joinToString { it.name } }.getOrElse { "no MBean: ${it.message}" }
        "failed: $causes\noperations: $operations"
    }
}

private fun threadsSection(): String {
    val groups = Thread.getAllStackTraces().keys
        .groupingBy { it.name.replace(Regex("[-_ #]?\\d+"), "#") }
        .eachCount()
        .entries.sortedByDescending { it.value }
    return buildString {
        appendLine("threads total=${groups.sumOf { it.value }}")
        groups.take(20).forEach { appendLine("${it.value} x ${it.key}") }
    }
}

private fun processMemorySection(): String {
    if (!System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)) {
        return File("/proc/self/status").takeIf(File::isFile)?.readLines()
            ?.filter { it.startsWith("VmRSS") || it.startsWith("VmSwap") || it.startsWith("RssAnon") || it.startsWith("RssFile") }
            ?.joinToString("\n") ?: "not available on this system"
    }
    Arena.ofConfined().use { arena ->
        val linker = Linker.nativeLinker()
        val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
        val currentProcess = linker.downcallHandle(
            kernel32.findOrThrow("GetCurrentProcess"),
            FunctionDescriptor.of(ValueLayout.ADDRESS),
        )
        val query = linker.downcallHandle(
            kernel32.findOrThrow("K32GetProcessMemoryInfo"),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
        )
        // PROCESS_MEMORY_COUNTERS_EX: two DWORDs, then nine SIZE_T counters (80 bytes).
        val counters = arena.allocate(80)
        counters.set(ValueLayout.JAVA_INT, 0, 80)
        val ok = query.invokeWithArguments(currentProcess.invokeWithArguments(), counters, 80) as Int
        check(ok != 0) { "K32GetProcessMemoryInfo failed" }
        fun counter(offset: Long) = counters.get(ValueLayout.JAVA_LONG, offset)
        return "working set=${mb(counter(16))} peak working set=${mb(counter(8))} " +
            "private=${mb(counter(64))} pagefile=${mb(counter(48))} page faults=${counters.get(ValueLayout.JAVA_INT, 4)}"
    }
}

internal data class MemoryRegion(
    val base: Long,
    val size: Long,
    val committed: Boolean,
    /** "private", "mapped" or "image". */
    val kind: String,
    val protect: Int,
)

internal fun summarizeMemoryRegions(regions: List<MemoryRegion>, topCount: Int = 12): String {
    val committed = regions.filter { it.committed }
    return buildString {
        committed.groupBy { it.kind }.entries.sortedByDescending { entry -> entry.value.sumOf { it.size } }.forEach { (kind, list) ->
            appendLine("$kind committed=${mb(list.sumOf { it.size })} regions=${list.size}")
        }
        // PAGE_WRITECOMBINE (0x400) is how a GPU driver maps the memory it shares with the CPU.
        val writeCombined = committed.filter { it.protect and 0x400 != 0 }
        appendLine("write-combined committed=${mb(writeCombined.sumOf { it.size })} regions=${writeCombined.size}")
        appendLine("largest committed regions:")
        committed.sortedByDescending { it.size }.take(topCount).forEach {
            appendLine("  ${mb(it.size)} at 0x${java.lang.Long.toHexString(it.base)} ${it.kind} protect=0x${Integer.toHexString(it.protect)}")
        }
    }
}

/** Walks the whole address space with VirtualQuery: shows whether the private bytes are one huge block or many small ones. */
private fun addressSpaceSection(): String {
    if (!System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)) return "windows only"
    val regions = ArrayList<MemoryRegion>()
    Arena.ofConfined().use { arena ->
        val linker = Linker.nativeLinker()
        val kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global())
        val query = linker.downcallHandle(
            kernel32.findOrThrow("VirtualQuery"),
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
        )
        // MEMORY_BASIC_INFORMATION on x64: RegionSize at 24, State at 32, Protect at 36, Type at 40 (48 bytes).
        val info = arena.allocate(48)
        var address = 0L
        while (address >= 0) {
            val written = query.invokeWithArguments(address, info, 48L) as Long
            if (written == 0L) break
            val size = info.get(ValueLayout.JAVA_LONG, 24)
            val state = info.get(ValueLayout.JAVA_INT, 32)
            val type = info.get(ValueLayout.JAVA_INT, 40)
            if (state == 0x1000) {
                val kind = when (type) {
                    0x20000 -> "private"
                    0x40000 -> "mapped"
                    0x1000000 -> "image"
                    else -> "other"
                }
                regions += MemoryRegion(address, size, committed = true, kind = kind, protect = info.get(ValueLayout.JAVA_INT, 36))
            }
            if (size <= 0L) break
            address += size
        }
    }
    return summarizeMemoryRegions(regions)
}
