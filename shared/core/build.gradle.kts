// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "com.radetski.skipi.shared.core"
        compileSdk = ProjectConfig.TARGET_SDK
        minSdk = ProjectConfig.MIN_SDK
        withHostTest {}
    }
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.http)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
