import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
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

/** Builds the in-process SKIPI Core library from the sibling checkout. */
abstract class BuildDesktopSkipiCoreTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val coreSourceDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val coreSources: ConfigurableFileCollection

    @get:Input
    abstract val goExecutable: Property<String>

    @get:Input
    abstract val cCompiler: Property<String>

    @get:Input
    abstract val cxxCompiler: Property<String>

    @get:OutputFile
    abstract val outputLibrary: RegularFileProperty

    init {
        group = "build"
        description = "Builds the in-process SKIPI Core library from ../skipi-core."
    }

    @TaskAction
    fun build() {
        val sourceDirectory = coreSourceDirectory.get().asFile
        require(sourceDirectory.isDirectory) {
            "SKIPI Core source directory is unavailable: ${sourceDirectory.path}"
        }
        val destination = outputLibrary.get().asFile
        destination.parentFile.mkdirs()

        val cc = resolveCompiler(cCompiler.get())
        require(cc.isFile) {
            "A GCC-compatible C compiler is required for Desktop SKIPI Core: ${cc.path}. " +
                if (isWindows()) "Install MSYS2 UCRT64 GCC or pass -PskipiCoreDesktopCc=<path-to-gcc>."
                else "Install GCC (e.g. build-essential or gcc) or pass -PskipiCoreDesktopCc=<path-to-gcc>."
        }
        val cxx = resolveCompiler(cxxCompiler.get())
        require(cxx.isFile) {
            "A GCC-compatible C++ compiler is required for Desktop SKIPI Core: ${cxx.path}. " +
                if (isWindows()) "Install MSYS2 UCRT64 G++ or pass -PskipiCoreDesktopCxx=<path-to-g++>."
                else "Install G++ (e.g. build-essential or g++) or pass -PskipiCoreDesktopCxx=<path-to-g++>."
        }

        execOperations.exec {
            workingDir = sourceDirectory
            commandLine(
                goExecutable.get(),
                "build",
                "-buildmode=c-shared",
                "-trimpath",
                "-ldflags=-s -w -buildid= -checklinkname=0",
                "-o",
                destination.absolutePath,
                "./cmd/skipicore-desktop",
            )
            environment("CGO_ENABLED", "1")
            environment("CC", cc.absolutePath)
            environment("CXX", cxx.absolutePath)
            environment("PATH", cc.parentFile.absolutePath + File.pathSeparator + System.getenv("PATH").orEmpty())
        }.rethrowFailure()

        require(destination.isFile && destination.length() > 0) {
            "SKIPI Core build completed without producing ${destination.path}."
        }
    }

    private fun resolveCompiler(configured: String): File {
        val direct = File(configured)
        if (direct.isFile) return direct.absoluteFile
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
        for (dir in pathDirs) {
            if (dir.isBlank()) continue
            val candidate = File(dir, configured)
            if (candidate.isFile) return candidate.absoluteFile
            if (isWindows() && !configured.endsWith(".exe", ignoreCase = true)) {
                val exeCandidate = File(dir, "$configured.exe")
                if (exeCandidate.isFile) return exeCandidate.absoluteFile
            }
        }
        return direct.absoluteFile
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)
}
