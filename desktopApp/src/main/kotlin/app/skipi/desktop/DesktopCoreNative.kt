// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.Path

internal const val DesktopCoreApiVersion = "1"

/**
 * Small Kotlin-facing representation of the stable C ABI exported by
 * `skipicore.dll` / `libskipicore.so`. Keeping this interface independent from
 * the FFM implementation makes lifecycle behaviour testable without loading a
 * native library.
 */
internal interface DesktopCoreNative : AutoCloseable {
    val apiVersion: String
    val coreVersion: String

    fun initializeAssets(directory: String)

    fun createController(): Long

    fun destroyController(handle: Long)

    fun start(handle: Long, configJson: String, tunFd: Long = 0)

    fun stop(handle: Long)

    fun isRunning(handle: Long): Boolean

    fun queryTrafficStats(handle: Long): String

    fun measureDelay(handle: Long, targetUrl: String): Long

    fun readMemoryStats(): String

    fun forceFreeMemory()
}

/**
 * Java 26 Foreign Function & Memory binding for the in-process SKIPI Core.
 *
 * The application enables `--enable-native-access=ALL-UNNAMED` in Gradle.
 * Every C string returned by the Core is copied and freed in this class, so no
 * caller can accidentally retain Go-allocated memory.
 */
internal class DesktopCoreFfmNative(
    libraryPath: Path,
) : DesktopCoreNative {
    private val linker = Linker.nativeLinker()
    // A Go c-shared library owns runtime threads and TLS for the lifetime of
    // the host process. Unloading it after Stop can crash the JVM, so retain
    // the library in the global arena until JVM shutdown.
    private val libraryArena = Arena.global()
    private val lookup = try {
        SymbolLookup.libraryLookup(libraryPath, libraryArena)
    } catch (error: Throwable) {
        throw IllegalStateException("Unable to load SKIPI Core library at $libraryPath", error)
    }

    private val freeStringFunction = downcall(
        "SkipiCoreDesktopFreeString",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
    )
    private val apiVersionFunction = downcall(
        "SkipiCoreDesktopApiVersion",
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
    private val coreVersionFunction = downcall(
        "SkipiCoreDesktopCoreVersion",
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
    private val initializeAssetsFunction = downcall(
        "SkipiCoreDesktopInitializeAssets",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS),
    )
    private val createControllerFunction = downcall(
        "SkipiCoreDesktopCreateController",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG),
    )
    private val destroyControllerFunction = downcall(
        "SkipiCoreDesktopDestroyController",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
    )
    private val startFunction = downcall(
        "SkipiCoreDesktopStart",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
        ),
    )
    private val stopFunction = downcall(
        "SkipiCoreDesktopStop",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
    )
    private val isRunningFunction = downcall(
        "SkipiCoreDesktopIsRunning",
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG),
    )
    private val trafficStatsFunction = downcall(
        "SkipiCoreDesktopQueryTrafficStats",
        FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
    )
    private val measureDelayFunction = downcall(
        "SkipiCoreDesktopMeasureDelay",
        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
    )
    private val memoryStatsFunction = downcall(
        "SkipiCoreDesktopReadMemoryStats",
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
    private val forceFreeMemoryFunction = downcall(
        "SkipiCoreDesktopForceFreeMemory",
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
    private val lastErrorFunction = downcall(
        "SkipiCoreDesktopLastError",
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )

    private var closed = false

    override val apiVersion: String
        get() = ownedString("read API version") { invokePointer(apiVersionFunction) }

    override val coreVersion: String
        get() = ownedString("read Core version") { invokePointer(coreVersionFunction) }

    override fun initializeAssets(directory: String) {
        withUtf8(directory) { value ->
            errorResult("initialize assets") {
                invokePointer(initializeAssetsFunction, value)
            }
        }
    }

    override fun createController(): Long {
        checkOpen()
        val handle = invoke(createControllerFunction) as? Long
            ?: error("SKIPI Core returned an invalid controller handle")
        if (handle != 0L) return handle
        throw failure("create controller")
    }

    override fun destroyController(handle: Long) {
        errorResult("destroy controller") {
            invokePointer(destroyControllerFunction, handle)
        }
    }

    override fun start(handle: Long, configJson: String, tunFd: Long) {
        withUtf8(configJson) { config ->
            errorResult("start controller") {
                invokePointer(startFunction, handle, config, tunFd)
            }
        }
    }

    override fun stop(handle: Long) {
        errorResult("stop controller") {
            invokePointer(stopFunction, handle)
        }
    }

    override fun isRunning(handle: Long): Boolean {
        checkOpen()
        return when (val value = invoke(isRunningFunction, handle) as? Int
            ?: error("SKIPI Core returned an invalid running-state result")) {
            0 -> false
            1 -> true
            -1 -> throw failure("read controller state")
            else -> error("SKIPI Core returned unknown running state $value")
        }
    }

    override fun queryTrafficStats(handle: Long): String =
        nullableOwnedString("query traffic statistics") {
            invokePointer(trafficStatsFunction, handle)
        }

    override fun measureDelay(handle: Long, targetUrl: String): Long = withUtf8(targetUrl) { target ->
        checkOpen()
        val delay = invoke(measureDelayFunction, handle, target) as? Long
            ?: error("SKIPI Core returned an invalid delay result")
        if (delay >= 0) delay else throw failure("measure delay")
    }

    override fun readMemoryStats(): String = ownedString("read memory statistics") {
        invokePointer(memoryStatsFunction)
    }

    override fun forceFreeMemory() {
        errorResult("free memory") {
            invokePointer(forceFreeMemoryFunction)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        // Do not close libraryArena: unloading a Go c-shared DLL is unsafe.
    }

    private fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle =
        linker.downcallHandle(lookup.findOrThrow(name), descriptor)

    private fun invoke(function: MethodHandle, vararg arguments: Any?): Any? {
        checkOpen()
        return try {
            function.invokeWithArguments(arguments.toList())
        } catch (error: Throwable) {
            throw IllegalStateException("SKIPI Core native call failed", error)
        }
    }

    private fun invokePointer(function: MethodHandle, vararg arguments: Any?): MemorySegment =
        invoke(function, *arguments) as? MemorySegment
            ?: error("SKIPI Core returned an invalid native pointer")

    private fun errorResult(operation: String, block: () -> MemorySegment) {
        val result = block()
        if (result.address() != 0L) throw failure(operation, result)
    }

    private fun ownedString(operation: String, block: () -> MemorySegment): String {
        val result = block()
        if (result.address() == 0L) throw failure(operation)
        return readOwnedString(result)
    }

    private fun nullableOwnedString(operation: String, block: () -> MemorySegment): String {
        val result = block()
        if (result.address() == 0L) throw failure(operation)
        return readOwnedString(result)
    }

    private fun failure(operation: String, explicitError: MemorySegment? = null): IllegalStateException {
        val explicitMessage = explicitError
            ?.takeUnless { it.address() == 0L }
            ?.let(::readOwnedString)
            .orEmpty()
        val detail = explicitMessage.ifBlank {
            runCatching { readOwnedString(invokePointer(lastErrorFunction)) }.getOrDefault("")
        }.ifBlank { "unknown native error" }
        return IllegalStateException("SKIPI Core could not $operation: $detail")
    }

    private fun readOwnedString(value: MemorySegment): String = try {
        value.reinterpret(Long.MAX_VALUE).getString(0)
    } finally {
        invoke(freeStringFunction, value)
    }

    private fun <T> withUtf8(value: String, block: (MemorySegment) -> T): T {
        checkOpen()
        Arena.ofConfined().use { arena ->
            return block(arena.allocateFrom(value))
        }
    }

    private fun checkOpen() {
        check(!closed) { "SKIPI Core native library is already closed" }
    }
}
