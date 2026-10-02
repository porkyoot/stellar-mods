---
name: minecraft-ui-rendering
kind: workflow
version: 1.0.0
tags:
  - domain: minecraft
  - subtype: ui-rendering
  - level: expert
description: "Modern Minecraft 1.21+ Screen and UI rendering skill. Covers GuiGraphics / DrawContext operations (drawing textures, sprites, text, gradients, tooltips, scissor test), custom Widgets (AbstractWidget, Button, EditBox), Screen lifecycle and event handling, matrix stack PoseStack transformations, and rich text formatting (Component, Style, ClickEvent, HoverEvent). Use when: creating custom screens, rendering HUD elements, floating widgets, interactive badges, or tooltips."
license: MIT
metadata:
  author: Antigravity
---

# Minecraft UI & Screen Rendering (1.21+)

## One-Liner
Build polished, responsive, and performant custom Minecraft GUIs, HUD overlays, and floating widgets using `GuiGraphics` (`DrawContext`), the Screen/Widget hierarchy, and rich text `Component` formatting.

---

## § 1 · Rendering Fundamentals: `GuiGraphics` / `DrawContext`

In Minecraft 1.20+, all 2D GUI and HUD rendering goes through `GuiGraphics` (Mojang mappings) or `DrawContext` (Yarn mappings).

### 1.1 Essential Drawing Operations

```kotlin
// Draw solid colored rectangle (ARGB color format)
graphics.fill(x1, y1, x2, y2, 0x80000000.toInt()) // Semi-transparent black background

// Draw border / outline
graphics.renderOutline(x, y, width, height, 0xFFFFFFFF.toInt())

// Draw centered formatted string
graphics.drawCenteredString(font, Component.literal("Title"), centerX, y, 0xFFFFFF)

// Draw left-aligned string with drop shadow
graphics.drawString(font, Component.literal("Status: OK"), x, y, 0x55FF55, true)

// Draw vanilla sprite from texture atlas
graphics.blitSprite(RenderPipelines.GUI, SPRITE_ID, x, y, width, height)

// Render interactive tooltip at cursor
graphics.renderTooltip(font, listOf(Component.literal("Translate [T]")), mouseX, mouseY)
```

### 1.2 Scissor Clipping (Scrollable Areas)
Use scissor test to clip rendering within a rectangular bounding box (e.g. scroll lists, translation text containers):
```kotlin
graphics.enableScissor(minX, minY, maxX, maxY)
try {
    // Render content that may overflow bounds
    renderScrollableContent(graphics)
} finally {
    graphics.disableScissor()
}
```

### 1.3 PoseStack Transformations
For scaling, rotation, and translating HUD elements:
```kotlin
val pose = graphics.pose()
pose.pushPose()
try {
    pose.translate(originX.toFloat(), originY.toFloat(), 0f)
    pose.scale(0.85f, 0.85f, 1f) // 85% scale for compact floating badge
    
    graphics.drawString(font, badgeText, 0, 0, 0xFFD700)
} finally {
    pose.popPose()
}
```

---

## § 2 · Screen & Widget Architecture

### 2.1 Custom Screen (`Screen`)
```kotlin
class StellarConfigScreen(parent: Screen?) : Screen(Component.translatable("stellar.config.title")) {

    override fun init() {
        super.init()

        val buttonWidth = 150
        val buttonHeight = 20
        val startX = (width - buttonWidth) / 2
        val startY = height / 4

        // Add standard button widget
        addRenderableWidget(
            Button.builder(Component.literal("Toggle Translation")) { button ->
                toggleFeature()
            }
            .bounds(startX, startY, buttonWidth, buttonHeight)
            .tooltip(Tooltip.create(Component.literal("Enables in-game sign and chat translation.")))
            .build()
        )
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        // Draw background darkening
        renderBackground(graphics, mouseX, mouseY, delta)
        
        // Draw custom overlays or headers
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF)
        
        // Render child widgets (buttons, textboxes)
        super.render(graphics, mouseX, mouseY, delta)
    }
}
```

### 2.2 Custom Floating Widget (`AbstractWidget`)
```kotlin
class FloatingTranslationBadge(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val onBadgeClicked: () -> Unit
) : AbstractWidget(x, y, width, height, Component.literal("[T]")) {

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val isHovered = isHoveredOrFocused
        val bgColor = if (isHovered) 0xAA2B2B2B.toInt() else 0x66000000.toInt()
        val textColor = if (isHovered) 0xFFD700 else 0xAAAAAA

        // Render badge box
        graphics.fill(x, y, x + width, y + height, bgColor)
        
        // Render centered [T] text
        val font = Minecraft.getInstance().font
        graphics.drawCenteredString(font, message, x + width / 2, y + (height - 8) / 2, textColor)
    }

    override fun onClick(mouseX: Double, mouseY: Double) {
        onBadgeClicked()
    }

    override fun updateWidgetNarration(narrationElementOutput: NarrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput)
    }
}
```

---

## § 3 · Text & Component Formatting

Minecraft's `Component` hierarchy is tree-structured:

### 3.1 Styling Text Components
```kotlin
val interactiveText = Component.literal("[Translate]")
    .withStyle { style ->
        style
            .withColor(TextColor.fromRgb(0x40E0D0)) // Turquoise
            .withUnderlined(true)
            .withClickEvent(ClickEvent.runCommand("/stellar translate last"))
            .withHoverEvent(HoverEvent.showText(Component.literal("Click to translate incoming chat")))
    }
```

### 3.2 Translatable Components & Placeholders
Always use translatable keys for user-facing UI messages to support resource pack localization (`assets/<modid>/lang/en_us.json`):
```kotlin
Component.translatable("stellar.lang.detected_source", sourceLanguage, latencyMs)
```

---

## § 4 · Performance & Memory Rules in GUI Loops

1. **Avoid String Concatenation & Component Allocation in `render()`:**
   - Pre-compute formatted text or cache `Component` instances.
   - Calling `Component.literal("..." + x)` inside `render()` triggers heap allocations 60+ times per second.
2. **Dispose of Textures & Buffers:**
   - Always free dynamic native textures (e.g. downloaded avatars or generated images) when screens close.
3. **State Isolation:**
   - Never mutate game logic or send network packets from purely visual animation calculations.
