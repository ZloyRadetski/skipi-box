// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.logs

import android.util.Log

/** Android destination of [AppLog]: logcat plus the in-app log viewer. */
internal object AndroidAppLogSink : AppLogSink {
    override fun log(level: AppLogLevel, tag: String, message: String, error: Throwable?, recordInApp: Boolean) {
        when (level) {
            AppLogLevel.Debug -> if (error == null) Log.d(tag, message) else Log.d(tag, message, error)
            AppLogLevel.Info -> if (error == null) Log.i(tag, message) else Log.i(tag, message, error)
            AppLogLevel.Warning -> if (error == null) Log.w(tag, message) else Log.w(tag, message, error)
            AppLogLevel.Error -> if (error == null) Log.e(tag, message) else Log.e(tag, message, error)
        }
        if (recordInApp) {
            AndroidLogcatRepository.append(viewerLevel(level), logcatMessage(tag, message, error))
        }
    }

    private fun viewerLevel(level: AppLogLevel): String = when (level) {
        AppLogLevel.Debug -> "debug"
        AppLogLevel.Info -> "info"
        AppLogLevel.Warning -> "warning"
        AppLogLevel.Error -> "error"
    }

    private fun logcatMessage(tag: String, message: String, error: Throwable?): String {
        return buildString {
            append(tag)
            append(": ")
            append(message)
            if (error != null) {
                // Android returns a String here, but JVM test doubles may return null.
                // Logging an invalid imported profile must never turn its rejection into a crash.
                @Suppress("UNNECESSARY_SAFE_CALL")
                val stackTrace = Log.getStackTraceString(error)?.trim().orEmpty()
                if (stackTrace.isNotEmpty()) {
                    append('\n')
                    append(stackTrace)
                }
            }
        }
    }
}
