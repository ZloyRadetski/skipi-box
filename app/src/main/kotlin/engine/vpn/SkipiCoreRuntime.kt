// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn

import android.content.Context
import app.R
import features.logs.AndroidAppLogger
import engine.xray.initializeAndroidXrayCoreEnvironment
import app.skipi.core.skipicore.CoreCallbackHandler
import app.skipi.core.skipicore.CoreController
import app.skipi.core.skipicore.Skipicore

internal object SkipiCoreRuntime {
    private var coreController: CoreController? = null
    @Volatile
    var activeOlcRtcBridge: ActiveOlcRtcBridge? = null
        private set

    fun start(
        context: Context,
        config: VpnServiceStartConfig,
        tunFd: Int,
    ) {
        require(config.dataDir.isNotBlank()) {
            context.getString(R.string.error_skipi_core_data_dir_missing)
        }
        context.initializeAndroidXrayCoreEnvironment(config.dataDir)
        if (!config.olcRtcConfigYaml.isNullOrBlank() && config.olcRtcSocksPort > 0) {
            runCatching {
                startOlcRtc(config.olcRtcConfigYaml, config.olcRtcSocksPort)
            }.onFailure { error ->
                stopOlcRtc()
                activeOlcRtcBridge = null
                throw IllegalStateException(
                    context.getString(R.string.error_skipi_core_start_failed, error.readableMessage()),
                    error,
                )
            }
        }
        val controller = Skipicore.newCoreController(SkipiCoreCallbackHandler())
        runCatching {
            controller.startLoop(config.xrayConfigJson, tunFd.toLong())
        }.onFailure { error ->
            stopOlcRtc()
            activeOlcRtcBridge = null
            runCatching { controller.stopLoop() }
                .onFailure { stopError ->
                    AndroidAppLogger.warn(LogTag, "Failed to stop SKIPI Core after start failure", stopError)
                }
            throw IllegalStateException(
                context.getString(R.string.error_skipi_core_start_failed, error.readableMessage()),
                error,
            )
        }
        coreController = controller
        activeOlcRtcBridge = config.activeOlcRtcBridge
    }

    fun stop() {
        stopOlcRtc()
        activeOlcRtcBridge = null
        val controller = coreController ?: return
        runCatching {
            controller.stopLoop()
        }.onFailure { error ->
            AndroidAppLogger.error(LogTag, "Failed to stop SKIPI Core", error)
        }
        coreController = null
    }

    fun setSocketProtector(protector: (Int) -> Boolean) {
        runCatching {
            val protectorClass = Class.forName("app.skipi.core.skipicore.SocketProtector")
            val proxyInstance = java.lang.reflect.Proxy.newProxyInstance(
                protectorClass.classLoader,
                arrayOf(protectorClass),
            ) { _, method, args ->
                if (method.name == "protect" && args != null && args.isNotEmpty()) {
                    val fd = (args[0] as Number).toInt()
                    protector(fd)
                } else {
                    false
                }
            }
            Skipicore::class.java.methods.firstOrNull { it.name == "setOlcRtcSocketProtector" }
                ?.invoke(null, proxyInstance)
            Skipicore::class.java.methods.firstOrNull { it.name == "setAmneziaWgSocketProtector" }
                ?.invoke(null, proxyInstance)
            AndroidAppLogger.info(LogTag, "Configured socket protectors for olcRTC and AmneziaWG")
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to set socket protector on Skipicore", error)
        }
    }

    fun startOlcRtc(configYaml: String, socksPort: Int) {
        val method = Skipicore::class.java.methods.firstOrNull { it.name == "startOlcRtc" }
            ?: run {
                AndroidAppLogger.warn(LogTag, "startOlcRtc not found in Skipicore library")
                return
            }
        try {
            if (method.parameterTypes.size == 2) {
                val portArg: Any = if (method.parameterTypes[1] == Long::class.javaPrimitiveType || method.parameterTypes[1] == Long::class.javaObjectType) {
                    socksPort.toLong()
                } else {
                    socksPort
                }
                method.invoke(null, configYaml, portArg)
            } else {
                method.invoke(null, configYaml)
            }
            AndroidAppLogger.info(LogTag, "OLCRTC started on socks port $socksPort")
        } catch (e: java.lang.reflect.InvocationTargetException) {
            val cause = e.targetException ?: e
            AndroidAppLogger.error(LogTag, "Failed to start OLCRTC", cause)
            throw cause
        } catch (e: Throwable) {
            AndroidAppLogger.error(LogTag, "Failed to start OLCRTC", e)
            throw e
        }
    }

    fun stopOlcRtc() {
        runCatching {
            val method = Skipicore::class.java.methods.firstOrNull { it.name == "stopOlcRtc" }
            method?.invoke(null)
            AndroidAppLogger.info(LogTag, "OLCRTC stopped")
        }.onFailure { error ->
            AndroidAppLogger.warn(LogTag, "Failed to stop OLCRTC", error)
        }
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

private class SkipiCoreCallbackHandler : CoreCallbackHandler {
    override fun startup(): Long {
        AndroidAppLogger.info("SkipiCore", "SKIPI Core started")
        return 0
    }

    override fun shutdown(): Long {
        AndroidAppLogger.info("SkipiCore", "SKIPI Core stopped")
        return 0
    }

    override fun onEmitStatus(code: Long, message: String?): Long {
        val text = message.orEmpty().ifBlank { "status code: $code" }
        AndroidAppLogger.info("SkipiCore", text)
        return 0
    }
}

private fun Throwable.readableMessage(): String {
    return message ?: javaClass.simpleName.orEmpty()
}
