// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

val generatedProjectInfoDir = layout.buildDirectory.dir("generated/projectInfo/kotlin")
val generateProjectInfo = tasks.register<GenerateProjectInfoTask>("generateProjectInfo") {
    description = "Generate shared ProjectInfo metadata for Android and Desktop"
    packageName.set("app")
    projectName.set(ProjectConfig.PROJECT_NAME)
    versionName.set(ProjectConfig.VERSION_NAME)
    versionCode.set(getGitVersionCode())
    xrayCoreVersion.set(ProjectConfig.XRAY_CORE_VERSION)
    skipiCoreVersion.set(ProjectConfig.SKIPI_CORE_VERSION)
    hevSocks5TunnelVersion.set(ProjectConfig.HEV_SOCKS5_TUNNEL_VERSION)
    outputDirectory.set(generatedProjectInfoDir)
}

kotlin {
    android {
        namespace = "com.radetski.skipi.shared.core"
        compileSdk = ProjectConfig.TARGET_SDK
        minSdk = ProjectConfig.MIN_SDK
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        withHostTest {}
    }
    jvm("desktop")

    sourceSets {
        val sharedJvmMain = create("jvmMain") {
            dependsOn(getByName("commonMain"))
        }
        getByName("androidMain").dependsOn(sharedJvmMain)
        getByName("desktopMain").dependsOn(sharedJvmMain)

        commonMain {
            kotlin.srcDir(generateProjectInfo)
        }
        commonMain.dependencies {
            implementation(libs.ktor.http)
            implementation(libs.kotlinx.serialization.json)
        }
        sharedJvmMain.dependencies {
            implementation(libs.kage)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("desktopTest").dependencies {
            implementation(libs.kage)
        }
    }
}
