plugins {
    java
    kotlin("jvm") version "2.4.10" apply false
    id("org.quiltmc.loom") version "1.15.1" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

    group = "com.stellar"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://maven.quiltmc.org/repository/release")
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

subprojects {
    apply(plugin = "org.quiltmc.loom")
    apply(plugin = "org.jetbrains.kotlin.jvm")

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

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
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

