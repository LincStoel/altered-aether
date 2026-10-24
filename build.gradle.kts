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
}

// Adds libs/ to the run's mods folder before each run. A copy rather than a sync, so other mods dropped into
// run/mods by hand for play-testing are left alone.
for ((run, dir) in listOf("Client" to "run", "Server" to "run-server")) {
    val copy = tasks.register<Copy>("copy${run}DevMods") {
        group = "mod development"
        description = "Copies the mods in libs/ into $dir/mods."
        from(devMods)
        into(layout.projectDirectory.dir("$dir/mods"))
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
