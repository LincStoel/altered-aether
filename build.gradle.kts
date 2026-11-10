import java.util.zip.ZipFile

plugins {
    id("java-library")
    id("net.neoforged.moddev") version "2.0.148"
}

version = property("mod_version") as String
group = property("mod_group_id") as String

base {
    archivesName = property("mod_id") as String
}

java.toolchain.languageVersion = JavaLanguageVersion.of(21)

// Debug tooling (the worldgen report and benchmark, the camera tour, the JEI screenshot, /altered_aether's dev
// commands) and the GameTests. Every dev run loads it as part of the mod; the released jar is built from main alone,
// so nothing in main can depend on it.
val dev: SourceSet = sourceSets.create("dev")

neoForge {
    enable {
        version = property("neo_version") as String
        // Run the original Minecraft classes instead of recompiled sources: recompiling renumbers
        // lambdas, which breaks other mods' mixins (e.g. Accessories via The Aether) in dev runs.
        isDisableRecompilation = true
    }

    parchment {
        mappingsVersion = property("parchment_mappings_version") as String
        minecraftVersion = property("parchment_minecraft_version") as String
    }

    addModdingDependenciesTo(dev)

    mods {
        register(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
            sourceSet(dev)
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = layout.projectDirectory.dir("run")
            // -PaaTour=stop,stop,... quick-plays the world run/saves/tour and takes the camera tour in debug/Tour.
            val tour = findProperty("aaTour") as String?
            systemProperty("altered_aether.tour", tour ?: "")
            // -PaaJeiShot=<category id> quick-plays it too, opens that JEI page, screenshots it and quits (debug/JeiShot).
            val jeiShot = findProperty("aaJeiShot") as String?
            systemProperty("altered_aether.jei_shot", jeiShot ?: "")
            if (tour != null || jeiShot != null) {
                programArguments.addAll("--quickPlaySingleplayer", "tour")
            }
            // -PaaJoin=host:port joins that server on start, to check a dedicated server end to end.
            val join = findProperty("aaJoin") as String?
            if (join != null) {
                programArguments.addAll("--quickPlayMultiplayer", join)
            }
        }
        register("server") {
            server()
            gameDirectory = layout.projectDirectory.dir("run-server")
            programArgument("--nogui")
            // -PaaReport=radius,step prints a worldgen report for the Aether on startup, then stops the server.
            systemProperty("altered_aether.report", (findProperty("aaReport") as String?) ?: "")
            // -PaaBench=radius[,biome] generates that many chunks around a point in the Aether (centred on the nearest
            // biome if one is named), logs the time and a surface tally, then stops.
            systemProperty("altered_aether.bench", (findProperty("aaBench") as String?) ?: "")
            // -PaaStructures=true adds each Aether structure's height against its ground to the report.
            systemProperty("altered_aether.report.structures", (findProperty("aaStructures") as String?) ?: "false")
            // -PaaClimate=<file> writes the climate at every in-region report sample to a CSV, for sizing BiomeTable.
            systemProperty("altered_aether.report.climate", (findProperty("aaClimate") as String?) ?: "")
            // -PaaRender=true writes top and side views of the benchmark's area to run-server/renders/. A benchmark
            // centred on "landmark:<type>" goes to the nearest landmark of that type.
            systemProperty("altered_aether.render", (findProperty("aaRender") as String?) ?: "false")
            // -PaaFocus=<block id> adds close-ups around the first few of that block in a rendered benchmark.
            systemProperty("altered_aether.focus", (findProperty("aaFocus") as String?) ?: "")
        }
        // Runs this mod's GameTests (see the gametest package) on a headless server and exits with their result.
        register("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = layout.projectDirectory.dir("run-gametest")
            systemProperty("neoforge.enabledGameTestNamespaces", property("mod_id") as String)
        }
        // Writes the mod's generated data (worldgen JSON, tags, lang, models) into src/generated/resources.
        register("data") {
            data()
            gameDirectory = layout.projectDirectory.dir("run-data")
            programArguments.addAll(
                "--mod", property("mod_id") as String,
                "--all",
                "--output", file("src/generated/resources").absolutePath,
                "--existing", file("src/main/resources").absolutePath,
            )
        }
        configureEach {
            // The dev source set's classpath holds main's too, so each run has the whole mod.
            sourceSet = dev
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }
}

repositories {
    // Mods published on Modrinth, as maven.modrinth:<project slug>:<version number>.
    maven("https://api.modrinth.com/maven") {
        content { includeGroup("maven.modrinth") }
    }
    maven("https://maven.bawnorton.com/releases") {
        content { includeGroup("com.github.bawnorton.mixinsquared") }
    }
}

fun modrinth(project: String, versionProperty: String) = "maven.modrinth:$project:${property(versionProperty)}"

// The Aether, Aeroblender and the mods they need at runtime: aether, aeroblender, TerraBlender (which Aeroblender
// requires), accessories (newer than the copy bundled in the Aether's jar) and owo-lib (which Accessories requires).
// They are compiled against, and loaded at runtime from the run folders' mods/ (see the copy tasks below), so jar-in-jar
// dependencies (Cumulus and Nitrogen inside the Aether's jar) resolve exactly as they do in a real instance.
val devMods: Configuration by configurations.creating
// Create, compiled against for the ambrosium campfire's fan processing (see CreateCompat).
val createMod: Configuration by configurations.creating
// Optional mods taken from Modrinth Maven rather than libs/optional/; selected with -PoptionalMods like the rest.
val optionalMaven: Configuration by configurations.creating
val optionalMavenModules = listOf("deep-aether", "lithium")

configurations.compileOnly {
    extendsFrom(devMods, createMod)
}
// The dev source set compiles and runs against everything main does, and main itself.
configurations.named(dev.compileOnlyConfigurationName) { extendsFrom(configurations.compileOnly.get()) }
configurations.named(dev.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
configurations.named(dev.runtimeOnlyConfigurationName) { extendsFrom(configurations.runtimeOnly.get()) }

dependencies {
    devMods(modrinth("aether", "aether_version")) { isTransitive = false }
    devMods(modrinth("aeroblender", "aeroblender_version")) { isTransitive = false }
    devMods(modrinth("terrablender", "terrablender_version")) { isTransitive = false }
    devMods(modrinth("accessories", "accessories_version")) { isTransitive = false }
    devMods(modrinth("owo-lib", "owo_lib_version")) { isTransitive = false }

    // The one optional mod compiled against: Create. Only classes loaded when Create is present reference it. Its JEI
    // page draws with Ponder's catnip library, which Create bundles as a jar-in-jar along with Flywheel (named in
    // catnip's signatures), so both are taken out of Create's jar for compiling too.
    createMod(modrinth("create", "create_version")) { isTransitive = false }
    compileOnly(files(createMod.files.map { jar ->
        zipTree(jar).matching { include("META-INF/jarjar/ponder-*.jar", "META-INF/jarjar/flywheel-*.jar") }
    }))
    // JEI, for that page (see compat/jei); it runs only in client runs, from libs/client.
    compileOnly(modrinth("jei", "jei_version")) { isTransitive = false }
    // MixinSquared cancels Deep Aether's aerwhale mixins when our own aerwhales are in use (see AAMixinPlugin).
    compileOnly("com.github.bawnorton.mixinsquared:mixinsquared-common:0.3.7")
    jarJar(implementation("com.github.bawnorton.mixinsquared:mixinsquared-neoforge:0.3.7")!!)

    optionalMaven(modrinth("deep-aether", "deep_aether_version")) { isTransitive = false }
    optionalMaven(modrinth("lithium", "lithium_version")) { isTransitive = false }

    "devImplementation"(sourceSets.main.get().output)

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Datagen output is committed and shipped like hand-written resources.
sourceSets.main.get().resources {
    srcDir("src/generated/resources")
    exclude(".cache/")
}

// Optional mods the content integrates with live in libs/optional/ (not committed, not compiled against), apart from
// those in optionalMaven. -PoptionalMods picks which of them go into a run: "all", "none", or comma-separated names
// (jar name prefixes or Modrinth project names, with - and _ alike) such as -PoptionalMods=snuffles,deep-aether.
// Client and server runs default to "all"; the GameTest server defaults to "none", since some optional mods (Quark)
// stop it from loading. Use it to test the mod both with and without each integration.
fun normalised(name: String) = name.lowercase().replace('-', '_')
val mavenOptionalPrefixes = optionalMavenModules.map(::normalised)
// A jar in libs/optional for a mod optionalMaven already provides is left out, so a run never gets two copies.
val optionalJars = files(fileTree("libs/optional") { include("*.jar") }.files
    .filter { jar -> mavenOptionalPrefixes.none { normalised(jar.name).startsWith(it) } })
val optionalJarNames = fileTree("libs/optional") { include("*.jar") }.files.map { it.name }

fun selectedOptional(selection: String): FileCollection {
    if (selection == "none") {
        return files()
    }
    val prefixes = if (selection == "all") null else selection.split(',').map { normalised(it.trim()) }
    val local = optionalJars.files.filter { jar -> prefixes == null || prefixes.any { normalised(jar.name).startsWith(it) } }
    // Kept free of script references (such as normalised), which the configuration cache can't store.
    val maven = optionalMaven.incoming.artifactView {
        componentFilter { id ->
            id is ModuleComponentIdentifier
                    && (prefixes == null || prefixes.any { id.module.lowercase().replace('-', '_').startsWith(it) })
        }
    }.files
    return files(local, maven)
}

// Iris and Sodium (libs/shaders/, not committed) go into client runs, with BSL selected through run/config/iris.properties
// (seeded from iris.properties.staged when Iris hasn't written one yet) so effects can be checked under a shaderpack.
// -Pshaders=false runs the client without them.
val shaderJars = fileTree("libs/shaders") { include("*.jar") }
val shadersEnabled = ((findProperty("shaders") as String?) ?: "true").toBoolean()
val shaderJarNames = shaderJars.files.map { it.name }

// Client-side conveniences for play-testing (JEI, Jade, Immersive Overlays, Xaero's Minimap and World Map), in
// libs/client/ (not committed), go into client runs only. -PclientMods=false runs the client without them.
val clientJars = fileTree("libs/client") { include("*.jar") }
val clientModsEnabled = ((findProperty("clientMods") as String?) ?: "true").toBoolean()
val clientJarNames = clientJars.files.map { it.name }

// Jars that earlier builds copied from libs/ itself, before devMods came from Modrinth Maven: removed from the run
// folders so the Maven copies don't load beside them.
val legacyDevJarNames = fileTree("libs") { include("*.jar") }.files.map { it.name }

// Adds the dev mods to the run's mods folder before each run. A copy rather than a sync, so other mods dropped into
// run/mods by hand for play-testing are left alone. What the last copy put there is listed in a manifest and removed
// first, so a version bump or a mod left out of the selection doesn't stay behind.
for ((run, dir) in listOf("Client" to "run", "Server" to "run-server", "Data" to "run-data",
        "GameTestServer" to "run-gametest")) {
    val modsDir = layout.projectDirectory.dir("$dir/mods").asFile
    val withOptional = run != "Data"
    val selection = (findProperty("optionalMods") as String?) ?: if (run == "GameTestServer") "none" else "all"
    val shaders = run == "Client" && shadersEnabled
    val clientMods = run == "Client" && clientModsEnabled
    val sources = files(
        devMods,
        if (withOptional) selectedOptional(selection) else files(),
        if (shaders) shaderJars else files(),
        if (clientMods) clientJars else files(),
    )
    val staleNames = optionalJarNames + shaderJarNames + clientJarNames + legacyDevJarNames
    val copy = tasks.register<Copy>("copy${run}DevMods") {
        group = "mod development"
        description = "Copies the dev mods (and the selected optional mods) into $dir/mods."
        from(sources)
        into(modsDir)
        val configDir = layout.projectDirectory.dir("$dir/config").asFile
        val manifest = modsDir.resolve(".altered-aether-dev-mods")
        doFirst {
            if (manifest.exists()) {
                manifest.readLines().filter { it.isNotBlank() }.forEach { modsDir.resolve(it).delete() }
            }
            staleNames.forEach { modsDir.resolve(it).delete() }
            val irisConfig = configDir.resolve("iris.properties")
            val staged = configDir.resolve("iris.properties.staged")
            if (shaders && !irisConfig.exists() && staged.exists()) {
                staged.copyTo(irisConfig)
            }
        }
        doLast {
            manifest.writeText(sources.files.joinToString("\n") { it.name })
        }
    }
    tasks.configureEach {
        if (name == "run$run") {
            dependsOn(copy)
        }
    }
}

val modMetadataProperties = listOf(
    "minecraft_version",
    "minecraft_version_range",
    "neo_version",
    "neo_version_range",
    "loader_version_range",
    "mod_id",
    "mod_name",
    "mod_license",
    "mod_version",
    "mod_authors",
    "mod_description",
).associateWith { project.property(it) as String }

val generateModMetadata by tasks.registering(ProcessResources::class) {
    val replaceProperties = modMetadataProperties
    inputs.properties(replaceProperties)
    expand(replaceProperties)
    from("src/main/templates")
    into("build/generated/sources/modMetadata")
}

sourceSets.main.get().resources.srcDir(generateModMetadata)
neoForge.ideSyncTask(generateModMetadata)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.jar {
    from("LICENSE")
}

// The released jar holds main alone: fails the build if dev-only code or test structures got into it.
val checkReleaseJar by tasks.registering {
    group = "verification"
    description = "Checks that the jar holds no debug tooling, GameTests or test structures."
    val jar = tasks.jar.flatMap { it.archiveFile }
    inputs.file(jar)
    doLast {
        val devOnly = listOf("com/linc/alteredaether/debug/", "com/linc/alteredaether/gametest/",
            "data/altered_aether/structure/arena.nbt")
        val found = ZipFile(jar.get().asFile).use { zip ->
            zip.entries().asSequence().map { it.name }.filter { name -> devOnly.any { name.startsWith(it) } }.toList()
        }
        if (found.isNotEmpty()) {
            throw GradleException("Dev-only files in the released jar: $found")
        }
    }
}
tasks.check {
    // The dev source set isn't in the jar, so build would otherwise never compile it.
    dependsOn(checkReleaseJar, dev.classesTaskName)
}
