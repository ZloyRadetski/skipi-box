// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import app.R
import features.logs.AppLog
import engine.xray.initializeAndroidXrayCoreEnvironment
import app.skipi.core.skipicore.CoreCallbackHandler
import app.skipi.core.skipicore.CoreController
import app.skipi.core.skipicore.Skipicore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreTrafficSnapshot
import platform.parseCoreTrafficSnapshot

internal const val NativeBridgeReadinessTimeoutMillis = 8_000L
private const val NativeBridgeReadinessPollIntervalMillis = 200L
private const val NativeBridgeSocksConnectTimeoutMillis = 300

internal object SkipiCoreRuntime {
    @Volatile
    private var coreController: CoreController? = null
    @Volatile
    var activeAmneziaWgBridge: ActiveAmneziaWgBridge? = null
        private set
    @Volatile
    private var amneziaWgSocketProtectorConfigured = false

    suspend fun start(
        context: Context,
        config: VpnServiceStartConfig,
        tunFd: Int,
    ) {
        require(config.dataDir.isNotBlank()) {
            context.getString(R.string.error_skipi_core_data_dir_missing)
        }
        context.initializeAndroidXrayCoreEnvironment(config.dataDir)
        try {
            if (!config.amneziaWgConfigJson.isNullOrBlank() && config.amneziaWgSocksPort > 0) {
                startAmneziaWg(config.amneziaWgConfigJson, config.amneziaWgSocksPort)
                check(awaitAmneziaWgReady(config.amneziaWgSocksPort)) {
                    "AmneziaWG local SOCKS listener did not become ready within ${NativeBridgeReadinessTimeoutMillis}ms"
                }
            }
        } catch (error: Throwable) {
            stopNativeBridges()
            if (error is CancellationException) throw error
            throw IllegalStateException(
                context.getString(R.string.error_skipi_core_start_failed, error.readableMessage()),
                error,
            )
        }
        val controller = Skipicore.newCoreController(SkipiCoreCallbackHandler())
        runCatching {
            controller.startLoop(config.xrayConfigJson, tunFd.toLong())
        }.onFailure { error ->
            stopNativeBridges()
            runCatching { controller.stopLoop() }
                .onFailure { stopError ->
                    AppLog.warn(LogTag, "Failed to stop SKIPI Core after start failure", stopError)
                }
            throw IllegalStateException(
                context.getString(R.string.error_skipi_core_start_failed, error.readableMessage()),
                error,
            )
        }
        coreController = controller
        activeAmneziaWgBridge = config.activeAmneziaWgBridge
    }

    fun stop() {
        stopNativeBridges()
        val controller = coreController ?: return
        runCatching {
            controller.stopLoop()
        }.onFailure { error ->
            AppLog.error(LogTag, "Failed to stop SKIPI Core", error)
        }
        coreController = null
    }

    fun setSocketProtector(protector: (Int) -> Boolean) {
        runCatching {
            val proxyInstance = createSocketProtector(protector)
            amneziaWgSocketProtectorConfigured = installSocketProtector("setAmneziaWgSocketProtector", proxyInstance)
            if (amneziaWgSocketProtectorConfigured) {
                AppLog.info(LogTag, "Configured the AmneziaWG socket protector")
            } else {
                AppLog.warn(LogTag, "AmneziaWG socket-protector API is unavailable in the current SkipiCore build")
            }
        }.onFailure { error ->
            amneziaWgSocketProtectorConfigured = false
            AppLog.warn(LogTag, "Failed to set socket protector on Skipicore", error)
        }
    }

    private fun createSocketProtector(protector: (Int) -> Boolean): Any {
        val protectorClass = Class.forName("app.skipi.core.skipicore.SocketProtector")
        return java.lang.reflect.Proxy.newProxyInstance(
            protectorClass.classLoader,
            arrayOf(protectorClass),
        ) { _, method, args ->
            if (method.name == "protect" && args != null && args.isNotEmpty()) {
                protector((args[0] as Number).toInt())
            } else {
                false
            }
        }
    }

    private fun installSocketProtector(methodName: String, proxyInstance: Any): Boolean {
        val method = Skipicore::class.java.methods.firstOrNull { it.name == methodName } ?: return false
        return runCatching {
            method.invoke(null, proxyInstance)
            true
        }.onFailure { error ->
            AppLog.warn(LogTag, "Failed to invoke $methodName", error)
        }.getOrDefault(false)
    }

    /** Starts the native AWG runner and its private local SOCKS listener. */
    fun startAmneziaWg(
        configJson: String,
        socksPort: Int,
        requireSocketProtector: Boolean = true,
    ) {
        check(!requireSocketProtector || amneziaWgSocketProtectorConfigured) {
            "The loaded SkipiCore does not support AmneziaWG socket protection; refusing to create a VPN routing loop"
        }
        if (!requireSocketProtector) {
            check(installSocketProtector("setAmneziaWgSocketProtector", createSocketProtector { true })) {
                "The loaded SkipiCore does not support AmneziaWG socket protection"
            }
        }
        val method = Skipicore::class.java.methods.firstOrNull { it.name == "startAmneziaWg" }
            ?: error("startAmneziaWg is not available in the loaded SkipiCore library")
        try {
            invokeStartWithPort(method, configJson, socksPort)
            AppLog.info(LogTag, "AmneziaWG started on socks port $socksPort")
        } catch (error: java.lang.reflect.InvocationTargetException) {
            val cause = error.targetException ?: error
            AppLog.error(LogTag, "Failed to start AmneziaWG", cause)
            throw cause
        } catch (error: Throwable) {
            AppLog.error(LogTag, "Failed to start AmneziaWG", error)
            throw error
        }
    }

    fun stopAmneziaWg() {
        runCatching {
            val method = Skipicore::class.java.methods.firstOrNull { it.name == "stopAmneziaWg" }
            if (method != null) {
                method.invoke(null)
                AppLog.info(LogTag, "AmneziaWG stopped")
            }
        }.onFailure { error ->
            AppLog.warn(LogTag, "Failed to stop AmneziaWG", error)
        }
    }

    private fun invokeStartWithPort(method: java.lang.reflect.Method, config: String, socksPort: Int) {
        if (method.parameterTypes.size == 2) {
            val portArg: Any = if (
                method.parameterTypes[1] == Long::class.javaPrimitiveType ||
                method.parameterTypes[1] == Long::class.javaObjectType
            ) {
                socksPort.toLong()
            } else {
                socksPort
            }
            method.invoke(null, config, portArg)
        } else {
            method.invoke(null, config)
        }
    }

    private fun stopNativeBridges() {
        stopAmneziaWg()
        activeAmneziaWgBridge = null
    }

    internal suspend fun awaitAmneziaWgReady(
        port: Int,
        timeoutMs: Long = NativeBridgeReadinessTimeoutMillis,
    ): Boolean = awaitLocalSocksReady("AmneziaWG", port, timeoutMs)

    private suspend fun awaitLocalSocksReady(
        runtimeName: String,
        port: Int,
        timeoutMs: Long,
    ): Boolean {
        AppLog.info(LogTag, "Waiting for $runtimeName local SOCKS 127.0.0.1:$port to accept connections (timeout ${timeoutMs}ms)")
        val ready = awaitNativeBridgeReadiness(timeoutMs) {
            withContext(Dispatchers.IO) {
                try {
                    java.net.Socket().use { socket ->
                        socket.connect(
                            java.net.InetSocketAddress("127.0.0.1", port),
                            NativeBridgeSocksConnectTimeoutMillis,
                        )
                    }
                    true
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
            }
        }
        if (ready) {
            AppLog.info(LogTag, "$runtimeName local SOCKS 127.0.0.1:$port is ready")
        } else {
            AppLog.warn(LogTag, "$runtimeName local SOCKS 127.0.0.1:$port did not become ready within ${timeoutMs}ms")
        }
        return ready
    }

    fun isRunning(): Boolean {
        return coreController?.isRunning == true
    }

    fun readMemoryStats(): String {
        return runCatching {
            val method = Skipicore::class.java.getMethod("readMemoryStats")
            method.invoke(null) as? String
        }.getOrNull().orEmpty()
    }

    /** Returns Xray traffic counters without creating an Xray API socket. */
    fun queryTrafficStats(): CoreTrafficSnapshot? {
        val controller = coreController ?: return null
        return parseCoreTrafficSnapshot(controller.queryTrafficStats())
    }

    fun forceFreeMemory() {
        runCatching {
            val method = Skipicore::class.java.getMethod("forceFreeMemory")
            method.invoke(null)
        }
    }

    fun fastSelectBestOutbound(
        configJson: String,
        candidateTags: String,
        probeUrl: String = "",
        timeoutMs: Long = 1200L,
    ): String {
        return runCatching {
            val method = Skipicore::class.java.getMethod(
                "fastSelectBestOutbound",
                String::class.java,
                String::class.java,
                String::class.java,
                Long::class.javaPrimitiveType,
            )
            method.invoke(null, configJson, candidateTags, probeUrl, timeoutMs) as? String
        }.getOrNull().orEmpty()
    }

    private const val LogTag = "SkipiCore"
}

/**
 * Waits for a native bridge's local SOCKS listener with one bounded, cancellable deadline.
 *
 * Xray only needs the local listener, so the application owns a single cooperative
 * readiness check rather than a blocking call into the core that could outlive the VPN caller timeout.
 */
internal suspend fun awaitNativeBridgeReadiness(
    timeoutMs: Long,
    pollIntervalMs: Long = NativeBridgeReadinessPollIntervalMillis,
    isReady: suspend () -> Boolean,
): Boolean {
    require(timeoutMs > 0) { "timeoutMs must be positive" }
    require(pollIntervalMs > 0) { "pollIntervalMs must be positive" }
    return withTimeoutOrNull<Boolean>(timeoutMs) {
        while (!isReady()) {
            delay(pollIntervalMs)
        }
        true
    } ?: false
}

private class SkipiCoreCallbackHandler : CoreCallbackHandler {
    override fun startup(): Long {
        AppLog.info("SkipiCore", "SKIPI Core started")
        return 0
    }

    override fun shutdown(): Long {
        AppLog.info("SkipiCore", "SKIPI Core stopped")
        return 0
    }

    override fun onEmitStatus(code: Long, message: String?): Long {
        val text = message.orEmpty().ifBlank { "status code: $code" }
        AppLog.info("SkipiCore", text)
        return 0
    }
}

private fun Throwable.readableMessage(): String {
    return message ?: javaClass.simpleName.orEmpty()
}
