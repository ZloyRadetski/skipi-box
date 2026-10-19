// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceFileUpdateDownloadPlanTest {
    @Test
    fun allPlanKeepsBuiltInsFirstAndEligibleCustomFilesInProfileOrder() {
        val scope = ResourceFileScope(trafficConfigId = 77, resourceFileSource = 999)
        val source = ResourceFileUpdateSource(
            id = 0,
            geoIpUrl = " ",
            geoSiteUrl = "",
            geoIpOnlyCnPrivateUrl = "\t",
            directCidrIpv4Url = "https://example.test/direct-v4",
            directCidrIpv6Url = "https://example.test/direct-v6",
        )
        val reservedCustomFiles = ResourceFileKind.entries.mapIndexed { index, kind ->
            customFile(
                id = index + 1,
                name = "nested/${kind.fileName}",
                url = "https://example.test/reserved-${kind.name}",
            )
        }
        val blankUrlCustomFile = customFile(10, "blank-url.dat", " \t ")
        val duplicateIdFirst = customFile(
            id = 20,
            name = "nested/shared-custom.dat",
            url = "  https://example.test/same-id-first \t",
        )
        val duplicateIdSecond = customFile(
            id = 20,
            name = "shared-custom.dat",
            url = "https://example.test/same-id-second",
        )
        val duplicateNameDifferentId = customFile(
            id = 21,
            name = "other/shared-custom.dat",
            url = "https://example.test/same-name-different-id",
        )
        val fallbackName = customFile(
            id = 22,
            name = "  ",
            url = "https://example.test/fallback-name",
        )
        val customFiles = reservedCustomFiles + listOf(
            blankUrlCustomFile,
            duplicateIdFirst,
            duplicateIdSecond,
            duplicateNameDifferentId,
            fallbackName,
        )

        val plan = planAllResourceFileDownloads(
            scope = scope,
            source = source,
            customFiles = customFiles,
        )

        assertEquals(scope, plan.scope)
        assertEquals(
            listOf<ResourceFileUpdateDownload>(
                ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoIp, " "),
                ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoSite, ""),
                ResourceFileUpdateDownload.BuiltIn(
                    ResourceFileKind.GeoIpOnlyCnPrivate,
                    "\t",
                ),
                ResourceFileUpdateDownload.BuiltIn(
                    ResourceFileKind.DirectCidrIpv4,
                    source.directCidrIpv4Url,
                ),
                ResourceFileUpdateDownload.BuiltIn(
                    ResourceFileKind.DirectCidrIpv6,
                    source.directCidrIpv6Url,
                ),
                ResourceFileUpdateDownload.Custom(
                    duplicateIdFirst,
                    "https://example.test/same-id-first",
                ),
                ResourceFileUpdateDownload.Custom(
                    duplicateIdSecond,
                    "https://example.test/same-id-second",
                ),
                ResourceFileUpdateDownload.Custom(
                    duplicateNameDifferentId,
                    "https://example.test/same-name-different-id",
                ),
                ResourceFileUpdateDownload.Custom(
                    fallbackName,
                    "https://example.test/fallback-name",
                ),
            ),
            plan.downloads,
        )
        assertEquals(999, plan.scope.resourceFileSource)
        assertTrue(plan.markResourceFileSourceAfterSuccess)
    }

    @Test
    fun builtInPlanPreservesUrlAndRejectsXrayCoreWithItsCurrentDisplayName() {
        val scope = ResourceFileScope(trafficConfigId = 78, resourceFileSource = 5)
        val source = ResourceFileUpdateSource(
            id = 4,
            geoIpUrl = " \t ",
            geoSiteUrl = "https://example.test/geosite",
            geoIpOnlyCnPrivateUrl = "https://example.test/geoip-only-cn-private",
            directCidrIpv4Url = "https://example.test/direct-v4",
            directCidrIpv6Url = "https://example.test/direct-v6",
        )

        val plan = planBuiltInResourceFileDownload(
            scope = scope,
            kind = ResourceFileKind.GeoIp,
            source = source,
        )

        assertEquals(scope, plan.scope)
        assertEquals(
            listOf<ResourceFileUpdateDownload>(
                ResourceFileUpdateDownload.BuiltIn(ResourceFileKind.GeoIp, " \t "),
            ),
            plan.downloads,
        )
        assertFalse(plan.markResourceFileSourceAfterSuccess)

        val error = assertFailsWith<IllegalStateException> {
            planBuiltInResourceFileDownload(
                scope = scope,
                kind = ResourceFileKind.XrayCore,
                source = source,
            )
        }
        assertEquals(
            "${ResourceFileKind.XrayCore.displayName} cannot be updated from URL",
            error.message,
        )
    }

    @Test
    fun customPlanTrimsUrlPreservesOriginalMetadataAndSkipsReservedOrBlankEntries() {
        val scope = ResourceFileScope(trafficConfigId = 79, resourceFileSource = 3)
        val file = customFile(
            id = 40,
            name = "nested/custom resource.dat",
            url = " \thttps://example.test/custom \t ",
        )

        val plan = planCustomResourceFileDownload(scope, file)

        assertEquals(scope, plan.scope)
        assertEquals(
            listOf<ResourceFileUpdateDownload>(
                ResourceFileUpdateDownload.Custom(file, "https://example.test/custom"),
            ),
            plan.downloads,
        )
        assertFalse(plan.markResourceFileSourceAfterSuccess)

        val reserved = customFile(
            id = 41,
            name = "parent\\${ResourceFileKind.XrayCore.fileName}",
            url = "https://example.test/reserved",
        )
        val blankUrl = customFile(id = 42, name = "empty-url.dat", url = " \t ")

        assertTrue(planCustomResourceFileDownload(scope, reserved).downloads.isEmpty())
        assertTrue(planCustomResourceFileDownload(scope, blankUrl).downloads.isEmpty())
        assertFalse(
            planCustomResourceFileDownload(scope, blankUrl).markResourceFileSourceAfterSuccess,
        )
    }

    private fun customFile(id: Int, name: String, url: String) =
        TrafficConfigCustomResourceFile(id = id, name = name, url = url)
}
