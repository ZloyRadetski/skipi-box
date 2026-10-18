// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.resources

import features.config.TrafficConfigCustomResourceFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream
import java.nio.file.Files
import java.util.Comparator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ResourceSnapshotFilesTest {

    @Test
    fun profileScopeRejectsNonPositiveTrafficConfigIds() {
        for (invalidId in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> {
                ResourceFileScope(
                    trafficConfigId = invalidId,
                    resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
                )
            }
        }
    }

    @Test
    fun profileFilesAreIsolatedAndCorePathRemainsAtTheGlobalRoot() = withXrayRoot { root ->
        val first = snapshot(root, trafficConfigId = 7)
        val sibling = snapshot(root, trafficConfigId = 12)

        first.replace(ResourceFileKind.GeoIp) { bytes(1, 2, 3) }

        assertEquals(File(root, "profiles/7"), first.dataDir)
        assertEquals(File(root, "profiles/12"), sibling.dataDir)
        assertEquals(File(root, "profiles/7/geoip.dat"), first.file(ResourceFileKind.GeoIp))
        assertEquals(listOf<Byte>(1, 2, 3), first.file(ResourceFileKind.GeoIp).readBytes().toList())
        assertFalse(sibling.file(ResourceFileKind.GeoIp).exists())
        assertEquals(File(root, "xray"), first.file(ResourceFileKind.XrayCore))
        assertEquals(first.file(ResourceFileKind.XrayCore), sibling.file(ResourceFileKind.XrayCore))
    }

    @Test
    fun statusReportsEmptyFilesAsUnavailableAndPreservesExistingMetadata() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 5)
        val empty = snapshot.file(ResourceFileKind.GeoIp).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf())
        }
        val populated = snapshot.file(ResourceFileKind.GeoSite).apply {
            writeBytes(byteArrayOf(4, 5, 6))
            setLastModified(1_700_000_000_000L)
        }
        val custom = TrafficConfigCustomResourceFile(2, "extra.dat", "https://geo.example/extra.dat")
        val customFile = snapshot.file(custom).apply { writeBytes(byteArrayOf(7, 8)) }

        val status = snapshot.currentStatus(listOf(custom))

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
    fun replacementRejectsEmptyInputAndLeavesPreviousContentsIntact() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 4)
        val target = snapshot.file(ResourceFileKind.GeoSite).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9, 10))
        }

        val error = runCatching { snapshot.replace(ResourceFileKind.GeoSite) { bytes() } }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf<Byte>(9, 10), target.readBytes().toList())
        assertFalse(File(target.parentFile, "${target.name}.replace.tmp").exists())
    }

    @Test
    fun atomicWriteFailurePreservesOldTargetAndCleansReplacementStagingFile() = withXrayRoot { root ->
        val target = File(root, "profiles/4/geosite.dat").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9, 10))
        }
        val snapshot = ResourceSnapshotFiles(
            xrayRootDir = root,
            scope = ResourceFileScope(
                trafficConfigId = 4,
                resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
            ),
            atomicWrite = { _, _ -> throw IllegalStateException("injected atomic write failure") },
        )

        val error = runCatching { snapshot.replace(ResourceFileKind.GeoSite) { bytes(11, 12) } }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(listOf<Byte>(9, 10), target.readBytes().toList())
        assertFalse(File(target.parentFile, "${target.name}.replace.tmp").exists())
    }

    @Test
    fun inputOpenFailureLeavesExistingReplacementStagingFileUntouched() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 6)
        val target = snapshot.file(ResourceFileKind.GeoIp).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2))
        }
        val replaceTempFile = File(target.parentFile, "${target.name}.replace.tmp")
            .apply { writeBytes(byteArrayOf(70, 71, 72)) }

        val error = runCatching {
            snapshot.replace(ResourceFileKind.GeoIp) { throw FileNotFoundException("source unavailable") }
        }.exceptionOrNull()

        assertTrue(error is FileNotFoundException)
        assertEquals(listOf<Byte>(1, 2), target.readBytes().toList())
        assertEquals(listOf<Byte>(70, 71, 72), replaceTempFile.readBytes().toList())
    }

    @Test
    fun replacementConsumesTheInputOnceAndWritesIntoTheCapturedProfile() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 9)
        val sibling = snapshot(root, trafficConfigId = 10)
        var opens = 0

        snapshot.replace(ResourceFileKind.GeoIp) {
            opens += 1
            bytes(20, 21)
        }

        assertEquals(1, opens)
        assertEquals(listOf<Byte>(20, 21), snapshot.file(ResourceFileKind.GeoIp).readBytes().toList())
        assertFalse(sibling.file(ResourceFileKind.GeoIp).exists())
    }

    @Test
    fun xrayCoreAndReservedCustomNamesAreGuardedBeforeInputIsOpened() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 2)
        var opens = 0
        val coreError = runCatching {
            snapshot.replace(ResourceFileKind.XrayCore) {
                opens += 1
                bytes(1)
            }
        }.exceptionOrNull()
        val collidingCustom = TrafficConfigCustomResourceFile(3, "geoip.dat", "https://geo.example/custom.dat")

        snapshot.replaceCustom(collidingCustom) {
            opens += 1
            bytes(2)
        }
        snapshot.deleteCustom(collidingCustom)

        assertTrue(coreError is IllegalArgumentException)
        assertEquals(0, opens)
        assertFalse(snapshot.file(ResourceFileKind.GeoIp).exists())
    }

    @Test
    fun customPathsUseTheSanitizedFilenameAndIdBasedFallback() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 6)

        assertEquals(
            File(snapshot.dataDir, "rules.dat"),
            snapshot.file(TrafficConfigCustomResourceFile(1, "folder\\rules.dat", "")),
        )
        assertEquals(
            File(snapshot.dataDir, "custom-resource-8.dat"),
            snapshot.file(TrafficConfigCustomResourceFile(8, "..", "")),
        )
        assertEquals(
            File(snapshot.dataDir, "custom-resource-11.dat"),
            snapshot.file(TrafficConfigCustomResourceFile(11, "", "")),
        )
    }

    @Test
    fun customRenameOverwritesAnExistingDestinationAndDeleteIsScoped() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 3)
        val sibling = snapshot(root, trafficConfigId = 4)
        val previous = TrafficConfigCustomResourceFile(1, "before.dat", "")
        val renamed = TrafficConfigCustomResourceFile(2, "after.dat", "")
        val source = snapshot.file(previous).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(30, 31, 32))
        }
        val destination = snapshot.file(renamed).apply { writeBytes(byteArrayOf(1)) }
        val siblingFile = sibling.file(renamed).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(40))
        }

        snapshot.renameCustom(previous, renamed)

        assertFalse(source.exists())
        assertEquals(listOf<Byte>(30, 31, 32), destination.readBytes().toList())
        assertEquals(listOf<Byte>(40), siblingFile.readBytes().toList())

        snapshot.deleteCustom(renamed)

        assertFalse(destination.exists())
        assertTrue(siblingFile.isFile)
    }

    @Test
    fun customRenameIgnoresReservedNamesAndSamePath() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 8)
        val builtInSource = snapshot.file(ResourceFileKind.GeoIp).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(50))
        }
        val ordinaryTarget = TrafficConfigCustomResourceFile(2, "ordinary.dat", "")
        val ordinarySourceState = TrafficConfigCustomResourceFile(1, "source.dat", "")
        val ordinarySource = snapshot.file(ordinarySourceState).apply {
            writeBytes(byteArrayOf(60))
        }
        val builtInTarget = snapshot.file(ResourceFileKind.GeoSite).apply { writeBytes(byteArrayOf(70)) }
        val sameNameBefore = TrafficConfigCustomResourceFile(3, "same.dat", "before")
        val sameNameAfter = TrafficConfigCustomResourceFile(4, "same.dat", "after")
        val samePath = snapshot.file(sameNameBefore).apply { writeBytes(byteArrayOf(80)) }

        snapshot.renameCustom(
            TrafficConfigCustomResourceFile(5, ResourceFileKind.GeoIp.fileName, ""),
            ordinaryTarget,
        )
        snapshot.renameCustom(
            ordinarySourceState,
            TrafficConfigCustomResourceFile(6, ResourceFileKind.GeoSite.fileName, ""),
        )
        snapshot.renameCustom(sameNameBefore, sameNameAfter)

        assertEquals(listOf<Byte>(50), builtInSource.readBytes().toList())
        assertFalse(snapshot.file(ordinaryTarget).exists())
        assertEquals(listOf<Byte>(60), ordinarySource.readBytes().toList())
        assertEquals(listOf<Byte>(70), builtInTarget.readBytes().toList())
        assertEquals(listOf<Byte>(80), samePath.readBytes().toList())
    }

    @Test
    fun missingCustomRenameLeavesAnExistingDestinationAlone() = withXrayRoot { root ->
        val snapshot = snapshot(root, trafficConfigId = 9)
        val missing = TrafficConfigCustomResourceFile(1, "missing.dat", "")
        val destinationState = TrafficConfigCustomResourceFile(2, "destination.dat", "")
        val destination = snapshot.file(destinationState).apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(90, 91))
        }

        snapshot.renameCustom(missing, destinationState)

        assertEquals(listOf<Byte>(90, 91), destination.readBytes().toList())
    }

    private fun snapshot(root: File, trafficConfigId: Int): ResourceSnapshotFiles = ResourceSnapshotFiles(
        xrayRootDir = root,
        scope = ResourceFileScope(
            trafficConfigId = trafficConfigId,
            resourceFileSource = ResourceFileSourceLoyalsoldierGithub,
        ),
        atomicWrite = ::writeForTest,
    )

    private fun writeForTest(target: File, write: (OutputStream) -> Unit) {
        target.parentFile?.mkdirs()
        target.outputStream().use(write)
    }

    private fun bytes(vararg values: Int) = ByteArrayInputStream(values.map(Int::toByte).toByteArray())

    private inline fun withXrayRoot(block: (File) -> Unit) {
        val root = Files.createTempDirectory("skipi-resource-snapshot-").toFile()
        try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
