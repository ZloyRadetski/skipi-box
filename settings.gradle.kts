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
        mavenCentral()
        ivy {
            name = "AndroidLibXrayLiteGitHubRelease"
            url = uri("https://github.com/2dust/AndroidLibXrayLite/releases/download")
            patternLayout {
                artifact("[revision]/[artifact].[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("com.github.2dust", "libv2ray")
            }
        }
        maven("https://jitpack.io") {
            content {
                includeGroupAndSubgroups("com.github.android-password-store")
            }
        }
    }
}

include(":app")
include(":hevtun")
include(":shared")
include(":desktop")
