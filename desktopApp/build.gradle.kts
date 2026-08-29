// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":shared:core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "app.skipi.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = ProjectConfig.PROJECT_NAME
            packageVersion = ProjectConfig.VERSION_NAME
            description = "SKIPI desktop proxy client"
            vendor = "Radetski"
        }
    }
}
