pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://files.minecraftforge.net/maven/")
        gradlePluginPortal()
    }
}

rootProject.name = "Velthoric"

include("common")
include("fabric")
include("neoforge")

include("vx-native")
include("vx-events")