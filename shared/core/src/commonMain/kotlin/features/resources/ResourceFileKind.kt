// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import app.ProjectInfo

enum class ResourceFileKind(
    val fileName: String,
) {
    GeoIp(ResourceFileGeoIpName),
    GeoSite(ResourceFileGeoSiteName),
    GeoIpOnlyCnPrivate(ResourceFileGeoIpOnlyCnPrivateName),
    DirectCidrIpv4(ResourceFileDirectCidrIpv4Name),
    DirectCidrIpv6(ResourceFileDirectCidrIpv6Name),
    XrayCore(ResourceFileXrayCoreName),
    ;

    val displayName: String
        get() = when (this) {
            GeoIp,
            GeoSite,
            GeoIpOnlyCnPrivate,
            DirectCidrIpv4,
            DirectCidrIpv6 -> fileName
            XrayCore -> "Xray-core ${ProjectInfo.XRAY_CORE_VERSION}"
        }
}
