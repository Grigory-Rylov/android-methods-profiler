plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.0" apply false
    id("idea")
}

idea.project.jdkName = "17"

allprojects {
    apply(plugin = "idea")
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

tasks.register("printVersionName") {
    doLast {
        println(project.findProperty("yampVersion").toString())
    }
} 