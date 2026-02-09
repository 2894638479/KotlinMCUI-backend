import org.gradle.kotlin.dsl.provideDelegate
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("fabric-loom")
    kotlin("jvm")
    id("me.modmuss50.mod-publish-plugin") version "0.8.1"
}

val maven_group: String by project
val archives_base_name: String by project
val license: String by project
val mod_version: String by project
val mod_id: String by project
val mod_name: String by project
val mod_description: String by project
val mod_authors: String by project
val mod_github: String by project
val mod_issues: String by project
val mod_mcmod: String by project
val mod_modrinth: String by project
val mod_cuseforge: String by project
val icon: String by project

val kotlin_version: String by project
val java_version: String by project
val kotlinmcui_version: String by project
val minecraft_version: String by project
val minecraft_version_range: String by project
val loader: String by project
val loader_version: String by project
val loader_version_range: String by project
val fabric_kotlin_version: String by project
val mod_menu_version: String by project

base.archivesName = archives_base_name

group = maven_group
version = "$mod_version+$minecraft_version"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com")
    maven("https://jitpack.io")
}

loom {
    accessWidenerPath = file("src/main/resources/kotlinmcuibackend.accesswidener")
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loader_version")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.116.8+1.21.1")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabric_kotlin_version")
    modImplementation("com.terraformersmc:modmenu:$mod_menu_version")
    val localFiles = files(
        "../kotlinmcui/build/libs/kotlinmcui-1.0.0-SNAPSHOT.jar",
        "../kotlinmcui/build/libs/kotlinmcui-1.0.0-SNAPSHOT-sources.jar",
    )
    if(localFiles.all { it.exists() }) {
        implementation(localFiles)
    } else {
        implementation("com.github.2894638479:KotlinMCUI:master-SNAPSHOT")
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.valueOf("JVM_$java_version"))
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.valueOf("VERSION_$java_version")
    targetCompatibility = JavaVersion.valueOf("VERSION_$java_version")
}

val sourcesJar: Jar by tasks
sourcesJar.exclude("fabric.mod.json")
sourcesJar.from("LICENSE")

tasks.jar {
    from("LICENSE")
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xcontext-parameters"))
}


tasks.processResources {
    val map = mapOf(
        "license" to license,
        "mod_version" to mod_version,
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_description" to mod_description,
        "mod_authors" to mod_authors,
        "mod_github" to mod_github,
        "mod_issues" to mod_issues,
        "mod_mcmod" to mod_mcmod,
        "mod_modrinth" to mod_modrinth,
        "mod_cuseforge" to mod_cuseforge,
        "icon" to icon,
        "kotlin_version" to kotlin_version,
        "java_version" to java_version,
        "kotlinmcui_version" to kotlinmcui_version,
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "loader" to loader,
        "loader_version" to loader_version,
        "loader_version_range" to loader_version_range,
        "fabric_kotlin_version" to fabric_kotlin_version,
        "mod_menu_version" to mod_menu_version,
    )
    inputs.properties(map)
    filesMatching("fabric.mod.json") { expand(map) }
}

publishMods {
    file = tasks.jar.get().archiveFile
    additionalFiles = files(sourcesJar.archiveFile)
    changelog = ""
    type = ALPHA
    displayName = "KotlinMCUI-backend ${project.version}"
    modLoaders.add(loader)

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "FjVgWB2Y"
        minecraftVersionRange {
            start = minecraft_version
            end = minecraft_version
        }
    }
}