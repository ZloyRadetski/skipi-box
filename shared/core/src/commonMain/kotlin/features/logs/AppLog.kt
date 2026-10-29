// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

enum class AppLogLevel { Debug, Info, Warning, Error }

/** Where application log lines go. Each host provides its own (logcat + in-app viewer, a log file, ...). */
interface AppLogSink {
    /**
     * @param recordInApp false for lines meant only for the platform log, not for the in-app log viewer.
     */
    fun log(level: AppLogLevel, tag: String, message: String, error: Throwable?, recordInApp: Boolean)
}

/**
 * Logging entry point for portable code. It never throws and works before a host has installed its
 * sink, so early start-up code can log safely.
 */
object AppLog {
    val defaultSink: AppLogSink = object : AppLogSink {
        override fun log(level: AppLogLevel, tag: String, message: String, error: Throwable?, recordInApp: Boolean) {
            println("[${level.name.uppercase()}] $tag: $message" + (error?.let { " ($it)" } ?: ""))
        }
    }

    // Written once at start-up and read from any thread.
    @kotlin.concurrent.Volatile
    var sink: AppLogSink = defaultSink
        private set

    fun install(sink: AppLogSink) {
        this.sink = sink
    }

    fun reset() {
        sink = defaultSink
    }

    fun debug(tag: String, message: String, error: Throwable? = null) = emit(AppLogLevel.Debug, tag, message, error, true)

    fun info(tag: String, message: String, error: Throwable? = null) = emit(AppLogLevel.Info, tag, message, error, true)

    fun warn(tag: String, message: String, error: Throwable? = null) = emit(AppLogLevel.Warning, tag, message, error, true)

    fun error(tag: String, message: String, error: Throwable? = null) = emit(AppLogLevel.Error, tag, message, error, true)

    /** A warning for the platform log only, kept out of the in-app viewer. */
    fun platformWarn(tag: String, message: String, error: Throwable? = null) =
        emit(AppLogLevel.Warning, tag, message, error, false)

    private fun emit(level: AppLogLevel, tag: String, message: String, error: Throwable?, recordInApp: Boolean) {
        // Logging must never turn the condition being reported into a crash.
        runCatching { sink.log(level, tag, message, error, recordInApp) }
    }
}
