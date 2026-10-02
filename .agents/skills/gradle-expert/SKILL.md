---
name: gradle-expert
kind: tool
version: 1.0.0
tags:
  - domain: build
  - subtype: gradle
  - level: expert
description: "Expert Gradle build automation skill focusing on modern Gradle with Kotlin DSL (build.gradle.kts), multi-project enterprise configurations, Minecraft Loom (Quilt Loom/Fabric Loom), build cache optimization, configuration avoidance, dependency resolution, and Detekt integration. Use when: authoring or debugging Gradle build scripts, optimizing build times, configuring multi-module projects, resolving dependency conflicts, or tuning Minecraft Loom tasks."
license: MIT
metadata:
  author: Antigravity
---

# Gradle Expert & Build Automation

## One-Liner
Design, optimize, and maintain fast, deterministic, and modular Gradle builds using Kotlin DSL (`build.gradle.kts`), modern Gradle conventions, Minecraft Loom (Quilt/Fabric), and strict task configuration avoidance.

---

## § 1 · Modern Gradle Kotlin DSL Conventions

### 1.1 Declarative Plugin Application
- Apply plugins using the declarative `plugins { }` block with version specifications:
  ```kotlin
  plugins {
      java
      kotlin("jvm") version "2.4.10" apply false
      id("org.quiltmc.loom") version "1.15.1"
      id("io.gitlab.arturbosch.detekt") version "1.23.8"
  }
  ```
- Use `apply false` in root builds when plugins should only be configured by specific subprojects.

### 1.2 Task Configuration Avoidance
- **Never** configure tasks eagerly with `tasks.create(...)` or `tasks.getByName(...)`.
- Use lazy task registration:
  ```kotlin
  // Good: lazy task creation
  tasks.register<Jar>("fatJar") {
      archiveClassifier.set("all")
      from(sourceSets.main.get().output)
  }
  ```
- Use lazy task configuration with `tasks.withType<T>().configureEach { ... }`:
  ```kotlin
  tasks.withType<JavaCompile>().configureEach {
      options.encoding = "UTF-8"
      options.release.set(21)
  }

  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
      compilerOptions {
          jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
          freeCompilerArgs.add("-Xjsr305=strict")
      }
  }
  ```

---

## § 2 · Multi-Project Architecture & Modularization

### 2.1 Project Hierarchy & Settings
- Declare module structure in `settings.gradle.kts`:
  ```kotlin
  rootProject.name = "stellar-mods"
  include(
      "stellar-core",
      "stellar-law",
      "stellar-ops",
      "stellar-tweak",
      "stellar-lang"
  )
  ```
- Apply consistent conventions across submodules in the root `build.gradle.kts`:
  ```kotlin
  allprojects {
      group = "com.stellar"
      version = "1.0.0"

      repositories {
          mavenCentral()
          maven("https://maven.quiltmc.org/repository/release")
          maven("https://maven.terraformersmc.com/")
          maven("https://maven.shedaniel.me/")
          maven(rootProject.file("local-repo"))
      }
  }
  ```

### 2.2 Project Dependencies & Scopes
- Use type-safe dependency declarations:
  ```kotlin
  dependencies {
      // Core module bundled or compiled against
      implementation(project(":stellar-core"))
      
      // Soft / compile-time only dependencies
      compileOnly(project(":stellar-law"))
      
      // Testing dependencies
      testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
      testImplementation("io.kotest:kotest-assertions-core:5.9.1")
  }
  ```
- **Scope rules:**
  - `implementation`: Internal dependency, not leaked to consumers' compile classpath.
  - `api`: Leaked to consumers' compile classpath (use sparingly for public interfaces).
  - `compileOnly`: Present only during compilation (e.g., soft dependencies, optional mod integrations).
  - `runtimeOnly`: Needed at runtime but not for compiling (e.g., logging backends, runtime drivers).

---

## § 3 · Minecraft Loom & Quilt Loom Specifics

Minecraft modding with Loom (`org.quiltmc.loom` / `fabric-loom`) introduces specialized lifecycles and tasks:

### 3.1 Loom Configuration Block
```kotlin
loom {
    splitEnvironmentSourceSets()

    mods {
        register("stellar_tweak") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }

    runs {
        named("client") {
            client()
            configName = "Quilt Client"
            ideConfigGenerated(true)
            runDir("run/client")
        }
        named("server") {
            server()
            configName = "Quilt Server"
            ideConfigGenerated(true)
            runDir("run/server")
        }
    }
}
```

### 3.2 Remapping Tasks
- `remapJar`: Transforms named/intermediary mappings to obfuscated or target distribution mappings.
- `remapSourcesJar`: Ensures source JARs map correctly for IDE debugging and consumer reference.
- **Jar-in-Jar (`include`):** Bundles internal libraries inside the mod JAR:
  ```kotlin
  dependencies {
      include(project(":stellar-core"))
  }
  ```

### 3.3 Common Loom Commands
```bash
# Generate decompiled Minecraft sources with Quiltflower for IDE navigation
./gradlew genSourcesWithQuiltflower

# Run client or dedicated server test instances
./gradlew runClient
./gradlew runServer

# Build all remapped artifacts
./gradlew remapJar
```

---

## § 4 · Build Cache & Performance Optimization

### 4.1 `gradle.properties` Performance Tuning
Ensure `gradle.properties` enables parallel execution, daemon caching, and sufficient JVM memory:
```properties
org.gradle.jvmargs=-Xmx4G -XX:+UseParallelGC
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=false # check compatibility with Loom before enabling
kotlin.incremental=true
kotlin.incremental.useClasspathSnapshot=true
```

### 4.2 Avoiding Common Slowdowns
- Avoid dynamic dependency versions (e.g. `2.+` or `latest.release`), which force network checks.
- Do not perform I/O, file scanning, or network requests directly in build script configuration blocks.
- Ensure task inputs and outputs (`@Input`, `@OutputFile`, `@InputFiles`) are annotated so Gradle can mark tasks `UP-TO-DATE`.

---

## § 5 · Quality Tooling: Detekt & Testing Setup

### 5.1 Multi-Module Detekt Integration
Configure Detekt across all projects with custom fallback logic:
```kotlin
allprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")

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
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
        }
    }
}
```

### 5.2 JUnit Platform / Kotest Integration
Ensure test tasks activate JUnit platform so Kotest specs execute properly:
```kotlin
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
}
```

---

## § 6 · Gradle Troubleshooting & Diagnostics

| Symptom | Diagnostic Command | Typical Solution |
|---|---|---|
| Dependency version conflict | `./gradlew :stellar-lang:dependencies --configuration compileClasspath` | Use `strictly`, force resolution, or exclude transitive module |
| Slow build times | `./gradlew build --profile` or `--scan` | Inspect configuration vs execution duration; parallelize tasks |
| Stale Loom mappings | `./gradlew --refresh-dependencies clean` | Clear corrupt Gradle cache or update mapping versions in `gradle.properties` |
| Daemon memory leak / stuck lock | `./gradlew --stop` | Kill daemon processes and restart fresh |
| Detekt failure | `./gradlew detekt` | Read HTML report at `build/reports/detekt/detekt.html` |
