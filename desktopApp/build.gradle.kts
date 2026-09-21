// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.testing.Test

val skipiCoreSourceDirectory = rootProject.file("../skipi-core")
val defaultDesktopCoreCompilerDirectory = rootProject.file("C:/msys64/ucrt64/bin")
val defaultDesktopCoreCompiler = defaultDesktopCoreCompilerDirectory.resolve("gcc.exe")
val defaultDesktopCoreCxxCompiler = defaultDesktopCoreCompilerDirectory.resolve("g++.exe")
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
            .orElse(defaultDesktopCoreCompiler.absolutePath),
    )
    cxxCompiler.set(
        providers.gradleProperty("skipiCoreDesktopCxx")
            .orElse(defaultDesktopCoreCxxCompiler.absolutePath),
    )
    outputLibrary.set(skipiCoreSourceDirectory.resolve("dist/skipicore.dll"))
    onlyIf { !explicitDesktopCoreLibrary.isPresent }
}

// Compose packages app resources from <root>/common and platform-specific
// subdirectories. Keep the Core runtime in common so the same task layout can
// later stage the Linux shared library without changing the packaging contract.
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
    outputLibraryName.set("skipicore.dll")
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
