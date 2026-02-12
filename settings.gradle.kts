pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net/")
    }

    plugins {
        id("net.minecraftforge.gradle") version extra["plugin_version"] as String
        kotlin("jvm") version extra["kotlin_version"] as String
    }
}

rootProject.name = "kotlinmcui-backend"