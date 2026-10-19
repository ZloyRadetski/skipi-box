// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigResourceSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class ResourceFileUpdateSourceTest {
    @Test
    fun providerCatalogKeepsTheAndroidOrderAndEveryUrl() {
        assertEquals(listOf(0, 1, 2, 3, 4), ResourceFileUpdateSources.map { it.id })
        assertEquals(
            listOf(
                expected(
                    ResourceFileSourceLoyalsoldierGithub,
                    "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat",
                    "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat",
                ),
                expected(
                    ResourceFileSourceV2FlyGithub,
                    "https://github.com/v2fly/geoip/releases/latest/download/geoip.dat",
                    "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat",
                ),
                expected(
                    ResourceFileSourceChocolate4UGithub,
                    "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geoip.dat",
                    "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/geosite.dat",
                ),
                expected(
                    ResourceFileSourceRunetFreedomGithub,
                    "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geoip.dat",
                    "https://github.com/runetfreedom/russia-v2ray-rules-dat/releases/latest/download/geosite.dat",
                ),
                expected(
                    ResourceFileSourceRoscomvpnGithub,
                    "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geoip/release/geoip.dat",
                    "https://cdn.jsdelivr.net/gh/hydraponique/roscomvpn-geosite/release/geosite.dat",
                ),
            ),
            ResourceFileUpdateSources,
        )
    }

    @Test
    fun indexedProvidersAndInvalidSourceIdsUseTheFirstProviderFallback() {
        val first = ResourceFileUpdateSources.first()
        ResourceFileUpdateSources.forEachIndexed { index, provider ->
            assertEquals(provider, resourceFileUpdateSourceAt(index))
            assertEquals(provider, resolveResourceFileUpdateSource(source = index))
            assertEquals(provider, TrafficConfigResourceSettings(source = index).resourceFileUpdateSource())
        }
        assertEquals(first, resourceFileUpdateSourceAt(-1))
        assertEquals(first, resourceFileUpdateSourceAt(ResourceFileSourceCustom))
        assertEquals(first, resolveResourceFileUpdateSource(source = -1))
        assertEquals(first, resolveResourceFileUpdateSource(source = 999))
        assertEquals(first, TrafficConfigResourceSettings(source = -1).resourceFileUpdateSource())
        assertEquals(first, TrafficConfigResourceSettings(source = 999).resourceFileUpdateSource())
    }

    @Test
    fun customSourceTrimsEachProvidedUrlAndUsesTheAndroidFallbackForBlankFields() {
        val expectedCustom = expected(
            ResourceFileSourceCustom,
            "https://custom.example/geoip.dat",
            ResourceFileUpdateSources.first().geoSiteUrl,
            "https://github.com/v2fly/geoip/releases/latest/download/geoip-only-cn-private.dat",
            "https://custom.example/ipv4.txt",
            ResourceFileUpdateSources.first().directCidrIpv6Url,
        )
        assertEquals(
            expectedCustom,
            resolveResourceFileUpdateSource(
                source = ResourceFileSourceCustom,
                customGeoIpUrl = "  https://custom.example/geoip.dat \n",
                customGeoSiteUrl = " \t ",
                customGeoIpOnlyCnPrivateUrl = "\n ",
                customDirectCidrIpv4Url = " https://custom.example/ipv4.txt \t",
                customDirectCidrIpv6Url = "",
            ),
        )

        val profileSettings = TrafficConfigResourceSettings(
            source = ResourceFileSourceCustom,
            customGeoIpUrl = "  https://custom.example/geoip.dat \n",
            customGeoSiteUrl = " \t ",
            customGeoIpOnlyCnPrivateUrl = "\n ",
            customDirectCidrIpv4Url = " https://custom.example/ipv4.txt \t",
            customDirectCidrIpv6Url = "",
        )
        assertEquals(expectedCustom, profileSettings.resourceFileUpdateSource())
    }

    @Test
    fun emptyCustomFieldsProduceCustomIdentityWithTheFirstProvidersUrls() {
        val first = ResourceFileUpdateSources.first()
        assertEquals(
            expected(
                ResourceFileSourceCustom,
                first.geoIpUrl,
                first.geoSiteUrl,
                first.geoIpOnlyCnPrivateUrl,
                first.directCidrIpv4Url,
                first.directCidrIpv6Url,
            ),
            resolveResourceFileUpdateSource(source = ResourceFileSourceCustom),
        )
    }

    private fun expected(
        id: Int,
        geoIpUrl: String,
        geoSiteUrl: String,
        geoIpOnlyCnPrivateUrl: String =
            "https://github.com/v2fly/geoip/releases/latest/download/geoip-only-cn-private.dat",
        directCidrIpv4Url: String = "https://raw.githubusercontent.com/mayaxcn/china-ip-list/master/chnroute.txt",
        directCidrIpv6Url: String = "https://raw.githubusercontent.com/mayaxcn/china-ip-list/master/chnroute_v6.txt",
    ) = ResourceFileUpdateSource(
        id = id,
        geoIpUrl = geoIpUrl,
        geoSiteUrl = geoSiteUrl,
        geoIpOnlyCnPrivateUrl = geoIpOnlyCnPrivateUrl,
        directCidrIpv4Url = directCidrIpv4Url,
        directCidrIpv6Url = directCidrIpv6Url,
    )
}
