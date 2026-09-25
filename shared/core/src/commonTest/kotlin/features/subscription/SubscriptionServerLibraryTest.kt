package features.subscription

import features.proxy.server.model.ProxyServer
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionServerLibraryTest {
    @Test
    fun replacing_one_group_keeps_manual_servers_and_reuses_matching_ids() {
        val manual = node("manual")
        val old = node("old")
        val library = SubscriptionServerLibrary(
            selectedServerId = 11,
            servers = listOf(
                SubscriptionServerRecord(10, null, manual),
                SubscriptionServerRecord(11, 7, old),
            ),
        )

        val updated = replaceSubscriptionServerGroup(library, 7, listOf(old))
        assertEquals(listOf(10, 11), updated.servers.map(SubscriptionServerRecord::id))
        assertEquals(null, updated.servers.first().subscriptionId)
        assertEquals(11, updated.selectedServerId)
    }

    private fun node(remarks: String): ProxyServer<*> = ProxyServer.parse(
        "vless://8b4a2b20-c533-4d13-a3e0-bb0a8d7eb9c6@example.com:443#$remarks",
    )
}
