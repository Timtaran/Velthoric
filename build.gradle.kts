import net.fabricmc.loom.task.RemapJarTask
import org.apache.commons.io.output.ByteArrayOutputStream
import org.gradle.internal.impldep.org.apache.commons.compress.archivers.zip.ZipFile

plugins {
    id("dev.architectury.loom") version "1.13-SNAPSHOT" apply false
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("com.gradleup.shadow") version "8.3.6" apply false
    id("net.xmx.velthoric.publishing")
}

architectury {
    minecraft = project.property("minecraft_version") as String
}

allprojects {
    group = project.property("maven_group") as String
    version = project.property("mod_version") as String
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "architectury-plugin")
    apply(plugin = "maven-publish")

    // Apply the publishing plugin to subprojects to expose the "velthoricPublishing" extension
    apply(plugin = "net.xmx.velthoric.publishing")

    val archiveMap = mapOf(
        "fabric" to "velthoric-fabric",
        "forge" to "velthoric-forge",
        "neoforge" to "velthoric-neoforge",
        "common" to "velthoric-common",
        "vx-events" to "vx-events",
        "vx-native" to "vx-native"
    )

    base {
        archivesName = archiveMap.getOrDefault(project.name, "$rootProject.archives_name-$project.name")
    }

    repositories {
        mavenCentral()
        maven("https://cursemaven.com")
        maven("https://maven.parchmentmc.org")
        maven("https://jitpack.io")
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")

        flatDir {
            dirs(rootProject.file("libraries"))
        }
    }

    tasks.withType(RemapJarTask).configureEach {
        if (project.name == "common" || project.name == "vx-events" || project.name == "vx-native") {
            addNestedDependencies = false
        }
    }

    loom {
        silentMojangMappingsLicense()

        mixin {
            useLegacyMixinAp = true
        }
        val refmaps = mapOf(
            "vx-events" to "vx-events-refmap.json",
            "common" to "velthoric-refmap.json"
        )

        refmaps.each { proj, refmap ->
            if (project.name == proj) {
                mixin.defaultRefmapName.set(refmap)
            }
        }
    }

    dependencies {
        minecraft("net.minecraft:minecraft:$rootProject.minecraft_version")

        mappings(loom.layered {
            officialMojangMappings()
            parchment("org.parchmentmc.data:parchment-${minecraft_version}:${parchment_version}@zip")
        })
    }

    java {
        withSourcesJar()

        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType(JavaCompile).configureEach {
        it.options.release = 21
    }

    tasks.withType(GenerateModuleMetadata).configureEach {
        enabled = false
    }
}

task generateJoltDoc {

    val joltModule = configurations.detachedConfiguration(
        dependencies.create("com.github.stephengold:jolt-jni-Linux64:${property("jolt_jni_version")}")
    ).singleFile

    val outputFile = file("docs/jolt.txt").get().asFile

    inputs.file(joltModule)
    outputs.file(outputFile)

    doLast {
        println("=== JoltDoc Generation Started ===")

        if (!joltModule.exists()) {
            throw new FileNotFoundException("Input JAR not found: ${joltModule.absolutePath}")
        }

        println("Analyzing JAR: ${joltModule.name}")
        println("Writing output to: ${outputFile.absolutePath}")

        outputFile.parentFile.mkdirs()
        outputFile.text = ""

        val zipFile = new ZipFile(joltModule)
        val classEntries = zipFile.entries().findAll { entry ->
            !entry.isDirectory() && entry.name.endsWith(".class")
        }

        println("Found ${classEntries.size()} class files.")

        classEntries.eachWithIndex { entry, idx ->
            val className = entry.name.replace("/", ".").replaceAll('\\.class$', '')
            println("[${idx + 1}/${classEntries.size()}] Processing class: ${className}")

            val commandOutput = ByteArrayOutputStream()

            project.exec {
                executable = "javap"
                args = listOf("-p", "-classpath", joltModule.absolutePath, className)
                standardOutput = commandOutput
                ignoreExitValue = true
            }

            outputFile.append("${className.substring(className.lastIndexOf(".") + 1)}.class:\n")

            val javapResult = commandOutput.toString()
            val methodLines = javapResult.lines()
                .findAll { it.contains("(") && it.contains(")") && !it.contains("{") && !it.contains("Compiled from") }
                .findAll { it.trim().startsWith("public") || it.trim().startsWith("protected") }

            println("  Found ${methodLines.size()} methods in ${className}.")

            methodLines.each { line ->
                var cleanedLine = line.trim()
                    .replaceAll("public |protected |static |final |synchronized |native ", "")

                if (line.trim().startsWith("protected")) {
                    cleanedLine = "protected ${cleanedLine}"
                }

                outputFile.append("  ${cleanedLine}\n")
                println("    Logged method: ${cleanedLine}")
            }

            outputFile.append("\n")
        }

        zipFile.close()
        println("=== JoltDoc Generation Finished ===")
    }
}