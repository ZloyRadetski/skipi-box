// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

/** HTTP GET over the host-chosen [route]; any failure or non-200 answer is reported as null. */
class JvmDiagnosticsHttpClient(
    private val route: DiagnosticsRoute,
    private val binding: DiagnosticsNetworkBinding = DiagnosticsNetworkBinding.None,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onFailure: (url: String, error: Throwable) -> Unit = { _, _ -> },
) : DiagnosticsHttpClient, DiagnosticsLatencyProbe {
    override suspend fun getText(url: String, accept: String): String? = withContext(ioDispatcher) {
        runCatching {
            route.withSocksAuthenticator {
                val connection = open(url)
                try {
                    connection.connectTimeout = ConnectTimeoutMillis
                    connection.readTimeout = ReadTimeoutMillis
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", RequestUserAgent)
                    connection.setRequestProperty("Accept", accept)
                    connection.connect()
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        null
                    } else {
                        connection.inputStream.bufferedReader().use { it.readText() }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }.getOrElse { error ->
            onFailure(url, error)
            null
        }
    }

    override suspend fun measureMillis(url: String, timeoutMillis: Long): Long? = withContext(ioDispatcher) {
        runCatching {
            route.withSocksAuthenticator {
                val connection = open(url)
                try {
                    connection.connectTimeout = timeoutMillis.toInt()
                    connection.readTimeout = timeoutMillis.toInt()
                    connection.instanceFollowRedirects = false
                    connection.setRequestProperty("User-Agent", RequestUserAgent)
                    val startedAt = System.nanoTime()
                    val code = connection.responseCode
                    val elapsed = (System.nanoTime() - startedAt) / 1_000_000
                    if (code in 200..399) elapsed else null
                } finally {
                    connection.disconnect()
                }
            }
        }.getOrElse { error ->
            onFailure(url, error)
            null
        }
    }

    private fun open(url: String): HttpURLConnection {
        val target = URI(url).toURL()
        return when (route) {
            is DiagnosticsRoute.Socks -> target.openConnection(route.javaProxy()) as HttpURLConnection
            DiagnosticsRoute.Direct ->
                runCatching { binding.openConnection(target) }.getOrNull()
                    ?: target.openConnection() as HttpURLConnection
        }
    }

    private companion object {
        const val ConnectTimeoutMillis = 10_000
        const val ReadTimeoutMillis = 15_000
        const val RequestUserAgent =
            "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}
