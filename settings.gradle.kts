pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
    }

    plugins {
        id("net.neoforged.moddev") version extra["plugin_version"] as String
        kotlin("jvm") version extra["kotlin_version"] as String
    }
}

rootProject.name = "kotlinmcui-backend"