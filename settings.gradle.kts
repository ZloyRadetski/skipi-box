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

// The local ../skipi-core/skipicore.aar shadows the release of the same version, so a stale
// one silently builds an app against an old core. It is used only when it is provably the
// required version; otherwise Gradle downloads the release and caches it.
val requiredSkipiCoreVersion: String = Regex("""SKIPI_CORE_VERSION\s*=\s*"([^"]+)"""")
    .find(file("buildSrc/src/main/kotlin/BuildConfig.kt").readText())
    ?.groupValues?.get(1)
    ?: error("SKIPI_CORE_VERSION not found in buildSrc/src/main/kotlin/BuildConfig.kt")

fun localSkipiCoreIsCurrent(dir: File, version: String): Boolean {
    val aar = dir.resolve("skipicore.aar")
    if (!aar.isFile) return false
    // A build script may state what it built: skipicore.aar.version holds the version name.
    val stamp = dir.resolve("skipicore.aar.version")
    val current = if (stamp.isFile) {
        stamp.readText().trim() == version
    } else {
        fun git(vararg args: String): String? = runCatching {
            val process = ProcessBuilder(listOf("git", "-C", dir.absolutePath) + args)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            output.takeIf { process.waitFor() == 0 && it.isNotEmpty() }
        }.getOrNull()
        // The checkout sits exactly on the required tag and the AAR was built after that commit.
        val tag = git("describe", "--tags", "--exact-match")
        val commitMillis = git("log", "-1", "--format=%ct")?.toLongOrNull()?.times(1000)
        tag == version && commitMillis != null && aar.lastModified() >= commitMillis
    }
    if (!current) {
        logger.warn(
            "Ignoring ${aar.path}: it is not provably SKIPI Core $version " +
                "(check out tag $version in ${dir.name} and rebuild, or write $version to skipicore.aar.version). " +
                "The release is downloaded and cached instead.",
        )
    }
    return current
}

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
        if (localSkipiCoreIsCurrent(localCoreDir, requiredSkipiCoreVersion)) {
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
        mavenCentral {
            content {
                // The Skipi core is published only to the repositories above; never ask Maven Central for it.
                excludeGroup("app.skipi.core")
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
include(":desktopApp")
include(":hevtun")
include(":shared:core")
include(":shared:yaml-jvm")
include(":shared:app")
include(":shared:ui")
