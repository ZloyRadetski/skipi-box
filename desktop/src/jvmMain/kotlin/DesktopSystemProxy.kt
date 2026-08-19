// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package desktop

import features.logs.AppLogger
import java.io.File

object DesktopSystemProxy {
    private const val LogTag = "DesktopSystemProxy"

    fun enable(host: String = "127.0.0.1", port: Int = 10808) {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        when {
            os.contains("win") -> enableWindows(host, port)
            os.contains("mac") -> enableMac(host, port)
            else -> enableLinux(host, port)
        }
    }

    fun disable() {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        when {
            os.contains("win") -> disableWindows()
            os.contains("mac") -> disableMac()
            else -> disableLinux()
        }
    }

    private fun enableWindows(host: String, port: Int) {
        runCatching {
            runCommand(
                "reg", "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyEnable",
                "/t", "REG_DWORD",
                "/d", "1",
                "/f"
            )
            runCommand(
                "reg", "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyServer",
                "/t", "REG_SZ",
                "/d", "socks=$host:$port",
                "/f"
            )
            AppLogger.info(LogTag, "Windows system proxy enabled on $host:$port")
        }.onFailure { error ->
            AppLogger.error(LogTag, "Failed to enable Windows system proxy", error)
        }
    }

    private fun disableWindows() {
        runCatching {
            runCommand(
                "reg", "add",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings",
                "/v", "ProxyEnable",
                "/t", "REG_DWORD",
                "/d", "0",
                "/f"
            )
            AppLogger.info(LogTag, "Windows system proxy disabled")
        }.onFailure { error ->
            AppLogger.error(LogTag, "Failed to disable Windows system proxy", error)
        }
    }

    private fun enableMac(host: String, port: Int) {
        runCatching {
            runCommand("networksetup", "-setsocksfirewallproxy", "Wi-Fi", host, port.toString())
            runCommand("networksetup", "-setsocksfirewallproxystate", "Wi-Fi", "on")
            AppLogger.info(LogTag, "macOS system SOCKS proxy enabled on $host:$port")
        }.onFailure { error ->
            AppLogger.error(LogTag, "Failed to enable macOS system proxy", error)
        }
    }

    private fun disableMac() {
        runCatching {
            runCommand("networksetup", "-setsocksfirewallproxystate", "Wi-Fi", "off")
            AppLogger.info(LogTag, "macOS system proxy disabled")
        }.onFailure { error ->
            AppLogger.error(LogTag, "Failed to disable macOS system proxy", error)
        }
    }

    private fun enableLinux(host: String, port: Int) {
        runCatching {
            runCommand("gsettings", "set", "org.gnome.system.proxy", "mode", "'manual'")
            runCommand("gsettings", "set", "org.gnome.system.proxy.socks", "host", "'$host'")
            runCommand("gsettings", "set", "org.gnome.system.proxy.socks", "port", port.toString())
            AppLogger.info(LogTag, "Linux system proxy enabled on $host:$port")
        }.onFailure { error ->
            AppLogger.error(LogTag, "Failed to enable Linux system proxy", error)
        }
    }

    private fun disableLinux() {
        runCatching {
            runCommand("gsettings", "set", "org.gnome.system.proxy", "mode", "'none'")
            AppLogger.info(LogTag, "Linux system proxy disabled")
        }.onFailure { error ->
            AppLogger.error(LogTag, "Failed to disable Linux system proxy", error)
        }
    }

    private fun runCommand(vararg args: String) {
        ProcessBuilder(*args)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor()
    }
}
