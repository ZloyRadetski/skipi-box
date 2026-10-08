@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val generatedXrayCoreJniLibsDir: Provider<Directory> = layout.buildDirectory.dir("generated/xrayCoreJniLibs")
val versionCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val androidTestJavaHome = providers.gradleProperty("skipiAndroidTestJavaHome").orNull

android {
    namespace = "app"
    compileSdk = ProjectConfig.TARGET_SDK

    defaultConfig {
        applicationId = ProjectConfig.PACKAGE_NAME
        minSdk = ProjectConfig.MIN_SDK
        targetSdk = ProjectConfig.TARGET_SDK
        versionCode = getGitVersionCode()
        versionName = ProjectConfig.VERSION_NAME
    }

    androidResources {
        localeFilters += listOf("en", "ru", "zh", "zh-rCN", "fa")
        noCompress += listOf("xz")
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    buildFeatures {
        compose = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            include(*ProjectConfig.SUPPORTED_ANDROID_ABIS.toTypedArray())
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isDebuggable = false
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Добавлена подпись тестовым debug-ключом для локального запуска:
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf(
                "DebugProbesKt.bin",
                "META-INF/*.kotlin_module",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/LICENSE",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.md",
                "META-INF/NOTICE.txt",
                "META-INF/versions/**",
            )
        }
    }

    lint {
        disable += setOf(
            "ChromeOsAbiSupport",
            "IconLauncherShape",
        )
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    sourceSets["debug"].assets.srcDir("$projectDir/schemas")
}

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("updateResourceFileAssets"))
}

dependencies {
    implementation(project(":shared:core"))
    implementation(project(":shared:yaml-jvm"))
    implementation(project(":shared:app"))
    implementation(project(":shared:ui"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    //noinspection UseTomlInstead
    implementation("app.skipi.core:skipicore:${ProjectConfig.SKIPI_CORE_VERSION}@aar")
    implementation(dependencies.project(":hevtun"))
    implementation(libs.ktor.http)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.navigation3.ui)
    implementation(libs.miuix.preference)
    implementation(libs.reorderable)
    implementation(libs.sora.editor)
    implementation(libs.zxing.android.embedded)
    implementation(libs.tukaani.xz)
    ksp(libs.androidx.room.compiler)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:2.8.4")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.enableAndroidTest = false
    }

    onVariants { variant ->
        variant.sources.assets?.addStaticSourceDirectory("build/generated/resourceFileAssets")
        variant.sources.jniLibs?.addStaticSourceDirectory("build/generated/xrayCoreJniLibs")
    }
}

// Keep Robolectric on its supported runtime without changing Desktop test JVMs.
val androidTestJavaLauncher = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}
val explicitAndroidTestJavaHome = androidTestJavaHome
    ?: providers.environmentVariable("JAVA21_HOME").orNull
    ?: providers.environmentVariable("JDK21_HOME").orNull

tasks.withType<Test>().configureEach {
    if (name.contains("UnitTest")) {
        if (explicitAndroidTestJavaHome != null) {
            val binary = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) "java.exe" else "java"
            val javaExecutable = File(explicitAndroidTestJavaHome, "bin/$binary")
            require(javaExecutable.isFile) { "Android test Java executable does not exist: $javaExecutable" }
            executable = javaExecutable.absolutePath
        } else {
            javaLauncher.set(androidTestJavaLauncher)
        }
    }
}

val aboutLibrariesJsonFile = layout.projectDirectory.file("src/main/assets/aboutlibraries.json")

val updateAboutLibrariesJson = tasks.register<GenerateAboutLibrariesJsonTask>("updateAboutLibrariesJson") {
    group = "documentation"
    description = "Update files/aboutlibraries.json from current app dependency metadata."
    outputFile.set(aboutLibrariesJsonFile)
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    mustRunAfter(updateAboutLibrariesJson)
    if (!aboutLibrariesJsonFile.asFile.exists()) {
        dependsOn(updateAboutLibrariesJson)
    }
}
