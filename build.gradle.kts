plugins {
    java
    kotlin("jvm") version "2.4.10" apply false
    id("org.quiltmc.loom") version "1.15.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    group = "com.stellar"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://maven.quiltmc.org/repository/release")
        maven("https://maven.terraformersmc.com/")
        maven("https://maven.shedaniel.me/")
        maven(rootProject.file("local-repo"))
    }

    dependencies {
        "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:${project.property("detekt_version")}")
    }

    detekt {
        buildUponDefaultConfig = true
        allRules = true
        val baseConfig = rootProject.file("config/detekt/detekt.yml")
        val moduleConfig = rootProject.file("config/detekt/detekt-${project.name}.yml")
        if (moduleConfig.exists()) {
            config.setFrom(baseConfig, moduleConfig)
        } else {
            config.setFrom(baseConfig)
        }
        parallel = true
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "21"
        reports {
            html.required.set(true)
            xml.required.set(true)
            sarif.required.set(true)
            md.required.set(true)
        }
    }
}

val rootLoom = extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>()
rootLoom.noIntermediateMappings()

dependencies {
    "minecraft"("com.mojang:minecraft:${project.property("minecraft_version")}")
    "mappings"(rootLoom.layered {
        mappings(rootProject.file("config/mappings/unobfuscated.tiny"))
    })

    "modImplementation"("org.quiltmc:quilt-loader:${project.property("quilt_loader_version")}")
    "modImplementation"("org.quiltmc.quilt-kotlin-libraries:core:${project.property("quilt_kotlin_version")}")

    // Global client runtime: aggregate all Stellar modules
    "implementation"(project(path = ":stellar-core", configuration = "namedElements"))
    "implementation"(project(path = ":stellar-law", configuration = "namedElements"))
    "implementation"(project(path = ":stellar-ops", configuration = "namedElements"))
    "implementation"(project(path = ":stellar-tweak", configuration = "namedElements"))
    "implementation"(project(path = ":stellar-lang", configuration = "namedElements"))
    "implementation"("com.microsoft.onnxruntime:onnxruntime:1.20.0")
}

rootLoom.runs.named("client") {
    configName = "Stellar Client"
    runDir = "run/client"
}

rootLoom.runs.named("server") {
    configName = "Stellar Server"
    runDir = "run/server"
}

tasks.named("runClient") {
    group = "loom"
    description = "Runs the global Minecraft client with the entire Stellar mod suite."
}

tasks.named("runServer") {
    group = "loom"
    description = "Runs the global Minecraft dedicated server with the entire Stellar mod suite."
}

tasks.register("installGitHooks") {
    group = "help"
    description = "Configures core.hooksPath to .githooks for root repository and all submodules."
    doLast {
        val repos = listOf(rootDir) + subprojects.map { it.projectDir }
        for (repo in repos) {
            val hooksDir = File(repo, ".githooks")
            if (hooksDir.exists()) {
                hooksDir.listFiles()?.filter { it.isFile }?.forEach { hook ->
                    hook.setExecutable(true)
                }
                runCatching {
                    ProcessBuilder("git", "config", "core.hooksPath", ".githooks")
                        .directory(repo)
                        .inheritIO()
                        .start()
                        .waitFor()
                }
                println("Configured git hooks in ${repo.name}")
            }
        }
    }
}

subprojects {
    apply(plugin = "org.quiltmc.loom")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")

    configure<JacocoPluginExtension> {
        toolVersion = "0.8.12"
    }

    val loom = extensions.getByName<net.fabricmc.loom.api.LoomGradleExtensionAPI>("loom")
    loom.noIntermediateMappings()

    dependencies {
        "minecraft"("com.mojang:minecraft:${project.property("minecraft_version")}")
        "mappings"(loom.layered {
            mappings(rootProject.file("config/mappings/unobfuscated.tiny"))
        })
        
        "modImplementation"("org.quiltmc:quilt-loader:${project.property("quilt_loader_version")}")
        "modImplementation"("org.quiltmc.quilt-kotlin-libraries:core:${project.property("quilt_kotlin_version")}")

        "testImplementation"("io.kotest:kotest-runner-junit5:${project.property("kotest_version")}")
        "testImplementation"("io.kotest:kotest-assertions-core:${project.property("kotest_version")}")
        "testImplementation"("io.kotest:kotest-property:${project.property("kotest_version")}")
    }

    loom.runs.create("gameTestServer") {
        server()
        vmArg("-Dfabric.gameTests=true")
        vmArg("-Dfabric.gameTests.reportPath=build/reports/gametest-results.xml")
        runDir("run/gametest")
    }

    // Disable individual subproject client/server runs so `./gradlew runClient` / `runServer` only run the global instance
    afterEvaluate {
        tasks.matching { it.name == "runClient" || it.name == "runServer" }.configureEach {
            enabled = false
        }
    }

    val javaToolchains = project.extensions.getByType<JavaToolchainService>()
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        javaLauncher.set(javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(26))
        })
        extensions.configure<JacocoTaskExtension> {
            includes = listOf("com.stellar.*")
        }
        finalizedBy("jacocoTestReport")
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        classDirectories.setFrom(
            classDirectories.files.map {
                fileTree(it) {
                    exclude("**/mixin/**")
                }
            }
        )
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }
    
    configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
    }

    tasks.withType<net.fabricmc.loom.task.RemapSourcesJarTask>().configureEach {
        enabled = false
    }
}

evaluationDependsOnChildren()

rootLoom.mods {
    create("stellar_law") {
        sourceSet("main", project(":stellar-law"))
    }
    create("stellar_ops") {
        sourceSet("main", project(":stellar-ops"))
    }
    create("stellar_tweak") {
        sourceSet("main", project(":stellar-tweak"))
    }
    create("stellar_lang") {
        sourceSet("main", project(":stellar-lang"))
    }
}

tasks.named("runClient") {
    dependsOn(subprojects.map { it.tasks.named("jar") })
}


