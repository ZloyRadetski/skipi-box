// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    android {
        namespace = "com.radetski.skipi.shared.app"
        compileSdk = ProjectConfig.TARGET_SDK
        minSdk = ProjectConfig.MIN_SDK
        withHostTest {}
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // The application contracts expose core models and StateFlow in
            // their public API, so both dependencies are intentionally API.
            api(project(":shared:core"))
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
