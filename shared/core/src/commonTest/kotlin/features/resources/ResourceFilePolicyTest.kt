// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import app.ProjectInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceFilePolicyTest {

    @Test
    fun resourceFileKindsKeepTheirAndroidOrderFileNamesAndDisplayNames() {
        val kinds = ResourceFileKind.entries

        assertEquals(
            listOf(
                ResourceFileKind.GeoIp,
                ResourceFileKind.GeoSite,
                ResourceFileKind.GeoIpOnlyCnPrivate,
                ResourceFileKind.DirectCidrIpv4,
                ResourceFileKind.DirectCidrIpv6,
                ResourceFileKind.XrayCore,
            ),
            kinds,
        )
        assertEquals(
            listOf(
                "geoip.dat",
                "geosite.dat",
                "geoip-only-cn-private.dat",
                "direct-cidr-v4.txt",
                "direct-cidr-v6.txt",
                "xray",
            ),
            kinds.map { it.fileName },
        )
        assertEquals(
            listOf(
                "geoip.dat",
                "geosite.dat",
                "geoip-only-cn-private.dat",
                "direct-cidr-v4.txt",
                "direct-cidr-v6.txt",
                "Xray-core ${ProjectInfo.XRAY_CORE_VERSION}",
            ),
            kinds.map { it.displayName },
        )
    }

    @Test
    fun profileSourceActivationUsesItsMarkerAndDoesNotAffectLegacyFiles() {
        assertTrue(
            requiresBundledResourceSourceActivation(
                isProfileScoped = true,
                storedSource = null,
                requestedSource = ResourceFileSourceV2FlyGithub,
            ),
        )
        assertTrue(
            requiresBundledResourceSourceActivation(
                isProfileScoped = true,
                storedSource = ResourceFileSourceLoyalsoldierGithub,
                requestedSource = ResourceFileSourceRoscomvpnGithub,
            ),
        )
        assertFalse(
            requiresBundledResourceSourceActivation(
                isProfileScoped = true,
                storedSource = ResourceFileSourceRunetFreedomGithub,
                requestedSource = ResourceFileSourceRunetFreedomGithub,
            ),
        )
        assertFalse(
            requiresBundledResourceSourceActivation(
                isProfileScoped = false,
                storedSource = null,
                requestedSource = ResourceFileSourceV2FlyGithub,
            ),
        )
        assertFalse(
            requiresBundledResourceSourceActivation(
                isProfileScoped = false,
                storedSource = ResourceFileSourceLoyalsoldierGithub,
                requestedSource = ResourceFileSourceRoscomvpnGithub,
            ),
        )
    }

    @Test
    fun missingAndEmptyTargetsRestoreBeforeCustomSourceProtection() {
        assertTrue(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceCustom,
                targetExists = false,
                targetLength = 0L,
                targetLastModifiedMillis = 0L,
                bundledUpdatedAtMillis = 0L,
                restoreAfterPackageUpdate = false,
            ),
        )
        assertTrue(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoSite,
                resourceFileSource = ResourceFileSourceCustom,
                targetExists = true,
                targetLength = 0L,
                targetLastModifiedMillis = 500L,
                bundledUpdatedAtMillis = 0L,
                restoreAfterPackageUpdate = false,
            ),
        )
    }

    @Test
    fun packageUpdateProtectsNonemptyCustomFilesExceptXrayCore() {
        assertFalse(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceCustom,
                targetExists = true,
                targetLength = 1024L,
                targetLastModifiedMillis = 500L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = true,
            ),
        )
        assertTrue(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.XrayCore,
                resourceFileSource = ResourceFileSourceCustom,
                targetExists = true,
                targetLength = 1024L,
                targetLastModifiedMillis = 500L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = true,
            ),
        )
    }

    @Test
    fun packageUpdateMustBeRequestedForNonemptyPresetFiles() {
        assertFalse(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
                targetExists = true,
                targetLength = 1024L,
                targetLastModifiedMillis = 500L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = false,
            ),
        )
    }

    @Test
    fun packageUpdateRestoresOnlyWhenBundledTimestampIsKnownAndStrictlyNewer() {
        assertTrue(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
                targetExists = true,
                targetLength = 1024L,
                targetLastModifiedMillis = 0L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = true,
            ),
        )
        assertTrue(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
                targetExists = true,
                targetLength = 1024L,
                targetLastModifiedMillis = 500L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = true,
            ),
        )
        assertFalse(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
                targetExists = true,
                targetLength = 1024L,
                targetLastModifiedMillis = 1000L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = true,
            ),
        )
        assertFalse(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
                targetExists = true,
                targetLength = 1024L,
                targetLastModifiedMillis = 0L,
                bundledUpdatedAtMillis = 0L,
                restoreAfterPackageUpdate = true,
            ),
        )
        assertFalse(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
                targetExists = true,
                targetLength = 1024L,
                targetLastModifiedMillis = 2000L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = true,
            ),
        )
    }
}
