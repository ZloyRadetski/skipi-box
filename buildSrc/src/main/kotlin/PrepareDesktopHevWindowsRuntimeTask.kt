import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Stages the Windows TUN runtime: the official hev-socks5-tunnel build with the Wintun driver
 * library and the MSYS runtime it was built against. The archive is pinned by SHA-256, like the
 * Xray geo archive; a changed hev version must come with its new digest.
 *
 * hev runs as its own process on Windows (its MSYS runtime cannot live inside the JVM), and creates
 * the Wintun adapter itself.
 */
abstract class PrepareDesktopHevWindowsRuntimeTask : DefaultTask() {
    @get:Input
    abstract val hevVersion: Property<String>

    @get:Input
    abstract val expectedArchiveSha256: Property<String>

    /** The Wintun license shipped next to the DLL, as its terms require. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val wintunLicense: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "distribution"
        description = "Stages the verified hev-socks5-tunnel Windows runtime (hev, Wintun, MSYS runtime)."
    }

    @TaskAction
    fun prepare() {
        val expected = expectedArchiveSha256.get().lowercase()
        require(expected.matches(Regex("[0-9a-f]{64}"))) {
            "The configured hev archive checksum must be a SHA-256 hex digest."
        }
        val destination = outputDirectory.get().asFile
        val marker = File(destination, ".hev-archive-sha256")
        if (isCurrent(destination, marker, expected)) return

        val archive = Files.createTempFile("skipi-hev-win-", ".zip").toFile()
        try {
            val url = "https://github.com/heiher/hev-socks5-tunnel/releases/download/" +
                "${hevVersion.get()}/hev-socks5-tunnel-win64.zip"
            val actual = download(url, archive)
            if (!actual.equals(expected, ignoreCase = true)) {
                throw GradleException("hev archive checksum mismatch. Expected $expected, got $actual.")
            }
            destination.mkdirs()
            extract(archive, destination)
            Files.copy(
                wintunLicense.get().asFile.toPath(),
                File(destination, "WINTUN_LICENSE.txt").toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
            File(destination, "HEV_RUNTIME_NOTICE.txt").writeText(
                """
                hev-socks5-tunnel ${hevVersion.get()} (MIT) - https://github.com/heiher/hev-socks5-tunnel
                wintun.dll (Wintun, see WINTUN_LICENSE.txt) - https://www.wintun.net/
                msys-2.0.dll (MSYS2 runtime, LGPL-3.0 with linking exception) - https://www.msys2.org/
                """.trimIndent() + System.lineSeparator(),
            )
            marker.writeText(expected + System.lineSeparator())
        } finally {
            archive.delete()
        }
    }

    private fun isCurrent(destination: File, marker: File, expected: String): Boolean =
        marker.isFile &&
            marker.readText().trim().equals(expected, ignoreCase = true) &&
            RuntimeFiles.all { File(destination, it).let { file -> file.isFile && file.length() > 0 } }

    private fun download(url: String, archive: File): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 300_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SKIPI desktop distribution builder")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw GradleException("Could not download hev-socks5-tunnel: HTTP ${connection.responseCode}.")
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

    private fun extract(archive: File, destination: File) {
        ZipFile(archive).use { zip ->
            RuntimeFiles.forEach { fileName ->
                val entry = zip.entries().asSequence().firstOrNull {
                    !it.isDirectory && it.name.substringAfterLast('/') == fileName
                } ?: throw GradleException("The hev archive does not contain $fileName.")
                val target = File(destination, fileName)
                val temporary = File(destination, ".$fileName.download")
                zip.getInputStream(entry).use { input ->
                    temporary.outputStream().buffered().use { output -> input.copyTo(output) }
                }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }

    private companion object {
        val RuntimeFiles = listOf("hev-socks5-tunnel.exe", "msys-2.0.dll", "wintun.dll")
    }
}
