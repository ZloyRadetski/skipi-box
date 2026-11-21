// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Whether the window is drawn by the graphics card or by the processor. Skiko and Java2D read their renderer
 * once, when the first window is made, so the choice is a small file the app reads at the very start of main()
 * and a change takes effect on the next start. It sits beside the other local data of the machine and not in the
 * app settings: it describes this computer (a weak laptop), and a backup restored on another one must not carry it.
 */
internal const val GRAPHICS_ACCELERATION_FILE = "graphics-acceleration"

internal enum class DesktopOs { Windows, Mac, Linux }

internal fun desktopOsOf(osName: String): DesktopOs {
    val name = osName.lowercase()
    return when {
        "windows" in name -> DesktopOs.Windows
        "mac" in name || "darwin" in name -> DesktopOs.Mac
        else -> DesktopOs.Linux
    }
}

/**
 * What a machine gets before the user chooses: the graphics card, on every system. The software renderer uses
 * less memory but loads the processor, which a weak laptop feels; it is one switch away in the settings.
 */
@Suppress("UNUSED_PARAMETER")
internal fun graphicsAccelerationDefault(os: DesktopOs): Boolean = true

/**
 * The system properties that select the renderer. Accelerated sets none: Skiko and Java2D pick the card's own
 * API (Direct3D, Metal, OpenGL). Software is Skiko's direct software renderer; on Windows Java2D's own Direct3D
 * pipeline goes off too and Skiko's background erase stays on, so a strip a drag has not painted yet takes the
 * interface color instead of black.
 */
internal fun graphicsRenderProperties(os: DesktopOs, accelerated: Boolean): Map<String, String> {
    if (accelerated) return emptyMap()
    val properties = linkedMapOf("skiko.renderApi" to "SOFTWARE_FAST")
    if (os == DesktopOs.Windows) {
        properties["skiko.rendering.noerasebackground"] = "false"
        properties["sun.java2d.d3d"] = "false"
    }
    return properties
}

/** The folder of the choice file, or null where the machine gives no usable home. */
internal fun graphicsPreferenceDirectory(os: DesktopOs, env: (String) -> String?, home: String?): Path? {
    fun String?.usable() = this?.takeIf { it.isNotBlank() }
    val userHome = home.usable()
    return when (os) {
        DesktopOs.Windows ->
            env("LOCALAPPDATA").usable()?.let { Path.of(it, "SKIPI") }
                ?: userHome?.let { Path.of(it, "AppData", "Local", "SKIPI") }
        DesktopOs.Mac -> userHome?.let { Path.of(it, "Library", "Application Support", "SKIPI") }
        DesktopOs.Linux ->
            env("XDG_CONFIG_HOME").usable()?.let { Path.of(it, "skipi") }
                ?: userHome?.let { Path.of(it, ".config", "skipi") }
    }
}

/** The stored choice; a missing folder, file or an unreadable value means the machine's default. */
internal fun readGraphicsAcceleration(directory: Path?, os: DesktopOs): Boolean {
    val fallback = graphicsAccelerationDefault(os)
    if (directory == null) return fallback
    val text = runCatching { Files.readString(directory.resolve(GRAPHICS_ACCELERATION_FILE)) }.getOrNull() ?: return fallback
    return when (text.trim().lowercase()) {
        "on" -> true
        "off" -> false
        else -> fallback
    }
}

/** Stores the choice, making the folder when it is not there yet. A failure must never block the app. */
internal fun writeGraphicsAcceleration(directory: Path, accelerated: Boolean): Result<Unit> = runCatching {
    Files.createDirectories(directory)
    Files.writeString(directory.resolve(GRAPHICS_ACCELERATION_FILE), if (accelerated) "on" else "off")
    Unit
}

/** Sets the renderer properties that nothing set before: the launcher or the user may have chosen one already. */
internal fun applyGraphicsRenderProperties(os: DesktopOs, accelerated: Boolean, properties: Properties) {
    graphicsRenderProperties(os, accelerated).forEach { (key, value) ->
        if (properties.getProperty(key) == null) properties.setProperty(key, value)
    }
}

internal fun applyStoredGraphicsAcceleration(os: DesktopOs, directory: Path?, properties: Properties) {
    applyGraphicsRenderProperties(os, readGraphicsAcceleration(directory, os), properties)
}

/** The folder of this machine's choice file. */
internal fun currentGraphicsPreferenceDirectory(): Pair<DesktopOs, Path?> {
    val os = desktopOsOf(System.getProperty("os.name").orEmpty())
    return os to graphicsPreferenceDirectory(os, System::getenv, System.getProperty("user.home"))
}

/**
 * Called first in main(), before anything touches AWT or Skiko: reads the choice and sets the renderer.
 * Anything that goes wrong leaves the renderer to the defaults.
 */
internal fun applyGraphicsAccelerationAtStart() {
    runCatching {
        val (os, directory) = currentGraphicsPreferenceDirectory()
        applyStoredGraphicsAcceleration(os, directory, System.getProperties())
    }
}
