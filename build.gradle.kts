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

    mods {
        register(property("mod_id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = layout.projectDirectory.dir("run")
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
            systemProperty("forge.logging.markers", "REGISTRIES")
            logLevel = org.slf4j.event.Level.DEBUG
        }
    }
}

// The Aether, Aeroblender and the mods they need at runtime, copied from the CurseForge instance into libs/ (not
// committed): aether, aeroblender, TerraBlender (which Aeroblender requires), accessories (newer than the copy
// bundled in the Aether's jar) and owo-lib (which Accessories requires).
// They are compiled against, and loaded at runtime from the run folders' mods/ (see copyClientDevMods), so jar-in-jar
// dependencies (Cumulus and Nitrogen inside the Aether's jar) resolve exactly as they do in a real instance.
val devMods = fileTree("libs") { include("*.jar") }

dependencies {
    compileOnly(devMods)

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

// Optional mods the content integrates with (Ars Nouveau, Ars Elemental, Snuffles, and Ars Nouveau's dependencies
// Curios and GeckoLib) live in libs/optional/ (not committed, not compiled against). -PoptionalMods picks which of them
// go into a client or server run: "all" (the default), "none", or comma-separated jar name prefixes such as
// -PoptionalMods=snuffles. Use it to test the mod both with and without each integration.
val optionalJars = fileTree("libs/optional") { include("*.jar") }
val optionalSelection = (findProperty("optionalMods") as String?) ?: "all"
val selectedOptionalJars: FileCollection = when (optionalSelection) {
    "all" -> optionalJars
    "none" -> files()
    else -> {
        val prefixes = optionalSelection.split(',').map { it.trim() }
        optionalJars.filter { jar -> prefixes.any { jar.name.startsWith(it) } }
    }
}
val optionalJarNames = optionalJars.files.map { it.name }

// Adds libs/ to the run's mods folder before each run. A copy rather than a sync, so other mods dropped into
// run/mods by hand for play-testing are left alone; only optional jars that weren't selected are removed first.
for ((run, dir) in listOf("Client" to "run", "Server" to "run-server", "Data" to "run-data")) {
    val modsDir = layout.projectDirectory.dir("$dir/mods").asFile
    val withOptional = run != "Data"
    val optionalNames = optionalJarNames.toList()
    val copy = tasks.register<Copy>("copy${run}DevMods") {
        group = "mod development"
        description = "Copies the mods in libs/ (and the selected libs/optional/ mods) into $dir/mods."
        from(devMods)
        if (withOptional) {
            from(selectedOptionalJars)
        }
        into(modsDir)
        doFirst {
            optionalNames.forEach { modsDir.resolve(it).delete() }
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
