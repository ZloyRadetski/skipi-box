// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import app.skipi.app.tools.DiagnosticsNetworkBinding
import app.skipi.app.tools.DiagnosticsRoute
import app.skipi.app.tools.DnsLeakTestController
import app.skipi.app.tools.DnsLeakTestRunner
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
import engine.network.NetworkDefaults
import engine.network.TunnelNetworks
import engine.proxy.LocalProxyLoopbackAddress
import engine.proxy.LocalProxyRuntime
import features.logs.AndroidAppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.URL

/**
 * Android host of the shared diagnostic tools. The controllers are app-scoped so a running test
 * and its result survive Activity recreation. The app is excluded from its own VPN tunnel, so
 * probes traverse the tunnel through the local SOCKS proxy runtime when it is active, and
 * otherwise through the bound VPN network.
 */
internal object AndroidDiagnostics {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var speedTestController: SpeedTestController? = null
    @Volatile private var dnsLeakController: DnsLeakTestController? = null
    @Volatile private var ipInfoController: IpInfoController? = null

    fun speedTest(context: Context): SpeedTestController = speedTestController ?: synchronized(this) {
        speedTestController ?: run {
            val appContext = context.applicationContext
            SpeedTestController(scope) { onState ->
                SpeedTestJob {
                    SpeedTestRunner(
                        transport = JvmSpeedTestTransport(TunnelSpeedTestConnectionFactory(appContext)),
                        config = SpeedTestConfig(),
                        onState = onState,
                    ).run()
                }
            }
        }.also { speedTestController = it }
    }

    fun dnsLeak(context: Context): DnsLeakTestController = dnsLeakController ?: synchronized(this) {
        dnsLeakController ?: run {
            val appContext = context.applicationContext
            DnsLeakTestController(scope) {
                val environment = AndroidDiagnosticsEnvironment.current(appContext)
                try {
                    DnsLeakTestRunner(
                        systemDnsServers = environment.systemDnsServers(appContext),
                        // Android retries a silent proxied probe over the bound VPN network.
                        dns = JvmDnsTxtResolver(environment.route, environment.binding, fallbackToDirect = true),
                        http = environment.httpClient,
                        hasVpnNetwork = environment.hasTunnel,
                    ).run()
                } catch (error: Throwable) {
                    AndroidAppLogger.warn(LogTag, "DNS leak test failed", error)
                    throw error
                }
            }
        }.also { dnsLeakController = it }
    }

    fun ipInfo(context: Context): IpInfoController = ipInfoController ?: synchronized(this) {
        ipInfoController ?: run {
            val appContext = context.applicationContext
            IpInfoController(scope) {
                val environment = AndroidDiagnosticsEnvironment.current(appContext)
                try {
                    IpInfoRunner(environment.httpClient, environment.hasTunnel).fetch()
                } catch (error: Throwable) {
                    AndroidAppLogger.warn(LogTag, "Failed to fetch IP details", error)
                    throw error
                }
            }
        }.also { ipInfoController = it }
    }

    /** Stops running tests; used when a tunnel operation invalidates their measurements. */
    fun stopAll() {
        speedTestController?.stop()
        dnsLeakController?.takeIf { it.state.value.running }?.stop()
    }

    private const val LogTag = "Diagnostics"
}

/** Route and network binding resolved for one tool run. */
private class AndroidDiagnosticsEnvironment(
    private val vpnNetwork: Network?,
    val route: DiagnosticsRoute,
) {
    val hasTunnel: Boolean get() = vpnNetwork != null || route is DiagnosticsRoute.Socks

    val binding: DiagnosticsNetworkBinding = object : DiagnosticsNetworkBinding {
        override fun openConnection(url: URL): HttpURLConnection? =
            vpnNetwork?.let { runCatching { it.openConnection(url) as? HttpURLConnection }.getOrNull() }

        override fun bind(socket: DatagramSocket) {
            vpnNetwork?.bindSocket(socket)
        }
    }

    val httpClient get() = JvmDiagnosticsHttpClient(
        route = route,
        binding = binding,
        onFailure = { url, error -> AndroidAppLogger.debug("Diagnostics", "HTTP request failed: $url (${error.message})") },
    )

    /**
     * DNS servers of the reference network: the VPN's own servers while a tunnel is up (that is
     * what other apps resolve through), otherwise the physical network's servers.
     */
    fun systemDnsServers(context: Context): List<String> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val network = vpnNetwork ?: connectivity.activeNetwork ?: return emptyList()
        return connectivity.getLinkProperties(network)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
    }

    companion object {
        fun current(context: Context): AndroidDiagnosticsEnvironment {
            val proxy = LocalProxyRuntime.current()
            val route = if (proxy != null) {
                val host = if (proxy.listenAddress == NetworkDefaults.IPV4_ANY_ADDRESS) {
                    LocalProxyLoopbackAddress
                } else {
                    proxy.listenAddress
                }
                DiagnosticsRoute.Socks(host, proxy.port, proxy.username, proxy.password)
            } else {
                DiagnosticsRoute.Direct
            }
            return AndroidDiagnosticsEnvironment(TunnelNetworks.locateVpnNetwork(context), route)
        }
    }
}

/** Tunnel-only speed test connections; they never fall back to the device's direct network. */
private class TunnelSpeedTestConnectionFactory(context: Context) : SpeedTestConnectionFactory {
    private val appContext = context.applicationContext

    override fun connect(url: String, configure: (HttpURLConnection) -> Unit): HttpURLConnection =
        TunnelNetworks.withLocalProxyAuthenticator {
            TunnelNetworks.openTunnelHttpConnection(appContext, URL(url)).apply {
                configure(this)
                connect()
            }
        }
}
