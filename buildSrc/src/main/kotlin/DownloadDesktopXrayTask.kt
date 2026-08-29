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

abstract class DownloadDesktopXrayTask : DefaultTask() {
    @get:Input
    abstract val xrayVersion: Property<String>

    @get:Input
    abstract val expectedArchiveSha256: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "distribution"
        description = "Downloads the pinned Xray Windows runtime and verifies its official archive checksum."
    }

    @TaskAction
    fun download() {
        val version = xrayVersion.get()
        val expectedChecksum = expectedArchiveSha256.get().lowercase()
        require(expectedChecksum.matches(Regex("[0-9a-f]{64}"))) {
            "The configured Xray archive checksum must be a SHA-256 hex digest."
        }

        val destination = outputDirectory.get().asFile
        val marker = File(destination, ".xray-archive-sha256")
        if (isCurrent(destination, marker, expectedChecksum)) {
            logger.lifecycle("Using verified Xray runtime already prepared at ${destination.absolutePath}")
            return
        }

        val archive = Files.createTempFile("skipi-xray-windows-", ".zip").toFile()
        try {
            val releaseUrl = "https://github.com/XTLS/Xray-core/releases/download/$version/Xray-windows-64.zip"
            val actualChecksum = downloadArchive(releaseUrl, archive)
            if (!actualChecksum.equals(expectedChecksum, ignoreCase = true)) {
                throw GradleException(
                    "Xray archive checksum mismatch. Expected $expectedChecksum, got $actualChecksum."
                )
            }

            extractRuntime(archive, destination, marker, expectedChecksum, version, releaseUrl)
        } finally {
            archive.delete()
        }
    }

    private fun isCurrent(destination: File, marker: File, expectedChecksum: String): Boolean =
        marker.isFile &&
            marker.readText().trim().equals(expectedChecksum, ignoreCase = true) &&
            runtimeFiles.all { File(destination, it).isFile && File(destination, it).length() > 0 }

    private fun downloadArchive(url: String, archive: File): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 300_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SKIPI desktop distribution builder")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw GradleException("Could not download Xray runtime: HTTP ${connection.responseCode}.")
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

    private fun extractRuntime(
        archive: File,
        destination: File,
        marker: File,
        checksum: String,
        version: String,
        releaseUrl: String,
    ) {
        destination.mkdirs()
        marker.delete()

        ZipFile(archive).use { zip ->
            runtimeFiles.forEach { fileName ->
                val entry = zip.entries().asSequence().firstOrNull {
                    !it.isDirectory && it.name.substringAfterLast('/') == fileName
                } ?: throw GradleException("The official Xray archive does not contain $fileName.")

                val target = File(destination, fileName)
                val temporary = File(destination, ".$fileName.download")
                zip.getInputStream(entry).use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                Files.move(
                    temporary.toPath(),
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }

        File(destination, "XRAY_SOURCE.txt").writeText(
            """
            Xray-core $version
            Release archive: $releaseUrl
            Source code: https://github.com/XTLS/Xray-core/tree/$version
            License: Mozilla Public License 2.0
            """.trimIndent() + System.lineSeparator(),
        )
        marker.writeText(checksum + System.lineSeparator())
    }

    private companion object {
        val runtimeFiles = listOf("xray.exe", "geoip.dat", "geosite.dat", "LICENSE")
    }
}
