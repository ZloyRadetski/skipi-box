// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.testing.Test
import java.io.File

val skipiCoreSourceDirectory = rootProject.file("../skipi-core")
val isWindowsHost = System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)
val desktopCoreLibraryName = if (isWindowsHost) "skipicore.dll" else "libskipicore.so"

fun findDefaultDesktopCompiler(binaryName: String): String {
    val isWindows = System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)
    if (isWindows) {
        val exe = if (binaryName.endsWith(".exe", ignoreCase = true)) binaryName else "$binaryName.exe"
        val candidates = listOf(
            File("C:/msys64/ucrt64/bin", exe),
            File("C:/msys64/mingw64/bin", exe),
        )
        candidates.firstOrNull { it.isFile }?.let { return it.absolutePath }
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
        for (dir in pathDirs) {
            if (dir.isBlank()) continue
            val candidate = File(dir, exe)
            if (candidate.isFile) return candidate.absolutePath
        }
        return File("C:/msys64/ucrt64/bin", exe).absolutePath
    } else {
        val pathDirs = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar)
        for (dir in pathDirs) {
            if (dir.isBlank()) continue
            val candidate = File(dir, binaryName)
            if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
        }
        val candidates = listOf(
            File("/usr/bin", binaryName),
            File("/usr/local/bin", binaryName),
        )
        candidates.firstOrNull { it.isFile && it.canExecute() }?.let { return it.absolutePath }
        return File("/usr/bin", binaryName).absolutePath
    }
}

val defaultDesktopCoreCompiler = findDefaultDesktopCompiler("gcc")
val defaultDesktopCoreCxxCompiler = findDefaultDesktopCompiler("g++")
val explicitDesktopCoreLibrary = providers.gradleProperty("skipiCoreDesktopLibrary")

val buildDesktopCore = tasks.register<BuildDesktopSkipiCoreTask>("buildDesktopCore") {
    coreSourceDirectory.set(skipiCoreSourceDirectory)
    coreSources.from(
        fileTree(skipiCoreSourceDirectory) {
            exclude(".git/**", "dist/**")
        },
    )
    goExecutable.set(providers.gradleProperty("skipiCoreDesktopGo").orElse("go"))
    cCompiler.set(
        providers.gradleProperty("skipiCoreDesktopCc")
            .orElse(defaultDesktopCoreCompiler),
    )
    cxxCompiler.set(
        providers.gradleProperty("skipiCoreDesktopCxx")
            .orElse(defaultDesktopCoreCxxCompiler),
    )
    // The sibling checkout is a read-only source dependency; keep generated binaries in this repo.
    outputLibrary.set(layout.buildDirectory.file("native/skipi-core/$desktopCoreLibraryName"))
    onlyIf { !explicitDesktopCoreLibrary.isPresent }
}

// Compose packages app resources from <root>/common and platform-specific
// subdirectories. Keep the Core runtime in common so the same task layout can
// stage both the Windows DLL and the Linux shared library.
val desktopCoreResourcesRoot = layout.buildDirectory.dir("generated/skipi-core-resources")
val desktopCoreRuntime = desktopCoreResourcesRoot.map { root -> root.dir("common") }

val prepareDesktopCoreRuntime = tasks.register<PrepareDesktopCoreRuntimeTask>("prepareDesktopCoreRuntime") {
    coreVersion.set(ProjectConfig.SKIPI_CORE_VERSION)
    coreLibraryPath.set(
        explicitDesktopCoreLibrary.orElse(
            buildDesktopCore.flatMap { task -> task.outputLibrary }.map { file -> file.asFile.absolutePath },
        ),
    )
    coreLicensePath.set(rootProject.file("../skipi-core/LICENSE").absolutePath)
    outputLibraryName.set(desktopCoreLibraryName)
    xrayVersion.set(ProjectConfig.XRAY_CORE_VERSION)
    expectedGeoArchiveSha256.set("244deaba2098c2964e49bba90df3707777e5f5f428a82d2f29604015f24beec2")
    outputDirectory.set(desktopCoreRuntime)
}

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:app"))
    implementation(project(":shared:ui"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "app.skipi.desktop.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            appResourcesRootDir.set(desktopCoreResourcesRoot)
            modules("java.net.http")
            targetFormats(TargetFormat.Msi)
            packageName = ProjectConfig.PROJECT_NAME
            packageVersion = ProjectConfig.VERSION_NAME
            description = "SKIPI desktop proxy client"
            vendor = "Radetski"
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(buildDesktopCore, prepareDesktopCoreRuntime)
}

prepareDesktopCoreRuntime.configure {
    dependsOn(buildDesktopCore)
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
