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
val coroutines_version = gradleProperty("coroutines_version")
val serialization_version = gradleProperty("serialization_version")
val slf4j_version = gradleProperty("slf4j_version")
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
// A headless jar carries no game version in its name on purpose: it runs on every one of them.
version = listOf(mod_version, "headless", loader).joinToString(separator = "+")

val branch = "headless-${loader}"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://jitpack.io")
}

loom {
    // No access widener and no mixins: this backend never touches a Minecraft class.
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

/** Kotlin travels inside the jar: the old game versions have no language loader for it, and the ones
 *  that exist bundle a Kotlin too old for classes compiled by 2.4. See tasks.jar. */
val bundledKotlin by configurations.creating

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loader_version")
    // Compiled against normally, and unpacked into the jar by tasks.jar: the framework's runtime
    // dependencies, which the old game versions have nobody else to provide.
    listOf(
        "org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version",
        "org.jetbrains.kotlinx:kotlinx-serialization-core:$serialization_version",
        "org.jetbrains.kotlinx:kotlinx-serialization-json:$serialization_version",
        // The game only started shipping this one around 1.17; older versions have no slf4j at all.
        "org.slf4j:slf4j-api:$slf4j_version",
        // A binding as well, not just the API: without one slf4j turns into a no-op logger and the
        // framework goes completely silent, which makes every failure here invisible.
        "org.slf4j:slf4j-jdk14:$slf4j_version",
    ).forEach {
        implementation(it)
        bundledKotlin(it)
    }
    val jarFile = file("../kotlinmcui/build/libs/kotlinmcui-$kotlinmcui_version.jar")
    val sourceFile = file("../kotlinmcui/build/libs/kotlinmcui-$kotlinmcui_version-sources.jar")
    if(jarFile.exists() && sourceFile.exists()) {
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
        // The jar has to load on the Java 8 the old game versions run on, so the API surface is
        // checked against 1.8 rather than the toolchain that compiles it.
        freeCompilerArgs.add("-Xjdk-release=1.8")
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
    // Self-contained Kotlin: the standard library is unpacked into the mod jar instead of being
    // required from a language-loader mod, because the versions this jar targets do not have one
    // new enough. Every loader puts a mod's own jar on its classpath, so the classes are visible
    // to this mod and to the framework it loads.
    from(bundledKotlin.map { zipTree(it) }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
    )
    inputs.properties(map)
    filesMatching("fabric.mod.json") { expand(map) }
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
    file = tasks.remapJar.get().archiveFile
    additionalFiles.from(sourcesJar.archiveFile, tasks.jar)
    changelog = "no changelog."
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
        requires("fabric-language-kotlin","kotlinmcui")
        minecraftVersionRange {
            start = minecraft_version
            end = minecraft_version
        }
    }
    curseforge {
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        projectId = "1460645"
        requires("fabric-language-kotlin","kotlinmcui")
        // Required on both sides: the core library declares this mod as a hard dependency.
        client = true
        server = true
        minecraftVersionRange {
            start = minecraft_version
            end = minecraft_version
        }
    }
    github {
        accessToken = providers.environmentVariable("GITHUB_TOKEN")
        repository = "2894638479/KotlinMCUI-backend"
        commitish = branch
        tagName = tag
    }
}