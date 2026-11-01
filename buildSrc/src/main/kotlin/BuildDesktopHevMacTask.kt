import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

/**
 * Builds hev-socks5-tunnel as an executable for the macOS desktop. On macOS the tunnel runs as a
 * root process of its own (it creates the utun interface), so unlike Linux it is not loaded into the
 * JVM and keeps its default build flags. The sources are copied first so the submodule checkout
 * never receives build products.
 */
abstract class BuildDesktopHevMacTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:Internal
    abstract val sourceDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val cCompiler: Property<String>

    @get:Internal
    abstract val workDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputExecutable: RegularFileProperty

    init {
        group = "build"
        description = "Builds the hev-socks5-tunnel executable for the macOS desktop TUN mode."
    }

    @TaskAction
    fun build() {
        val source = sourceDirectory.get().asFile
        require(File(source, "Makefile").isFile) { "hev-socks5-tunnel sources are unavailable: ${source.path}" }
        val work = workDirectory.get().asFile
        fileSystemOperations.delete { delete(work) }
        fileSystemOperations.copy {
            from(source)
            into(work)
            exclude(".git", "bin/**", "build/**", "third-part/*/bin/**", "third-part/*/build/**")
        }

        execOperations.exec {
            workingDir = work
            commandLine("make", "-j4", "CC=${cCompiler.get()}")
        }.rethrowFailure()

        val built = File(work, "bin/hev-socks5-tunnel")
        require(built.isFile && built.length() > 0) { "hev-socks5-tunnel build produced no executable" }
        val destination = outputExecutable.get().asFile
        destination.parentFile.mkdirs()
        built.copyTo(destination, overwrite = true)
        destination.setExecutable(true, false)
    }
}
