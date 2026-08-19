// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object DesktopPingTester {
    suspend fun testTcp(host: String, port: Int, timeoutMs: Int = 3000): Long = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                System.currentTimeMillis() - start
            }
        }.getOrDefault(-1L)
    }
}
