// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package desktop

import features.proxy.server.model.ProxyServer
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.importer.importPayloads
import features.proxy.server.usecase.importer.parseProxyServersFromPayloads
import features.subscription.runtime.subscriptionDeviceHeaders
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DesktopSubscriptionManager(
    private val hwid: String = "desktop-${System.getProperty("user.name") ?: "pc"}",
) {
    private val client = HttpClient(CIO)

    suspend fun fetchAndParse(
        url: String,
        userAgent: String = "SKIPI/0.1.0/Desktop",
    ): List<ProxyServer<*>> = withContext(Dispatchers.IO) {
        val devHeaders = subscriptionDeviceHeaders(hwid)
        val responseText = client.get(url) {
            headers {
                append("User-Agent", userAgent)
                devHeaders.forEach { (k, v) -> append(k, v) }
            }
        }.bodyAsText()

        val source = ProxyServerImportSource.SubscriptionUrl
        val importResult = parseProxyServersFromPayloads(
            payloads = responseText.importPayloads(source),
            context = ProxyServerImportContext(source = source),
        )
        importResult.servers
    }
}
