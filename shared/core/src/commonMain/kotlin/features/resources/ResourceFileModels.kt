// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile

data class ResourceFileScope(
    val trafficConfigId: Int,
    val resourceFileSource: Int,
) {
    init {
        require(trafficConfigId > 0) { "Traffic config id must be positive" }
    }
}

data class ResourceFileStatus(
    val exists: Boolean = false,
    val sizeBytes: Long = 0,
    val updatedAtMillis: Long = 0,
)

data class CustomResourceFileStatus(
    val file: TrafficConfigCustomResourceFile,
    val status: ResourceFileStatus = ResourceFileStatus(),
)

data class ResourceFilesStatus(
    val geoIp: ResourceFileStatus = ResourceFileStatus(),
    val geoSite: ResourceFileStatus = ResourceFileStatus(),
    val geoIpOnlyCnPrivate: ResourceFileStatus = ResourceFileStatus(),
    val directCidrIpv4: ResourceFileStatus = ResourceFileStatus(),
    val directCidrIpv6: ResourceFileStatus = ResourceFileStatus(),
    val xrayCore: ResourceFileStatus = ResourceFileStatus(),
    val customResourceFiles: List<CustomResourceFileStatus> = emptyList(),
)
