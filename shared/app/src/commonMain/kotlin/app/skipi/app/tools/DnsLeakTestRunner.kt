// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.app.tools

import features.tools.dnsleak.DnsLeakAnalysis
import features.tools.dnsleak.DnsLeakExitInfo
import features.tools.dnsleak.DnsLeakSilentServer
import features.tools.dnsleak.DnsLeakStage
import features.tools.dnsleak.DnsLeakTestFailure
import features.tools.dnsleak.DnsLeakTestOutcome
import features.tools.dnsleak.DnsProbeResult
import features.tools.dnsleak.IpWhoIsResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

data class DnsLeakTestConfig(
    val echoDomain: String = "o-o.myaddr.l.google.com",
    val exitIpUrl: String = "https://api.ipify.org",
    val geoIpUrl: String = "https://ipwho.is/",
    val publicResolvers: List<String> = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
    /** Extra attempts for a silent HTTP endpoint or DNS server. */
    val probeAttempts: Int = 2,
    val httpAttempts: Int = 2,
    val retryDelayMillis: Long = 400L,
)

/**
 * Self-contained DNS leak detection without any third-party leak service.
 *
 * Every tested DNS path (the host's system servers plus well-known public resolvers) is asked
 * directly for Google's echo TXT record, which reveals the resolver egress and the EDNS Client
 * Subnet our queries expose. Those networks are compared with the HTTP exit network: a path
 * that resolves through a different network is a leak.
 */
class DnsLeakTestRunner(
    private val systemDnsServers: List<String>,
    private val dns: DnsTxtResolver,
    private val http: DiagnosticsHttpClient,
    private val hasVpnNetwork: Boolean,
    private val config: DnsLeakTestConfig = DnsLeakTestConfig(),
) {
    /**
     * Asks the exit and every DNS server at the same time, then locates each distinct address once.
     * [onStage] is told which of the two phases is under way.
     */
    suspend fun run(onStage: (DnsLeakStage) -> Unit = {}): DnsLeakTestOutcome = coroutineScope {
        onStage(DnsLeakStage.Probing)
        val exitIpLookup = async { fetchExitIp() }
        val probeRun = async { probeServers() }
        val exitIp = exitIpLookup.await()
        val probes = probeRun.await()

        onStage(DnsLeakStage.Locating)
        val geoByIp = fetchGeoData(
            (probes.mapNotNull(::observedIp) + listOfNotNull(exitIp)).distinct(),
        )
        val exit = exitIp?.let { ip -> exitInfo(ip, geoByIp[ip]) }
        val resolvers = DnsLeakAnalysis.resolvers(probes, geoByIp)
        if (exit == null && resolvers.isEmpty()) throw DnsLeakTestFailure(hasVpnNetwork)
        DnsLeakTestOutcome(
            resolvers = resolvers,
            verdict = DnsLeakAnalysis.verdict(resolvers, exit),
            exit = exit,
            silentServers = probes.filter { observedIp(it) == null }
                .map { DnsLeakSilentServer(it.server, it.isSystemServer) },
        )
    }

    private fun observedIp(probe: DnsProbeResult): String? =
        probe.clientSubnetIp ?: probe.egressIp.takeIf(String::isNotBlank)

    /** Probes every system and public resolver in parallel, retrying silent servers once. */
    private suspend fun probeServers(): List<DnsProbeResult> = coroutineScope {
        val servers = buildList {
            systemDnsServers.filter { it.isNotBlank() }.forEach { add(it to true) }
            config.publicResolvers.forEach { add(it to false) }
        }.distinctBy { it.first }
        servers.map { (server, isSystemServer) ->
            async {
                val records = retrying(config.probeAttempts) { dns.queryTxt(server, config.echoDomain) }
                DnsProbeResult(
                    server = server,
                    isSystemServer = isSystemServer,
                    egressIp = records?.let(DnsLeakAnalysis::egressIpFromTxt).orEmpty(),
                    clientSubnetIp = records?.let(DnsLeakAnalysis::clientSubnetFromTxt),
                )
            }
        }.awaitAll()
    }

    private suspend fun fetchExitIp(): String? =
        retrying(config.httpAttempts) { httpText(config.exitIpUrl)?.trim()?.takeIf(String::isNotBlank) }

    private fun exitInfo(ip: String, geo: IpWhoIsResponse?) = DnsLeakExitInfo(
        ip = ip,
        countryCode = geo?.countryCode.orEmpty(),
        countryName = geo?.country.orEmpty(),
        isp = geo?.connection?.isp ?: geo?.connection?.org.orEmpty(),
        asn = geo?.connection?.asn,
    )

    private suspend fun fetchGeoData(ips: List<String>): Map<String, IpWhoIsResponse> = coroutineScope {
        ips.map { ip ->
            async {
                ip to retrying(config.httpAttempts) { httpText("${config.geoIpUrl}$ip")?.let(DnsLeakAnalysis::parseIpWhoIs) }
            }
        }
            .awaitAll()
            .mapNotNull { (ip, geo) -> geo?.let { ip to it } }
            .toMap()
    }

    private suspend fun httpText(url: String): String? = http.getText(url, accept = "*/*")

    /** Runs [block] up to [attempts] times until it yields a non-null value. */
    private suspend fun <T> retrying(attempts: Int, block: suspend () -> T?): T? {
        var value = block()
        var attempt = 1
        while (value == null && attempt < attempts) {
            delay(config.retryDelayMillis)
            value = block()
            attempt += 1
        }
        return value
    }
}
