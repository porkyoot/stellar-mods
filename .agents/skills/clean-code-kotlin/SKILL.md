---
name: clean-code-kotlin
kind: workflow
version: 1.0.0
tags:
  - domain: software
  - subtype: kotlin
  - level: expert
description: "Expert Kotlin craftsmanship skill focusing on writing clean, idiomatic, high-performance, and maintainable Kotlin code for the JVM and Minecraft mods. Enforces Modern Kotlin 2.x (K2) features (value classes, sealed hierarchies, pattern matching, context parameters), zero-allocation hot-path practices (avoiding boxing/lambda allocations in tick loops), Java-Kotlin interop for Mixins (@JvmStatic, @JvmField), Kotest testing, and Detekt static analysis compliance. Use when: writing Kotlin code, refactoring Kotlin classes, optimizing JVM performance, designing APIs, or conducting code reviews."
license: MIT
metadata:
  author: Antigravity
---

# Clean Code Kotlin & Craftsmanship

## One-Liner
Write idiomatic, expressive, maintainable, and high-performance Kotlin code following Modern Kotlin 2.x idioms, zero-allocation hot-path patterns for game loops, robust Java interop, and strict Detekt compliance.

---

## § 1 · Core Architectural & Idiomatic Principles

### 1.1 Expression-Oriented Design
- Prefer expressions over statements (`if`, `when`, and `try` as expressions returning values).
- Prefer single-expression function bodies when the function logic is straightforward and self-explanatory:
  ```kotlin
  fun canTranslate(component: Component): Boolean =
      component.contents !is TranslatableContents && config.isEnabled
  ```
- Use `when` without an argument as a cleaner replacement for cascading `if-else-if` chains:
  ```kotlin
  when {
      packet.isCancelled -> return
      packet.hasPermission(ADMIN) -> handleAdminAction(packet)
      else -> handleUserAction(packet)
  }
  ```

### 1.2 Immutability by Default
- Prefer `val` over `var` universally. Only use `var` for localized accumulators or well-encapsulated mutable state.
- Prefer read-only collection interfaces (`List`, `Map`, `Set`) over mutable variants (`MutableList`).
- If an internal collection must be mutable, expose it as read-only via backing properties:
  ```kotlin
  private val _pendingTranslations = mutableMapOf<UUID, TranslationJob>()
  val pendingTranslations: Map<UUID, TranslationJob> get() = _pendingTranslations
  ```

### 1.3 Scope Functions with Clear Intent
Use scope functions strictly according to their semantic purpose to avoid unreadable nesting ("scope function soup"):
- **`let`**: Null-checks (`item?.let { process(it) }`) or transforming a value within a local scope.
- **`apply`**: Object configuration/initialization returning the receiver (`Config().apply { timeout = 5000 }`).
- **`also`**: Side effects that do not alter the receiver (logging, caching, debugging).
- **`run`**: Object configuration and computing a result, or scoping statements.
- **`takeIf` / `takeUnless`**: Filtering values based on a predicate instead of explicit `if`.

---

## § 2 · Modern Kotlin Idioms (Kotlin 2.x / K2)

### 2.1 Value Classes for Zero-Allocation Type Safety
- Use `@JvmInline value class` for domain identifiers, wrappers, and units to achieve strong type safety without runtime heap allocation:
  ```kotlin
  @JvmInline
  value class MessageId(val value: Long)

  @JvmInline
  value class ScoreboardKey(val raw: String)
  ```

### 2.2 Sealed Hierarchies & Exhaustive Pattern Matching
- Model finite domain states, network packets, and events using `sealed interface` and `data object`:
  ```kotlin
  sealed interface TranslationStatus {
      data object Idle : TranslationStatus
      data class InProgress(val progress: Float) : TranslationStatus
      data class Completed(val result: String, val latencyMs: Long) : TranslationStatus
      data class Failed(val error: Throwable) : TranslationStatus
  }

  fun renderStatus(status: TranslationStatus): Component = when (status) {
      is TranslationStatus.Idle -> Component.empty()
      is TranslationStatus.InProgress -> Component.literal("Translating (${(status.progress * 100).toInt()}%)...")
      is TranslationStatus.Completed -> Component.literal(status.result).withStyle(ChatFormatting.GREEN)
      is TranslationStatus.Failed -> Component.literal("Error: ${status.error.message}").withStyle(ChatFormatting.RED)
  }
  ```
- **Never** add an arbitrary `else` branch to a `when` expression over a sealed hierarchy; let the compiler enforce exhaustiveness.

### 2.3 Smart Casting & Safe Calls
- Trust and leverage Kotlin's smart casting. Do not cast with `as` when `is` checks or smart casts apply.
- Avoid the non-null assertion operator (`!!`). Use `?: error(...)`, `?: return`, or explicit validation checks instead.

---

## § 3 · Zero-Allocation Hot-Path Practices (Minecraft & Tick Loops)

In Minecraft modding, methods running 20 times per second per entity/block (or every frame in rendering) must avoid garbage collection churn:

### 3.1 Lambda & Iterator Allocation
- Standard collection operations (`filter`, `map`, `groupBy`) allocate intermediate lists and iterator instances.
- In **hot paths** (e.g. tick loops, packet handlers, mixin injection points, render callbacks):
  - Use primitive indexed `for (i in 0 until size)` over `list.forEach { }` or `for (item in list)` when indexing is cheap.
  - Avoid capturing lambdas in tick methods (lambdas that capture local variables allocate a new instance on every invocation).
  - Use `Sequence` for multi-stage transformations only on large, cold collections; in hot paths, prefer direct loops into pre-sized arrays/collections.

### 3.2 Primitive Specialization
- Use primitive arrays (`IntArray`, `FloatArray`, `LongArray`) instead of generic object arrays (`Array<Int>`, `List<Int>`) to avoid auto-boxing (`java.lang.Integer`).
- When storing coordinates or vectors, use primitive packing (e.g. packing x, y, z into a single `Long`) or `@JvmInline value class`.

### 3.3 Lazy Initialization Safety
- `by lazy` uses `LazyThreadSafetyMode.SYNCHRONIZED` by default, which introduces volatile reads and lock overhead.
- If thread safety is guaranteed or the property is initialized on a single thread (e.g., client render loop or dedicated server thread), use:
  ```kotlin
  val formatter by lazy(LazyThreadSafetyMode.NONE) { SimpleDateFormat("HH:mm:ss") }
  ```

---

## § 4 · Java Interoperability & Minecraft Mixins

### 4.1 JVM Annotations
- Use `@JvmStatic` in companion objects or singleton `object`s when methods must be callable as standard static methods from Java (e.g. Fabric entrypoints, Mixin targets).
- Use `@JvmField` for constants and mutable fields where getter/setter method invocation overhead should be eliminated.
- Use `@JvmOverloads` when exposing functions with default parameters to Java callers.

### 4.2 Mixin Compatibility with Kotlin
- **Backing fields:** Remember that Kotlin properties create a private field with getter/setter. In Spongeian Mixins (`@Shadow`, `@Inject`, `@Redirect`), target the exact method or field signature generated by `kotlinc`.
- **Synthetic name mangling:** Functions marked `internal` have name mangling in bytecode (e.g. `foo$stellar_lang`). When writing Java Mixins targeting Kotlin code, use `@JvmName` to specify a clean bytecode name:
  ```kotlin
  @JvmName("broadcastRuleUpdate")
  internal fun broadcastRuleUpdate(rule: Rule) { ... }
  ```
- **Lateinit properties:** Avoid using `lateinit var` for primitive types (unsupported in Kotlin). Use standard nullability (`var field: Int? = null`) or explicit sentinel values (`-1`).

---

## § 5 · Error Handling & Detekt Static Analysis

### 5.1 Result and Functional Error Handling
- Use `runCatching { }` and `Result<T>` for operations that can fail without throwing fatal exceptions:
  ```kotlin
  fun fetchTranslation(text: String): Result<String> = runCatching {
      httpClient.post(endpoint) { setBody(text) }.body()
  }
  ```
- Use `onSuccess` and `onFailure` or `recover` to handle results cleanly.

### 5.2 Detekt Rule Compliance
Adhere to the project's Detekt configuration (`config/detekt/detekt.yml`):
- **Naming Conventions:** Class names in `PascalCase`, functions and variables in `camelCase`, constants in `UPPER_SNAKE_CASE`.
- **Complexity:** Keep cyclomatic complexity under the configured threshold (default: 15). Split large functions into cohesive private helpers.
- **Magic Numbers:** Extract raw numbers into named constants (`private const val DEFAULT_TIMEOUT_MS = 3000L`).
- **Suppression:** Only suppress Detekt rules when technically unavoidable (e.g. generated Mixin methods, large test files), and always specify the rule explicitly:
  ```kotlin
  @Suppress("LongMethod", "ComplexCondition")
  ```

---

## § 6 · Testing with Kotest

The Stellar ecosystem standardizes on **Kotest** for Kotlin unit and integration tests:

### 6.1 Spec Styles
Prefer `FunSpec` for straightforward tests and `DescribeSpec` for nested BDD-style context:
```kotlin
class ChatTranslationManagerSpec : FunSpec({
    beforeEach {
        TranslationService.clearCache()
    }

    test("should translate outgoing message when translation is enabled") {
        val original = Component.literal("Hello")
        val translated = ChatTranslationManager.translate(original)
        
        translated.string shouldBe "Bonjour"
    }

    test("should ignore message when target language matches source") {
        val original = Component.literal("Bonjour")
        val result = ChatTranslationManager.translate(original)
        
        result shouldBe original
    }
})
```

### 6.2 Kotest Assertions & Matchers
- Use fluent matchers: `result shouldBe expected`, `result shouldNotBe null`, `text shouldContain "needle"`.
- Use `shouldThrow<IllegalArgumentException> { ... }` for expected exceptions.
- Use `assertSoftly { ... }` when verifying multiple properties simultaneously so that all failures are reported together.

---

## § 7 · Code Quality & Review Checklist

- [ ] Are variables `val` unless mutation is strictly required?
- [ ] Are public collections read-only and backed by private mutable state?
- [ ] Are finite states modeled using `sealed interface` and `data object`?
- [ ] Are value classes (`@JvmInline value class`) used for domain IDs and keys?
- [ ] Are hot paths (tick loops, render calls) free of lambda allocations, iterators, and auto-boxing?
- [ ] Are `@JvmStatic` / `@JvmField` applied correctly for Java and Mixin interop?
- [ ] Does the code pass Detekt checks (`./gradlew detekt`) without new warnings?
- [ ] Are tests written using Kotest with clear behavioral specifications and fluent matchers?
