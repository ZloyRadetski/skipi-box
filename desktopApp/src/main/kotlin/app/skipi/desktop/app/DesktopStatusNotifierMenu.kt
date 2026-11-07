// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/**
 * The tray menu a panel draws: the same entries as the Compose menu, numbered from 1 in order. The numbers are
 * the ids the panel sends back.
 */
internal class DesktopStatusNotifierMenu(entries: List<DesktopTrayMenuEntry>) {
    private val lock = Any()
    private var current = entries

    /** Bumped whenever a label or the order of the entries changes, so a panel knows to ask for the layout again. */
    var revision: Int = 1
        private set

    val entries: List<DesktopTrayMenuEntry> get() = synchronized(lock) { current }

    /** Takes the newest entries, with the newest actions behind them. True when the panel has to redraw. */
    fun set(next: List<DesktopTrayMenuEntry>): Boolean = synchronized(lock) {
        val changed = current.map(::shapeOf) != next.map(::shapeOf)
        current = next
        if (changed) revision += 1
        changed
    }

    fun click(id: Int) {
        (entries.getOrNull(id - 1) as? DesktopTrayMenuEntry.Action)?.onClick?.invoke()
    }

    private fun shapeOf(entry: DesktopTrayMenuEntry): String = when (entry) {
        is DesktopTrayMenuEntry.Action -> "action:${entry.label}"
        is DesktopTrayMenuEntry.Info -> "info:${entry.label}"
        DesktopTrayMenuEntry.Separator -> "separator"
    }
}
