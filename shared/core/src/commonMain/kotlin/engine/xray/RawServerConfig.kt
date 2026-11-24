// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package engine.xray

import features.proxy.server.model.Custom
import features.proxy.server.model.customXrayConfigProxyServerHosts
import features.proxy.server.model.formatCustomXrayConfigJson
import features.proxy.server.model.parseCustomXrayConfigJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Everything a platform tells the one builder of a raw JSON server's config. It lives in shared/core because the
 * rewriter, the strict tunnel, the stats config and the JSON formatting it combines are all here; the platforms
 * only decide what these values are.
 */
class RawServerConfigInput(
    val server: Custom,
    /** The application's own listeners (or the tunnel's); replace the JSON's inbounds when it overrides them. */
    val inbounds: List<JsonObject>,
    /** The application's log section; a pasted profile never decides logging. */
    val log: JsonObject,
    /** The DNS plan from the hosts to resolve before the tunnel is up (the raw server's, plus the routing extras'). */
    val buildDnsPlan: (startupProxyServerDomains: List<String>) -> XrayDnsPlan,
    val directOutbound: JsonObject,
    val enableLocalDns: Boolean,
    val dnsHijackInboundTags: List<String>,
    /** SKIPI's rules for the server, applied only when it overrides inbound and DNS. */
    val skipiRouting: CustomSkipiRouting? = null,
    val strictFullTunnel: Boolean = false,
    val collectTrafficStats: Boolean = false,
    val layout: RawServerConfigLayout,
)

/** How the finished config is laid out; the two platforms' started configs differ in it, byte for byte. */
enum class RawServerConfigLayout {
    /** Keys keep the JSON's order, compact, one trailing LF; without the override the JSON's own inbounds stay. */
    Compact,

    /** `log` and `inbounds` first, the rest in the JSON's order, pretty-printed; the application's inbounds always apply. */
    Pretty,
}

class RawServerConfigResult(
    val json: String,
    /** The SKIPI rules not applied as written; see [CustomSkipiRouting.unappliedRules]. */
    val unappliedRules: List<String> = emptyList(),
)

fun buildRawServerConfig(input: RawServerConfigInput): RawServerConfigResult {
    val customJson = parseCustomXrayConfigJsonObject(input.server.configJson)
    var unapplied = emptyList<String>()
    val rewritten = if (input.server.overrideInboundAndDns) {
        val startupProxyServerDomains = (
            customXrayConfigProxyServerHosts(input.server.configJson) +
                input.skipiRouting?.extras?.invoke(XrayTags.PROXY)?.serverHosts.orEmpty()
            ).startupProxyServerHostDnsDomains()
        val result = rewriteCustomXrayDnsConfigWithReport(
            CustomXrayDnsRewriteRequest(
                config = customJson,
                inbounds = input.inbounds,
                dnsPlan = input.buildDnsPlan(startupProxyServerDomains),
                enableLocalDns = input.enableLocalDns,
                dnsHijackInboundTags = input.dnsHijackInboundTags,
                directOutbound = input.directOutbound,
                skipiRouting = input.skipiRouting,
            ),
        )
        unapplied = result.unappliedRules
        result.config
    } else {
        customJson
    }
    val routed = if (input.strictFullTunnel) rewritten.withStrictFullTunnelRouting() else rewritten
    val json = when (input.layout) {
        RawServerConfigLayout.Compact -> {
            val withLog = routed.updated { put("log", input.log) }
            val withStats = if (input.collectTrafficStats) withLog.withXrayTrafficStatsConfig() else withLog
            XrayConfigJson.encodeToString(withStats).withSingleTrailingLf()
        }

        RawServerConfigLayout.Pretty -> {
            val withStats = if (input.collectTrafficStats) routed.withXrayTrafficStatsConfig() else routed
            formatCustomXrayConfigJson(
                buildJsonObject {
                    put("log", input.log)
                    put("inbounds", JsonArray(input.inbounds))
                    withStats.forEach { (key, value) ->
                        if (key != "inbounds" && key != "log") put(key, value)
                    }
                },
            )
        }
    }
    return RawServerConfigResult(json, unapplied)
}
