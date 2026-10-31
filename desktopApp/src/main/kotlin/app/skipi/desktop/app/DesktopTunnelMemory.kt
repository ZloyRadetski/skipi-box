// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.settings.coreMemoryKilobytes

/**
 * The core's own memory in kilobytes, as Android shows it, or 0 when the core gives no figure.
 * The core shares the process with the JVM and the UI, so the process's resident set is not its footprint.
 */
internal fun readDesktopTunnelMemoryKb(readMemoryStats: () -> Result<String>): Long =
    readMemoryStats().getOrNull()?.let(::coreMemoryKilobytes) ?: 0L
