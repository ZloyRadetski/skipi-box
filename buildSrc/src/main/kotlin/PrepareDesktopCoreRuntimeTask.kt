import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Stages an in-process SKIPI Core library and the Xray geo data it consumes.
 *
 * The native library is deliberately supplied by the sibling `skipi-core`
 * checkout (or an explicit Gradle property). It is never substituted with an
 * `xray.exe` executable.
 */
abstract class PrepareDesktopCoreRuntimeTask : DefaultTask() {
    @get:Input
    abstract val coreVersion: Property<String>

    @get:Input
    abstract val coreLibraryPath: Property<String>

    @get:Input
    abstract val coreLicensePath: Property<String>

    @get:Input
    abstract val outputLibraryName: Property<String>

    @get:Input
    abstract val xrayVersion: Property<String>

    @get:Input
    abstract val expectedGeoArchiveSha256: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "distribution"
        description = "Stages the in-process SKIPI Core desktop library and verified geo assets."
    }

    @TaskAction
    fun prepare() {
        val expectedChecksum = expectedGeoArchiveSha256.get().lowercase()
        require(expectedChecksum.matches(Regex("[0-9a-f]{64}"))) {
            "The configured Xray geo archive checksum must be a SHA-256 hex digest."
        }

        val sourceLibrary = File(coreLibraryPath.get()).absoluteFile
        require(sourceLibrary.isFile && sourceLibrary.length() > 0) {
            "SKIPI Core desktop library was not found at ${sourceLibrary.path}. " +
                "Build ../skipi-core with the Desktop shared-library command, or pass " +
                "-PskipiCoreDesktopLibrary=<path-to-${outputLibraryName.get()}>."
        }

        val destination = outputDirectory.get().asFile
        destination.mkdirs()
        val destinationLibrary = File(destination, outputLibraryName.get())
        copyIfChanged(sourceLibrary, destinationLibrary)
        val sourceLicense = File(coreLicensePath.get()).absoluteFile
        require(sourceLicense.isFile && sourceLicense.length() > 0) {
            "SKIPI Core GPL license was not found at ${sourceLicense.path}."
        }
        copyIfChanged(sourceLicense, File(destination, "SKIPI_CORE_LICENSE.txt"))
        stageCoreAttribution(destination)

        val marker = File(destination, ".xray-geo-archive-sha256")
        if (!areGeoAssetsCurrent(destination, marker, expectedChecksum)) {
            downloadAndExtractGeoAssets(destination, marker, expectedChecksum)
        }
    }

    private fun stageCoreAttribution(destination: File) {
        File(destination, "SKIPI_CORE_SOURCE.txt").writeText(
            """
            SKIPI Core ${coreVersion.get()}
            Source code: https://github.com/ZloyRadetski/skipi-core
            License: GPL-3.0
            """.trimIndent() + System.lineSeparator(),
        )
    }

    private fun areGeoAssetsCurrent(destination: File, marker: File, expectedChecksum: String): Boolean =
        marker.isFile &&
            marker.readText().trim().equals(expectedChecksum, ignoreCase = true) &&
            geoRuntimeFiles.all { File(destination, it).isFile && File(destination, it).length() > 0 }

    private fun downloadAndExtractGeoAssets(destination: File, marker: File, expectedChecksum: String) {
        val archive = Files.createTempFile("skipi-xray-geo-", ".zip").toFile()
        try {
            val releaseUrl = "https://github.com/XTLS/Xray-core/releases/download/${xrayVersion.get()}/Xray-windows-64.zip"
            val actualChecksum = downloadArchive(releaseUrl, archive)
            if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                throw GradleException(
                    "Xray geo archive checksum mismatch. Expected $expectedChecksum, got $actualChecksum.",
                )
            }
            extractGeoAssets(archive, destination)
            marker.writeText(expectedChecksum + System.lineSeparator())
        } finally {
            archive.delete()
        }
    }

    private fun downloadArchive(url: String, archive: File): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 300_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SKIPI desktop distribution builder")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw GradleException("Could not download Xray geo assets: HTTP ${connection.responseCode}.")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                archive.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractGeoAssets(archive: File, destination: File) {
        ZipFile(archive).use { zip ->
            geoRuntimeFiles.forEach { fileName ->
                val entry = zip.entries().asSequence().firstOrNull {
                    !it.isDirectory && it.name.substringAfterLast('/') == fileName
                } ?: throw GradleException("The official Xray archive does not contain $fileName.")
                val target = File(destination, fileName)
                val temporary = File(destination, ".$fileName.download")
                zip.getInputStream(entry).use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private fun copyIfChanged(source: File, target: File) {
        if (target.isFile && target.length() == source.length() && sha256(target) == sha256(source)) return
        val temporary = File(target.parentFile, ".${target.name}.copy")
        Files.copy(source.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private companion object {
        val geoRuntimeFiles = listOf("geoip.dat", "geosite.dat", "LICENSE")
    }
}
