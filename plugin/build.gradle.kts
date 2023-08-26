plugins {
    // Java support
    id("java")
    // Kotlin support
    id("org.jetbrains.kotlin.jvm")
    // Gradle IntelliJ Plugin
    id("org.jetbrains.intellij") version "1.13.3"
    // Gradle Changelog Plugin
    id("org.jetbrains.changelog") version "1.3.1"
}

fun properties(key: String) = project.findProperty(key).toString()

group = properties("pluginGroup")
version = properties("yampVersion")

// Configure project's dependencies
repositories {
    mavenCentral()
}

intellij {
    pluginName.set("plugin_".plus(properties("pluginName")))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file.
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

dependencies {
    implementation(project(":core"))
    implementation("com.github.Grigory-Rylov:adb-facade-core:0.1.8")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.github.Grigory-Rylov:andoid_method_trace_recorder:2.1.0")

    implementation(platform("io.projectreactor:reactor-bom:2020.0.20"))
    implementation("io.rsocket:rsocket-core:1.1.2")
    implementation("io.rsocket:rsocket-transport-netty:1.1.2")
    implementation("io.rsocket.broker:rsocket-broker-frames:0.3.0")


    implementation("org.jooq:joor-java-8:0.9.7")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.google.code.gson:gson:2.8.6")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.2")


    testImplementation("junit:junit:4.12")
}


tasks {

    patchPluginXml {
        version.set(properties("yampVersion"))
        sinceBuild.set(properties("pluginSinceBuild"))
        untilBuild.set(properties("pluginUntilBuild"))

        changeNotes.set(
            """
            Fixed Android Studio Giraffe support.<br>
          """
        )
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    runIdeForUiTests {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    runIde {
        // Absolute path to installed target 3.5 Android Studio to use as
        // IDE Development Instance (the "Contents" directory is macOS specific):
        ideDir.set(file("/Applications/Android Studio.app/Contents"))
    }
}
