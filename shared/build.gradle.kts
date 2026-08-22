// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    android {
        namespace = "app.shared"
        compileSdk = ProjectConfig.TARGET_SDK
        minSdk = ProjectConfig.MIN_SDK
    }

    jvm("desktop")

    sourceSets {
        commonMain {
            dependencies {
                implementation(compose.ui)
                implementation(compose.foundation)
                api(compose.components.resources)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.miuix.ui)
                implementation(libs.miuix.icons)
                implementation(libs.miuix.preference)
                implementation(libs.androidx.navigation3.runtime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.http)
                implementation(libs.reorderable)
                implementation(libs.coil)
                implementation(libs.coil.compose)
                implementation(libs.snakeyaml.engine)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.ktor.client.android)
            }
        }

        getByName("desktopMain") {
            dependencies {
                implementation(libs.ktor.client.cio)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "app.shared.res"
    generateResClass = always
}

