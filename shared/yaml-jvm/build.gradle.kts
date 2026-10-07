// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.snakeyaml.engine) {
        exclude(group = "org.junit.jupiter", module = "junit-jupiter-api")
    }

    testImplementation(kotlin("test"))
}
