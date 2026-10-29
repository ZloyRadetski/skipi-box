// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import features.resources.ResourceFileKind
import features.resources.ResourceFileSourceLoyalsoldierGithub
import features.resources.ResourceFileSourceRoscomvpnGithub
import features.resources.ResourceFileSourceV2FlyGithub
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Android source oracle for the existing bundled-asset and legacy-migration behavior. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = android.app.Application::class,
    shadows = [RealOsRenameShadow::class],
)
class AndroidResourceFileStoreBundledOracleTest {

    @Test
    fun v2FlyAssetsRestoreAllNonCoreFilesAndCommitTheProfileSourceMarker() = withFilesContext { context ->
        val store = AndroidResourceFileStore(context, scope(trafficConfigId = 21, source = ResourceFileSourceV2FlyGithub))

        val paths = store.preparePaths(
            resourceFileSource = ResourceFileSourceV2FlyGithub,
            restoreBundledFiles = true,
        )

        assertEquals(store.dataDir.absolutePath, paths.dataDir)
        builtInKinds.forEach { kind ->
            val target = store.file(kind)
            assertTrue("${kind.fileName} should be restored from the packaged V2Fly asset", target.isFile)
            assertTrue("${kind.fileName} should be nonempty after XZ decompression", target.length() > 0L)
        }
        assertEquals("1", File(store.dataDir, ResourceSourceMarkerFileName).readText())
        assertTrue(store.file(ResourceFileKind.XrayCore).parentFile!!.isDirectory)
    }

    @Test
    fun missingRoscomVpnCnPrivateAssetLeavesTheSourceMarkerUncommitted() = withFilesContext { context ->
        val store = AndroidResourceFileStore(context, scope(trafficConfigId = 22, source = ResourceFileSourceRoscomvpnGithub))

        store.ensureBundledFiles(
            resourceFileSource = ResourceFileSourceRoscomvpnGithub,
            restoreAfterPackageUpdate = false,
        )

        val geoIpOnlyCnPrivate = store.file(ResourceFileKind.GeoIpOnlyCnPrivate)
        assertFalse(geoIpOnlyCnPrivate.exists())
        assertFalse(File(store.dataDir, ResourceSourceMarkerFileName).exists())
        listOf(
            ResourceFileKind.GeoIp,
            ResourceFileKind.GeoSite,
            ResourceFileKind.DirectCidrIpv4,
            ResourceFileKind.DirectCidrIpv6,
        ).forEach { kind ->
            assertTrue("${kind.fileName} should remain restored despite the missing provider asset", store.file(kind).length() > 0L)
        }
    }

    @Test
    fun firstProfileReceivesLegacyVisibleFilesOnceWithoutOverwritingItsExistingFile() = withFilesContext { context ->
        val first = AndroidResourceFileStore(context, scope(trafficConfigId = 23, source = ResourceFileSourceV2FlyGithub))
        val legacyRoot = File(context.filesDir, "xray").apply { mkdirs() }
        File(legacyRoot, ResourceFileKind.GeoIp.fileName).writeText("legacy-geoip")
        File(legacyRoot, ResourceFileKind.GeoSite.fileName).writeText("legacy-geosite")
        File(legacyRoot, "custom-visible.dat").writeText("legacy-custom")
        File(legacyRoot, ResourceFileKind.XrayCore.fileName).writeText("global-core")
        File(legacyRoot, ".hidden-resource").writeText("hidden")
        first.file(ResourceFileKind.GeoIp).apply {
            parentFile!!.mkdirs()
            writeText("current-profile")
        }

        first.preparePaths(
            resourceFileSource = ResourceFileSourceV2FlyGithub,
            restoreBundledFiles = false,
        )

        assertEquals("current-profile", first.file(ResourceFileKind.GeoIp).readText())
        assertEquals("legacy-geosite", first.file(ResourceFileKind.GeoSite).readText())
        assertEquals("legacy-custom", File(first.dataDir, "custom-visible.dat").readText())
        assertFalse(File(first.dataDir, ".hidden-resource").exists())
        assertEquals("1", File(first.dataDir, ResourceSourceMarkerFileName).readText())
        assertEquals("done", File(legacyRoot, LegacyMigrationMarkerFileName).readText())
        assertEquals("global-core", first.file(ResourceFileKind.XrayCore).readText())

        val second = AndroidResourceFileStore(context, scope(trafficConfigId = 24, source = ResourceFileSourceRoscomvpnGithub))
        second.preparePaths(
            resourceFileSource = ResourceFileSourceRoscomvpnGithub,
            restoreBundledFiles = false,
        )

        assertFalse(second.file(ResourceFileKind.GeoIp).exists())
        assertFalse(File(second.dataDir, ResourceSourceMarkerFileName).exists())
        assertEquals("legacy-geoip", File(legacyRoot, ResourceFileKind.GeoIp.fileName).readText())
    }

    private fun scope(trafficConfigId: Int, source: Int) = XrayResourceFileScope(
        trafficConfigId = trafficConfigId,
        resourceFileSource = source,
    )

    private inline fun withFilesContext(block: (Context) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "resource-bundle-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
        try {
            block(context)
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        const val ResourceSourceMarkerFileName = ".resource-source"
        const val LegacyMigrationMarkerFileName = ".profile-resources-migrated-v1"
        val builtInKinds = ResourceFileKind.entries.filter { it != ResourceFileKind.XrayCore }
    }
}
