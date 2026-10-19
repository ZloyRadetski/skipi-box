// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigResourceSettings

/** URLs resolved for one provider selection, including the user's custom-source values. */
data class ResourceFileUpdateSource(
    val id: Int,
    val geoIpUrl: String,
    val geoSiteUrl: String,
    val geoIpOnlyCnPrivateUrl: String,
    val directCidrIpv4Url: String,
    val directCidrIpv6Url: String,
)

/** Preset resource sources in the stable order used by the Android selector. */
val ResourceFileUpdateSources = listOf(
    ResourceFileUpdateSource(
        id = ResourceFileSourceLoyalsoldierGithub,
        geoIpUrl = ResourceFileLoyalsoldierGeoIpUrl,
        geoSiteUrl = ResourceFileLoyalsoldierGeoSiteUrl,
        geoIpOnlyCnPrivateUrl = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
        directCidrIpv4Url = ResourceFileDirectCidrIpv4Url,
        directCidrIpv6Url = ResourceFileDirectCidrIpv6Url,
    ),
    ResourceFileUpdateSource(
        id = ResourceFileSourceV2FlyGithub,
        geoIpUrl = ResourceFileV2FlyGeoIpUrl,
        geoSiteUrl = ResourceFileV2FlyGeoSiteUrl,
        geoIpOnlyCnPrivateUrl = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
        directCidrIpv4Url = ResourceFileDirectCidrIpv4Url,
        directCidrIpv6Url = ResourceFileDirectCidrIpv6Url,
    ),
    ResourceFileUpdateSource(
        id = ResourceFileSourceChocolate4UGithub,
        geoIpUrl = ResourceFileChocolate4UGeoIpUrl,
        geoSiteUrl = ResourceFileChocolate4UGeoSiteUrl,
        geoIpOnlyCnPrivateUrl = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
        directCidrIpv4Url = ResourceFileDirectCidrIpv4Url,
        directCidrIpv6Url = ResourceFileDirectCidrIpv6Url,
    ),
    ResourceFileUpdateSource(
        id = ResourceFileSourceRunetFreedomGithub,
        geoIpUrl = ResourceFileRunetFreedomGeoIpUrl,
        geoSiteUrl = ResourceFileRunetFreedomGeoSiteUrl,
        geoIpOnlyCnPrivateUrl = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
        directCidrIpv4Url = ResourceFileDirectCidrIpv4Url,
        directCidrIpv6Url = ResourceFileDirectCidrIpv6Url,
    ),
    ResourceFileUpdateSource(
        id = ResourceFileSourceRoscomvpnGithub,
        geoIpUrl = ResourceFileRoscomvpnGeoIpUrl,
        geoSiteUrl = ResourceFileRoscomvpnGeoSiteUrl,
        geoIpOnlyCnPrivateUrl = ResourceFileV2FlyGeoIpOnlyCnPrivateUrl,
        directCidrIpv4Url = ResourceFileDirectCidrIpv4Url,
        directCidrIpv6Url = ResourceFileDirectCidrIpv6Url,
    ),
)

/** Resolves a preset index, falling back to the first source for an invalid index. */
fun resourceFileUpdateSourceAt(index: Int): ResourceFileUpdateSource =
    ResourceFileUpdateSources.getOrElse(index) { ResourceFileUpdateSources.first() }

/** Resolves the provider and, for the custom provider, trims each URL independently. */
fun resolveResourceFileUpdateSource(
    source: Int,
    customGeoIpUrl: String = "",
    customGeoSiteUrl: String = "",
    customGeoIpOnlyCnPrivateUrl: String = "",
    customDirectCidrIpv4Url: String = "",
    customDirectCidrIpv6Url: String = "",
): ResourceFileUpdateSource {
    if (source != ResourceFileSourceCustom) return resourceFileUpdateSourceAt(source)

    val fallback = ResourceFileUpdateSources.first()
    return ResourceFileUpdateSource(
        id = ResourceFileSourceCustom,
        geoIpUrl = customGeoIpUrl.trim().ifBlank { fallback.geoIpUrl },
        geoSiteUrl = customGeoSiteUrl.trim().ifBlank { fallback.geoSiteUrl },
        geoIpOnlyCnPrivateUrl = customGeoIpOnlyCnPrivateUrl.trim().ifBlank {
            fallback.geoIpOnlyCnPrivateUrl
        },
        directCidrIpv4Url = customDirectCidrIpv4Url.trim().ifBlank {
            fallback.directCidrIpv4Url
        },
        directCidrIpv6Url = customDirectCidrIpv6Url.trim().ifBlank {
            fallback.directCidrIpv6Url
        },
    )
}

/** Resolves the update source represented by one stored traffic profile. */
fun TrafficConfigResourceSettings.resourceFileUpdateSource(): ResourceFileUpdateSource =
    resolveResourceFileUpdateSource(
        source = source,
        customGeoIpUrl = customGeoIpUrl,
        customGeoSiteUrl = customGeoSiteUrl,
        customGeoIpOnlyCnPrivateUrl = customGeoIpOnlyCnPrivateUrl,
        customDirectCidrIpv4Url = customDirectCidrIpv4Url,
        customDirectCidrIpv6Url = customDirectCidrIpv6Url,
    )
