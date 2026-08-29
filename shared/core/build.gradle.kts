// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
