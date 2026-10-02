---
name: minecraft-mixin-expert
kind: workflow
version: 1.0.0
tags:
  - domain: minecraft
  - subtype: mixin
  - level: expert
description: "Expert Spongeian Mixin and MixinExtras engineering skill for Minecraft modding. Covers injection point selection (HEAD, RETURN, INVOKE, TAIL), MixinExtras (@WrapOperation, @ModifyExpressionValue, @Local, @Share), conflict avoidance (eliminating @Redirect and @Overwrite), target descriptors, non-Minecraft class remapping (remap = false), Kotlin-specific Mixin nuances, priority management, and crash diagnostics. Use when: writing Mixins, modifying vanilla or mod classes, resolving Mixin conflict crashes, or optimizing bytecode injections."
license: MIT
metadata:
  author: Antigravity
---

# Minecraft Mixin & MixinExtras Engineering

## One-Liner
Write clean, non-destructive, conflict-free, and maintainable bytecode transformations for Minecraft using Spongeian Mixins and MixinExtras.

---

## § 1 · Core Philosophy: Minimizing Conflict Surface

Mixin conflicts occur when multiple mods attempt to modify the same vanilla or third-party method. The more "holistic" an injection is, the higher its conflict surface.

### 1.1 The Conflict Hierarchy (Safest to Most Destructive)

| Injector | Semantics & Impact | Conflict Risk |
|---|---|---|
| `@Inject` (`HEAD`, `RETURN`, `TAIL`) | Inserts code without replacing existing invocations. Multiple injections coexist cleanly. | **Minimal** |
| `@ModifyVariable` | Modifies a single method parameter or local variable. | **Low** |
| `@ModifyExpressionValue` *(MixinExtras)* | Modifies the returned value of a method call or field read without replacing the call itself. Chainable across multiple mods. | **Low** |
| `@WrapOperation` *(MixinExtras)* | Wraps an operation, allowing custom code before/after while calling `original.call(...)`. Multiple `@WrapOperation`s nest safely. | **Low / Moderate** |
| `@WrapWithCondition` *(MixinExtras)* | Conditionally cancels an invocation without replacing it. | **Low / Moderate** |
| `@Redirect` | **Completely takes over a method call.** If two mods `@Redirect` the same call point, the game will crash during mixin application. | **Extreme** (Avoid!) |
| `@Overwrite` | **Completely replaces an entire method body.** Breaks all other mods targeting that method. | **Fatal** (Strictly Forbidden) |

> **Rule of Thumb:**
> - Never use `@Overwrite`. Use `@Inject(at = @At("HEAD"), cancellable = true)` or `@WrapOperation`.
> - Never use `@Redirect`. Replace it with `@WrapOperation` or `@ModifyExpressionValue`.

---

## § 2 · MixinExtras Patterns (Modern Best Practices)

Fabric Loader 0.15+ bundles **MixinExtras** natively (`io.github.llamalad7:mixinextras`). Always prefer MixinExtras over legacy Mixin workarounds.

### 2.1 `@WrapOperation` (Replacing `@Redirect`)
Wrap an existing call to add pre/post logic or conditionally call original:
```java
@WrapOperation(
    method = "render",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(...)I")
)
private int wrapDrawString(GuiGraphics instance, Font font, Component text, int x, int y, int color, Operation<Integer> original) {
    if (shouldSuppressText()) {
        return 0;
    }
    // Modifies parameter or executes logic around the original call
    return original.call(instance, font, modifyText(text), x, y, color);
}
```

### 2.2 `@ModifyExpressionValue` (Intercepting Return Values)
Safely alter the result of an expression without touching the method execution:
```java
@ModifyExpressionValue(
    method = "tickMovement",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSprinting()Z")
)
private boolean forceSprintCondition(boolean original) {
    return original || shouldAutoSprint();
}
```

### 2.3 `@Local` (Capturing Local Variables Cleanly)
Avoid fragile `@Inject(locals = LocalCapture.CAPTURE_FAILHARD)`. Use `@Local`:
```java
@Inject(
    method = "openSignEditor",
    at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(...)V")
)
private void captureSignBlockEntity(SignBlockEntity sign, boolean front, CallbackInfo ci, @Local SignText signText) {
    TranslationService.registerSign(sign, signText);
}
```

---

## § 3 · Critical Compatibility Rules

### 3.1 `remap = false` on Non-Minecraft Classes
Minecraft classes undergo deobfuscation/remapping via refmaps. Third-party or JVM classes (`java.*`, `org.joml.*`, `com.google.*`, `io.netty.*`) do NOT exist in the Minecraft refmap:
```java
// Correct: remap = false for non-Minecraft targets
@At(
    value = "INVOKE",
    target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
    remap = false
)
```
*Omitting `remap = false` on foreign classes causes `Target not found` refmap crashes in production builds.*

### 3.2 Explicit Method Descriptors
Avoid ambiguous method names that could match multiple overloads:
```java
// Good: Fully qualified method descriptor avoids overload ambiguity
@Inject(
    method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
    at = @At("HEAD")
)
private void onRender(GuiGraphics graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) { ... }
```

### 3.3 Name Spacing with `@Unique`
All non-injected helper methods and fields in a Mixin class become members of the target class bytecode. Avoid naming collisions:
```java
@Unique
private static final Logger stellar$LOGGER = LogUtils.getLogger();

@Unique
private boolean stellar$isCustomHandlingActive = false;
```

---

## § 4 · Kotlin-Specific Mixin Guidelines

When writing Mixins in Kotlin:

1. **Backing Fields & Accessors:** Kotlin generates private fields with public getter/setter methods. When targeting Kotlin code, inspect decompiled bytecode to confirm whether you need to target `getMyField()` or `myField`.
2. **Synthetic Names & Mangling:** Functions marked `internal` in Kotlin have mangled bytecode names (e.g. `doThing$stellar_core`). Target the mangled name or use `@JvmName` on the declaration.
3. **Companion Objects:** Do not put Mixins inside companion objects. Mixins must be top-level classes or standalone `object` singletons.
4. **Lateinit Variables:** In Kotlin Mixin classes, prefer nullable types (`var thing: Target? = null`) over `lateinit var`, especially for `@Shadow` properties.

---

## § 5 · Configuration & Diagnostics

### 5.1 `modid.mixins.json` Essentials
```json
{
  "required": true,
  "package": "com.stellar.lang.mixin",
  "compatibilityLevel": "JAVA_21",
  "injectors": {
    "defaultRequire": 1,
    "maxShiftBy": 3
  },
  "client": [
    "AbstractSignEditScreenMixin",
    "AbstractSignRendererMixin"
  ]
}
```
*Setting `defaultRequire: 1` ensures failures happen immediately at startup rather than silently failing to inject.*

### 5.2 Debugging Bytecode Output
Add JVM flag to run configurations to export transformed class bytecode:
```bash
-Dmixin.debug.export=true
```
Transformed classes are saved to `.mixin.out/classes/`, allowing direct inspection of decompiled output.
