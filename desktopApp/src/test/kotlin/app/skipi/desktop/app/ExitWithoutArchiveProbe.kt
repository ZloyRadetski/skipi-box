// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/** A JVM that ends the way a second SKIPI start does; see DesktopClassArchiveTest. */
internal object ExitWithoutArchiveProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.firstOrNull() == "skip") exitWithoutClassArchive()
    }
}
