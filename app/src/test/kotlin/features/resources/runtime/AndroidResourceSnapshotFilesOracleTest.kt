// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources.runtime

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.ResourceFileKind
import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileSourceLoyalsoldierGithub
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLinux

/** Android filesystem oracle for the profile-scoped snapshot operations moved to shared JVM code. */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [35],
    application = android.app.Application::class,
    shadows = [RealLinuxRenameShadow::class],
)
class AndroidResourceSnapshotFilesOracleTest {

    @Test
    fun profileScopeRejectsNonPositiveTrafficConfigIds() {
        for (invalidId in listOf(0, -1)) {
            val error = runCatching { scope(trafficConfigId = invalidId) }.exceptionOrNull()

            assertTrue("traffic config id $invalidId should be rejected", error is IllegalArgumentException)
        }
    }

    @Test
    fun builtInFilesAreIsolatedByProfileWhileXrayCoreStaysGlobal() = withFilesContext { context, root ->
        val first = AndroidResourceFileStore(context, scope(trafficConfigId = 7))
        val sibling = AndroidResourceFileStore(context, scope(trafficConfigId = 12))
        val source = File(root, "geoip-source.dat").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        first.replace(ResourceFileKind.GeoIp, Uri.fromFile(source))

        assertEquals(File(File(root, "xray/profiles/7"), "geoip.dat"), first.file(ResourceFileKind.GeoIp))
        assertEquals(File(File(root, "xray/profiles/12"), "geoip.dat"), sibling.file(ResourceFileKind.GeoIp))
        assertEquals(byteArrayOf(1, 2, 3).toList(), first.file(ResourceFileKind.GeoIp).readBytes().toList())
        assertFalse(sibling.file(ResourceFileKind.GeoIp).exists())
        assertEquals(File(root, "xray"), first.file(ResourceFileKind.XrayCore).parentFile)
        assertEquals(first.file(ResourceFileKind.XrayCore), sibling.file(ResourceFileKind.XrayCore))
    }

    @Test
    fun statusTreatsEmptyFilesAsUnavailableAndReportsMetadataForExistingPaths() = withFilesContext { context, _ ->
        val store = AndroidResourceFileStore(context, scope(trafficConfigId = 4))
        val empty = store.file(ResourceFileKind.GeoIp).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf())
        }
        val populated = store.file(ResourceFileKind.GeoSite).apply {
            writeBytes(byteArrayOf(5, 6, 7, 8))
            setLastModified(1_700_000_000_000L)
        }
        val custom = TrafficConfigCustomResourceFile(id = 3, name = "extra.dat", url = "https://geo.example/extra.dat")
        val customFile = store.file(custom).apply { writeBytes(byteArrayOf(9, 10)) }

        val status = store.currentStatus(listOf(custom))

        assertFalse(status.geoIp.exists)
        assertEquals(0L, status.geoIp.sizeBytes)
        assertEquals(empty.lastModified(), status.geoIp.updatedAtMillis)
        assertTrue(status.geoSite.exists)
        assertEquals(populated.length(), status.geoSite.sizeBytes)
        assertEquals(populated.lastModified(), status.geoSite.updatedAtMillis)
        assertEquals(custom, status.customResourceFiles.single().file)
        assertTrue(status.customResourceFiles.single().status.exists)
        assertEquals(customFile.length(), status.customResourceFiles.single().status.sizeBytes)
    }

    @Test
    fun builtInReplacementReplacesOnlyTheScopedTarget() = withFilesContext { context, root ->
        val store = AndroidResourceFileStore(context, scope(trafficConfigId = 9))
        val target = store.file(ResourceFileKind.GeoIp).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1))
        }
        val source = File(root, "replacement.dat").apply { writeBytes(byteArrayOf(20, 21, 22)) }

        store.replace(ResourceFileKind.GeoIp, Uri.fromFile(source))

        assertEquals(listOf<Byte>(20, 21, 22), target.readBytes().toList())
        assertTrue(source.isFile)
        assertFalse(File(target.parentFile, "${target.name}.replace.tmp").exists())
    }

    @Test
    fun emptyReplacementFailsWithoutChangingExistingFile() = withFilesContext { context, root ->
        val store = AndroidResourceFileStore(context, scope(trafficConfigId = 5))
        val target = store.file(ResourceFileKind.GeoSite).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(30, 31))
        }
        val source = File(root, "empty.dat").apply { writeBytes(byteArrayOf()) }

        val error = runCatching { store.replace(ResourceFileKind.GeoSite, Uri.fromFile(source)) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf<Byte>(30, 31), target.readBytes().toList())
        assertFalse(File(target.parentFile, "${target.name}.replace.tmp").exists())
    }

    @Test
    fun xrayCoreReplacementIsRejectedBeforeOpeningThePickedUri() = withFilesContext { context, _ ->
        val store = AndroidResourceFileStore(context, scope(trafficConfigId = 8))
        val unavailableUri = Uri.parse("content://missing.resource.provider/file")

        val error = runCatching { store.replace(ResourceFileKind.XrayCore, unavailableUri) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals(File(context.filesDir, "xray"), store.file(ResourceFileKind.XrayCore).parentFile)
    }

    @Test
    fun customReplacementCannotOverwriteBuiltInFile() = withFilesContext { context, _ ->
        val store = AndroidResourceFileStore(context, scope(trafficConfigId = 6))
        val builtIn = store.file(ResourceFileKind.GeoIp).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(40, 41))
        }
        val collidingCustom = TrafficConfigCustomResourceFile(
            id = 1,
            name = ResourceFileKind.GeoIp.fileName,
            url = "https://geo.example/custom.dat",
        )
        val unavailableUri = Uri.parse("content://missing.resource.provider/file")

        store.replaceCustom(collidingCustom, unavailableUri)
        store.deleteCustom(collidingCustom)

        assertEquals(listOf<Byte>(40, 41), builtIn.readBytes().toList())
    }

    @Test
    fun customRenameReplacesExistingDestinationAndDeleteRemovesOnlyThatFile() = withFilesContext { context, _ ->
        val store = AndroidResourceFileStore(context, scope(trafficConfigId = 3))
        val previous = TrafficConfigCustomResourceFile(1, "before.dat", "https://geo.example/before.dat")
        val renamed = TrafficConfigCustomResourceFile(2, "after.dat", "https://geo.example/after.dat")
        val source = store.file(previous).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(50, 51, 52))
        }
        val destination = store.file(renamed).apply { writeBytes(byteArrayOf(1)) }
        val unrelated = store.file(TrafficConfigCustomResourceFile(3, "other.dat", ""))
            .apply { writeBytes(byteArrayOf(60)) }

        store.renameCustom(previous, renamed)

        assertFalse(source.exists())
        assertEquals(listOf<Byte>(50, 51, 52), destination.readBytes().toList())
        assertTrue(unrelated.isFile)

        store.deleteCustom(renamed)

        assertFalse(destination.exists())
        assertTrue(unrelated.isFile)
    }

    private fun scope(trafficConfigId: Int) = XrayResourceFileScope(
        trafficConfigId = trafficConfigId,
        resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
    )

    private inline fun withFilesContext(block: (Context, File) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "resource-snapshot-${System.nanoTime()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
        try {
            block(context, root)
        } finally {
            root.deleteRecursively()
        }
    }
}

/** Robolectric 4.16's instrumented Linux.rename is a no-op; model its POSIX same-directory rename. */
@Implements(className = "libcore.io.Linux", minSdk = 26, isInAndroidSdk = false)
class RealLinuxRenameShadow : ShadowLinux() {
    @Implementation
    fun rename(oldPath: String, newPath: String) {
        try {
            Files.move(Path.of(oldPath), Path.of(newPath), ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (error: IOException) {
            throw IllegalStateException("Unable to rename $oldPath to $newPath", error)
        }
    }
}
