// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream

class ResourceSnapshotFilesBundledFilesTest {

    @Test
    fun firstScopedActivationRestoresEveryV2FlyAssetAndCommitsTheSourceMarker() = withXrayRoot { root ->
        val snapshot = snapshot(root, id = 7, source = ResourceFileSourceV2FlyGithub)
        val assets = FakeAssets(v2FlyAssets())

        snapshot.ensureBundledFiles(
            resourceFileSource = ResourceFileSourceV2FlyGithub,
            bundledUpdatedAtMillis = 1_000L,
            restoreAfterPackageUpdate = false,
            openAsset = assets::open,
            onRestoreFailure = { kind, cause -> throw AssertionError("Unexpected restore failure for $kind", cause) },
        )

        builtInKinds.forEach { kind ->
            assertContentEquals("v2fly:${kind.fileName}".encodeToByteArray(), snapshot.file(kind).readBytes())
        }
        assertEquals("1", markerFile(snapshot).readText())
        assertFalse(assets.requests.any { it == ResourceFileKind.XrayCore.fileName })
    }

    @Test
    fun changedProviderKeepsSuccessfulPartialRestoresAndRetriesUntilTheMarkerCanCommit() = withXrayRoot { root ->
        val snapshot = snapshot(root, id = 8, source = ResourceFileSourceLoyalsoldierGithub)
        snapshot.markResourceFileSource(ResourceFileSourceLoyalsoldierGithub)
        builtInKinds.forEach { kind -> snapshot.file(kind).apply { parentFile.mkdirs(); writeText("old:${kind.fileName}") } }
        val assets = FakeAssets(roscomVpnAssets(includeCnPrivate = false))
        val failures = mutableListOf<Pair<ResourceFileKind, Throwable>>()
        val events = mutableListOf<String>()

        snapshot.ensureBundledFiles(
            resourceFileSource = ResourceFileSourceRoscomvpnGithub,
            bundledUpdatedAtMillis = 0L,
            restoreAfterPackageUpdate = false,
            openAsset = { path ->
                events += "open:$path"
                assets.open(path)
            },
            onRestoreFailure = { kind, error ->
                events += "failure:$kind"
                failures += kind to error
            },
        )

        assertEquals(listOf(ResourceFileKind.GeoIpOnlyCnPrivate), failures.map { it.first })
        assertTrue(failures.single().second is FileNotFoundException)
        assertTrue(
            events.indexOf("failure:${ResourceFileKind.GeoIpOnlyCnPrivate}") <
                events.indexOf("open:xray/${ResourceFileKind.DirectCidrIpv4.fileName}.xz"),
            "Failure reporting for one file should happen before the next file is opened",
        )
        assertContentEquals("roscom:geoip".encodeToByteArray(), snapshot.file(ResourceFileKind.GeoIp).readBytes())
        assertContentEquals("roscom:geosite".encodeToByteArray(), snapshot.file(ResourceFileKind.GeoSite).readBytes())
        assertContentEquals(
            "old:${ResourceFileKind.GeoIpOnlyCnPrivate.fileName}".encodeToByteArray(),
            snapshot.file(ResourceFileKind.GeoIpOnlyCnPrivate).readBytes(),
        )
        assertContentEquals(
            "roscom:${ResourceFileKind.DirectCidrIpv4.fileName}".encodeToByteArray(),
            snapshot.file(ResourceFileKind.DirectCidrIpv4).readBytes(),
        )
        assertContentEquals(
            "roscom:${ResourceFileKind.DirectCidrIpv6.fileName}".encodeToByteArray(),
            snapshot.file(ResourceFileKind.DirectCidrIpv6).readBytes(),
        )
        assertEquals("0", markerFile(snapshot).readText())

        assets.values["geo/roscomvpn/${ResourceFileKind.GeoIpOnlyCnPrivate.fileName}"] =
            "roscom:cn-private".encodeToByteArray()
        failures.clear()
        events.clear()

        snapshot.ensureBundledFiles(
            resourceFileSource = ResourceFileSourceRoscomvpnGithub,
            bundledUpdatedAtMillis = 0L,
            restoreAfterPackageUpdate = false,
            openAsset = { path ->
                events += "open:$path"
                assets.open(path)
            },
            onRestoreFailure = { kind, error -> failures += kind to error },
        )

        assertTrue(failures.isEmpty())
        assertContentEquals(
            "roscom:cn-private".encodeToByteArray(),
            snapshot.file(ResourceFileKind.GeoIpOnlyCnPrivate).readBytes(),
        )
        assertEquals("4", markerFile(snapshot).readText())
    }

    @Test
    fun markerWriteFailureLeavesRestoredFilesAndAnUncommittedSourceForRetry() = withXrayRoot { root ->
        val scope = ResourceFileScope(trafficConfigId = 9, resourceFileSource = ResourceFileSourceV2FlyGithub)
        val assets = FakeAssets(v2FlyAssets())
        val failingWriter = ResourceSnapshotFiles(
            xrayRootDir = root,
            scope = scope,
            atomicWrite = { target, write ->
                if (target.name == ResourceSourceMarkerFileName) {
                    throw IllegalStateException("injected source-marker failure")
                }
                writeAtomically(target, write)
            },
        )

        val error = assertFailsWith<IllegalStateException> {
            failingWriter.ensureBundledFiles(
                resourceFileSource = ResourceFileSourceV2FlyGithub,
                bundledUpdatedAtMillis = 0L,
                restoreAfterPackageUpdate = false,
                openAsset = assets::open,
                onRestoreFailure = { kind, cause -> throw AssertionError("Unexpected restore failure for $kind", cause) },
            )
        }

        assertEquals("injected source-marker failure", error.message)
        builtInKinds.forEach { kind -> assertTrue(failingWriter.file(kind).length() > 0L) }
        assertFalse(markerFile(failingWriter).exists())
        val requestsBeforeRetry = assets.requests.size

        snapshot(root, id = 9, source = ResourceFileSourceV2FlyGithub).ensureBundledFiles(
            resourceFileSource = ResourceFileSourceV2FlyGithub,
            bundledUpdatedAtMillis = 0L,
            restoreAfterPackageUpdate = false,
            openAsset = assets::open,
            onRestoreFailure = { kind, cause -> throw AssertionError("Unexpected restore failure for $kind", cause) },
        )

        assertTrue(assets.requests.size > requestsBeforeRetry)
        assertEquals("1", File(failingWriter.dataDir, ResourceSourceMarkerFileName).readText())
    }

    @Test
    fun nonemptyCustomFilesStayProtectedButMissingOrEmptyCustomFilesStillTryRootFallbacks() = withXrayRoot { root ->
        val snapshot = snapshot(root, id = 10, source = ResourceFileSourceCustom)
        snapshot.markResourceFileSource(ResourceFileSourceCustom)
        builtInKinds.forEach { kind ->
            snapshot.file(kind).apply {
                parentFile.mkdirs()
                writeText("custom:${kind.fileName}")
                setLastModified(500L)
            }
        }
        val assets = FakeAssets(emptyMap())
        val failures = mutableListOf<ResourceFileKind>()

        snapshot.ensureBundledFiles(
            resourceFileSource = ResourceFileSourceCustom,
            bundledUpdatedAtMillis = 1_000L,
            restoreAfterPackageUpdate = true,
            openAsset = assets::open,
            onRestoreFailure = { kind, _ -> failures += kind },
        )

        assertTrue(assets.requests.isEmpty())
        assertTrue(failures.isEmpty())
        assertContentEquals("custom:geoip.dat".encodeToByteArray(), snapshot.file(ResourceFileKind.GeoIp).readBytes())

        val geoIp = snapshot.file(ResourceFileKind.GeoIp)
        geoIp.delete()
        snapshot.ensureBundledFiles(
            resourceFileSource = ResourceFileSourceCustom,
            bundledUpdatedAtMillis = 1_000L,
            restoreAfterPackageUpdate = false,
            openAsset = assets::open,
            onRestoreFailure = { kind, _ -> failures += kind },
        )

        val expectedCustomCandidates = listOf("geoip.dat.xz", "geoip.dat", "geoip.dat.xz", "geoip.dat")
        assertEquals(expectedCustomCandidates, assets.requests)
        assertEquals(listOf(ResourceFileKind.GeoIp), failures)
        assertEquals("5", markerFile(snapshot).readText())

        geoIp.writeBytes(byteArrayOf())
        assets.requests.clear()
        failures.clear()
        snapshot.ensureBundledFiles(
            resourceFileSource = ResourceFileSourceCustom,
            bundledUpdatedAtMillis = 1_000L,
            restoreAfterPackageUpdate = false,
            openAsset = assets::open,
            onRestoreFailure = { kind, _ -> failures += kind },
        )

        assertEquals(expectedCustomCandidates, assets.requests)
        assertEquals(listOf(ResourceFileKind.GeoIp), failures)
        assertEquals("5", markerFile(snapshot).readText())
    }

    @Test
    fun firstProfileMigratesVisibleLegacyFilesOnceAndPreservesExistingProfileFiles() = withXrayRoot { root ->
        val first = snapshot(root, id = 11, source = ResourceFileSourceV2FlyGithub)
        val firstGeoIp = first.file(ResourceFileKind.GeoIp).apply {
            parentFile.mkdirs()
            writeText("current-profile")
        }
        File(root, ResourceFileKind.GeoIp.fileName).writeText("legacy-geoip")
        File(root, ResourceFileKind.GeoSite.fileName).writeText("legacy-geosite")
        File(root, "legacy-custom-rules.dat").writeText("legacy-custom")
        File(root, ResourceFileKind.XrayCore.fileName).writeText("global-core")
        File(root, ".legacy-hidden").writeText("hidden")

        first.migrateLegacyFilesIfNeeded(ResourceFileSourceV2FlyGithub)

        assertEquals("current-profile", firstGeoIp.readText())
        assertEquals("legacy-geosite", first.file(ResourceFileKind.GeoSite).readText())
        assertEquals("legacy-custom", File(first.dataDir, "legacy-custom-rules.dat").readText())
        assertFalse(File(first.dataDir, ".legacy-hidden").exists())
        assertEquals("1", markerFile(first).readText())
        assertEquals("done", File(root, LegacyMigrationMarkerFileName).readText())
        assertEquals("global-core", first.file(ResourceFileKind.XrayCore).readText())

        val second = snapshot(root, id = 12, source = ResourceFileSourceRoscomvpnGithub)
        second.migrateLegacyFilesIfNeeded(ResourceFileSourceRoscomvpnGithub)

        assertFalse(second.file(ResourceFileKind.GeoIp).exists())
        assertFalse(markerFile(second).exists())
        assertEquals("legacy-geoip", File(root, ResourceFileKind.GeoIp.fileName).readText())
    }

    @Test
    fun globalMigrationMarkerFailureLeavesTheProfileMarkerAndOnlyAnotherProfileCanRetry() = withXrayRoot { root ->
        val source = ResourceFileSourceV2FlyGithub
        val first = ResourceSnapshotFiles(
            xrayRootDir = root,
            scope = ResourceFileScope(trafficConfigId = 15, resourceFileSource = source),
            atomicWrite = { target, write ->
                if (target.name == LegacyMigrationMarkerFileName) {
                    throw IllegalStateException("injected migration-marker failure")
                }
                writeAtomically(target, write)
            },
        )
        File(root, "legacy-visible.dat").writeText("legacy bytes")

        val error = assertFailsWith<IllegalStateException> {
            first.migrateLegacyFilesIfNeeded(source)
        }

        assertEquals("injected migration-marker failure", error.message)
        assertEquals("legacy bytes", File(first.dataDir, "legacy-visible.dat").readText())
        assertEquals(source.toString(), markerFile(first).readText())
        assertFalse(File(root, LegacyMigrationMarkerFileName).exists())

        snapshot(root, id = 15, source = source).migrateLegacyFilesIfNeeded(source)

        assertFalse(
            File(root, LegacyMigrationMarkerFileName).exists(),
            "A profile with its source marker already written should skip migration retry",
        )

        val second = snapshot(root, id = 16, source = ResourceFileSourceRoscomvpnGithub)
        second.migrateLegacyFilesIfNeeded(ResourceFileSourceRoscomvpnGithub)

        assertEquals("legacy bytes", File(second.dataDir, "legacy-visible.dat").readText())
        assertEquals(ResourceFileSourceRoscomvpnGithub.toString(), markerFile(second).readText())
        assertEquals("done", File(root, LegacyMigrationMarkerFileName).readText())
    }

    @Test
    fun firstFoundCorruptXzAssetFailsWithoutTryingItsRawFallback() = withXrayRoot { root ->
        val snapshot = snapshot(root, id = 13, source = ResourceFileSourceV2FlyGithub)
        val target = snapshot.file(ResourceFileKind.GeoIp).apply {
            parentFile.mkdirs()
            writeText("old")
        }
        val assets = FakeAssets(
            mapOf(
                "geo/v2fly/geoip.dat.xz" to "not a valid XZ stream".encodeToByteArray(),
                "geo/v2fly/geoip.dat" to "valid raw fallback".encodeToByteArray(),
            ),
        )

        assertFailsWith<Throwable> {
            snapshot.restoreBundled(
                kind = ResourceFileKind.GeoIp,
                resourceFileSource = ResourceFileSourceV2FlyGithub,
                openAsset = assets::open,
            )
        }

        assertEquals(listOf("geo/v2fly/geoip.dat.xz"), assets.requests)
        assertEquals("old", target.readText())
    }

    @Test
    fun restoringXrayCoreIsRejectedBeforeAnyAssetIsOpened() = withXrayRoot { root ->
        val snapshot = snapshot(root, id = 17, source = ResourceFileSourceV2FlyGithub)
        val assets = FakeAssets(emptyMap())

        assertFailsWith<IllegalArgumentException> {
            snapshot.restoreBundled(
                kind = ResourceFileKind.XrayCore,
                resourceFileSource = ResourceFileSourceV2FlyGithub,
                openAsset = assets::open,
            )
        }

        assertTrue(assets.requests.isEmpty())
    }

    @Test
    fun aValidFirstFoundXzAssetIsDecompressedBeforeAtomicReplacement() = withXrayRoot { root ->
        val snapshot = snapshot(root, id = 14, source = ResourceFileSourceV2FlyGithub)
        val expected = "decompressed bundled data".encodeToByteArray()
        val assets = FakeAssets(mapOf("geo/v2fly/geoip.dat.xz" to compressWithXz(expected)))

        snapshot.restoreBundled(
            kind = ResourceFileKind.GeoIp,
            resourceFileSource = ResourceFileSourceV2FlyGithub,
            openAsset = assets::open,
        )

        assertEquals(listOf("geo/v2fly/geoip.dat.xz"), assets.requests)
        assertContentEquals(expected, snapshot.file(ResourceFileKind.GeoIp).readBytes())
    }

    @Test
    fun unscopedSourceMismatchDoesNotForceBundledActivationOrWriteAProfileMarker() = withXrayRoot { root ->
        val snapshot = ResourceSnapshotFiles(root, scope = null, atomicWrite = ::writeAtomically)
        builtInKinds.forEach { kind ->
            snapshot.file(kind).apply { parentFile.mkdirs(); writeText("already managed") }
        }
        val assets = FakeAssets(emptyMap())

        snapshot.ensureBundledFiles(
            resourceFileSource = ResourceFileSourceRoscomvpnGithub,
            bundledUpdatedAtMillis = 10_000L,
            restoreAfterPackageUpdate = false,
            openAsset = assets::open,
            onRestoreFailure = { kind, cause -> throw AssertionError("Unexpected restore failure for $kind", cause) },
        )

        assertTrue(assets.requests.isEmpty())
        assertFalse(File(root, ResourceSourceMarkerFileName).exists())
        assertTrue(builtInKinds.all { snapshot.file(it).readText() == "already managed" })
    }

    private fun snapshot(root: File, id: Int, source: Int): ResourceSnapshotFiles = ResourceSnapshotFiles(
        xrayRootDir = root,
        scope = ResourceFileScope(trafficConfigId = id, resourceFileSource = source),
        atomicWrite = ::writeAtomically,
    )

    private fun markerFile(snapshot: ResourceSnapshotFiles) = File(snapshot.dataDir, ResourceSourceMarkerFileName)

    private fun v2FlyAssets(): MutableMap<String, ByteArray> = linkedMapOf(
        "geo/v2fly/${ResourceFileKind.GeoIp.fileName}" to "v2fly:${ResourceFileKind.GeoIp.fileName}".encodeToByteArray(),
        "geo/v2fly/${ResourceFileKind.GeoSite.fileName}" to "v2fly:${ResourceFileKind.GeoSite.fileName}".encodeToByteArray(),
        "geo/v2fly/${ResourceFileKind.GeoIpOnlyCnPrivate.fileName}" to
            "v2fly:${ResourceFileKind.GeoIpOnlyCnPrivate.fileName}".encodeToByteArray(),
        "xray/${ResourceFileKind.DirectCidrIpv4.fileName}" to
            "v2fly:${ResourceFileKind.DirectCidrIpv4.fileName}".encodeToByteArray(),
        "xray/${ResourceFileKind.DirectCidrIpv6.fileName}" to
            "v2fly:${ResourceFileKind.DirectCidrIpv6.fileName}".encodeToByteArray(),
    )

    private fun roscomVpnAssets(includeCnPrivate: Boolean): Map<String, ByteArray> = buildMap {
        put("geo/roscomvpn/${ResourceFileKind.GeoIp.fileName}", "roscom:geoip".encodeToByteArray())
        put("geo/roscomvpn/${ResourceFileKind.GeoSite.fileName}", "roscom:geosite".encodeToByteArray())
        if (includeCnPrivate) {
            put(
                "geo/roscomvpn/${ResourceFileKind.GeoIpOnlyCnPrivate.fileName}",
                "roscom:cn-private".encodeToByteArray(),
            )
        }
        put("xray/${ResourceFileKind.DirectCidrIpv4.fileName}", "roscom:${ResourceFileKind.DirectCidrIpv4.fileName}".encodeToByteArray())
        put("xray/${ResourceFileKind.DirectCidrIpv6.fileName}", "roscom:${ResourceFileKind.DirectCidrIpv6.fileName}".encodeToByteArray())
    }

    private class FakeAssets(values: Map<String, ByteArray>) {
        val values = values.toMutableMap()
        val requests = mutableListOf<String>()

        fun open(path: String): InputStream? {
            requests += path
            return values[path]?.let(::ByteArrayInputStream)
        }
    }

    private fun writeAtomically(target: File, write: (OutputStream) -> Unit) {
        val parent = target.parentFile ?: error("Missing parent directory for ${target.absolutePath}")
        Files.createDirectories(parent.toPath())
        val prefix = "${target.name}.".let { if (it.length >= 3) it else it.padEnd(3, '_') }
        val temporary = File.createTempFile(prefix, ".tmp", parent)
        try {
            temporary.outputStream().use(write)
            if (temporary.length() <= 0L) error("${target.name} is empty")
            Files.move(temporary.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            temporary.delete()
        }
    }

    private fun compressWithXz(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        XZOutputStream(output, LZMA2Options(6)).use { it.write(data) }
        return output.toByteArray()
    }

    private inline fun withXrayRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("skipi-bundled-snapshot-").toFile()
        try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private companion object {
        const val ResourceSourceMarkerFileName = ".resource-source"
        const val LegacyMigrationMarkerFileName = ".profile-resources-migrated-v1"
        val builtInKinds = ResourceFileKind.entries.filter { it != ResourceFileKind.XrayCore }
    }
}
