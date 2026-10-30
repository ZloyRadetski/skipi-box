// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    android {
        namespace = "com.radetski.skipi.shared.app"
        compileSdk = ProjectConfig.TARGET_SDK
        minSdk = ProjectConfig.MIN_SDK
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        withHostTest {}
    }
    jvm("desktop")

    // Android and Desktop share one JVM source set on top of the default hierarchy.
    applyDefaultHierarchyTemplate {
        common {
            group("jvm") {
                // withAndroidTarget() does not know the android.kotlin.multiplatform.library target.
                withCompilations { compilation -> compilation.platformType == KotlinPlatformType.androidJvm }
                withJvm()
            }
        }
    }

    sourceSets {
        val sharedJvmMain = getByName("jvmMain")

        commonMain.dependencies {
            // The application contracts expose core models and StateFlow in
            // their public API, so both dependencies are intentionally API.
            api(project(":shared:core"))
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            // The shared Xray outbound planner reads and selects raw custom
            // outbound JSON as part of its portable planning behavior.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
        }
    }
}
