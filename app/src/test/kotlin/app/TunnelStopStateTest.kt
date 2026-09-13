package app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelStopStateTest {
    @Test
    fun tunnelStop_clearsOnlyRuntimeTunnelState() {
        val state = AppState(
            proxyRunning = true,
            selectedProxyServerId = 42,
            enableKillSwitch = true,
            enableTrafficStatsNotification = true,
            unappliedRoutingRules = listOf("stale-rule"),
        )

        val stopped = state.withTunnelStopped()

        assertFalse(stopped.proxyRunning)
        assertTrue(stopped.unappliedRoutingRules.isEmpty())
        assertEquals(
            state.copy(
                proxyRunning = false,
                unappliedRoutingRules = emptyList(),
            ),
            stopped,
        )
    }
}
