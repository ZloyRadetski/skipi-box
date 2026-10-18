// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import app.ResourceFileKind
import features.resources.ResourceFileSourceChocolate4UGithub
import features.resources.ResourceFileSourceCustom
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileSourceRoscomvpnGithub
import features.resources.ResourceFileSourceRunetFreedomGithub
import features.resources.ResourceFileSourceV2FlyGithub
import features.resources.requiresBundledResourceSourceActivation
import features.resources.resourceFileSourceAssetDir
import features.resources.shouldRestoreBundledResourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidResourceFileStoreTest {

    @Test
    fun testResourceFileSourceAssetDirMapping() {
        assertEquals("geo/loyalsoldier", resourceFileSourceAssetDir(ResourceFileSourceLoyalsoldierGithub))
        assertEquals("geo/v2fly", resourceFileSourceAssetDir(ResourceFileSourceV2FlyGithub))
        assertEquals("geo/chocolate4u", resourceFileSourceAssetDir(ResourceFileSourceChocolate4UGithub))
        assertEquals("geo/runetfreedom", resourceFileSourceAssetDir(ResourceFileSourceRunetFreedomGithub))
        assertEquals("geo/roscomvpn", resourceFileSourceAssetDir(ResourceFileSourceRoscomvpnGithub))
        assertNull(resourceFileSourceAssetDir(ResourceFileSourceCustom))
        assertNull(resourceFileSourceAssetDir(-1))
    }

    @Test
    fun testShouldRestoreBundledWhenMissingOrEmpty() {
        assertTrue(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
                targetExists = false,
                targetLength = 0L,
                targetLastModifiedMillis = 0L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = false,
            ),
        )

        assertTrue(
            shouldRestoreBundledResourceFile(
                kind = ResourceFileKind.GeoSite,
                resourceFileSource = ResourceFileSourceChocolate4UGithub,
                targetExists = true,
                targetLength = 0L,
                targetLastModifiedMillis = 500L,
                bundledUpdatedAtMillis = 1000L,
                restoreAfterPackageUpdate = false,
            ),
        )
    }

    @Test
    fun testShouldRestoreBundledOnPackageUpdateForPresets() {
        val presetSources = listOf(
            ResourceFileSourceLoyalsoldierGithub,
            ResourceFileSourceV2FlyGithub,
            ResourceFileSourceChocolate4UGithub,
            ResourceFileSourceRunetFreedomGithub,
            ResourceFileSourceRoscomvpnGithub,
        )

        presetSources.forEach { source ->
            assertTrue(
                "Source $source should restore on package update when bundled is newer",
                shouldRestoreBundledResourceFile(
                    kind = ResourceFileKind.GeoIp,
                    resourceFileSource = source,
                    targetExists = true,
                    targetLength = 1024L,
                    targetLastModifiedMillis = 500L,
                    bundledUpdatedAtMillis = 1000L,
                    restoreAfterPackageUpdate = true,
                ),
            )
        }
    }

    @Test
    fun testShouldNotRestoreCustomSourceOnPackageUpdate() {
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
    }

    @Test
    fun testShouldNotRestoreWhenTargetIsNewerThanBundled() {
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

    @Test
    fun testMissingAndEmptyFilesRestoreEvenForCustomSource() {
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
    fun testPackageUpdateKeepsCustomFilesExceptXrayCore() {
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
    fun testPackageUpdateRequiresKnownAndStrictlyNewerBundledTimestamp() {
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

    @Test
    fun testProfileActivatesItsOwnBundledSnapshotWhenProviderChanges() {
        assertTrue(
            requiresBundledResourceSourceActivation(
                isProfileScoped = true,
                storedSource = ResourceFileSourceLoyalsoldierGithub,
                requestedSource = ResourceFileSourceRoscomvpnGithub,
            ),
        )
        assertTrue(
            requiresBundledResourceSourceActivation(
                isProfileScoped = true,
                storedSource = null,
                requestedSource = ResourceFileSourceV2FlyGithub,
            ),
        )
        assertFalse(
            requiresBundledResourceSourceActivation(
                isProfileScoped = true,
                storedSource = ResourceFileSourceRunetFreedomGithub,
                requestedSource = ResourceFileSourceRunetFreedomGithub,
            ),
        )
    }

    @Test
    fun testLegacyUnscopedDirectoryDoesNotReinterpretItsProvider() {
        assertFalse(
            requiresBundledResourceSourceActivation(
                isProfileScoped = false,
                storedSource = ResourceFileSourceLoyalsoldierGithub,
                requestedSource = ResourceFileSourceRoscomvpnGithub,
            ),
        )
    }
}
