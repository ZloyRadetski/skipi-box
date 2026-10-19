// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile
import features.config.sanitizeCustomResourceFileName

/** A captured, ordered set of resource URLs to download for one profile scope. */
data class ResourceFileUpdateDownloadPlan(
    val scope: ResourceFileScope,
    val downloads: List<ResourceFileUpdateDownload>,
    val markResourceFileSourceAfterSuccess: Boolean,
)

/** One built-in or custom resource included in a [ResourceFileUpdateDownloadPlan]. */
sealed interface ResourceFileUpdateDownload {
    data class BuiltIn(
        val kind: ResourceFileKind,
        val url: String,
    ) : ResourceFileUpdateDownload

    data class Custom(
        val file: TrafficConfigCustomResourceFile,
        val url: String,
    ) : ResourceFileUpdateDownload
}

/** Creates a full update plan with every updateable built-in followed by eligible custom files. */
fun planAllResourceFileDownloads(
    scope: ResourceFileScope,
    source: ResourceFileUpdateSource,
    customFiles: List<TrafficConfigCustomResourceFile>,
): ResourceFileUpdateDownloadPlan {
    val builtInDownloads: List<ResourceFileUpdateDownload> = UpdateableResourceFileKinds.map { kind ->
        ResourceFileUpdateDownload.BuiltIn(
            kind = kind,
            url = source.urlFor(kind),
        )
    }
    val downloads = builtInDownloads + customFiles.mapNotNull { customFile ->
        customFile.toResourceFileUpdateDownloadOrNull()
    }
    return ResourceFileUpdateDownloadPlan(
        scope = scope,
        downloads = downloads,
        markResourceFileSourceAfterSuccess = true,
    )
}

/** Creates a one-kind plan. Xray core remains managed by the locked publisher. */
fun planBuiltInResourceFileDownload(
    scope: ResourceFileScope,
    kind: ResourceFileKind,
    source: ResourceFileUpdateSource,
): ResourceFileUpdateDownloadPlan {
    if (kind == ResourceFileKind.XrayCore) {
        error("${kind.displayName} cannot be updated from URL")
    }
    return ResourceFileUpdateDownloadPlan(
        scope = scope,
        downloads = listOf(ResourceFileUpdateDownload.BuiltIn(kind = kind, url = source.urlFor(kind))),
        markResourceFileSourceAfterSuccess = false,
    )
}

/** Creates a one-custom-file plan, or an empty plan when the file cannot be downloaded. */
fun planCustomResourceFileDownload(
    scope: ResourceFileScope,
    file: TrafficConfigCustomResourceFile,
): ResourceFileUpdateDownloadPlan {
    val download = file.toResourceFileUpdateDownloadOrNull()
    return ResourceFileUpdateDownloadPlan(
        scope = scope,
        downloads = listOfNotNull(download),
        markResourceFileSourceAfterSuccess = false,
    )
}

/** Resolves the sanitized target basename used by both planning and scoped file storage. */
internal fun resourceFileName(customFile: TrafficConfigCustomResourceFile): String =
    sanitizeCustomResourceFileName(
        value = customFile.name,
        fallback = "custom-resource-${customFile.id}.dat",
    )

private fun TrafficConfigCustomResourceFile.toResourceFileUpdateDownloadOrNull(): ResourceFileUpdateDownload.Custom? {
    val targetName = resourceFileName(this)
    if (ResourceFileKind.entries.any { kind -> kind.fileName == targetName }) return null
    val updateUrl = url.trim()
    if (updateUrl.isBlank()) return null
    return ResourceFileUpdateDownload.Custom(
        file = this,
        url = updateUrl,
    )
}

private fun ResourceFileUpdateSource.urlFor(kind: ResourceFileKind): String = when (kind) {
    ResourceFileKind.GeoIp -> geoIpUrl
    ResourceFileKind.GeoSite -> geoSiteUrl
    ResourceFileKind.GeoIpOnlyCnPrivate -> geoIpOnlyCnPrivateUrl
    ResourceFileKind.DirectCidrIpv4 -> directCidrIpv4Url
    ResourceFileKind.DirectCidrIpv6 -> directCidrIpv6Url
    ResourceFileKind.XrayCore -> error("${kind.displayName} cannot be updated from URL")
}

private val UpdateableResourceFileKinds = listOf(
    ResourceFileKind.GeoIp,
    ResourceFileKind.GeoSite,
    ResourceFileKind.GeoIpOnlyCnPrivate,
    ResourceFileKind.DirectCidrIpv4,
    ResourceFileKind.DirectCidrIpv6,
)
