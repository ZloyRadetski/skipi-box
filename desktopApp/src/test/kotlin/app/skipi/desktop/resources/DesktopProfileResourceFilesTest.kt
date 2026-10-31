// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.ResourceFileKind
import features.resources.ResourceFileSourceRoscomvpnGithub
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopProfileResourceFilesTest {

    @Test
    fun snapshotUsesTheSuppliedProfileIdAndKeepsItsFilesSeparateFromAnotherProfile() = withXrayRoot { root ->
        val files = DesktopProfileResourceFiles(xrayRootDir = root)
        val requested = profile(id = 7, source = ResourceFileSourceRoscomvpnGithub)
        val other = profile(id = 12, source = ResourceFileSourceRoscomvpnGithub)
        val requestedSnapshot = files.snapshot(requested)
        val otherSnapshot = files.snapshot(other)

        requestedSnapshot.replace(ResourceFileKind.GeoIp) { "requested-profile".encodeToByteArray().inputStream() }

        assertEquals(root.resolve("profiles/7/geoip.dat"), requestedSnapshot.file(ResourceFileKind.GeoIp))
        assertEquals(root.resolve("profiles/12/geoip.dat"), otherSnapshot.file(ResourceFileKind.GeoIp))
        assertEquals("requested-profile", requestedSnapshot.file(ResourceFileKind.GeoIp).readText())
        assertFalse(otherSnapshot.file(ResourceFileKind.GeoIp).exists())
    }

    @Test
    fun successfulReplacementReplacesExistingBytesAndEmptyReplacementPreservesThem() = withXrayRoot { root ->
        val snapshot = DesktopProfileResourceFiles(xrayRootDir = root).snapshot(profile(id = 3))
        val target = snapshot.file(ResourceFileKind.GeoSite).apply {
            parentFile?.mkdirs()
            writeText("before")
        }

        snapshot.replace(ResourceFileKind.GeoSite) { "after".encodeToByteArray().inputStream() }
        val emptyError = runCatching {
            snapshot.replace(ResourceFileKind.GeoSite) { byteArrayOf().inputStream() }
        }.exceptionOrNull()

        assertEquals("after", target.readText())
        assertTrue(emptyError is IllegalStateException)
        assertFalse(java.io.File(target.parentFile, "${target.name}.replace.tmp").exists())
        assertEquals("after", target.readText())
    }

    @Test
    fun desktopSnapshotReportsItsProfileStatusAndLeavesGlobalCorePathAtXrayRoot() = withXrayRoot { root ->
        val snapshot = DesktopProfileResourceFiles(xrayRootDir = root).snapshot(profile(id = 19))
        val target = snapshot.file(ResourceFileKind.DirectCidrIpv4).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2))
        }

        val status = snapshot.currentStatus()

        assertTrue(status.directCidrIpv4.exists)
        assertEquals(target.length(), status.directCidrIpv4.sizeBytes)
        assertEquals(target.lastModified(), status.directCidrIpv4.updatedAtMillis)
        assertEquals(root.resolve("xray"), snapshot.file(ResourceFileKind.XrayCore))
    }

    private fun profile(id: Int, source: Int = ResourceFileSourceRoscomvpnGithub) = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "",
        resourceSettings = TrafficConfigResourceSettings(source = source),
    )

    private inline fun withXrayRoot(block: (java.io.File) -> Unit) {
        val root = Files.createTempDirectory("skipi-desktop-resource-snapshot-").toFile()
        try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
