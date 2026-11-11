// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import utils.powerShellLiteral
import utils.shellQuote
import features.updater.DesktopUpdatePackage
import java.nio.file.Path

/**
 * What installing a downloaded update means for a given package. Built without touching the system so it can
 * be tested; [DesktopUpdateInstaller] runs it.
 */
internal sealed interface DesktopUpdateInstallPlan {
    /**
     * A helper that outlives this process: it waits for the app to exit, replaces or installs it and starts the
     * new version. The app exits as soon as the helper is running.
     */
    data class AfterExit(val command: List<String>) : DesktopUpdateInstallPlan

    /** The system's own installer takes the file (a dmg, a deb, an rpm) and the app keeps running. */
    data class HandOver(val command: List<String>) : DesktopUpdateInstallPlan
}

/** Where the running build lives: the launcher, and the folder a portable build is replaced in. */
internal data class DesktopInstallLocation(
    val launcher: Path,
    /** The folder a portable build is replaced in: `<root>/SKIPI.exe` on Windows, `<root>/bin/SKIPI` on Linux. */
    val portableRoot: Path?,
    val processId: Long,
)

/** The steps of an update, or null when the package cannot be installed from a downloaded file. */
internal fun desktopUpdateInstallPlan(
    pkg: DesktopUpdatePackage,
    file: Path,
    location: DesktopInstallLocation,
    appImage: String? = null,
): DesktopUpdateInstallPlan? = when (pkg) {
    DesktopUpdatePackage.WindowsMsi -> DesktopUpdateInstallPlan.AfterExit(
        windowsPowerShell(windowsMsiScript(file, location)),
    )
    DesktopUpdatePackage.WindowsPortable -> location.portableRoot?.let { root ->
        DesktopUpdateInstallPlan.AfterExit(windowsPowerShell(windowsPortableScript(file, root, location)))
    }
    DesktopUpdatePackage.LinuxAppImage -> appImage?.let { current ->
        DesktopUpdateInstallPlan.AfterExit(shell(linuxAppImageScript(file, current, location)))
    }
    DesktopUpdatePackage.LinuxPortable -> location.portableRoot?.let { root ->
        DesktopUpdateInstallPlan.AfterExit(shell(linuxPortableScript(file, root, location)))
    }
    DesktopUpdatePackage.LinuxDeb,
    DesktopUpdatePackage.LinuxRpm -> DesktopUpdateInstallPlan.HandOver(listOf("xdg-open", file.toString()))
    DesktopUpdatePackage.MacDmg -> DesktopUpdateInstallPlan.HandOver(listOf("open", file.toString()))
}

private fun windowsPowerShell(script: String): List<String> =
    listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", script)

private fun shell(script: String): List<String> = listOf("/bin/sh", "-c", script)

private fun ps(value: Any): String = value.toString().powerShellLiteral()

private fun sh(value: Any): String = value.toString().shellQuote()

/**
 * The MSI is per machine, so msiexec asks for administrator rights (one UAC prompt). Whatever the answer, the
 * app is started again afterwards: the new version when the install went through, the old one when it did not.
 */
private fun windowsMsiScript(msi: Path, location: DesktopInstallLocation): String = listOf(
    "Wait-Process -Id ${location.processId} -Timeout 60 -ErrorAction SilentlyContinue",
    "try { Start-Process -FilePath msiexec.exe -ArgumentList ${ps("/i \"$msi\" /passive /norestart")} -Verb RunAs -Wait } catch { }",
    "Start-Process -FilePath ${ps(location.launcher)}",
).joinToString("; ")

/**
 * A portable build is replaced in its own folder: the new files are unpacked aside and swapped in, and the old
 * folder comes back if the swap fails, so a failed update never leaves the app without files.
 */
private fun windowsPortableScript(archive: Path, root: Path, location: DesktopInstallLocation): String = listOf(
    "Wait-Process -Id ${location.processId} -Timeout 60 -ErrorAction SilentlyContinue",
    "\$root = ${ps(root)}",
    "\$old = \$root + '.old'",
    "\$tmp = Join-Path \$env:TEMP ('skipi-update-' + [guid]::NewGuid())",
    "try { " + listOf(
        "Expand-Archive -LiteralPath ${ps(archive)} -DestinationPath \$tmp -Force",
        "\$src = Join-Path \$tmp 'SKIPI'",
        "if (-not (Test-Path (Join-Path \$src ${ps(location.launcher.fileName)}))) { throw 'The archive has no launcher' }",
        "if (Test-Path \$old) { Remove-Item -LiteralPath \$old -Recurse -Force }",
        "Move-Item -LiteralPath \$root -Destination \$old",
        "try { Copy-Item -LiteralPath \$src -Destination \$root -Recurse } " +
            "catch { Remove-Item -LiteralPath \$root -Recurse -Force -ErrorAction SilentlyContinue; Move-Item -LiteralPath \$old -Destination \$root; throw }",
        "Remove-Item -LiteralPath \$old -Recurse -Force -ErrorAction SilentlyContinue",
    ).joinToString("; ") + " } catch { } finally { Remove-Item -LiteralPath \$tmp -Recurse -Force -ErrorAction SilentlyContinue }",
    "Start-Process -FilePath ${ps(location.launcher)}",
).joinToString("; ")

/** The AppImage file is replaced in one rename, so the old one stays usable until the new one is complete. */
private fun linuxAppImageScript(download: Path, current: String, location: DesktopInstallLocation): String = listOf(
    "while kill -0 ${location.processId} 2>/dev/null; do sleep 0.3; done",
    "cp ${sh(download)} ${sh("$current.new")} && chmod +x ${sh("$current.new")} && mv -f ${sh("$current.new")} ${sh(current)}",
    "(nohup ${sh(current)} >/dev/null 2>&1 &)",
).joinToString("; ")

private fun linuxPortableScript(archive: Path, root: Path, location: DesktopInstallLocation): String = listOf(
    "while kill -0 ${location.processId} 2>/dev/null; do sleep 0.3; done",
    "tmp=\$(mktemp -d)",
    "if tar -xzf ${sh(archive)} -C \"\$tmp\" && [ -x \"\$tmp/SKIPI/bin/SKIPI\" ]; then " +
        "rm -rf ${sh("$root.old")}; mv ${sh(root)} ${sh("$root.old")} && " +
        "if mv \"\$tmp/SKIPI\" ${sh(root)}; then rm -rf ${sh("$root.old")}; else mv ${sh("$root.old")} ${sh(root)}; fi; fi",
    "rm -rf \"\$tmp\"",
    "(nohup ${sh(location.launcher)} >/dev/null 2>&1 &)",
).joinToString("; ")

/** Runs an install plan. Returns true when the app must now exit so the helper can replace it. */
internal class DesktopUpdateInstaller(
    private val start: (List<String>) -> Result<Unit> = { command ->
        runCatching { ProcessBuilder(command).redirectErrorStream(true).start(); Unit }
    },
) {
    fun install(plan: DesktopUpdateInstallPlan): Result<Boolean> = when (plan) {
        is DesktopUpdateInstallPlan.AfterExit -> start(plan.command).map { true }
        is DesktopUpdateInstallPlan.HandOver -> start(plan.command).map { false }
    }
}
