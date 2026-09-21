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

/** Builds the in-process Windows SKIPI Core library from the sibling checkout. */
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
        description = "Builds the in-process Windows SKIPI Core DLL from ../skipi-core."
    }

    @TaskAction
    fun build() {
        val sourceDirectory = coreSourceDirectory.get().asFile
        require(sourceDirectory.isDirectory) {
            "SKIPI Core source directory is unavailable: ${sourceDirectory.path}"
        }
        val destination = outputLibrary.get().asFile
        destination.parentFile.mkdirs()

        val cc = File(cCompiler.get()).absoluteFile
        require(cc.isFile) {
            "A GCC-compatible C compiler is required for Desktop SKIPI Core: ${cc.path}. " +
                "Install MSYS2 UCRT64 GCC or pass -PskipiCoreDesktopCc=<path-to-gcc>."
        }
        val cxx = File(cxxCompiler.get()).absoluteFile
        require(cxx.isFile) {
            "A GCC-compatible C++ compiler is required for Desktop SKIPI Core: ${cxx.path}."
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
}
