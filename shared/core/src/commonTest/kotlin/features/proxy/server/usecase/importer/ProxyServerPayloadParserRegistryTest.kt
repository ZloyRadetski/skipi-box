// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.usecase.importer

import features.proxy.server.model.HTTP
import features.proxy.server.usecase.ProxyServerImportContext
import features.proxy.server.usecase.ProxyServerImportSource
import features.proxy.server.usecase.ProxyServerPayloadParser
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProxyServerPayloadParserRegistryTest {
    @Test
    fun standardRouteImportsUrlOnceAndRetainsFailureOrdering() {
        val validUrl = "http://proxy.example:8080"
        val invalidUrl = "vless://invalid"
        val failures = mutableListOf<ProxyServerUrlImportFailure>()
        val parsers = standardProxyServerPayloadParsers(
            mihomoYamlParser = { _, _ ->
                features.proxy.server.usecase.EmptyProxyServerImportResult
            },
            onUrlFailure = { _, failure -> failures += failure },
        )

        val result = runImmediateSuspend {
            importProxyServerPayloadText(
            text = "$invalidUrl\n$validUrl\n$invalidUrl\n$validUrl",
            context = ProxyServerImportContext(ProxyServerImportSource.Clipboard),
            parsers = parsers,
            )
        }

        assertEquals(2, result.urlCount)
        assertEquals(listOf(invalidUrl), failures.map(ProxyServerUrlImportFailure::url))
        assertEquals(listOf(0), failures.map(ProxyServerUrlImportFailure::index))
        assertIs<HTTP>(result.servers.single())
    }

    @Test
    fun earlierHostParserWinsBeforeUrlRecognition() {
        val yamlResult = features.proxy.server.usecase.ProxyServerImportResult(
            urlCount = 1,
            servers = listOf(HTTP(remarks = "host-parser")),
        )
        var yamlCalls = 0
        val parsers: List<ProxyServerPayloadParser> = standardProxyServerPayloadParsers(
            mihomoYamlParser = { _, _ ->
                yamlCalls++
                yamlResult
            },
        )

        val result = runImmediateSuspend {
            parseProxyServerPayloads(
            payloads = listOf("http://proxy.example:8080"),
            context = ProxyServerImportContext(ProxyServerImportSource.Clipboard),
            parsers = parsers,
            )
        }

        assertEquals(1, yamlCalls)
        assertEquals(yamlResult, result)
    }

    private fun <T> runImmediateSuspend(block: suspend () -> T): T {
        var completion: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                completion = result
            }
        })
        return checkNotNull(completion) { "Test operation suspended unexpectedly" }.getOrThrow()
    }
}
