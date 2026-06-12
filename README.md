# CHRONICA

Living World Simulation layer for Minecraft Java 1.21.1 on NeoForge 21.1.x.

## Current implementation

This repository is initialized as a Java 21 NeoForge mod project with:

- `ChronicaWorldData` SavedData persistence root
- Civilization, settlement, NPC-memory, diplomacy, trade route, history, quest, reputation and territory domain models
- Budgeted server-thread simulation manager
- Civilization, economy, diplomacy, trade route and quest simulators
- Deterministic world initialization and compressed pre-history
- Name generation with three phoneme families
- Procedural history book page writer
- Basic `chronica:civ_npc` entity, entity attributes, goals and interaction
- Block, item, entity and network payload registration
- `/chronica` command tree
- `chronica-common.toml` with all planned config fields
- JUnit 5 unit-test stubs for the main systems

## Build

```bash
./gradlew build
```

The first NeoForge build can take time because Gradle has to resolve the Minecraft/NeoForge toolchain.

## Commands

```text
/chronica info
/chronica civs
/chronica civ <name>
/chronica reputation
/chronica history [civ]
/chronica quests
/chronica map
/chronica reload
/chronica simulate <years>
/chronica debug territory
/chronica spawn_caravan <civA> <civB>
/chronica force_war <civA> <civB>
```

## Notes

The current code is an implementation-ready MVP backbone. Client GUI rendering, real settlement structure injection templates, real caravan entity model/pathing, MineColonies/Create/Xaero compat adapters and Fabric v2 port are intentionally left as the next development layer.
