<div align="center">

# 🌌 Stellar Mod Suite

**A modular, high-performance Minecraft Quilt mod ecosystem for anti-cheat networking, server moderation, and advanced client utilities.**

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2+%20%2F%2026.2-blue.svg)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Quilt-purple.svg)](https://quiltmc.org/)
[![Language](https://img.shields.io/badge/Kotlin-2.4-orange.svg)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-red.svg)](https://adoptium.net/)
[![Linter](https://img.shields.io/badge/Detekt-Passing-green.svg)](https://detekt.dev/)

</div>

---

## 🧭 Modules Overview

The Stellar ecosystem is organized as a multi-project Gradle build. Each module has a focused responsibility and communicates through standardized, decoupled interfaces.

| Module | Type | Icon | Description |
| :--- | :--- | :---: | :--- |
| [**Stellarcore**](stellar-core/) | **Internal Library**<br>*(Fat Jar)* | 🌌 | Central technical core providing common constants, canonical identifiers, unified logging, and shared utilities across all mods. Not distributed standalone. |
| [**Stellarlaw**](stellar-law/) | **Common Protocol**<br>*(Client & Server)* | <img src="stellar-law/src/main/resources/assets/stellar_law/icon.png" width="48" height="48" alt="Stellarlaw" /> | Anti-cheat API and standardized rule broadcast protocol. Enables servers to broadcast permitted features and allows clients to automatically adapt. |
| [**Stellarops**](stellar-ops/) | **Server-Side Mod**<br>*(Dedicated Server)* | <img src="stellar-ops/src/main/resources/assets/stellar_ops/icon.png" width="48" height="48" alt="Stellarops" /> | Powerful server moderation and forensic toolkit featuring explosion/fire attribution (Blame System), advanced X-Ray detection, and AI chat/sign moderation. |
| [**Stellartweak**](stellar-tweak/) | **Client-Side Mod**<br>*(Client Only)* | <img src="stellar-tweak/src/main/resources/assets/stellar_tweak/icon.png" width="48" height="48" alt="Stellartweak" /> | Ultimate client toolkit with smart block placement, geometric shape generation, auto-MLG, auto-parkour, smart inventory sorting, and auto tool/weapon swaps. |

---

## 🏛️ Ecosystem Architecture

```
                             ┌───────────────────────┐
                             │      Stellarcore      │
                             │   (Common Core Lib)   │
                             └───────────┬───────────┘
                                         │ (bundled via fat jar)
                             ┌───────────▼───────────┐
                             │       Stellarlaw      │
                             │  (Anti-Cheat & Rules) │
                             └─────┬───────────┬─────┘
             (optional compileOnly)│           │(optional compileOnly)
                     ┌─────────────┘           └─────────────┐
                     ▼                                       ▼
           ┌───────────────────┐                   ┌───────────────────┐
           │     Stellarops    │                   │    Stellartweak   │
           │  (Server Admin)   │                   │  (Client Tweaks)  │
           │ 100% Server-Side  │                   │  100% Client-Side │
           └───────────────────┘                   └───────────────────┘
```

### Architectural Guardrails
* **Encapsulation:** Subprojects isolate dependencies via specialized [Detekt configurations](config/detekt/).
* **Separation of Sides:** `stellar-ops` cannot access client classes; `stellar-tweak` cannot access dedicated server classes.
* **Loose Coupling:** `stellar-ops` and `stellar-tweak` interact with `stellar-law` as an optional soft dependency (`compileOnly` & `"optional": true` in `quilt.mod.json`), operating seamlessly whether Stellarlaw is installed or omitted.

---

## 🛠️ Development & Building

### Requirements
- **Java Development Kit (JDK) 21** or later
- Linux, macOS, or Windows

### Common Gradle Commands

```bash
# Launch global Minecraft client with all Stellar modules loaded
./gradlew runClient

# Launch global Minecraft dedicated server with all Stellar modules loaded
./gradlew runServer

# Compile and build all project JARs
./gradlew build

# Run all Kotest unit tests across modules
./gradlew test

# Run Detekt static analysis and architectural boundary checks
./gradlew detekt

# Launch headless Mojang GameTest server for validation
./gradlew runGameTestServer
```

### Build Artifacts
Upon running `./gradlew build`, the production JARs are generated in:
- `stellar-law/build/libs/stellar-law-1.0.0.jar`
- `stellar-ops/build/libs/stellar-ops-1.0.0.jar`
- `stellar-tweak/build/libs/stellar-tweak-1.0.0.jar`

*(Note: `stellar-core` is automatically shaded inside each of the above JARs).*
