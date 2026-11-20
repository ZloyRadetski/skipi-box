import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.inject.Inject

/**
 * Stages the hev-socks5-tunnel runtime for a platform where hev runs as a process of its own
 * (Windows and macOS). SKIPI Core builds and publishes it as an asset of every Core release, next
 * to the library, with its checksum in `sha256sums-desktop.txt`; the app builds nothing and only
 * downloads the asset of the Core version it is pinned to, verifies it and unpacks it flat into
 * [outputDirectory]. Linux ships no hev file: it is linked into libskipicore.so.
 */
abstract class PrepareDesktopHevRuntimeTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Input
    abstract val coreVersion: Property<String>

    /** The release asset, `hev-windows-x64.zip` or `hev-macos-<arch>.tar.gz`. */
    @get:Input
    abstract val assetName: Property<String>

    /** Files that must exist in [outputDirectory] once unpacked. */
    @get:Input
    abstract val requiredFiles: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "distribution"
        description = "Downloads and verifies the hev-socks5-tunnel runtime published with the pinned SKIPI Core release."
    }

    @TaskAction
    fun prepare() {
        val version = coreVersion.get()
        val asset = assetName.get()
        val destination = outputDirectory.get().asFile
        val base = "https://github.com/ZloyRadetski/skipi-core/releases/download/$version"

        val sumsFile = download("$base/sha256sums-desktop.txt")
        val sums = try { sumsFile.readText() } finally { sumsFile.delete() }
        val expected = sums.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.endsWith(" $asset") || it.endsWith(" *$asset") }
            ?.substringBefore(' ')
            ?.lowercase()
            ?: throw GradleException("SKIPI Core $version publishes no checksum for $asset.")
        require(expected.matches(Regex("[0-9a-f]{64}"))) { "The checksum of $asset in SKIPI Core $version is not a SHA-256 digest." }

        val marker = File(destination, ".hev-archive-sha256")
        if (marker.isFile && marker.readText().trim() == expected && requiredFiles.get().all { File(destination, it).isFile }) return

        val archive = download("$base/$asset")
        try {
            val actual = sha256(archive)
            if (actual != expected) {
                throw GradleException("$asset of SKIPI Core $version does not match its published checksum. Expected $expected, got $actual.")
            }
            fileSystemOperations.delete { delete(destination) }
            destination.mkdirs()
            if (asset.endsWith(".zip")) extractZip(archive, destination) else extractTarGz(archive, destination)
        } finally {
            archive.delete()
        }
        requiredFiles.get().forEach { name ->
            require(File(destination, name).let { it.isFile && it.length() > 0 }) { "$asset of SKIPI Core $version does not contain $name." }
        }
        marker.writeText(expected + System.lineSeparator())
    }

    private fun extractZip(archive: File, destination: File) {
        val root = destination.canonicalFile
        ZipFile(archive).use { zip ->
            for (entry in zip.entries()) {
                if (entry.isDirectory) continue
                val target = File(root, entry.name).canonicalFile
                require(target.path.startsWith(root.path + File.separator)) { "Unsafe path in the hev archive: ${entry.name}" }
                target.parentFile.mkdirs()
                zip.getInputStream(entry).use { input -> target.outputStream().buffered().use { input.copyTo(it) } }
            }
        }
    }

    private fun extractTarGz(archive: File, destination: File) {
        execOperations.exec {
            commandLine("tar", "-xzf", archive.absolutePath, "-C", destination.absolutePath)
        }.rethrowFailure().assertNormalExitValue()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun download(url: String): File {
        val target = Files.createTempFile("skipi-hev-", ".download").toFile()
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 300_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SKIPI desktop distribution builder")
        }
        try {
            if (connection.responseCode !in 200..299) {
                target.delete()
                throw GradleException("Could not download $url: HTTP ${connection.responseCode}.")
            }
            connection.inputStream.use { input -> target.outputStream().buffered().use { input.copyTo(it) } }
        } finally {
            connection.disconnect()
        }
        return target
    }
}
