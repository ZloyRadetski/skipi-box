// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import engine.hevtun.HevSocks5TunnelConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HevTunRuntimeTest {
    @Test
    fun start_waits_for_the_native_initialization_signal() = runBlocking {
        val initialized = CompletableDeferred<Unit>()
        var running = false
        val gateway = object : HevTunNativeGateway {
            override fun startService(configPath: String, fd: Int): Boolean {
                running = true
                return true
            }

            override fun stopService(): Boolean {
                running = false
                return true
            }

            override fun isRunning(): Boolean = running
            override fun isReady(): Boolean = initialized.isCompleted
        }
        val config = testConfig("hevtun-ready-test")
        val runtime = HevTunRuntime(gateway, readinessTimeoutMillis = 1_000L, readinessPollIntervalMillis = 1L)

        try {
            val start = async(start = CoroutineStart.UNDISPATCHED) {
                runtime.start(config, tunFd = 123)
            }

            assertFalse(start.isCompleted, "Running alone must not report the tunnel initialized")
            initialized.complete(Unit)
            start.await()
            runtime.stop()
        } finally {
            File(config.configPath).parentFile?.deleteRecursively()
        }
    }

    @Test
    fun readiness_fails_when_native_thread_exits_before_initialization() = runBlocking {
        val ready = awaitHevTunReadiness(
            isRunning = { false },
            isReady = { false },
            timeoutMillis = 100L,
            pollIntervalMillis = 1L,
        )

        assertFalse(ready)
    }

    @Test
    fun readiness_fails_when_thread_exits_during_polling() = runBlocking {
        var checks = 0
        val ready = awaitHevTunReadiness(
            isRunning = { ++checks < 3 },
            isReady = { false },
            timeoutMillis = 100L,
            pollIntervalMillis = 1L,
        )

        assertFalse(ready)
    }

    @Test
    fun start_succeeds_after_gateway_initialization_and_stops_cleanly() = runBlocking {
        val tempDir = createTempDirectory("hevtun-test").toFile()
        try {
            val configFile = File(tempDir, "tun.yml")
            val logFile = File(tempDir, "tun.log")
            val config = HevSocks5TunnelConfig(
                configPath = configFile.absolutePath,
                logPath = logFile.absolutePath,
                socksAddress = "127.0.0.1",
                socksPort = 10808,
                mtu = 1500,
                ipv4Address = "172.19.0.1",
                ipv6Address = null,
            )

            var running = false
            var initialized = false
            val gateway = object : HevTunNativeGateway {
                override fun startService(configPath: String, fd: Int): Boolean {
                    running = true
                    initialized = true
                    return true
                }

                override fun stopService(): Boolean {
                    running = false
                    return true
                }

                override fun isRunning(): Boolean = running

                override fun isReady(): Boolean = running && initialized
            }

            val runtime = HevTunRuntime(
                nativeGateway = gateway,
                readinessTimeoutMillis = 100L,
                readinessPollIntervalMillis = 1L,
            )

            runtime.start(config, tunFd = 123)
            assertTrue(runtime.isRunning())
            runtime.stop()
            assertFalse(runtime.isRunning())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun start_fails_and_cleans_up_when_gateway_fails_readiness() = runBlocking {
        val tempDir = createTempDirectory("hevtun-test-fail").toFile()
        try {
            val configFile = File(tempDir, "tun.yml")
            val logFile = File(tempDir, "tun.log")
            val config = HevSocks5TunnelConfig(
                configPath = configFile.absolutePath,
                logPath = logFile.absolutePath,
                socksAddress = "127.0.0.1",
                socksPort = 10808,
                mtu = 1500,
                ipv4Address = "172.19.0.1",
                ipv6Address = null,
            )

            var stopCalled = false
            val gateway = object : HevTunNativeGateway {
                override fun startService(configPath: String, fd: Int): Boolean = true
                override fun stopService(): Boolean {
                    stopCalled = true
                    return true
                }
                override fun isRunning(): Boolean = false
                override fun isReady(): Boolean = false
            }

            val runtime = HevTunRuntime(
                nativeGateway = gateway,
                readinessTimeoutMillis = 50L,
                readinessPollIntervalMillis = 1L,
            )

            val error = assertFailsWith<IllegalStateException> {
                runtime.start(config, tunFd = 123)
            }
            assertTrue(error.message?.contains("Hev TUN native service did not become ready") == true)
            assertTrue(stopCalled)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun start_times_out_and_stops_a_native_worker_that_never_initializes() = runBlocking {
        var stopCalls = 0
        var running = false
        val gateway = object : HevTunNativeGateway {
            override fun startService(configPath: String, fd: Int): Boolean {
                running = true
                return true
            }

            override fun stopService(): Boolean {
                stopCalls += 1
                running = false
                return true
            }

            override fun isRunning(): Boolean = running
            override fun isReady(): Boolean = false
        }
        val runtime = HevTunRuntime(gateway, readinessTimeoutMillis = 25L, readinessPollIntervalMillis = 1L)

        val config = testConfig("hevtun-timeout-test")
        try {
            assertFailsWith<IllegalStateException> {
                runtime.start(config, tunFd = 123)
            }

            assertEquals(1, stopCalls)
            assertFalse(runtime.isRunning())
        } finally {
            File(config.configPath).parentFile?.deleteRecursively()
        }
    }

    @Test
    fun start_propagates_native_log_diagnostics_on_readiness_failure() = runBlocking {
        val tempDir = createTempDirectory("hevtun-diag-test").toFile()
        try {
            val configFile = File(tempDir, "tun.yml")
            val logFile = File(tempDir, "tun.log")
            logFile.writeText("ERROR: failed to configure TUN non-blocking ioctl\n")

            val config = HevSocks5TunnelConfig(
                configPath = configFile.absolutePath,
                logPath = logFile.absolutePath,
                socksAddress = "127.0.0.1",
                socksPort = 10808,
                mtu = 1500,
                ipv4Address = "172.19.0.1",
                ipv6Address = null,
            )

            val gateway = object : HevTunNativeGateway {
                override fun startService(configPath: String, fd: Int): Boolean = true
                override fun stopService(): Boolean = true
                override fun isRunning(): Boolean = false
                override fun isReady(): Boolean = false
            }

            val runtime = HevTunRuntime(
                nativeGateway = gateway,
                readinessTimeoutMillis = 50L,
                readinessPollIntervalMillis = 1L,
            )

            val error = assertFailsWith<IllegalStateException> {
                runtime.start(config, tunFd = 123)
            }
            assertTrue(error.message?.contains("ERROR: failed to configure TUN non-blocking ioctl") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun readHevTunDiagnostics_handles_missing_empty_and_truncated_files() {
        assertEquals("", readHevTunDiagnostics(""))
        assertEquals("", readHevTunDiagnostics("/path/does/not/exist/tun.log"))

        val tempFile = File.createTempFile("diag-test", ".log")
        try {
            tempFile.writeText("")
            assertEquals("", readHevTunDiagnostics(tempFile.absolutePath))

            tempFile.writeText("Line 1\nLine 2\nLine 3")
            assertEquals("Line 1\nLine 2\nLine 3", readHevTunDiagnostics(tempFile.absolutePath))

            val longContent = "A".repeat(3000) + "\nTail line"
            val read = readHevTunDiagnostics(tempFile.apply { writeText(longContent) }.absolutePath, maxChars = 100)
            assertTrue(read.endsWith("Tail line"))
            assertTrue(read.length <= 100)

            tempFile.writeText(
                "DEBUG socks5 client auth private-user:private-password\n" +
                    "INFO socks5 server auth user: private-user pass: private-password\n" +
                    "ERROR: failed to initialize tunnel\n",
            )
            val safeDiagnostics = readHevTunDiagnostics(tempFile.absolutePath)
            assertEquals("ERROR: failed to initialize tunnel", safeDiagnostics)
            assertFalse(safeDiagnostics.contains("private-user"))
            assertFalse(safeDiagnostics.contains("private-password"))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun native_library_load_error_is_propagated_without_replacement() {
        val expected = UnsatisfiedLinkError("missing native dependency")
        var requestedLibrary: String? = null

        val actual = assertFailsWith<UnsatisfiedLinkError> {
            loadHevTunNativeLibrary { libraryName ->
                requestedLibrary = libraryName
                throw expected
            }
        }

        assertEquals(HevTunNativeLibraryName, requestedLibrary)
        assertSame(expected, actual)
    }

    private fun testConfig(prefix: String): HevSocks5TunnelConfig {
        val directory = createTempDirectory(prefix).toFile()
        return HevSocks5TunnelConfig(
            configPath = File(directory, "tun.yml").absolutePath,
            logPath = File(directory, "tun.log").absolutePath,
            socksAddress = "127.0.0.1",
            socksPort = 10808,
            mtu = 1500,
            ipv4Address = "172.19.0.1",
            ipv6Address = null,
        )
    }
}
