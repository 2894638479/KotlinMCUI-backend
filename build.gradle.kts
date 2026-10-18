import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("fabric-loom")
    kotlin("jvm")
    id("me.modmuss50.mod-publish-plugin") version "2.2.1"
}

/** Reads a value from `gradle.properties`, failing the build when it is missing. */
fun gradleProperty(name: String) = providers.gradleProperty(name).get()

val maven_group = gradleProperty("maven_group")
val archives_base_name = gradleProperty("archives_base_name")
val license = gradleProperty("license")
val mod_version = gradleProperty("mod_version")
val mod_id = gradleProperty("mod_id")
val mod_name = gradleProperty("mod_name")
val mod_description = gradleProperty("mod_description")
val mod_authors = gradleProperty("mod_authors")
val mod_github = gradleProperty("mod_github")
val mod_issues = gradleProperty("mod_issues")
val mod_mcmod = gradleProperty("mod_mcmod")
val mod_modrinth = gradleProperty("mod_modrinth")
val mod_curseforge = gradleProperty("mod_curseforge")
val icon = gradleProperty("icon")

val kotlin_version = gradleProperty("kotlin_version")
val java_version = gradleProperty("java_version")
val toolkit_version = gradleProperty("toolkit_version")
val kotlinmcui_version = gradleProperty("kotlinmcui_version")
val kotlinmcui_version_range = gradleProperty("kotlinmcui_version_range")
val minecraft_version = gradleProperty("minecraft_version")
val minecraft_version_range = gradleProperty("minecraft_version_range")
val loader = gradleProperty("loader")
val loader_suffix = gradleProperty("loader_suffix")
val loader_version = gradleProperty("loader_version")
val loader_version_range = gradleProperty("loader_version_range")
val fabric_kotlin_version = gradleProperty("fabric_kotlin_version")
val mod_menu_version = gradleProperty("mod_menu_version")

base.archivesName = archives_base_name

group = maven_group
version = listOf(mod_version,loader,loader_suffix,minecraft_version)
    .filter { it.isNotBlank() }
    .joinToString(separator = "+")
    .replace("neoforge","neo")

val branch = listOf(loader,loader_suffix)
    .filter { it.isNotBlank() }
    .joinToString(separator = "+")
    .plus("-$minecraft_version")

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://jitpack.io")
}

loom {
    accessWidenerPath = file("src/main/resources/kotlinmcuibackend.accesswidener")
    // There is deliberately no preview run configuration. The preview is driven by the JVM property
    // -Dkotlinmcui.preview=<page reference> (-Dkotlinmcui.preview.capture=true adds the screenshot
    // and the quit), and the plain runClient already carries it because the JVM reads
    // JAVA_TOOL_OPTIONS itself and the game's JVM inherits it:
    //
    //   JAVA_TOOL_OPTIONS="-Dkotlinmcui.preview=<reference>" ./gradlew runClient
    //
    // No build-script support, and the same command works for anyone depending on the framework.
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
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabric_kotlin_version")
    modCompileOnly("com.terraformersmc:modmenu:$mod_menu_version")
    modLocalRuntime("com.terraformersmc:modmenu:$mod_menu_version")
    // Fabric API is a required dependency (its resource loader is what puts the mod
    // resources - the core jar's language files - into the game), so the development client
    // has to have it too, or the loader refuses to start.
    modLocalRuntime("net.fabricmc.fabric-api:fabric-api:0.92.12+1.20.1")
    // A sibling core checkout means we are developing against it, so quietly taking the published
    // version instead would build against a different core without saying so. Only a clean checkout
    // that has no sibling (CI, a fresh clone) may take the published coordinate.
    val sibling = rootProject.file("../kotlinmcui")
    if(sibling.isDirectory) {
        val jarFile = sibling.resolve("build/libs/kotlinmcui-$kotlinmcui_version.jar")
        val sourceFile = sibling.resolve("build/libs/kotlinmcui-$kotlinmcui_version-sources.jar")
        check(jarFile.exists()) {
            "the sibling core checkout has no jar: run `./gradlew jar` in ../kotlinmcui (looked for ${jarFile.path})"
        }
        check(sourceFile.exists()) {
            "the sibling core checkout has no sources jar: run `./gradlew sourcesJar` in ../kotlinmcui (looked for ${sourceFile.path})"
        }
        // Being present is not the same as being the same build: the core compiles `jar` and
        // `sourcesJar` independently, so a rename can reach the main jar while the sources jar still
        // describes the old API - the classes then fail to resolve against names the sources no
        // longer mention. A real mismatch is hours apart; a normal rebuild is milliseconds apart,
        // and Gradle does not order the two tasks, which is what the slack is for: without it this
        // check would fire on a perfectly good build.
        val sourcesOlderBy = jarFile.lastModified() - sourceFile.lastModified()
        check(sourcesOlderBy <= 5_000L) {
            "the sibling core's sources jar is older than its main jar by ${sourcesOlderBy / 1000}s: " +
                "run `./gradlew sourcesJar` in ../kotlinmcui (${sourceFile.name} predates ${jarFile.name})"
        }
        implementation(files(jarFile.path))
        compileOnly(files(sourceFile.path))
    } else {
        implementation("com.github.2894638479:KotlinMCUI:v$kotlinmcui_version")
    }
}

kotlin {
    jvmToolchain(toolkit_version.toInt())
    compilerOptions {
        jvmTarget.set(JvmTarget.valueOf("JVM_$java_version"))
    }
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.valueOf("VERSION_$java_version")
    targetCompatibility = JavaVersion.valueOf("VERSION_$java_version")
}

val sourcesJar = tasks.named<Jar>("sourcesJar").get()
sourcesJar.exclude("fabric.mod.json")
sourcesJar.from("LICENSE")

tasks.jar {
    from("LICENSE")
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
        "mod_curseforge" to mod_curseforge,
        "icon" to icon,
        "kotlin_version" to kotlin_version,
        "java_version" to java_version,
        "kotlinmcui_version" to kotlinmcui_version,
        "kotlinmcui_version_range" to kotlinmcui_version_range,
        "minecraft_version" to minecraft_version,
        "minecraft_version_range" to minecraft_version_range,
        "loader" to loader,
        "loader_version" to loader_version,
        "loader_version_range" to loader_version_range,
        "fabric_kotlin_version" to fabric_kotlin_version,
        "mod_menu_version" to mod_menu_version,
        // The Mod Menu entrypoint is a nested class, so its JVM name contains '$'; Groovy's
        // template expansion would read that as a placeholder. Build the value here instead.
        "modmenu_entry" to "io.github.u2894638479.kotlinmcuibackend.Entry\$ModMenu",
    )
    inputs.properties(map)
    filesMatching("fabric.mod.json") { expand(map) }
}

// The tag this build publishes under. A tag build takes it from the ref; a local build only needs a
// name, because the release workflow is the only publisher now - nothing publishes from a branch push.
val versionTag = "v$version".replace('+','-')
val releaseTag = providers.environmentVariable("GITHUB_REF_NAME").orNull?.takeIf { it.isNotBlank() }
    ?: versionTag

tasks.register("assertReleaseTag") {
    group = "publishing"
    description = "Fails unless the tag being built is the version this branch builds."
    doLast {
        val ref = providers.environmentVariable("GITHUB_REF_NAME").orNull
            ?: error("GITHUB_REF_NAME is not set: this task runs in the release workflow, on a tag")
        // Against the version, not against releaseTag: releaseTag is read from this same variable, so
        // comparing the two would only prove the variable equals itself.
        check(ref == versionTag) {
            "the tag '$ref' does not match '$versionTag', which is this branch's version"
        }
    }
}

val changelogText = layout.projectDirectory.file("CHANGELOG.md").asFile
    .takeIf { it.isFile }?.readText()?.trim().orEmpty()
    .ifEmpty { "no changelog." }

publishMods {
    file = tasks.remapJar.get().archiveFile
    additionalFiles.from(sourcesJar.archiveFile, tasks.jar)
    changelog = changelogText
    type = when {
        mod_version.contains("SNAPSHOT",true) -> ALPHA
        mod_version.contains("alpha",true) -> ALPHA
        mod_version.contains("beta",true) -> BETA
        else -> STABLE
    }
    displayName = "$branch-$mod_version"
    modLoaders.add(loader)

    modrinth {
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        projectId = "FjVgWB2Y"
        optional("modmenu")
        requires("fabric-language-kotlin","kotlinmcui")
        minecraftVersionRange {
            start = minecraft_version
            end = minecraft_version
        }
    }
    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = "1460645"
        optional("modmenu")
        requires("fabric-language-kotlin","kotlinmcui")
        client = true
        server = false
        minecraftVersionRange {
            start = minecraft_version
            end = minecraft_version
        }
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "2894638479/KotlinMCUI-backend"
        commitish = providers.environmentVariable("GITHUB_SHA").orNull ?: branch
        tagName = releaseTag
    }
}