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
import java.io.ByteArrayOutputStream
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

    /**
     * libcronet, the NaiveProxy network stack the Core loads from its own folder: beside [outputLibrary], named
     * libcronet.dll, libcronet.so or libcronet.dylib.
     */
    @get:OutputFile
    abstract val outputCronetLibrary: RegularFileProperty

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

        stageHevRuntime(sourceDirectory)
        val linkHev = buildLinuxHev(sourceDirectory)
        val xrayModfile = prepareXrayPatches(sourceDirectory)

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

        // with_purego: the NaiveProxy outbound loads libcronet at run time instead of linking it.
        val tags = listOfNotNull("skipi_hev".takeIf { linkHev }, "with_purego").joinToString(",")
        execOperations.exec {
            workingDir = sourceDirectory
            commandLine(
                goExecutable.get(),
                "build",
                "-buildmode=c-shared",
                "-tags",
                tags,
                "-trimpath",
                "-ldflags=-s -w -buildid= -checklinkname=0",
                "-o",
                destination.absolutePath,
                "./cmd/skipicore-desktop",
            )
            environment("CGO_ENABLED", "1")
            if (xrayModfile != null) {
                // The patched xray-core (patches/xray-geodata in the Core): go.mod stays as it is.
                environment(
                    "GOFLAGS",
                    // GOFLAGS is split on spaces; quote the whole flag so a project path with a space works.
                    listOf(System.getenv("GOFLAGS").orEmpty(), "\"-modfile=${xrayModfile.absolutePath}\"")
                        .filter { it.isNotBlank() }
                        .joinToString(" "),
                )
            }
            environment("CC", cc.absolutePath)
            environment("CXX", cxx.absolutePath)
            // Windows spells the variable "Path"; a second "PATH" key would leave Go and gcc with the
            // original value, so gcc's own DLL directory would be missing and cgo would die silently.
            val pathKey = System.getenv().keys.firstOrNull { it.equals("PATH", ignoreCase = true) } ?: "PATH"
            environment(
                pathKey,
                cc.parentFile.absolutePath + File.pathSeparator + System.getenv(pathKey).orEmpty(),
            )
        }.rethrowFailure()

        require(destination.isFile && destination.length() > 0) {
            "SKIPI Core build completed without producing ${destination.path}."
        }
        fetchCronet(sourceDirectory, outputCronetLibrary.get().asFile)
    }

    /**
     * Puts libcronet of the cronet-go commit the Core pins beside the library, through the Core's own script, which
     * checks its checksum (and on macOS links it from the pinned static archive). Without it the Core works, but every
     * NaiveProxy connection fails, so a missing script or a failed fetch stops the build. An older Core checkout
     * without NaiveProxy has no script and needs no libcronet.
     */
    private fun fetchCronet(sourceDirectory: File, target: File) {
        val script = File(sourceDirectory, "scripts/fetch-cronet.sh")
        if (!script.isFile) return
        val platform = cronetPlatform()
        execOperations.exec {
            workingDir = sourceDirectory
            commandLine("bash", "scripts/fetch-cronet.sh", platform, target.parentFile.absolutePath.replace('\\', '/'))
        }.rethrowFailure().assertNormalExitValue()
        require(target.isFile && target.length() > 0) {
            "scripts/fetch-cronet.sh $platform did not produce ${target.path}."
        }
    }

    private fun cronetPlatform(): String {
        val osName = System.getProperty("os.name").orEmpty()
        val arch = when (System.getProperty("os.arch").orEmpty().lowercase()) {
            "amd64", "x86_64" -> "amd64"
            "aarch64", "arm64" -> "arm64"
            else -> System.getProperty("os.arch").orEmpty()
        }
        val os = when {
            osName.contains("windows", ignoreCase = true) -> "windows"
            osName.contains("mac", ignoreCase = true) -> "darwin"
            else -> "linux"
        }
        return "$os-$arch"
    }

    /**
     * On Windows and macOS the Core library carries the hev runtime (go:embed), staged by the Core's own script
     * before the build. Without bash and its tools the library is still built, but a started tunnel reports that
     * this build carries no HEV TUN runtime. Release libraries come from the Core release, which has it staged.
     */
    /**
     * On Linux hev is linked into the Core as static libraries (build tag skipi_hev); without it the Core uses a
     * stub and every connect fails with "HEV TUN is not built into this Core". Returns whether the tag can be used.
     */
    private fun buildLinuxHev(sourceDirectory: File): Boolean {
        val osName = System.getProperty("os.name").orEmpty()
        if (!osName.contains("linux", ignoreCase = true)) return false
        val script = File(sourceDirectory, "scripts/build-hev-linux.sh")
        if (!script.isFile) return false
        execOperations.exec {
            workingDir = sourceDirectory
            commandLine("bash", "scripts/build-hev-linux.sh")
        }.rethrowFailure().assertNormalExitValue()
        return true
    }

    private fun stageHevRuntime(sourceDirectory: File) {
        val platform = when {
            isWindows() -> "windows"
            System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true) -> "macos"
            else -> return
        }
        val script = File(sourceDirectory, "scripts/stage-hev-payload.sh")
        if (!script.isFile) return
        try {
            execOperations.exec {
                workingDir = sourceDirectory
                commandLine("bash", "scripts/stage-hev-payload.sh", platform)
            }.rethrowFailure().assertNormalExitValue()
        } catch (error: Exception) {
            logger.warn("The HEV TUN runtime could not be staged into the SKIPI Core build ($platform): ${error.message}")
        }
    }

    /**
     * The Core builds against a patched xray-core (faster, leaner geo loading). Its tools/xrayoverlay applies the
     * patches to the xray-core version pinned in go.mod and prints the go.mod variant to build with. A patch that no
     * longer applies fails the build here: it is never skipped, so an xray-core update cannot ship half-patched.
     * An older Core checkout without the tool builds as before.
     */
    private fun prepareXrayPatches(sourceDirectory: File): File? {
        if (!File(sourceDirectory, "tools/xrayoverlay").isDirectory) return null
        val output = ByteArrayOutputStream()
        execOperations.exec {
            workingDir = sourceDirectory
            commandLine(goExecutable.get(), "run", "./tools/xrayoverlay")
            standardOutput = output
        }.rethrowFailure().assertNormalExitValue()
        val modfile = File(output.toString().trim().lineSequence().last())
        require(modfile.isFile) { "tools/xrayoverlay did not produce a go.mod: ${modfile.path}" }
        return modfile
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
