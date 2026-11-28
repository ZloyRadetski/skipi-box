// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.proxy.server.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** A raw JSON config whose outbound speaks naive shows its own name and HTTP/2 or HTTP/3 transport. */
class CustomNaiveDisplayTest {
    private fun config(quic: Boolean) =
        """{"outbounds":[{"tag":"proxy","protocol":"naive","settings":{"address":"naive.example.com","port":443${if (quic) ",\"quic\":true" else ""}}}]}"""

    @Test
    fun protocolIsShownAsNaive() {
        assertEquals("JSON (Naive)", customXrayConfigProtocolDisplayName(config(false)))
    }

    @Test
    fun transportIsHttp2ByDefault() {
        assertEquals("HTTP/2", customXrayConfigTransportDisplay(config(false)))
    }

    @Test
    fun transportIsHttp3WithQuic() {
        assertEquals("HTTP/3", customXrayConfigTransportDisplay(config(true)))
    }
}
