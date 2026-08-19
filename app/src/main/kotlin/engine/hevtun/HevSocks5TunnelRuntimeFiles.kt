// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.hevtun

import android.content.Context
import engine.xray.prepareXrayCoreLogPaths

internal fun Context.deleteHevSocks5TunnelLogFile() {
    val file = applicationContext.prepareXrayCoreLogPaths().hevSocks5TunnelLogFile(HevSocks5TunnelLogFileName)
    if (file.exists() && !file.delete()) {
        error("Failed to delete ${file.absolutePath}")
    }
}
