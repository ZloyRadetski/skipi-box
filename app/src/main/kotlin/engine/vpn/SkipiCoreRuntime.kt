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

    fun start(
        context: Context,
        config: VpnServiceStartConfig,
        tunFd: Int,
    ) {
        require(config.dataDir.isNotBlank()) {
            context.getString(R.string.error_skipi_core_data_dir_missing)
        }
        context.initializeAndroidXrayCoreEnvironment(config.dataDir)
        val controller = Skipicore.newCoreController(SkipiCoreCallbackHandler())
        runCatching {
            controller.startLoop(config.xrayConfigJson, tunFd.toLong())
        }.onFailure { error ->
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
    }

    fun stop() {
        val controller = coreController ?: return
        runCatching {
            controller.stopLoop()
        }.onFailure { error ->
            AndroidAppLogger.error(LogTag, "Failed to stop SKIPI Core", error)
        }
        coreController = null
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
