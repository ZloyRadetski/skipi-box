// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.util.concurrent.TimeUnit

data class DesktopXrayProcessState(
    val isRunning: Boolean,
    val pid: Long? = null,
    val lastOutput: String = "",
)

/** Owns the single local Xray process started by a Desktop SKIPI session. */
class DesktopXrayProcessController(
    private val runtimeProvider: () -> Result<DesktopXrayRuntime> = DesktopXrayRuntimes::discover,
    private val runtimeConfigDirectory: Path = defaultConfigDirectory(),
) {
    private var process: Process? = null
    private val outputLines = ArrayDeque<String>()

    @Synchronized
    fun state(): DesktopXrayProcessState {
        val active = process?.takeIf(Process::isAlive)
        if (active == null) process = null
        return DesktopXrayProcessState(
            isRunning = active != null,
            pid = active?.pid(),
            lastOutput = outputLines.joinToString("\n"),
        )
    }

    @Synchronized
    fun start(config: String): Result<DesktopXrayProcessState> = runCatching {
        check(!state().isRunning) { "SKIPI Xray is already running" }
        val runtime = runtimeProvider().getOrThrow()
        val configPath = writeRuntimeConfig(config)
        validateConfig(runtime, configPath)
        val processBuilder = ProcessBuilder(xrayCommand(runtime, configPath))
            .directory(runtime.directory.toFile())
            .redirectErrorStream(true)
        processBuilder.environment()["XRAY_LOCATION_ASSET"] = runtime.geoIp.parent.toString()
        val started = processBuilder.start()
        process = started
        DesktopLogger.info("XrayController", "Started Xray process (PID ${started.pid()}) with config $configPath")
        collectOutput(started)
        state()
    }.onFailure { error ->
        DesktopLogger.error("XrayController", "Failed to start Xray", error)
    }

    @Synchronized
    fun stop(): Result<DesktopXrayProcessState> = runCatching {
        val active = process ?: return@runCatching state()
        val pid = active.pid()
        active.destroy()
        if (!active.waitFor(3, TimeUnit.SECONDS)) {
            active.destroyForcibly()
            active.waitFor(3, TimeUnit.SECONDS)
        }
        process = null
        DesktopLogger.info("XrayController", "Stopped Xray process (PID $pid)")
        state()
    }.onFailure { error ->
        DesktopLogger.error("XrayController", "Failed to stop Xray", error)
    }

    private fun writeRuntimeConfig(config: String): Path {
        require(config.isNotBlank()) { "Xray runtime config must not be blank" }
        Files.createDirectories(runtimeConfigDirectory)
        val destination = runtimeConfigDirectory.resolve("active-config.json")
        val temporary = destination.resolveSibling("${destination.fileName}.tmp")
        Files.writeString(temporary, config, StandardCharsets.UTF_8, CREATE, TRUNCATE_EXISTING)
        try {
            Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, destination, REPLACE_EXISTING)
        }
        return destination
    }

    private fun validateConfig(runtime: DesktopXrayRuntime, configPath: Path) {
        val processBuilder = ProcessBuilder(xrayCommand(runtime, configPath, testOnly = true))
            .directory(runtime.directory.toFile())
            .redirectErrorStream(true)
        processBuilder.environment()["XRAY_LOCATION_ASSET"] = runtime.geoIp.parent.toString()
        val validation = processBuilder.start()
        val output = validation.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader -> reader.readText() }
        val exitCode = validation.waitFor()
        check(exitCode == 0) {
            val detail = output.trim().takeIf(String::isNotEmpty)?.let { ": ${it.take(1_000)}" }.orEmpty()
            "Xray rejected the generated config$detail"
        }
    }

    private fun collectOutput(started: Process) {
        Thread(
            {
                started.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        DesktopLogger.info("Xray", line)
                        synchronized(this) {
                            outputLines.addLast(line)
                            while (outputLines.size > MaxStoredOutputLines) outputLines.removeFirst()
                        }
                    }
                }
            },
            "skipi-xray-output",
        ).apply {
            isDaemon = true
            start()
        }
    }

    private companion object {
        const val MaxStoredOutputLines = 40

        fun defaultConfigDirectory(): Path {
            val base = System.getenv("APPDATA")
                ?.takeIf(String::isNotBlank)
                ?: System.getProperty("user.home")
            return Path.of(base, "SKIPI", "runtime")
        }
    }
}

internal fun xrayCommand(
    runtime: DesktopXrayRuntime,
    configPath: Path,
    testOnly: Boolean = false,
): List<String> = buildList {
    add(runtime.executable.toString())
    add("run")
    if (testOnly) add("-test")
    add("-c")
    add(configPath.toString())
}
