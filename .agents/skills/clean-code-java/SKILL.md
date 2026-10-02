---
name: clean-code-java
kind: workflow
version: 1.0.0
tags:
  - domain: software
  - subtype: java
  - level: expert
description: "Expert Java craftsmanship skill focusing on writing clean, idiomatic, high-performance, and maintainable Java code. Enforces Modern Java 21 features (records, sealed classes, pattern matching), Effective Java design patterns, SOLID principles, zero-allocation hot-path practices, concurrency safety, and fluent testing with AssertJ and ArchUnit. Use when: writing Java code, refactoring Java classes, optimizing Java performance, designing Java APIs, or conducting Java code reviews."
license: MIT
metadata:
  author: theNeoAI <lucas_hsueh@hotmail.com>
---

# Clean Code Java & Software Craftsmanship

## One-Liner
Write idiomatic, robust, maintainable, and high-performance Java code following Effective Java principles, modern Java 21 idioms, and clean architecture.

---

## § 1 · Core Architectural & Design Principles

### 1.1 SOLID & Clean Architecture
- **Single Responsibility (SRP):** Each class and method should have exactly one reason to change. Separate domain computation from I/O, networking, and state management.
- **Open / Closed (OCP):** Prefer composition and polymorphism over modifying existing branched switch statements. Use strategy patterns, factory registries, or sealed hierarchies.
- **Liskov Substitution (LSP):** Subtypes must be substitutable for their base types without altering program correctness. Never throw `UnsupportedOperationException` for inherited methods.
- **Interface Segregation (ISP):** Prefer fine-grained, cohesive interfaces over monolithic "kitchen sink" interfaces. Clients should never be forced to depend on methods they do not use.
- **Dependency Inversion (DIP):** Depend on abstractions (interfaces, records, abstract models), not concrete implementations. Pass dependencies through constructors.

### 1.2 Deep Modules vs Shallow Wrappers
- Prefer **deep modules** (modules with simple, intuitive interfaces that encapsulate rich, complex internal functionality) over **shallow wrappers** (classes that simply pass calls through without adding meaningful abstraction or state management).
- Minimize information leakage across module boundaries. Keep internal state private and immutable wherever possible.

---

## § 2 · Modern Java 21 Idioms & Best Practices

### 2.1 Records for Data Carriers
- Use `record` for immutable data transfer objects, payloads, event notifications, and calculation results.
- Leverage compact constructors for validation and normalization:
  ```java
  public record Vector3i(int x, int y, int z) {
      public Vector3i {
          // compact constructor: validation without re-assigning fields
      }
      public Vector3i add(int dx, int dy, int dz) {
          return new Vector3i(x + dx, y + dy, z + dz);
      }
  }
  ```

### 2.2 Sealed Interfaces & Exhaustive Pattern Matching
- Model finite domain states and command hierarchies using `sealed interface` / `sealed class` and `permits`.
- Use pattern matching in `switch` expressions to ensure exhaustive compile-time checking without needing default catch-alls:
  ```java
  public sealed interface ActionStatus permits Pending, Executing, Succeeded, Failed {}

  public String formatStatus(ActionStatus status) {
      return switch (status) {
          case Pending p -> "Waiting in queue: " + p.position();
          case Executing e -> "Executing tick " + e.elapsedTicks();
          case Succeeded s -> "Completed in " + s.durationMs() + "ms";
          case Failed f -> "Failed: " + f.reason();
      };
  }
  ```

### 2.3 Optionals & Null-Safety
- **Never use `null` as a business value.** Use `Optional<T>` for return types when a value may legitimately be absent.
- **Do not use `Optional` for fields, method parameters, or collection elements.**
- Avoid `optional.get()` without prior checks. Prefer functional combinators: `map()`, `flatMap()`, `filter()`, `orElse()`, `orElseGet()`, `ifPresent()`.
- Annotate APIs clearly with `@Nullable` and `@NotNull` / `@NonNull` to enable static analysis.

### 2.4 Streams vs Imperative Loops
- Use the **Streams API** for declarative transformations, filtering, and aggregation over collections.
- Avoid side-effects inside stream operations (e.g. mutating external state in `forEach` or `map`).
- In **hot paths** (e.g. per-tick loops, physics steps, high-frequency renderers), prefer standard indexed `for (int i = 0; i < size; i++)` or enhanced for-loops over intermediate Stream allocations to eliminate GC pressure.

---

## § 3 · Effective Java Principles (Joshua Bloch)

1. **Static Factory Methods & Builders:**
   - Consider static factory methods (`of()`, `from()`, `empty()`) instead of multiple overloaded constructors.
   - Use the Builder pattern when dealing with constructors having more than 4 optional or configuration parameters.
2. **Immutability by Default:**
   - Make all fields `final` unless state mutation is explicitly required.
   - Return defensive copies or unmodifiable collections (`List.copyOf()`, `Collections.unmodifiableList()`) for mutable internal state.
3. **Equals, HashCode & Comparable:**
   - Always override `hashCode` when overriding `equals`.
   - Ensure `equals` is reflexive, symmetric, transitive, and consistent.
   - Use `Comparator.comparing(...)` and chained combinators (`.thenComparing(...)`) for clean, readable ordering logic.
4. **Defensive Programming & Fail-Fast:**
   - Validate method parameters immediately at entry (`Objects.requireNonNull(arg, "arg must not be null")`).
   - Throw standard exceptions (`IllegalArgumentException`, `IllegalStateException`, `IndexOutOfBoundsException`) with clear, informative messages.
5. **Exceptions & Resource Management:**
   - Always use `try-with-resources` for `AutoCloseable` resources (streams, sockets, files).
   - Never swallow exceptions silently. Log at appropriate levels or wrap in domain-specific exceptions.

---

## § 4 · Concurrency & Thread Safety

- **Understand Thread Boundaries:** Clearly distinguish between main/tick threads, worker thread pools, and async I/O loops.
- **Memory Visibility & Atomicity:**
  - Use `volatile` for single-variable state flags shared across threads without compound operations.
  - Use `AtomicInteger`, `AtomicReference`, or `LongAdder` for lock-free thread-safe mutations.
  - Use concurrent data structures (`ConcurrentHashMap`, `ConcurrentLinkedQueue`, `CopyOnWriteArrayList`) instead of manual synchronization blocks where possible.
- **Avoid Deadlocks & Contention:**
  - Never call foreign/external methods or untrusted callbacks while holding a lock.
  - Keep synchronized sections minimal and focused solely on atomic state transitions.

---

## § 5 · Testing & Quality Governance

### 5.1 Fluent Testing with AssertJ & JUnit 5
- Use descriptive test method names that express intent: `shouldRejectActionWhenQueueIsFull()`.
- Prefer AssertJ fluent assertions for clear failure diagnostics:
  ```java
  assertThat(result)
      .isNotNull()
      .extracting(ExecutionResult::status)
      .isEqualTo(Status.SUCCESS);
  ```
- Use parameterized tests (`@ParameterizedTest`, `@ValueSource`, `@MethodSource`) to cover boundary values and combinatorial edge cases cleanly.

### 5.2 Architectural Testing with ArchUnit
- Enforce package dependencies, layering, and immutability rules through automated test cases:
  ```java
  @ArchTest
  static final ArchRule no_client_code_in_common =
      noClasses().that().resideInAPackage("..common..")
          .should().dependOnClassesThat().resideInAPackage("..client..");
  ```

---

## § 6 · Code Quality & Review Checklist

When writing or reviewing Java code:
- [ ] Is every class and method focused on a single responsibility?
- [ ] Are variables and parameters `final` and collections defensively copied?
- [ ] Are records and sealed hierarchies used where appropriate?
- [ ] Are parameter null-checks and invariants validated at method entry points?
- [ ] Are resources cleaned up with `try-with-resources`?
- [ ] Are hot paths free of unnecessary object allocations and boxing/unboxing?
- [ ] Is public API behavior documented with concise, meaningful Javadoc?
- [ ] Are unit tests behavioral, deterministic, and isolated?
