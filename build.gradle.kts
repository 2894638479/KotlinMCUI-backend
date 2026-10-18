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

/** Only slf4j travels inside the jar; the Kotlin runtime comes from the language loader. See
 *  tasks.jar. */
val bundledRuntime by configurations.creating

dependencies {
    minecraft("com.mojang:minecraft:$minecraft_version")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:$loader_version")
    // The Kotlin language loader brings the framework's whole runtime with it - Kotlin itself, the
    // coroutines, serialization and slf4j - so this jar stays a few KB instead of carrying copies.
    // One FLK covers every game version this backend targets: its own metadata declares 1.14
    // through the newest release, which is exactly the range the jar is built to run on.
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabric_kotlin_version")
    // slf4j is the one runtime piece neither the loader nor the language loader brings, and the game
    // only started shipping it around 1.17: on 1.14 and 1.16 the framework's logger cannot even
    // initialise without it. The pair is about fifty kilobytes, and a bundled copy is harmless on
    // Fabric - the whole 1.14 to 26.3 range was run with it before this.
    listOf(
        "org.slf4j:slf4j-api:$slf4j_version",
        "org.slf4j:slf4j-jdk14:$slf4j_version",
    ).forEach {
        implementation(it)
        bundledRuntime(it)
    }
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
        // describes the old API. A real mismatch is hours apart; a normal rebuild is milliseconds
        // apart, and Gradle does not order the two tasks, which is what the slack is for: without it
        // this check would fire on a perfectly good build.
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
    // Unpacked, not nested: the old game versions have no slf4j of their own, and every loader puts
    // a mod's own jar on its classpath, so the classes are visible to this mod and to the framework
    // it loads.
    from(bundledRuntime.map { zipTree(it) }) {
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