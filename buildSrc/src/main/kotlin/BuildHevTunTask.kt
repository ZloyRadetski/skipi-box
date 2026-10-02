// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import java.util.Properties
import javax.inject.Inject

abstract class BuildHevTunTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localPropertiesFile: RegularFileProperty

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    abstract val androidAbi: Property<String>

    @get:Input
    abstract val artifact: Property<String>

    @get:Input
    abstract val runtimeSourcePatchVersion: Property<String>

    init {
        group = "build"
        description = "Build hev-socks5-tunnel JNI library and CLI executable for Android."
        runtimeSourcePatchVersion.convention(RuntimeSourcePatchVersion)
    }

    @TaskAction
    fun build() {
        val finalOutput = outputFile.get().asFile
        val artifact = HevTunArtifact.fromName(artifact.get())
        val abi = androidAbi.get()
        val ndkBuild = findNdkBuild(findNdkDir())
        val sourceDir = prepareBuildSource(sourceDirectory.get().asFile)
        val outputDir = finalOutput.parentFile
        val ndkLibsOutDir = temporaryDir.resolve("libs")
        val ndkOutDir = temporaryDir.resolve("obj")
        val projectDir = temporaryDir.resolve("ndk-project")
        val jniDir = projectDir.resolve("jni")
        val appBuildScript = jniDir.resolve("Android.mk")

        if (projectDir.exists()) {
            projectDir.deleteRecursively()
        }
        if (ndkLibsOutDir.exists()) {
            ndkLibsOutDir.deleteRecursively()
        }
        if (ndkOutDir.exists()) {
            ndkOutDir.deleteRecursively()
        }
        jniDir.mkdirs()
        writeGeneratedAndroidMk(appBuildScript, sourceDir)
        outputDir.mkdirs()
        ndkLibsOutDir.mkdirs()

        execOperations.exec {
            workingDir = projectDir
            commandLine(
                ndkBuild.absolutePath,
                "-j${Runtime.getRuntime().availableProcessors().coerceIn(1, MaxNativeBuildWorkers)}",
                "NDK_PROJECT_PATH=${projectDir.absolutePath}",
                "APP_BUILD_SCRIPT=${appBuildScript.absolutePath}",
                "APP_ABI=$abi",
                "APP_MODULES=${artifact.moduleName}",
                "APP_PLATFORM=android-${minSdk.get()}",
                "NDK_LIBS_OUT=${ndkLibsOutDir.absolutePath}",
                "NDK_OUT=${ndkOutDir.absolutePath}",
                "APP_CFLAGS=-O3 -DPKGNAME=engine/vpn/hevtun -DCLSNAME=HevTunNative",
                "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu",
            )
        }

        val builtOutput = ndkLibsOutDir.resolve("$abi/${artifact.builtFileName}")
        if (!builtOutput.exists() || builtOutput.length() <= 0) {
            throw GradleException("Failed to build Hev TUN ${artifact.displayName}: ${builtOutput.absolutePath}")
        }
        outputDir.mkdirs()
        builtOutput.copyTo(finalOutput, overwrite = true)
        if (!finalOutput.exists() || finalOutput.length() <= 0) {
            throw GradleException("Failed to package Hev TUN ${artifact.displayName}: ${finalOutput.absolutePath}")
        }
    }

    private companion object {
        // Bump this value whenever applyRuntimeSourcePatches changes so cached
        // native outputs cannot survive a runtime overlay edit.
        const val RuntimeSourcePatchVersion = "runtime-readiness-v2"

        // Large CPU counts on Windows can make ndk-build race its generated
        // dependency files. Eight parallel compiler jobs still rebuild HEV
        // quickly while keeping those paths deterministic.
        const val MaxNativeBuildWorkers = 8
    }

    private fun writeGeneratedAndroidMk(target: File, sourceDir: File) {
        val sourcePath = sourceDir.absolutePath.replace('\\', '/')
        target.writeText(
            """
            HEV_TUNNEL_PATH := $sourcePath

            include $(HEV_TUNNEL_PATH)/Android.mk

            LOCAL_PATH := $(HEV_TUNNEL_PATH)
            SRCDIR := $(LOCAL_PATH)/src

            include $(CLEAR_VARS)
            include $(LOCAL_PATH)/build.mk
            LOCAL_MODULE := hev-socks5-tunnel-cli
            LOCAL_SRC_FILES := $(filter-out src/hev-jni.c,$(patsubst $(SRCDIR)/%,src/%,$(SRCFILES)))
            LOCAL_C_INCLUDES := \
                $(LOCAL_PATH)/src \
                $(LOCAL_PATH)/src/misc \
                $(LOCAL_PATH)/src/core/include \
                $(LOCAL_PATH)/third-part/yaml/include \
                $(LOCAL_PATH)/third-part/lwip/src/include \
                $(LOCAL_PATH)/third-part/lwip/src/ports/include \
                $(LOCAL_PATH)/third-part/hev-task-system/include
            LOCAL_CFLAGS += -DFD_SET_DEFINED -DSOCKLEN_T_DEFINED
            LOCAL_CFLAGS += $(VERSION_CFLAGS)
            ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
            LOCAL_CFLAGS += -mfpu=neon
            endif
            LOCAL_STATIC_LIBRARIES := yaml lwip hev-task-system
            LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
            LOCAL_LDFLAGS += -Wl,-z,common-page-size=16384
            include $(BUILD_EXECUTABLE)
            """.trimIndent(),
        )
    }

    private fun prepareBuildSource(sourceDir: File): File {
        val patchedSourceDir = temporaryDir.resolve("patched-source")
        if (patchedSourceDir.exists()) {
            patchedSourceDir.deleteRecursively()
        }
        sourceDir.copyRecursively(patchedSourceDir, overwrite = true)
        replaceSymlinkPlaceholderFiles(patchedSourceDir)
        applyRuntimeSourcePatches(patchedSourceDir)
        return patchedSourceDir
    }

    private fun applyRuntimeSourcePatches(sourceDir: File) {
        val hasBuiltInReadiness = hasBuiltInReadinessApi(sourceDir)
        if (!hasBuiltInReadiness) {
            applySourceReplacement(
            sourceDir,
            "src/hev-main.h",
            """#endif

/**
 * hev_socks5_tunnel_main:""",
            """#endif

typedef void (*HevSocks5TunnelReadyCallback) (void);

/**
 * hev_socks5_tunnel_set_ready_callback:
 * @callback: callback invoked after tunnel initialization succeeds
 *
 * Set an optional callback to be invoked after the tunnel, gateway, worker
 * tasks, and mapped DNS have initialized successfully.
 */
void hev_socks5_tunnel_set_ready_callback (HevSocks5TunnelReadyCallback callback);

/**
 * hev_socks5_tunnel_main:""",
            )
            applySourceReplacement(
            sourceDir,
            "src/hev-main.c",
            """#include "hev-main.h"

static int
hev_socks5_tunnel_main_inner""",
            """#include "hev-main.h"

static HevSocks5TunnelReadyCallback ready_callback;

void
hev_socks5_tunnel_set_ready_callback (HevSocks5TunnelReadyCallback callback)
{
    ready_callback = callback;
}

static int
hev_socks5_tunnel_main_inner""",
            )
            applySourceReplacement(
            sourceDir,
            "src/hev-main.c",
            "    res = hev_socks5_tunnel_init (tun_fd);\n" +
                "    if (res < 0)\n" +
                "        goto free_task_sys;\n\n" +
                "    hev_socks5_tunnel_run ();",
            "    res = hev_socks5_tunnel_init (tun_fd);\n" +
                "    if (res < 0)\n" +
                "        goto free_task_sys;\n\n" +
                "    if (ready_callback)\n" +
                "        ready_callback ();\n\n" +
                "    hev_socks5_tunnel_run ();",
            )
        applySourceReplacement(
            sourceDir,
            "src/hev-jni.c",
            "static atomic_int is_running;",
            "static atomic_int is_running;\nstatic atomic_int is_ready;",
        )
        applySourceReplacement(
            sourceDir,
            "src/hev-jni.c",
            "static jboolean native_is_running (JNIEnv *env, jobject thiz);",
            "static jboolean native_is_running (JNIEnv *env, jobject thiz);\n" +
                "static jboolean native_is_ready (JNIEnv *env, jobject thiz);",
        )
        applySourceReplacement(
            sourceDir,
            "src/hev-jni.c",
            "{ \"TProxyIsRunning\", \"()Z\", (void *)native_is_running },",
            "{ \"TProxyIsRunning\", \"()Z\", (void *)native_is_running },\n" +
                "    { \"TProxyIsReady\", \"()Z\", (void *)native_is_ready },",
        )
        applySourceReplacement(
            sourceDir,
            "src/hev-jni.c",
            """static void
detach_current_thread (void *env)
{
    (*java_vm)->DetachCurrentThread (java_vm);
}
""",
            """static void
detach_current_thread (void *env)
{
    (*java_vm)->DetachCurrentThread (java_vm);
}

static void
mark_tunnel_ready (void)
{
    atomic_store_explicit (&is_ready, 1, memory_order_release);
}
""",
        )
        applySourceReplacement(
            sourceDir,
            "src/hev-jni.c",
            "    hev_socks5_tunnel_main (tdata->path, tdata->fd);",
            "    hev_socks5_tunnel_set_ready_callback (mark_tunnel_ready);\n" +
                "    hev_socks5_tunnel_main (tdata->path, tdata->fd);\n" +
                "    hev_socks5_tunnel_set_ready_callback (NULL);",
        )
        applySourceReplacement(
            sourceDir,
            "src/hev-jni.c",
            "    atomic_store_explicit (&is_running, 1, memory_order_release);",
            "    atomic_store_explicit (&is_ready, 0, memory_order_release);\n" +
                "    atomic_store_explicit (&is_running, 1, memory_order_release);",
        )
        applySourceReplacement(
            sourceDir,
            "src/hev-jni.c",
            "    if (atomic_load_explicit (&is_running, memory_order_acquire))\n" +
                "        hev_socks5_tunnel_quit ();\n" +
                "    res = pthread_join (work_thread, NULL);",
            "    atomic_store_explicit (&is_ready, 0, memory_order_release);\n" +
                "    if (atomic_load_explicit (&is_running, memory_order_acquire))\n" +
                "        hev_socks5_tunnel_quit ();\n" +
                "    res = pthread_join (work_thread, NULL);",
        )
        applySourceReplacement(
            sourceDir,
            "src/hev-jni.c",
            "static jlongArray\nnative_get_stats (JNIEnv *env, jobject thiz)",
            """static jboolean
native_is_ready (JNIEnv *env, jobject thiz)
{
    int ready = atomic_load_explicit (&is_ready, memory_order_acquire);
    int running = atomic_load_explicit (&is_running, memory_order_acquire);

    return ready && running ? JNI_TRUE : JNI_FALSE;
}

static jlongArray
native_get_stats (JNIEnv *env, jobject thiz)""",
        )
        }
        applySourceReplacement(
            sourceDir,
            "src/hev-socks5-session.c",
            "LOG_D (\"%p socks5 client auth %s:%s\", self, srv->user, srv->pass);",
            "LOG_D (\"%p socks5 client auth\", self);",
        )
        applySourceReplacement(
            sourceDir,
            "src/core/src/hev-socks5-server.c",
            "    if (!user) {\n" +
                "        name[nlen] = '\\0';\n" +
                "        LOG_I (\"%p socks5 server auth user: %s\", self, name);\n" +
                "        return -1;\n" +
                "    }",
            "    if (!user) {\n" +
                "        LOG_I (\"%p socks5 server authentication failed\", self);\n" +
                "        return -1;\n" +
                "    }",
        )
        applySourceReplacement(
            sourceDir,
            "src/core/src/hev-socks5-server.c",
            "    if (res < 0) {\n" +
                "        name[nlen] = '\\0';\n" +
                "        pass[plen] = '\\0';\n" +
                "        LOG_I (\"%p socks5 server auth user: %s pass: %s\", self, name, pass);\n" +
                "        return -1;\n" +
                "    }",
            "    if (res < 0) {\n" +
                "        LOG_I (\"%p socks5 server authentication failed\", self);\n" +
                "        return -1;\n" +
                "    }",
        )
    }

    private fun hasBuiltInReadinessApi(sourceDir: File): Boolean {
        val tunnelHeader = sourceDir.resolve("src/hev-socks5-tunnel.h")
        val jniSource = sourceDir.resolve("src/hev-jni.c")
        if (!tunnelHeader.isFile || !jniSource.isFile) return false

        val headerText = tunnelHeader.readText().normalizeLineEndings()
        val jniText = jniSource.readText().normalizeLineEndings()
        val readinessApiDeclared = Regex(
            "\\bint\\s+hev_socks5_tunnel_is_ready\\s*\\(\\s*void\\s*\\)\\s*;",
        ).containsMatchIn(headerText)
        val readinessImplementationPresent = Regex(
            "static\\s+jboolean\\s+native_is_ready\\s*\\([^)]*\\)\\s*\\{[^}]*" +
                "hev_socks5_tunnel_is_ready\\s*\\(\\s*\\)",
        ).containsMatchIn(jniText)

        return readinessApiDeclared && readinessImplementationPresent
    }

    private fun applySourceReplacement(sourceDir: File, relativePath: String, expected: String, replacement: String) {
        val file = sourceDir.resolve(relativePath)
        if (!file.isFile) {
            throw GradleException("Cannot apply Hev TUN patch; source file is missing: ${file.absolutePath}")
        }

        // Patch a deterministic LF-normalized copy: the checked-out native
        // sources may use CRLF, while the patch blocks are written with LF.
        val source = file.readText().normalizeLineEndings()
        val normalizedExpected = expected.normalizeLineEndings()
        val normalizedReplacement = replacement.normalizeLineEndings()
        val replacementCount = source.countOccurrences(normalizedReplacement)
        val expectedCount = source.countOccurrences(normalizedExpected)

        if (replacementCount == 1) {
            val remainingExpectedCount = source
                .replace(normalizedReplacement, "")
                .countOccurrences(normalizedExpected)
            if (remainingExpectedCount != 0) {
                throw GradleException(
                    "Cannot apply Hev TUN patch to $relativePath: found one replacement block and " +
                        "$remainingExpectedCount remaining source block(s)",
                )
            }
            // Also persist normalization when the patch was already applied.
            if (file.readText() != source) file.writeText(source)
            return
        }

        if (replacementCount > 1) {
            throw GradleException(
                "Cannot apply Hev TUN patch to $relativePath: found $replacementCount replacement blocks; expected at most one",
            )
        }
        if (expectedCount != 1) {
            throw GradleException(
                "Cannot apply Hev TUN patch to $relativePath: expected one source block, found $expectedCount; " +
                    "found $replacementCount replacement block(s)",
            )
        }
        file.writeText(source.replace(normalizedExpected, normalizedReplacement))
    }

    private fun String.countOccurrences(value: String): Int {
        require(value.isNotEmpty())
        var count = 0
        var offset = 0
        while (true) {
            val found = indexOf(value, offset)
            if (found < 0) return count
            count += 1
            offset = found + value.length
        }
    }

    private fun String.normalizeLineEndings(): String = replace("\r\n", "\n").replace('\r', '\n')

    private fun replaceSymlinkPlaceholderFiles(sourceDir: File) {
        val sourceRoot = sourceDir.canonicalFile
        sourceDir.walkTopDown()
            .filter { file -> file.isFile && file.length() in 1..256 }
            .forEach { file ->
                val targetPath = runCatching { file.readText().trim() }.getOrNull()
                    ?.takeIf { value -> value.startsWith("../") && !value.contains('\n') }
                    ?: return@forEach
                val target = file.parentFile.resolve(targetPath).canonicalFile
                if (target.isFile && target.isInsideDirectory(sourceRoot)) {
                    target.copyTo(file, overwrite = true)
                }
            }
    }

    private fun File.isInsideDirectory(directory: File): Boolean {
        var current: File? = canonicalFile
        while (current != null) {
            if (current == directory) {
                return true
            }
            current = current.parentFile
        }
        return false
    }

    private fun findNdkBuild(ndkDir: File): File {
        val executable = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "ndk-build.cmd"
        } else {
            "ndk-build"
        }
        val ndkBuild = ndkDir.resolve(executable)
        if (!ndkBuild.exists()) {
            throw GradleException("Android NDK ndk-build not found: ${ndkBuild.absolutePath}")
        }
        return ndkBuild
    }

    private fun findNdkDir(): File {
        listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT").forEach { name ->
            System.getenv(name)?.takeIf(String::isNotBlank)?.let { return File(it) }
        }

        val localProperties = localPropertiesFile.orNull?.asFile
        if (localProperties != null && localProperties.exists()) {
            val properties = Properties()
            localProperties.inputStream().use(properties::load)
            properties.getProperty("ndk.dir")?.takeIf(String::isNotBlank)?.let { return File(it) }
            properties.getProperty("sdk.dir")?.takeIf(String::isNotBlank)?.let { path ->
                File(path, "ndk").latestChildDirectoryForHevTun()?.let { return it }
            }
        }

        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").forEach { name ->
            System.getenv(name)?.takeIf(String::isNotBlank)?.let { path ->
                File(path, "ndk").latestChildDirectoryForHevTun()?.let { return it }
            }
        }

        throw GradleException("Android NDK not found. Set ndk.dir, ANDROID_NDK_HOME, or install an NDK under the Android SDK.")
    }
}

private fun File.latestChildDirectoryForHevTun(): File? {
    return listFiles()
        ?.filter(File::isDirectory)
        ?.maxByOrNull { directory -> directory.name }
}

private enum class HevTunArtifact(
    val artifactName: String,
    val moduleName: String,
    val builtFileName: String,
    val displayName: String,
) {
    JniLibrary(
        artifactName = "jni",
        moduleName = "hev-socks5-tunnel",
        builtFileName = "libhev-socks5-tunnel.so",
        displayName = "JNI library",
    ),
    CliExecutable(
        artifactName = "cli",
        moduleName = "hev-socks5-tunnel-cli",
        builtFileName = "hev-socks5-tunnel-cli",
        displayName = "CLI executable",
    );

    companion object {
        fun fromName(name: String): HevTunArtifact {
            return entries.firstOrNull { artifact -> artifact.artifactName == name }
                ?: throw GradleException("Unsupported Hev TUN artifact: $name")
        }
    }
}
