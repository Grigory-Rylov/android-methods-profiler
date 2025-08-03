import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}

repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation("com.github.Grishberg:simpleperf-parser:1.0.6")
    implementation("com.github.Grigory-Rylov:adb-facade-core:0.1.8")
    implementation("com.github.Grigory-Rylov:andoid_method_trace_recorder:2.1.0")
    implementation("com.github.Grigory-Rylov:proguard-deobfuscator:0.4.0")
    implementation("com.github.Grishberg:mvtrace-dependencies:1.0.1")
    implementation("com.github.Grishberg:tree-table:0.1.1")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("junit:junit:4.12")
}

// Удалён блок с ручной настройкой dependsOn и classpath для JavaCompile 