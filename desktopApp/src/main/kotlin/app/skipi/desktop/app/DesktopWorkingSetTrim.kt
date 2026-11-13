// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import kotlinx.coroutines.delay
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout

/** How long after the start the first trim waits, so the window has been drawn and its warm-up garbage is dead. */
internal const val WorkingSetStartupSettleMillis = 15_000L

/** How long the window must stay without focus (behind another window, minimized, hidden) before it is trimmed. */
internal const val WorkingSetBlurSettleMillis = 5_000L

/** The least time between two trims while the window stays in the background. */
internal const val WorkingSetMinGapMillis = 30_000L

internal data class WorkingSetActivity(
    val windowFocused: Boolean,
    val unfocusedForMillis: Long,
    /** Null until the first trim has run. */
    val sinceLastTrimMillis: Long?,
    val sinceStartMillis: Long,
)

/**
 * The working set is what Task Manager shows as the app's memory, and it also keeps the pages the app only touched
 * once at startup resident. Trimming hands them back: the pages the app really uses come back by themselves on the
 * next touch. It is done once after the start has settled, then whenever the window sits in the background, never
 * while it is focused, so the interface a person is using is not made to fault its pages in again and again.
 */
internal fun shouldTrimWorkingSet(activity: WorkingSetActivity): Boolean {
    val sinceLastTrim = activity.sinceLastTrimMillis
        ?: return activity.sinceStartMillis >= WorkingSetStartupSettleMillis
    return !activity.windowFocused &&
        activity.unfocusedForMillis >= WorkingSetBlurSettleMillis &&
        sinceLastTrim >= WorkingSetMinGapMillis
}

/** Watches the window's focus and trims the working set when [shouldTrimWorkingSet] says so. */
internal suspend fun runDesktopWorkingSetTrim(
    isFocused: () -> Boolean,
    trim: () -> Unit = ::trimProcessWorkingSet,
    nowMillis: () -> Long = System::currentTimeMillis,
    pollMillis: Long = 2_000L,
) {
    val startedAt = nowMillis()
    var unfocusedSince: Long? = null
    var lastTrimAt: Long? = null
    while (true) {
        delay(pollMillis)
        val now = nowMillis()
        val focused = isFocused()
        if (focused) unfocusedSince = null else if (unfocusedSince == null) unfocusedSince = now
        val activity = WorkingSetActivity(
            windowFocused = focused,
            unfocusedForMillis = unfocusedSince?.let { now - it } ?: 0L,
            sinceLastTrimMillis = lastTrimAt?.let { now - it },
            sinceStartMillis = now - startedAt,
        )
        if (shouldTrimWorkingSet(activity)) {
            trim()
            lastTrimAt = now
        }
    }
}

/** Builds an action once, on first use, and runs that same action every time after. */
internal class OnceBuiltAction(build: () -> () -> Unit) {
    private val action: () -> Unit by lazy(build)
    fun run() = action()
}

/** The foreign-function handles of K32EmptyWorkingSet, linked once and kept: a trim is then one native call. */
private val windowsWorkingSetTrim = OnceBuiltAction {
    val linker = Linker.nativeLinker()
    val kernel32 = SymbolLookup.libraryLookup("kernel32", java.lang.foreign.Arena.global())
    val currentProcess = linker.downcallHandle(
        kernel32.findOrThrow("GetCurrentProcess"),
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
    val emptyWorkingSet = linker.downcallHandle(
        kernel32.findOrThrow("K32EmptyWorkingSet"),
        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
    )
    val trim: () -> Unit = { emptyWorkingSet.invokeWithArguments(currentProcess.invokeWithArguments()) }
    trim
}

/** Windows only: K32EmptyWorkingSet on this process. Other systems return their memory on their own. */
internal fun trimProcessWorkingSet() {
    if (!System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)) return
    runCatching { windowsWorkingSetTrim.run() }
        .onFailure { error -> DesktopLogger.info("WorkingSet", "The working set could not be trimmed: ${error.message}") }
}
