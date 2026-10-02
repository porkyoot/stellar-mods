---
name: minecraft-networking
kind: workflow
version: 1.0.0
tags:
  - domain: minecraft
  - subtype: networking
  - level: expert
description: "Modern Minecraft 1.20.5+ and 1.21+ network packet architecture skill for Fabric and Quilt. Covers CustomPacketPayload, PayloadTypeRegistry (playC2S, playS2C, configuration), StreamCodec definitions (composite, ByteBufCodecs), ClientPlayNetworking, ServerPlayNetworking, off-thread decoding vs main-thread execution, and payload validation security. Use when: implementing network packets, client-server communication, syncing data, or debugging packet deserialization."
license: MIT
metadata:
  author: Antigravity
---

# Minecraft Modern Networking (1.20.5+ / 1.21+)

## One-Liner
Implement robust, type-safe, and asynchronous client-server network protocols in Minecraft 1.21+ using `CustomPacketPayload`, `StreamCodec`, and Fabric Networking API.

---

## § 1 · Network Architecture Overview

Starting in Minecraft 1.20.5 and continuing through 1.21+, the legacy raw `PacketByteBuf` networking system was replaced by type-safe payload records implementing `CustomPacketPayload` and serialized via `StreamCodec`.

### 1.1 Key Concepts

| Component | Responsibility |
|---|---|
| `CustomPacketPayload.Type<T>` | Represents the canonical packet identifier (`ResourceLocation` / `Identifier`). |
| `StreamCodec<B, T>` | Defines how payload objects are read from and written to the packet buffer (`FriendlyByteBuf` / `RegistryFriendlyByteBuf`). |
| `PayloadTypeRegistry` | Central registry for registering packet types across phases (`playC2S`, `playS2C`, `configurationC2S`, etc.). |
| `ServerPlayNetworking` / `ClientPlayNetworking` | Entrypoints for sending payloads and registering packet listeners. |

---

## § 2 · Creating a Custom Payload

### 2.1 Payload Definition (Kotlin Example)

```kotlin
package com.stellar.law.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

data class RuleSyncPayload(
    val ruleId: String,
    val enabled: Boolean,
    val severityLevel: Int,
    val targetUuid: UUID
) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<RuleSyncPayload> = TYPE

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("stellar_law", "rule_sync")
        val TYPE: CustomPacketPayload.Type<RuleSyncPayload> = CustomPacketPayload.Type(ID)

        val CODEC: StreamCodec<RegistryFriendlyByteBuf, RuleSyncPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RuleSyncPayload::ruleId,
            ByteBufCodecs.BOOL, RuleSyncPayload::enabled,
            ByteBufCodecs.VAR_INT, RuleSyncPayload::severityLevel,
            StreamCodec.unit(UUID.randomUUID()), // Or custom UUID codec
            ::RuleSyncPayload
        )
    }
}
```

### 2.2 Reusable Codecs (`ByteBufCodecs`)
- `ByteBufCodecs.STRING_UTF8`: Strings with length prefixes.
- `ByteBufCodecs.VAR_INT`: Variable-length integers (efficient for small numbers).
- `ByteBufCodecs.BOOL`: Single byte booleans.
- `ByteBufCodecs.FLOAT` / `DOUBLE`: Floating point numbers.
- `ByteBufCodecs.fromCodec(Codec<T>)`: Bridges standard DataFixerUpper/Mojang `Codec<T>` into a `StreamCodec`.

---

## § 3 · Registration & Handlers

Registration **must** occur during early mod initialization (e.g. `ModInitializer` on Fabric/Quilt):

### 3.1 Registering Payload Types (Common)
```kotlin
fun registerPackets() {
    // S2C: Server to Client (e.g. broadcasting rules)
    PayloadTypeRegistry.playS2C().register(RuleSyncPayload.TYPE, RuleSyncPayload.CODEC)

    // C2S: Client to Server (e.g. client requesting exemption)
    PayloadTypeRegistry.playC2S().register(ClientActionPayload.TYPE, ClientActionPayload.CODEC)
}
```

### 3.2 Handling Packets & Thread Safety
Network packet deserialization occurs on the Netty I/O worker threads.
**You must NEVER mutate game state, spawn entities, or update player positions directly on the Netty thread.** Always delegate to the game loop:

#### Client-Side Handler:
```kotlin
ClientPlayNetworking.registerGlobalReceiver(RuleSyncPayload.TYPE) { payload, context ->
    val client = context.client()
    
    // Switch from Netty thread to main client render/tick thread
    client.execute {
        StellarLawClient.applyRule(payload.ruleId, payload.enabled)
    }
}
```

#### Server-Side Handler:
```kotlin
ServerPlayNetworking.registerGlobalReceiver(ClientActionPayload.TYPE) { payload, context ->
    val server = context.server()
    val player = context.player()

    // Switch from Netty thread to main server tick thread
    server.execute {
        StellarOpsServer.processAction(player, payload)
    }
}
```

---

## § 4 · Sending Packets

### 4.1 From Client to Server
```kotlin
// Check if the server supports the packet before sending:
if (ClientPlayNetworking.canSend(ClientActionPayload.TYPE)) {
    ClientPlayNetworking.send(ClientActionPayload("auto_mlg_trigger"))
}
```

### 4.2 From Server to Client
```kotlin
// Send to a single player
ServerPlayNetworking.send(player, RuleSyncPayload("no_xray", true, 2, player.uuid))

// Broadcast to all players in a world/server
server.playerList.players.forEach { onlinePlayer ->
    ServerPlayNetworking.send(onlinePlayer, payload)
}
```

---

## § 5 · Security & Validation Checklist

1. **Verify Sender Authority:** Never trust C2S payloads blindly. Validate that the sender (`context.player()`) actually has permission or is in the correct dimension/range.
2. **Buffer Limits:** Avoid unbounded collections in payload codecs. Always specify maximum limits for strings and lists:
   ```kotlin
   ByteBufCodecs.stringUtf8(256) // Disallows oversized string packets
   ```
3. **Null & Coordinate Validation:** Validate coordinates (e.g. check for `NaN`, infinite values, or coordinates outside world borders) before applying actions.
