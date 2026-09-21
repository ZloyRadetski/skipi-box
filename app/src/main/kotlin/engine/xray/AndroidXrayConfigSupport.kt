// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import android.content.Context
import features.logs.androidCoreLogAccessFile
import features.logs.androidCoreLogErrorFile
import java.io.File

internal data class XrayCoreLogPaths(
    val accessLogPath: String,
    val errorLogPath: String,
)

internal fun Context.prepareXrayCoreLogPaths(): XrayCoreLogPaths {
    return XrayCoreLogPaths(
        accessLogPath = androidCoreLogAccessFile().absolutePath,
        errorLogPath = androidCoreLogErrorFile().absolutePath,
    )
}

internal fun XrayCoreLogPaths.logDirectoryPath(): String {
    return File(errorLogPath).parentFile?.absolutePath
        ?: File(accessLogPath).parentFile?.absolutePath
        ?: error("Xray log directory is unavailable")
}
