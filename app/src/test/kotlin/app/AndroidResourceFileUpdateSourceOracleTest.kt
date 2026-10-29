// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.ResourceFileSourceChocolate4UGithub
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileSourceRoscomvpnGithub
import features.resources.ResourceFileSourceRunetFreedomGithub
import features.resources.ResourceFileSourceV2FlyGithub
import features.resources.resourceFileUpdateSource as profileResourceFileUpdateSource
import org.junit.Assert.assertEquals
import org.junit.Test
import features.resources.ResourceFileUpdateSource
import features.resources.ResourceFileUpdateSources
import features.resources.resourceFileUpdateSourceAt

/** Source-backed baseline for the Android global and profile resource-update resolvers. */
class AndroidResourceFileUpdateSourceOracleTest {
    @Test
    fun providersKeepTheirFiveStableIdsOrderAndExactUrls() {
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
    fun sourceIndexAndResolverInvalidValuesKeepTheFirstProviderFallback() {
        val first = ResourceFileUpdateSources.first()
        assertEquals(first, resourceFileUpdateSourceAt(-1))
        assertEquals(first, resourceFileUpdateSourceAt(ResourceFileSourceCustom))
        assertEquals(first, AppState(resourceFileSource = -1).resourceFileUpdateSource())
        assertEquals(first, AppState(resourceFileSource = 999).resourceFileUpdateSource())
        assertEquals(first, TrafficConfigResourceSettings(source = -1).profileResourceFileUpdateSource())
        assertEquals(first, TrafficConfigResourceSettings(source = 999).profileResourceFileUpdateSource())

        val customDefaults = expected(
            ResourceFileSourceCustom,
            first.geoIpUrl,
            first.geoSiteUrl,
            first.geoIpOnlyCnPrivateUrl,
            first.directCidrIpv4Url,
            first.directCidrIpv6Url,
        )
        assertEquals(customDefaults, AppState(resourceFileSource = ResourceFileSourceCustom).resourceFileUpdateSource())
        assertEquals(
            customDefaults,
            TrafficConfigResourceSettings(source = ResourceFileSourceCustom).profileResourceFileUpdateSource(),
        )
    }

    @Test
    fun customProfileUrlsAreTrimmedIndividuallyAndBlankValuesUseTheirOriginalDefaults() {
        val profile = TrafficConfigResourceSettings(
            source = ResourceFileSourceCustom,
            customGeoIpUrl = "  https://custom.example/geoip.dat \n",
            customGeoSiteUrl = " \t ",
            customGeoIpOnlyCnPrivateUrl = "\n ",
            customDirectCidrIpv4Url = " https://custom.example/ipv4.txt \t",
            customDirectCidrIpv6Url = "",
        )

        assertEquals(
            expected(
                ResourceFileSourceCustom,
                "https://custom.example/geoip.dat",
                ResourceFileUpdateSources.first().geoSiteUrl,
                "https://github.com/v2fly/geoip/releases/latest/download/geoip-only-cn-private.dat",
                "https://custom.example/ipv4.txt",
                ResourceFileUpdateSources.first().directCidrIpv6Url,
            ),
            profile.profileResourceFileUpdateSource(),
        )
    }

    @Test
    fun globalResolverUsesGlobalFieldsAndMatchesTheProfileResolverForTheSameInputs() {
        val profileState = TrafficConfigState(
            id = 77,
            name = "Profile with a different source",
            rawConfig = "",
            resourceSettings = TrafficConfigResourceSettings(source = ResourceFileSourceV2FlyGithub),
        )
        val globalRoscomvpn = AppState(
            resourceFileSource = ResourceFileSourceRoscomvpnGithub,
            trafficConfigs = listOf(profileState),
            activeTrafficConfigId = profileState.id,
        )
        assertEquals(ResourceFileUpdateSources[4], globalRoscomvpn.resourceFileUpdateSource())
        assertEquals(ResourceFileUpdateSources[1], profileState.resourceSettings.profileResourceFileUpdateSource())

        (ResourceFileSourceLoyalsoldierGithub..ResourceFileSourceRoscomvpnGithub).forEach { source ->
            val global = AppState(resourceFileSource = source).resourceFileUpdateSource()
            val profile = TrafficConfigResourceSettings(source = source).profileResourceFileUpdateSource()
            assertEquals("source=$source", global, profile)
        }

        val globalCustom = AppState(
            resourceFileSource = ResourceFileSourceCustom,
            customResourceFileGeoIpUrl = " https://same.example/ip ",
            customResourceFileGeoSiteUrl = "\t",
            customResourceFileGeoIpOnlyCnPrivateUrl = " https://same.example/cn ",
            customResourceFileDirectCidrIpv4Url = "",
            customResourceFileDirectCidrIpv6Url = " https://same.example/v6\n",
        )
        val profileCustom = TrafficConfigResourceSettings(
            source = ResourceFileSourceCustom,
            customGeoIpUrl = " https://same.example/ip ",
            customGeoSiteUrl = "\t",
            customGeoIpOnlyCnPrivateUrl = " https://same.example/cn ",
            customDirectCidrIpv4Url = "",
            customDirectCidrIpv6Url = " https://same.example/v6\n",
        )
        val expectedCustom = expected(
            ResourceFileSourceCustom,
            "https://same.example/ip",
            ResourceFileUpdateSources.first().geoSiteUrl,
            "https://same.example/cn",
            ResourceFileUpdateSources.first().directCidrIpv4Url,
            "https://same.example/v6",
        )
        assertEquals(expectedCustom, globalCustom.resourceFileUpdateSource())
        assertEquals(expectedCustom, profileCustom.profileResourceFileUpdateSource())
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
