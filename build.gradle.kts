import org.gradle.kotlin.dsl.provideDelegate
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("net.minecraftforge.gradle")
    id("idea")
    id("org.spongepowered.mixin") version "0.7.+"
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
val kff_version: String by project

base.archivesName = archives_base_name

group = maven_group
version = "$mod_version+$loader+$minecraft_version"

repositories {
    mavenCentral()
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://jitpack.io")
}


configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}


minecraft {
    mappings("official",minecraft_version)
    copyIdeResources = true
    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))

    runs {
        create("client") {
            isClient = true
        }
        configureEach {
            workingDirectory = "run"
            args += "-mixin.config=kotlinmcuibackend.mixin.json"
            mods {
                create(mod_id) {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

mixin {
    add(sourceSets.main.get(), "${mod_id}.refmap.json")
    config("${mod_id}.mixin.json")
}

dependencies {
    minecraft("net.minecraftforge:forge:${minecraft_version}-${loader_version}")
    implementation("thedarkcolour:kotlinforforge:${kff_version}")
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
    val localFiles = files(
        "../kotlinmcui/build/libs/kotlinmcui-$kotlinmcui_version.jar",
        "../kotlinmcui/build/libs/kotlinmcui-$kotlinmcui_version-sources.jar",
    )
    if(localFiles.all { it.exists() }) {
        implementation(localFiles)
    } else {
        implementation("com.github.2894638479:KotlinMCUI:v$kotlinmcui_version")
    }
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.valueOf("JVM_$java_version"))
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.valueOf("VERSION_$java_version")
    targetCompatibility = JavaVersion.valueOf("VERSION_$java_version")
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(java_version.toInt())
}

val sourcesJar: Jar by tasks
sourcesJar.exclude("META-INF/mods.toml")
sourcesJar.exclude("pack.mcmeta")
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
        "kff_version" to kff_version,
    )
    inputs.properties(map)
    filesMatching("META-INF/mods.toml") { expand(map) }
    filesMatching("pack.mcmeta") { expand(map) }
}

val tag = "v$version".replace('+','-')

val shouldPublish by lazy {
    providers.exec {
        commandLine("git", "ls-remote", "--tags", "origin", "refs/tags/$tag")
    }.standardOutput.asText.get().isBlank()
}
tasks.configureEach {
    if(group == "publishing") enabled = shouldPublish
}

publishMods {
    file = tasks.jar.get().archiveFile
    additionalFiles = files(sourcesJar.archiveFile)
    changelog = "no changelog."
    type = when {
        mod_version.contains("SNAPSHOT",true) -> ALPHA
        mod_version.contains("alpha",true) -> ALPHA
        mod_version.contains("beta",true) -> BETA
        else -> STABLE
    }
    displayName = "KotlinMCUI-backend $loader-$mod_version"
    modLoaders.add(loader)

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "FjVgWB2Y"
        optional("fabric-language-kotlin","kotlin-for-forge","modmenu")
        requires("kotlinmcui")
        minecraftVersionRange {
            start = minecraft_version
            end = minecraft_version
        }
    }
    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = "1460645"
        optional("fabric-language-kotlin","kotlin-for-forge","modmenu")
//        requires("kotlinmcui")
        clientRequired = true
        serverRequired = false
        minecraftVersionRange {
            start = minecraft_version
            end = minecraft_version
        }
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "2894638479/KotlinMCUI-backend"
        commitish = "$loader-$minecraft_version"
        tagName = tag
    }
}