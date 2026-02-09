pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }

    plugins {
        id("fabric-loom") version extra["plugin_version"] as String
        kotlin("jvm") version extra["kotlin_version"] as String
    }
}

rootProject.name = "kotlinmcui-backend"