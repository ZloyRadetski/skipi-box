// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import android.content.Context
import features.logs.androidCoreLogAccessFile
import features.logs.androidCoreLogErrorFile

internal fun Context.prepareXrayCoreLogPaths(): XrayCoreLogPaths {
    return XrayCoreLogPaths(
        accessLogPath = androidCoreLogAccessFile().absolutePath,
        errorLogPath = androidCoreLogErrorFile().absolutePath,
    )
}
