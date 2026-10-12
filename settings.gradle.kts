val javaVersion = System.getProperty("java.version")
if (javaVersion?.startsWith("26") == true || javaVersion?.startsWith("27") == true) {
    System.setProperty("java.version", "21.0.2")
}

pluginManagement {
    repositories {
        maven("https://maven.quiltmc.org/repository/release")
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "stellar-mods"
include("stellar-core", "stellar-law", "stellar-ops", "stellar-tweak", "stellar-lang", "stellar-keys", "stellar-rays", "stellar-exploration")
