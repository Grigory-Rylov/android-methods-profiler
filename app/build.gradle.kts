import org.gradle.internal.os.OperatingSystem
import org.gradle.api.tasks.bundling.Jar
import org.gradle.process.ExecOperations

plugins {
    java
    kotlin("jvm")
}

val properties: (String) -> String = { key ->
    project.findProperty(key).toString()
}

group = properties("pluginGroup")
version = properties("yampVersion")

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_17
}

sourceSets {
    main {
        java {
            if (OperatingSystem.current().isMacOsX) {
                srcDir("src/mac/java")
            } else {
                srcDir("src/default/java")
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    implementation("com.github.Grigory-Rylov:adb-facade-core:0.1.8")
    implementation("com.github.Grigory-Rylov:adb-facade-ddmlib:0.1.2")
    implementation("com.github.Grigory-Rylov:andoid_method_trace_recorder:2.1.0")
    implementation("com.github.Grishberg:tree-table:0.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")
    implementation("com.android.tools.ddms:ddmlib:26.6.3")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.slf4j:slf4j-api:1.7.25")
    implementation("org.slf4j:slf4j-log4j12:1.7.25")
    implementation("log4j:log4j:1.2.17")

    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:2.23.0")
    testImplementation("com.nhaarman.mockitokotlin2:mockito-kotlin:2.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.3.2")
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-XDignore.symbol.file=true")
}

tasks.register<Jar>("fatJar") {
    manifest {
        attributes(
            "Main-Class" to "com.github.grishberg.profiler.Launcher",
            "Implementation-Title" to "Android Methods Profiler",
            "Implementation-Version" to archiveVersion
        )
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveBaseName.set("yamp")

    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    }) {
        exclude("MaterialUISwingDemo*.class")
        exclude("module-info.class")
    }

    with(tasks.jar.get())
}

val installerScript = when {
    OperatingSystem.current().isLinux -> "build_scripts/linuxApplication"
    OperatingSystem.current().isWindows -> ""
    OperatingSystem.current().isMacOsX -> "build_scripts/macosx.sh"
    else -> ""
}

tasks.register<Exec>("buildInstaller") {
    dependsOn("fatJar")
    workingDir(file("."))
    println("Current WD: ${workingDir}")
    commandLine("./$installerScript", version.toString())
}
