// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.testing.Test
import java.io.File

val skipiCoreSourceDirectory = rootProject.file("../skipi-core")
val isWindowsHost = System.getProperty("os.name").orEmpty().contains("windows", ignoreCase = true)
val isMacHost = System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)
val isLinuxHost = System.getProperty("os.name").orEmpty().contains("linux", ignoreCase = true)
val desktopCoreLibraryName = when {
    isWindowsHost -> "skipicore.dll"
    isMacHost -> "libskipicore.dylib"
    else -> "libskipicore.so"
}

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

val desktopHevSourceDirectory = rootProject.layout.projectDirectory.dir("hevtun/src/main/jni/hev-socks5-tunnel")

// The Linux TUN mode loads hev-socks5-tunnel in-process next to Core; Windows will ship wintun instead.
val buildDesktopHev = tasks.register<BuildDesktopHevTask>("buildDesktopHev") {
    sourceDirectory.set(desktopHevSourceDirectory)
    sources.from(
        fileTree(desktopHevSourceDirectory) {
            exclude(".git", "bin/**", "build/**", "third-part/*/bin/**", "third-part/*/build/**")
        },
    )
    cCompiler.set(providers.gradleProperty("skipiCoreDesktopCc").orElse("gcc"))
    workDirectory.set(layout.buildDirectory.dir("native/hev-socks5-tunnel-src"))
    outputLibrary.set(layout.buildDirectory.file("native/hev-socks5-tunnel/libhev-socks5-tunnel.so"))
    onlyIf { !isWindowsHost && !isMacHost }
}

// macOS TUN: hev-socks5-tunnel runs as a root process of its own, started by the app after the user
// has granted administrator rights; it creates the utun interface itself.
val buildDesktopHevMac = tasks.register<BuildDesktopHevMacTask>("buildDesktopHevMac") {
    sourceDirectory.set(desktopHevSourceDirectory)
    sources.from(
        fileTree(desktopHevSourceDirectory) {
            exclude(".git", "bin/**", "build/**", "third-part/*/bin/**", "third-part/*/build/**")
        },
    )
    cCompiler.set(providers.gradleProperty("skipiCoreDesktopCc").orElse("cc"))
    workDirectory.set(layout.buildDirectory.dir("native/hev-socks5-tunnel-mac-src"))
    outputExecutable.set(layout.buildDirectory.file("native/hev-socks5-tunnel-mac/hev-socks5-tunnel"))
    onlyIf { isMacHost }
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
    coreLibraryFile.set(layout.file(coreLibraryPath.map(::File)))
    coreLicensePath.set(rootProject.file("../skipi-core/LICENSE").absolutePath)
    outputLibraryName.set(desktopCoreLibraryName)
    xrayVersion.set(ProjectConfig.XRAY_CORE_VERSION)
    expectedGeoArchiveSha256.set("2e04a9995a6f2ad1b28c0bb25c708ecb2695e43a1c9c7889c4b6ac2b48cf7a6a")
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
    implementation(project(":shared:yaml-jvm"))
    implementation(project(":shared:app"))
    implementation(project(":shared:ui"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zxing.core)
    implementation(libs.dbus.java.core)
    implementation(libs.dbus.java.transport.native.unixsocket)
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Desktop bundles the same font files already shipped by Android; keep the source assets canonical.
tasks.named<org.gradle.language.jvm.tasks.ProcessResources>("processResources") {
    mustRunAfter(rootProject.tasks.named("updateResourceFileAssets"))
    from(rootProject.file("app/src/main/res/font")) {
        include("*.ttf")
        into("font")
    }
    from(rootProject.file("app/src/main/assets")) {
        include("geo/**", "xray/**")
    }
}

// jpackage rejects a macOS bundle version whose first number is 0, so 0.5.0 is bundled as 1.5.0.
val macPackageVersion = ProjectConfig.VERSION_NAME.split(".").let { parts ->
    if (parts.size == 3 && parts[0] == "0") "1.${parts[1]}.${parts[2]}" else ProjectConfig.VERSION_NAME
}

compose.desktop {
    application {
        mainClass = "app.skipi.desktop.MainKt"
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // glibc keeps freed native memory in per-thread arenas (C2 compiler, IO and render threads come and go),
        // which held hundreds of MB of RSS; trimming once a minute gives it back. glibc only, so not on Windows.
        if (isLinuxHost) jvmArgs += "-XX:TrimNativeHeapInterval=60000"
        // The UI and its services use ~40 MB of Java heap. G1 spends ~65 MB of native memory on its own bookkeeping
        // however small the heap is (the serial collector: 0.2 MB, ~100 MB less resident in all), so use the serial
        // one and cap the heap. It keeps what a burst made it commit until a full collection, so the app runs one
        // once a minute while a lot is free (DesktopHeapTrim); without ShrinkHeapInSteps that single collection
        // gives all of it back (a 200 MB burst: 225 -> 7 MB).
        jvmArgs += listOf(
            "-Xmx512m",
            "-XX:+UseSerialGC",
            "-XX:-ShrinkHeapInSteps",
            "-XX:MinHeapFreeRatio=10",
            "-XX:MaxHeapFreeRatio=30",
        )

        nativeDistributions {
            appResourcesRootDir.set(desktopCoreResourcesRoot)
            modules("java.net.http", "jdk.net", "jdk.security.auth")
            // Installers: MSI on Windows, deb and rpm on Linux (the AppImage is made from the app image by
            // packaging/linux/build-appimage.sh), dmg on macOS. The portable images come from createDistributable.
            targetFormats(
                *when {
                    isMacHost -> arrayOf(TargetFormat.Dmg)
                    isLinuxHost -> arrayOf(TargetFormat.Deb, TargetFormat.Rpm)
                    else -> arrayOf(TargetFormat.Msi)
                },
            )
            packageName = ProjectConfig.PROJECT_NAME
            packageVersion = ProjectConfig.VERSION_NAME
            description = "SKIPI desktop proxy client"
            vendor = "Radetski"
            windows {
                iconFile.set(project.file("packaging/icons/skipi.ico"))
                menu = true
                menuGroup = ProjectConfig.PROJECT_NAME
                shortcut = true
                dirChooser = true
                // TUN needs administrator rights, so SKIPI is installed for the whole machine.
                perUserInstall = false
                // Fixed forever: it is what lets a new MSI upgrade the installed one instead of installing beside it.
                upgradeUuid = "251111c2-8358-4a5a-b610-e328755aef03"
            }
            linux {
                iconFile.set(project.file("packaging/icons/skipi.png"))
                shortcut = true
                menuGroup = "Network"
                appCategory = "Network"
                // jpackage rejects --linux-rpm-license-type for a deb and --linux-deb-maintainer for an rpm, so each
                // option is only passed to its own package type (-PskipiPackageRpm=true selects the rpm); Compose adds
                // both options to every Linux package type otherwise.
                if (providers.gradleProperty("skipiPackageRpm").orNull == "true") {
                    rpmLicenseType = "GPL-3.0-only"
                } else {
                    debMaintainer = "Radetski <extendamailduck@duck.com>"
                }
            }
            macOS {
                iconFile.set(project.file("packaging/icons/skipi.icns"))
                bundleID = "app.skipi.desktop"
                dockName = ProjectConfig.PROJECT_NAME
                packageVersion = macPackageVersion
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(
        buildDesktopCore,
        prepareDesktopCoreRuntime,
        prepareDesktopHevRuntime,
        prepareDesktopHevWindowsRuntime,
        prepareDesktopHevMacRuntime,
    )
}

prepareDesktopCoreRuntime.configure {
    dependsOn(buildDesktopCore)
}

val prepareDesktopHevRuntime = tasks.register<Copy>("prepareDesktopHevRuntime") {
    from(buildDesktopHev.flatMap { task -> task.outputLibrary })
    into(desktopCoreRuntime)
    onlyIf { !isWindowsHost && !isMacHost }
}

val prepareDesktopHevMacRuntime = tasks.register<Copy>("prepareDesktopHevMacRuntime") {
    from(buildDesktopHevMac.flatMap { task -> task.outputExecutable })
    into(desktopCoreRuntime.map { root -> root.dir("hev") })
    filePermissions { unix("rwxr-xr-x") }
    onlyIf { isMacHost }
}

// Windows TUN: hev-socks5-tunnel runs as its own process next to the Wintun driver library.
val prepareDesktopHevWindowsRuntime = tasks.register<PrepareDesktopHevWindowsRuntimeTask>("prepareDesktopHevWindowsRuntime") {
    hevVersion.set(ProjectConfig.HEV_SOCKS5_TUNNEL_VERSION)
    expectedArchiveSha256.set("a9209ad121a3f636f814b0bb899b6464d3ec201eabbb3a82f2aa486e32879ba7")
    wintunLicense.set(desktopHevSourceDirectory.file("third-part/wintun/LICENSE.txt"))
    gccRuntime.set(layout.projectDirectory.file("packaging/windows/msys-gcc_s-seh-1.dll"))
    expectedGccRuntimeSha256.set("a9cfd10e2a4bf74f60530fc2f4f8fbe4055b50ff7ccd61b9440ef2fbe9c85aef")
    outputDirectory.set(desktopCoreRuntime.map { root -> root.dir("hev") })
    onlyIf { isWindowsHost }
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // The app's default paths (logs, settings, libraries) hang off the home directory; a test run gets
    // its own so it never writes into the user's ~/SKIPI (or %APPDATA%\SKIPI).
    val testHome = layout.buildDirectory.dir("test-home").get().asFile
    doFirst { testHome.mkdirs() }
    systemProperty("user.home", testHome.absolutePath)
    environment("APPDATA", testHome.absolutePath)
}
