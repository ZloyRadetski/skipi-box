// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

/** One line of the tray menu; a null label is a separator. */
internal class DesktopTrayMenuEntry(val id: Int, val label: String?)

/** The tray menu: connect or disconnect, a separator and exit. The ids are the ones the panel sends back. */
internal class DesktopTrayMenu(
    toggleLabel: String,
    exitLabel: String,
    private val onToggleTunnel: () -> Unit,
    private val onExit: () -> Unit,
) {
    private val lock = Any()
    private var toggle = toggleLabel
    private var exit = exitLabel

    /** Bumped whenever the entries change, so a panel knows to ask for the layout again. */
    var revision: Int = 1
        private set

    val entries: List<DesktopTrayMenuEntry>
        get() = synchronized(lock) {
            listOf(
                DesktopTrayMenuEntry(ToggleId, toggle),
                DesktopTrayMenuEntry(SeparatorId, null),
                DesktopTrayMenuEntry(ExitId, exit),
            )
        }

    /** True when a label changed. */
    fun setLabels(toggleLabel: String, exitLabel: String): Boolean = synchronized(lock) {
        if (toggleLabel == toggle && exitLabel == exit) return false
        toggle = toggleLabel
        exit = exitLabel
        revision += 1
        true
    }

    fun click(id: Int) {
        when (id) {
            ToggleId -> onToggleTunnel()
            ExitId -> onExit()
        }
    }

    private companion object {
        const val ToggleId = 1
        const val SeparatorId = 2
        const val ExitId = 3
    }
}

/** An ARGB tray icon, one pixel per Int as in a BufferedImage. */
internal class DesktopTrayPixmap(val width: Int, val height: Int, val argb: IntArray) {
    override fun equals(other: Any?): Boolean =
        other is DesktopTrayPixmap && width == other.width && height == other.height && argb.contentEquals(other.argb)

    override fun hashCode(): Int = 31 * (31 * width + height) + argb.contentHashCode()
}

/** The StatusNotifierItem wire format: four bytes per pixel, alpha first, network byte order. */
internal fun trayPixmapBytes(argb: IntArray): ByteArray {
    val bytes = ByteArray(argb.size * 4)
    argb.forEachIndexed { index, pixel ->
        bytes[index * 4] = (pixel ushr 24).toByte()
        bytes[index * 4 + 1] = (pixel ushr 16).toByte()
        bytes[index * 4 + 2] = (pixel ushr 8).toByte()
        bytes[index * 4 + 3] = pixel.toByte()
    }
    return bytes
}
