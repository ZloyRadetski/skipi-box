// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import app.skipi.app.tools.DiagnosticsHttpClient
import app.skipi.app.tools.DiagnosticsRoute
import app.skipi.app.tools.DnsLeakTestController
import app.skipi.app.tools.DnsLeakTestRunner
import app.skipi.app.tools.DnsTxtResolver
import app.skipi.app.tools.IpInfoController
import app.skipi.app.tools.IpInfoRunner
import app.skipi.app.tools.JvmDiagnosticsHttpClient
import app.skipi.app.tools.JvmDnsTxtResolver
import app.skipi.app.tools.JvmSpeedTestTransport
import app.skipi.app.tools.SpeedTestConfig
import app.skipi.app.tools.SpeedTestConnectionFactory
import app.skipi.app.tools.SpeedTestController
import app.skipi.app.tools.SpeedTestJob
import app.skipi.app.tools.SpeedTestRunner
import app.skipi.app.tools.socksSpeedTestConnectionFactory
import app.skipi.ui.settings.SkipiSettingsDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import platform.LocalProxyXrayConfigOptions
import java.io.IOException
import java.util.Hashtable
import javax.naming.directory.InitialDirContext

/** Probes traverse the tunnel's local SOCKS listener while Core runs and go direct otherwise, as on Android. */
internal fun desktopDiagnosticsRoute(active: LocalProxyXrayConfigOptions?): DiagnosticsRoute =
    if (active == null) {
        DiagnosticsRoute.Direct
    } else {
        // The listener may bind a LAN address; the app always reaches it over loopback.
        DiagnosticsRoute.Socks("127.0.0.1", active.socksPort, active.username, active.password)
    }

/** Reads the `dns://a dns://b` provider URL the JDK's JNDI DNS provider reports for the OS resolvers. */
internal fun parseJndiDnsProviderUrl(url: String?): List<String> = url.orEmpty()
    .split(Regex("\\s+"))
    .map { token -> token.trim().removePrefix("dns://").trimEnd('/').trim('[', ']') }
    .filter(String::isNotBlank)

/** System resolvers of the reference network; empty when the JDK cannot report them. */
internal fun discoverDesktopSystemDnsServers(): List<String> = runCatching {
    val environment = Hashtable<String, String>()
    environment["java.naming.factory.initial"] = "com.sun.jndi.dns.DnsContextFactory"
    val context = InitialDirContext(environment)
    try {
        parseJndiDnsProviderUrl(context.environment["java.naming.provider.url"] as? String)
    } finally {
        context.close()
    }
}.getOrDefault(emptyList())

/** Settings entries Desktop shows; the diagnostic tools repeat the existing mobile ones. */
internal fun desktopSettingsVisibleDestinations(): Set<SkipiSettingsDestination> = setOf(
    SkipiSettingsDestination.Appearance,
    SkipiSettingsDestination.General,
    SkipiSettingsDestination.LocalProxy,
    SkipiSettingsDestination.NetworkAutomation,
    SkipiSettingsDestination.Subscriptions,
    SkipiSettingsDestination.BackupReset,
    SkipiSettingsDestination.SpeedTest,
    SkipiSettingsDestination.DnsLeakTest,
    SkipiSettingsDestination.IpInfo,
    SkipiSettingsDestination.Logs,
    SkipiSettingsDestination.About,
)

/**
 * Desktop host of the shared diagnostic tools. [activeOptions] is the running Core session's
 * listener (null when stopped); the route is resolved again for every run so a reconnect that
 * changes the dynamic port is picked up.
 */
internal class DesktopDiagnostics(
    private val activeOptions: () -> LocalProxyXrayConfigOptions?,
    private val httpClientFor: (DiagnosticsRoute) -> DiagnosticsHttpClient = { route -> JvmDiagnosticsHttpClient(route) },
    private val dnsResolverFor: (DiagnosticsRoute) -> DnsTxtResolver = { route -> JvmDnsTxtResolver(route) },
    private val speedConnectionsFor: (DiagnosticsRoute) -> SpeedTestConnectionFactory = ::desktopSpeedTestConnections,
    private val systemDnsServers: () -> List<String> = ::discoverDesktopSystemDnsServers,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val speedTest = SpeedTestController(scope) { onState ->
        SpeedTestJob {
            val route = desktopDiagnosticsRoute(activeOptions())
            SpeedTestRunner(JvmSpeedTestTransport(speedConnectionsFor(route)), SpeedTestConfig(), onState).run()
        }
    }

    val dnsLeak = DnsLeakTestController(scope) {
        val route = desktopDiagnosticsRoute(activeOptions())
        DnsLeakTestRunner(
            systemDnsServers = systemDnsServers(),
            dns = dnsResolverFor(route),
            http = httpClientFor(route),
            hasVpnNetwork = route is DiagnosticsRoute.Socks,
        ).run()
    }

    val ipInfo = IpInfoController(scope) {
        val route = desktopDiagnosticsRoute(activeOptions())
        IpInfoRunner(httpClientFor(route), isVpnTunnel = route is DiagnosticsRoute.Socks).fetch()
    }

    /** Running measurements stop whenever the tunnel changes underneath them. */
    fun stopAll() {
        speedTest.stop()
        if (dnsLeak.state.value.running) dnsLeak.stop()
        ipInfo.stop()
    }

    override fun close() {
        stopAll()
        scope.cancel()
    }
}

/** Speed test connections are tunnel-only: without a running tunnel they fail rather than measure the direct path. */
private fun desktopSpeedTestConnections(route: DiagnosticsRoute): SpeedTestConnectionFactory = when (route) {
    is DiagnosticsRoute.Socks -> socksSpeedTestConnectionFactory(route)
    DiagnosticsRoute.Direct -> SpeedTestConnectionFactory { _, _ -> throw IOException("No active VPN tunnel is available") }
}
