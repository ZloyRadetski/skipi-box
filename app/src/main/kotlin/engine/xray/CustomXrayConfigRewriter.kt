// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import app.effectiveLocalDnsEnabled
import features.proxy.server.model.Custom
import features.proxy.server.model.customXrayConfigProxyServerHosts
import features.proxy.server.model.parseCustomXrayConfigJsonObject

/** Android boundary for the shared raw-profile DNS rewriter. */
internal object CustomXrayConfigRewriter {
    fun rewrite(
        request: XrayConfigRequest,
        server: Custom,
    ) = parseCustomXrayConfigJsonObject(server.configJson).let { config ->
        if (!server.overrideInboundAndDns) {
            config
        } else {
            val startupProxyServerDomains = customXrayConfigProxyServerHosts(server.configJson)
                .startupProxyServerHostDnsDomains()
            val dnsPlan = request.buildXrayDnsPlan(startupProxyServerDomains)
            rewriteCustomXrayDnsConfig(
                CustomXrayDnsRewriteRequest(
                    config = config,
                    inbounds = request.inbounds,
                    dnsPlan = dnsPlan,
                    enableLocalDns = request.appState.effectiveLocalDnsEnabled,
                    dnsHijackInboundTags = request.dnsHijackInboundTags,
                    directOutbound = buildFreedomOutbound(
                        tag = XrayTags.DIRECT,
                        domainStrategy = request.appState.xrayDirectOutboundDomainStrategy(),
                        appState = request.appState,
                    ),
                ),
            )
        }
    }
}
