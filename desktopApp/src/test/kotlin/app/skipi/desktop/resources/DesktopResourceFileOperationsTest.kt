// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.desktop

import features.config.TrafficConfigCustomResourceFile
import features.resources.ResourceFileKind
import features.resources.ResourceFileOperations
import features.resources.ResourceFileScope
import features.resources.ResourceFileSourceV2FlyGithub
import features.resources.resourceFileSourceAssetDir
import features.resources.runtime.openDecompressedStream
import java.io.File
import java.nio.file.Files
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopResourceFileOperationsTest {

    @Test
    fun statusAndReplaceStayInTheCapturedProfileWhenThePickerChangesSelection() = withXrayRoot { root ->
        val files = DesktopProfileResourceFiles(root)
        val scope = ResourceFileScope(trafficConfigId = 51, resourceFileSource = ResourceFileSourceV2FlyGithub)
        val siblingScope = scope.copy(trafficConfigId = 52)
        val custom = TrafficConfigCustomResourceFile(id = 3, name = "custom-rules.dat", url = "")
        val targetProfile = files.snapshot(scope)
        val siblingProfile = files.snapshot(siblingScope)
        val oldBuiltIn = byteArrayOf(1, 2, 3)
        val customBytes = byteArrayOf(4, 5)
        val siblingBytes = byteArrayOf(6, 7, 8)
        val selectedBuiltInBytes = byteArrayOf(9, 10, 11, 12)
        val selectedCustomBytes = byteArrayOf(13, 14, 15)
        targetProfile.file(ResourceFileKind.GeoIp).apply {
            parentFile.mkdirs()
            writeBytes(oldBuiltIn)
        }
        targetProfile.file(custom).writeBytes(customBytes)
        val siblingTarget = siblingProfile.file(ResourceFileKind.GeoIp).apply {
            parentFile.mkdirs()
            writeBytes(siblingBytes)
        }
        val core = targetProfile.file(ResourceFileKind.XrayCore).apply {
            parentFile.mkdirs()
            writeText("global-core-bytes")
        }
        val pickedBuiltInFile = Files.createTempFile(root.toPath(), "picked-built-in-", ".dat")
        Files.write(pickedBuiltInFile, selectedBuiltInBytes)
        val pickedCustomFile = Files.createTempFile(root.toPath(), "picked-custom-", ".dat")
        Files.write(pickedCustomFile, selectedCustomBytes)
        var selectedProfileId = scope.trafficConfigId
        var pickerCalls = 0
        val pickedFiles = listOf(pickedBuiltInFile, pickedCustomFile)
        val operations: ResourceFileOperations = DesktopResourceFileOperations(files) {
            selectedProfileId = siblingScope.trafficConfigId
            pickedFiles[pickerCalls++]
        }

        val initialStatus = runBlocking { operations.status(scope, listOf(custom)) }
        val statusAfterReplace = requireNotNull(
            runBlocking { operations.replace(scope, ResourceFileKind.GeoIp, listOf(custom)) },
        )
        val statusAfterCustomReplace = requireNotNull(
            runBlocking { operations.replaceCustom(scope, custom, listOf(custom)) },
        )

        assertEquals(2, pickerCalls)
        assertEquals(siblingScope.trafficConfigId, selectedProfileId)
        assertContentEquals(selectedBuiltInBytes, targetProfile.file(ResourceFileKind.GeoIp).readBytes())
        assertContentEquals(selectedCustomBytes, targetProfile.file(custom).readBytes())
        assertContentEquals(siblingBytes, siblingTarget.readBytes())
        assertEquals("global-core-bytes", core.readText())
        assertTrue(initialStatus.geoIp.exists)
        assertEquals(selectedBuiltInBytes.size.toLong(), statusAfterReplace.geoIp.sizeBytes)
        assertEquals(custom, statusAfterReplace.customResourceFiles.single().file)
        assertEquals(customBytes.size.toLong(), statusAfterReplace.customResourceFiles.single().status.sizeBytes)
        assertEquals(selectedCustomBytes.size.toLong(), statusAfterCustomReplace.customResourceFiles.single().status.sizeBytes)
        assertTrue(statusAfterReplace.xrayCore.exists)
    }

    @Test
    fun cancelledPickerReturnsNullAndLeavesTheSelectedProfileFileUntouched() = withXrayRoot { root ->
        val files = DesktopProfileResourceFiles(root)
        val scope = ResourceFileScope(trafficConfigId = 61, resourceFileSource = ResourceFileSourceV2FlyGithub)
        val snapshot = files.snapshot(scope)
        val oldBytes = byteArrayOf(20, 21, 22)
        val target = snapshot.file(ResourceFileKind.GeoSite).apply {
            parentFile.mkdirs()
            writeBytes(oldBytes)
        }
        var pickerCalls = 0
        val operations: ResourceFileOperations = DesktopResourceFileOperations(files) {
            pickerCalls += 1
            null
        }

        val result = runBlocking { operations.replace(scope, ResourceFileKind.GeoSite) }

        assertEquals(1, pickerCalls)
        assertEquals(null, result)
        assertContentEquals(oldBytes, target.readBytes())
    }

    @Test
    fun selectingAnEmptyFileFailsWithoutReplacingTheExistingResource() = withXrayRoot { root ->
        val files = DesktopProfileResourceFiles(root)
        val scope = ResourceFileScope(trafficConfigId = 62, resourceFileSource = ResourceFileSourceV2FlyGithub)
        val snapshot = files.snapshot(scope)
        val oldBytes = byteArrayOf(30, 31, 32)
        val target = snapshot.file(ResourceFileKind.GeoIp).apply {
            parentFile.mkdirs()
            writeBytes(oldBytes)
        }
        val emptyPickedFile = Files.createTempFile(root.toPath(), "empty-resource-", ".dat")
        val operations: ResourceFileOperations = DesktopResourceFileOperations(files) { emptyPickedFile }

        val error = assertFailsWith<IllegalStateException> {
            runBlocking { operations.replace(scope, ResourceFileKind.GeoIp) }
        }

        assertEquals("${ResourceFileKind.GeoIp.fileName} is empty", error.message)
        assertContentEquals(oldBytes, target.readBytes())
        assertFalse(File(snapshot.dataDir, "${target.name}.replace.tmp").exists())
    }

    @Test
    fun customRenameAndDeleteChangeOnlyTheCapturedProfile() = withXrayRoot { root ->
        val files = DesktopProfileResourceFiles(root)
        val scope = ResourceFileScope(trafficConfigId = 71, resourceFileSource = ResourceFileSourceV2FlyGithub)
        val siblingScope = scope.copy(trafficConfigId = 72)
        val previous = TrafficConfigCustomResourceFile(id = 1, name = "before.dat", url = "")
        val renamed = TrafficConfigCustomResourceFile(id = previous.id, name = "after.dat", url = "")
        val contents = byteArrayOf(40, 41, 42)
        val siblingContents = byteArrayOf(43, 44)
        val profile = files.snapshot(scope)
        val sibling = files.snapshot(siblingScope)
        val source = profile.file(previous).apply {
            parentFile.mkdirs()
            writeBytes(contents)
        }
        val siblingTarget = sibling.file(renamed).apply {
            parentFile.mkdirs()
            writeBytes(siblingContents)
        }
        val operations: ResourceFileOperations = DesktopResourceFileOperations(files) {
            error("Custom rename and delete must not open the picker")
        }

        val renameStatus = runBlocking { operations.renameCustom(scope, previous, renamed, listOf(renamed)) }
        val renamedTarget = profile.file(renamed)
        val bytesAfterRename = renamedTarget.readBytes()
        val deleteStatus = runBlocking { operations.deleteCustom(scope, renamed, listOf(renamed)) }

        assertFalse(source.exists())
        assertContentEquals(contents, bytesAfterRename)
        assertEquals(renamed, renameStatus.customResourceFiles.single().file)
        assertEquals(contents.size.toLong(), renameStatus.customResourceFiles.single().status.sizeBytes)
        assertFalse(renamedTarget.exists())
        assertEquals(0L, deleteStatus.customResourceFiles.single().status.sizeBytes)
        assertContentEquals(siblingContents, siblingTarget.readBytes())
    }

    @Test
    fun restoreUsesThePackagedProviderAssetForTheCapturedProfileAndLeavesCoreAlone() = withXrayRoot { root ->
        val files = DesktopProfileResourceFiles(root)
        val scope = ResourceFileScope(trafficConfigId = 81, resourceFileSource = ResourceFileSourceV2FlyGithub)
        val siblingScope = scope.copy(trafficConfigId = 82)
        val profile = files.snapshot(scope)
        val sibling = files.snapshot(siblingScope)
        val target = profile.file(ResourceFileKind.GeoIp).apply {
            parentFile.mkdirs()
            writeText("old-profile-geoip")
        }
        val siblingTarget = sibling.file(ResourceFileKind.GeoIp).apply {
            parentFile.mkdirs()
            writeText("sibling-geoip")
        }
        val core = profile.file(ResourceFileKind.XrayCore).apply {
            parentFile.mkdirs()
            writeText("global-core-bytes")
        }
        val assetPath = "${resourceFileSourceAssetDir(scope.resourceFileSource)}/${ResourceFileKind.GeoIp.fileName}.xz"
        val asset = requireNotNull(
            DesktopProfileResourceFiles::class.java.classLoader?.getResourceAsStream(assetPath),
        ) { "Expected packaged resource asset $assetPath" }
        val expected = openDecompressedStream(asset, assetPath).use { it.readBytes() }

        val operations: ResourceFileOperations = DesktopResourceFileOperations(files) {
            error("Bundled restore must not open the picker")
        }
        val result = runBlocking { operations.restoreBundled(scope, ResourceFileKind.GeoIp) }

        assertTrue(target.isFile)
        assertContentEquals(expected, target.readBytes())
        assertTrue(result.geoIp.exists)
        assertEquals("sibling-geoip", siblingTarget.readText())
        assertEquals("global-core-bytes", core.readText())
    }

    private inline fun <T> withXrayRoot(block: (File) -> T): T {
        val root = Files.createTempDirectory("skipi-desktop-resource-operations-").toFile()
        return try {
            block(root)
        } finally {
            Files.walk(root.toPath()).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }
}
