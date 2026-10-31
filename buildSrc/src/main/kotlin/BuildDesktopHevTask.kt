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
 * Builds hev-socks5-tunnel as one self-contained shared library for the Linux desktop. The
 * sources are copied first so the submodule checkout never receives build products.
 */
abstract class BuildDesktopHevTask : DefaultTask() {
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
    abstract val outputLibrary: RegularFileProperty

    init {
        group = "build"
        description = "Builds libhev-socks5-tunnel.so for the Linux desktop TUN mode."
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

        val cc = cCompiler.get()
        fun make(vararg arguments: String) {
            execOperations.exec {
                workingDir = work
                commandLine(listOf("make", "-j4", "CC=$cc") + arguments)
            }.rethrowFailure()
        }

        // The bundled libraries are linked in statically so the result needs nothing but libc.
        // TPFLAGS is emptied on purpose: hev's default enables hev-task-system's stack overflow
        // detector, which installs its own SIGSEGV/SIGBUS handler. Inside the JVM, which raises
        // SIGSEGV itself at safepoints, that handler crashes the whole application.
        make("CFLAGS=-fPIC", "TPFLAGS=", "tp-static")
        val archives = listOf("lwip/bin/liblwip.a", "hev-task-system/bin/libhev-task-system.a", "yaml/bin/libyaml.a")
            .joinToString(" ") { File(work, "third-part/$it").absolutePath }
        make(
            "CFLAGS=-DENABLE_LIBRARY -fPIC",
            "LDFLAGS=-shared -pthread -Wl,--exclude-libs,ALL $archives",
            "bin/libhev-socks5-tunnel.so",
        )

        val built = File(work, "bin/libhev-socks5-tunnel.so")
        require(built.isFile && built.length() > 0) { "hev-socks5-tunnel build produced no library" }
        // The detector's code is always linked (fini references it); only its creation, which
        // installs the signal handlers, depends on the flag.
        val taskSystem = File(work, "third-part/hev-task-system/build/kern/core/hev-task-system.o")
        require(taskSystem.isFile && !taskSystem.readBytes().containsAscii("hev_task_stack_detector_new")) {
            "hev-socks5-tunnel was built with the stack overflow detector, which crashes the JVM"
        }
        val destination = outputLibrary.get().asFile
        destination.parentFile.mkdirs()
        built.copyTo(destination, overwrite = true)
    }
}

private fun ByteArray.containsAscii(text: String): Boolean {
    val needle = text.toByteArray(Charsets.US_ASCII)
    outer@ for (start in 0..size - needle.size) {
        for (index in needle.indices) if (this[start + index] != needle[index]) continue@outer
        return true
    }
    return false
}
