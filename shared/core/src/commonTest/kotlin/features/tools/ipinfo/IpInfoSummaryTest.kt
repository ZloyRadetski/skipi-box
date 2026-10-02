// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.tools.ipinfo

import kotlin.test.Test
import kotlin.test.assertEquals

class IpInfoSummaryTest {
    @Test
    fun formatsOnlyPresentValuesAndKeepsRouteInformation() {
        assertEquals(
            "IPv4: 192.0.2.1\nCountry: 🇺🇸 Example (US)\nLocation: City, Region\nRoute: VPN Tunnel / Proxy",
            IpInfoSummary.format(
                IpInfoSummaryInput(
                    ipv4 = "192.0.2.1", ipv6 = null, country = "Example", countryCode = "US",
                    flagEmoji = "🇺🇸", city = "City", region = "Region", postal = "",
                    latitude = null, longitude = null, isp = "", org = "", asn = null,
                    domain = "", timezoneId = "", timezoneUtc = "", currentTime = "", isVpnTunnel = true,
                ),
            ),
        )
    }
}
