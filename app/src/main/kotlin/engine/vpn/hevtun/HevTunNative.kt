// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.vpn.hevtun

import androidx.annotation.Keep

internal const val HevTunNativeLibraryName = "hev-socks5-tunnel"

internal fun loadHevTunNativeLibrary(loadLibrary: (String) -> Unit) {
    loadLibrary(HevTunNativeLibraryName)
}

internal interface HevTunNativeGateway {
    fun startService(configPath: String, fd: Int): Boolean
    fun stopService(): Boolean
    fun isRunning(): Boolean
    fun isReady(): Boolean
    fun getStats(): LongArray = longArrayOf(0, 0, 0, 0)
}

@Keep
internal object HevTunNative : HevTunNativeGateway {
    init {
        loadHevTunNativeLibrary { libraryName -> System.loadLibrary(libraryName) }
    }

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStartService(configPath: String, fd: Int): Boolean

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyStopService(): Boolean

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyIsRunning(): Boolean

    @JvmStatic
    @Suppress("FunctionName")
    private external fun TProxyIsReady(): Boolean

    @JvmStatic
    @Keep
    @Suppress("FunctionName")
    private external fun TProxyGetStats(): LongArray

    override fun startService(configPath: String, fd: Int): Boolean {
        return TProxyStartService(configPath, fd)
    }

    override fun stopService(): Boolean {
        return TProxyStopService()
    }

    override fun isRunning(): Boolean {
        return TProxyIsRunning()
    }

    override fun isReady(): Boolean {
        return TProxyIsReady()
    }

    override fun getStats(): LongArray {
        return TProxyGetStats()
    }
}
