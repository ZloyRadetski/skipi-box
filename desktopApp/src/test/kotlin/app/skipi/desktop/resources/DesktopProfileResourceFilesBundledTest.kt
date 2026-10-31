// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigResourceSettings
import features.config.TrafficConfigState
import features.resources.ResourceFileKind
import features.resources.ResourceFileSourceV2FlyGithub
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopProfileResourceFilesBundledTest {

    @Test
    fun prepareRestoresTheProfileBundledSnapshotFromDesktopClasspathAssets() = withXrayRoot { root ->
        val profile = profile(id = 31)
        val files = DesktopProfileResourceFiles(xrayRootDir = root)

        val prepared = files.prepare(
            profile = profile,
            bundledUpdatedAtMillis = 1_000L,
            restoreAfterPackageUpdate = false,
        )

        assertEquals(File(root, "profiles/31"), prepared.dataDir)
        builtInKinds.forEach { kind ->
            assertTrue(prepared.file(kind).isFile, "${kind.fileName} should be restored")
            assertTrue(prepared.file(kind).length() > 0L, "${kind.fileName} should be nonempty")
        }
        assertEquals(ResourceFileSourceV2FlyGithub.toString(), File(prepared.dataDir, ".resource-source").readText())
    }

    @Test
    fun prepareUsesTheSuppliedBundleTimestampWhenCheckingPackageUpdateRestores() = withXrayRoot { root ->
        val profile = profile(id = 32)
        val files = DesktopProfileResourceFiles(xrayRootDir = root)
        val existing = files.snapshot(profile)
        existing.markResourceFileSource(ResourceFileSourceV2FlyGithub)
        builtInKinds.forEach { kind ->
            existing.file(kind).apply {
                parentFile.mkdirs()
                writeText("user:${kind.fileName}")
                setLastModified(2_000L)
            }
        }

        val prepared = files.prepare(
            profile = profile,
            bundledUpdatedAtMillis = 1_000L,
            restoreAfterPackageUpdate = true,
        )

        builtInKinds.forEach { kind ->
            assertContentEquals(
                "user:${kind.fileName}".encodeToByteArray(),
                prepared.file(kind).readBytes(),
                "A target newer than the explicit bundled timestamp should remain untouched",
            )
        }
    }

    private fun profile(id: Int) = TrafficConfigState(
        id = id,
        name = "Profile $id",
        rawConfig = "",
        resourceSettings = TrafficConfigResourceSettings(source = ResourceFileSourceV2FlyGithub),
    )

    private inline fun withXrayRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("skipi-desktop-bundled-snapshot-").toFile()
        try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private companion object {
        val builtInKinds = ResourceFileKind.entries.filter { it != ResourceFileKind.XrayCore }
    }
}
