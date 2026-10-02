// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.proxy

import features.proxy.server.model.Custom
import features.proxy.server.model.HTTP
import features.proxy.server.model.StrategyGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProxyServerTextCopyTest {
    @Test
    fun defaultCopyUsesPortableTextAndDoesNotExportJsonUnnecessarily() = kotlinx.coroutines.test.runTest {
        var exportCalls = 0
        val customJson = "{\"outbounds\":[] }"

        val custom = copyProxyServerText(Custom(configJson = customJson)) {
            exportCalls++
            "full-json"
        }
        val url = copyProxyServerText(HTTP(server = "proxy.example", port = "8080")) {
            exportCalls++
            "full-json"
        }

        assertEquals(ProxyServerTextCopyResult.Success(customJson), custom)
        assertEquals(ProxyServerTextCopyResult.Success("http://proxy.example:8080"), url)
        assertEquals(0, exportCalls)
    }

    @Test
    fun explicitFormatsAndCompositeDefaultUseExporterAndMapFailures() = kotlinx.coroutines.test.runTest {
        var exportCalls = 0
        val exporter: suspend () -> String = {
            exportCalls++
            "{\"outbounds\":[{\"tag\":\"proxy\"}]}"
        }

        val fullJson = copyProxyServerText(HTTP(), ProxyServerTextCopyFormat.FullJson, exporter)
        val composite = copyProxyServerText(StrategyGroup(remarks = "balanced"), exportFullJson = exporter)
        val failed = copyProxyServerText(StrategyGroup(), exportFullJson = { error("invalid config") })

        assertEquals(ProxyServerTextCopyResult.Success("{\"outbounds\":[{\"tag\":\"proxy\"}]}"), fullJson)
        assertIs<ProxyServerTextCopyResult.Success>(composite)
        assertEquals(ProxyServerTextCopyResult.InvalidConfig, failed)
        assertEquals(2, exportCalls)
    }
}
