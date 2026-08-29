// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

val desktopXrayRuntime = layout.buildDirectory.dir("generated/xray-runtime")

val downloadDesktopXray = tasks.register<DownloadDesktopXrayTask>("downloadDesktopXray") {
    xrayVersion.set(ProjectConfig.XRAY_CORE_VERSION)
    expectedArchiveSha256.set("c7172078fca4711bcd92a4774dcd1822544579c58816197575c47533317fd8d1")
    outputDirectory.set(desktopXrayRuntime)
}

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared:core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "app.skipi.desktop.MainKt"

        nativeDistributions {
            appResourcesRootDir.set(desktopXrayRuntime)
            targetFormats(TargetFormat.Msi)
            packageName = ProjectConfig.PROJECT_NAME
            packageVersion = ProjectConfig.VERSION_NAME
            description = "SKIPI desktop proxy client"
            vendor = "Radetski"
        }
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(downloadDesktopXray)
}
