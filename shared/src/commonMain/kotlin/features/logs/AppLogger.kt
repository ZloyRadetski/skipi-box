// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

/**
 * Platform-agnostic logging facade. Hosts install a platform sink at startup
 * (Logcat + rotating files on Android); the default sink prints to stdout.
 */
object AppLogger {
    interface Sink {
        fun debug(tag: String, message: String, error: Throwable? = null)
        fun info(tag: String, message: String, error: Throwable? = null)
        fun warn(tag: String, message: String, error: Throwable? = null)
        fun error(tag: String, message: String, error: Throwable? = null)
    }

    private var sink: Sink = PrintlnLoggerSink

    fun install(sink: Sink) {
        this.sink = sink
    }

    fun debug(tag: String, message: String, error: Throwable? = null) {
        sink.debug(tag, message, error)
    }

    fun info(tag: String, message: String, error: Throwable? = null) {
        sink.info(tag, message, error)
    }

    fun warn(tag: String, message: String, error: Throwable? = null) {
        sink.warn(tag, message, error)
    }

    fun error(tag: String, message: String, error: Throwable? = null) {
        sink.error(tag, message, error)
    }
}

private object PrintlnLoggerSink : AppLogger.Sink {
    override fun debug(tag: String, message: String, error: Throwable?) {
        println("[$tag] DEBUG: $message")
    }

    override fun info(tag: String, message: String, error: Throwable?) {
        println("[$tag] INFO: $message")
    }

    override fun warn(tag: String, message: String, error: Throwable?) {
        println("[$tag] WARN: $message ${error?.message.orEmpty()}")
    }

    override fun error(tag: String, message: String, error: Throwable?) {
        System.err.println("[$tag] ERROR: $message ${error?.message.orEmpty()}")
    }
}

