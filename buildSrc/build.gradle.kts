plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(26)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(localGroovy())
    implementation("org.tukaani:xz:1.10")
}
