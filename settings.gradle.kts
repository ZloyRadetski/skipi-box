// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "SKIPI"

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        val localCoreDir = file("../skipi-core")
        if (localCoreDir.isDirectory && localCoreDir.resolve("skipicore.aar").isFile) {
            ivy {
                name = "SkipiCoreLocal"
                url = localCoreDir.toURI()
                patternLayout {
                    artifact("[artifact].[ext]")
                }
                metadataSources {
                    artifact()
                }
                content {
                    includeModule("app.skipi.core", "skipicore")
                }
            }
        }
        ivy {
            name = "SkipiCoreGitHubRelease"
            url = uri("https://github.com/ZloyRadetski/skipi-core/releases/download")
            patternLayout {
                artifact("[revision]/[artifact].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("app.skipi.core", "skipicore")
            }
        }
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroupAndSubgroups("com.github.android-password-store")
            }
        }
    }
}

include(":app")
include(":desktopApp")
include(":hevtun")
include(":shared:core")
