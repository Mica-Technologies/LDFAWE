# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git Guidelines

- **Create commits** when work reaches a logical checkpoint — keep them descriptive and well-organized.
- **Never push** to any remote. The user will review and push manually.
- Use conventional, descriptive commit messages that explain *why*, not just *what*.
- Group related changes into single commits; don't lump unrelated work together.

## Build Commands

```bash
# Setup workspace (required first time, or after clean)
./gradlew setupDecompWorkspace

# Build the mod
./gradlew build

# Run Minecraft client in dev
./gradlew runClient

# Run Minecraft server in dev
./gradlew runServer

# Clean build artifacts
./gradlew clean

# Run tests (JUnit 5)
./gradlew test
```

**Requirements:** Java 17 (Azul Zulu Community recommended). The build system uses RetroFuturaGradle targeting JVM 8. Heap is set to `-Xmx3G` in `gradle.properties` for decompilation.

**JDK Location:** The JDK is managed via IntelliJ and located at `C:\Users\<username>\.jdks\azul-17.0.18`. When running Gradle from the CLI, set `JAVA_HOME` to this path:
```bash
JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew build
```

## Architecture Overview

This is **LDFAWE** (Limitless Development Fast Async WorldEdit), a **Minecraft 1.12.2 Forge mod** (mod ID: `fawe`) that provides high-performance asynchronous WorldEdit operations. It is a fork of the legacy [FastAsyncWorldEdit](https://github.com/boy0001/FastAsyncWorldedit) project, maintained for Forge 1.12.2. The build system is GregTechCEu Buildscripts (RetroFuturaGradle wrapper).

### Source Layout

```
src/main/java/com/boydti/fawe/
├── Fawe.java              # Core singleton — main entry point, command registration
├── FaweAPI.java           # Public API for external mods
├── FaweCache.java         # Block/material caching
├── FaweVersion.java       # Version information
├── IFawe.java             # Platform abstraction interface
│
├── forge/                 # Forge platform integration
│   ├── ForgeMain.java     #   @Mod entry point (modid: com.boydti.fawe, v3.5.1)
│   ├── FaweForge.java     #   IFawe implementation for Forge
│   ├── ForgeCommand.java  #   Command wrapper
│   ├── ForgePlayer.java   #   Player wrapper
│   ├── ForgeTaskMan.java  #   Async task manager
│   └── v112/              #   Version-specific (1.12.2) queue/chunk implementations
│       ├── ForgeQueue_All.java
│       └── ForgeChunk_All.java
│
├── command/               # Command processing (bindings, parsers)
│   ├── AnvilCommands.java
│   ├── CFICommand.java    #   Create From Image
│   ├── Rollback.java
│   ├── Cancel.java
│   └── *Binding.java      #   Parameter bindings (Mask, Pattern, etc.)
│
├── config/                # Configuration system
│   ├── Settings.java      #   Main settings/config
│   ├── Commands.java      #   Command strings
│   ├── BBC.java           #   Broadcast messages / i18n keys
│   └── Config.java
│
├── object/                # Core data structures (~295 classes)
│   ├── FawePlayer.java    #   Player state/session
│   ├── FaweQueue.java     #   Async queue interface
│   ├── FaweChunk.java     #   Chunk operations
│   ├── brush/             #   Brush tools (heightmap, scroll, sweep, visualization)
│   ├── changeset/         #   Change tracking / undo history
│   ├── clipboard/         #   Schematic handling, block remapping
│   ├── extent/            #   Extent implementations (functional composition)
│   ├── mask/              #   Selection masks
│   ├── pattern/           #   Block patterns
│   ├── schematic/         #   Schematic I/O
│   ├── regions/           #   Region selections
│   ├── queue/             #   Queue implementations
│   ├── visitor/           #   Region visitors
│   └── io/zstd/           #   ZSTD compression utilities
│
├── jnbt/                  # NBT format and Anvil region file handling
│   └── anvil/             #   Anvil region file format (filters, generators, history)
│
├── logging/               # Edit logging/audit and rollback
├── regions/               # Region protection integration (PlotSquared)
├── example/               # Base implementations (MappedFaweQueue, NMSRelighter)
├── util/                  # Utility classes (50+ files)
├── configuration/         # YAML configuration system
├── database/              # Persistence (RollbackDatabase, DBHandler)
├── web/                   # Web integration
└── wrappers/              # World wrappers
```

### Key Subsystems

| Subsystem | Purpose | Key Classes |
|---|---|---|
| **Async Queue** | Non-blocking world edits via queue-based processing | `FaweQueue`, `ForgeQueue_All`, `SetQueue`, `TaskManager` |
| **Brushes** | Terrain sculpting tools with real-time preview | `object/brush/`, `VisualQueue` |
| **Clipboard/Schematic** | Load/save/paste schematics with ZSTD compression | `object/clipboard/`, `object/schematic/`, `ClipboardFormat` |
| **History/Rollback** | Per-player edit tracking and rollback | `LoggingChangeSet`, `RollbackDatabase`, `object/changeset/` |
| **Extent System** | Functional composition for world modifications (WorldEdit pattern) | `object/extent/`, `HistoryExtent` |
| **Masks & Patterns** | Selection filtering and block placement patterns | `object/mask/`, `object/pattern/` |
| **Anvil/NBT** | Direct region file manipulation and NBT operations | `jnbt/anvil/` |
| **CFI** | Create From Image — terrain generation from image files | `CFICommand`, `HeightMapMCAGenerator` |
| **Region Protection** | Integration with PlotSquared, RedProtect | `regions/` |

### Entry Points

1. **Mod Entry:** `ForgeMain.java` — `@Mod` annotated class, handles Forge lifecycle (`preInit`, `serverLoad`, `serverStopping`)
2. **Core Singleton:** `Fawe.java` — Initializes subsystems, registers commands, manages player sessions
3. **Platform Bridge:** `FaweForge.java` — Implements `IFawe` interface for Forge-specific queue creation and player wrapping
4. **Queue System:** `ForgeQueue_All.java` — Version-specific async chunk processing, block placement, lighting updates

### Dependencies

- **WorldEdit 6.1.3** — Core world editing API (`com.sk89q.worldedit`)
- **ZSTD-JNI 1.1.1** — High-performance compression for clipboard/schematic data
- **FastUtil-Lite 1.0** — Optimized Java collections
- **Snake YAML 1.16** — Configuration parsing

### Version

Version is derived from Git tags. No manual version setting needed (see `modVersion` in `gradle.properties`).

## Resources

```
src/main/resources/
├── mcmod.info             # Forge mod metadata
├── fawe.properties        # Version info
├── ldfawe_at.cfg          # Access transformers
├── extrablocks.json       # Extended block data
├── pack.mcmeta            # Resource pack manifest
└── [i18n message files]   # Multi-language support (en, cn, fr, de, es, it, nl, ru, tr)
```

## In-Depth System Documentation

See `docs/` for detailed technical documentation on major subsystems:
- `docs/ASYNC_QUEUE_SYSTEM.md` — Queue architecture, chunk processing pipeline, threading model
- `docs/EXTENT_AND_EDIT_SESSION.md` — Extent composition, EditSession lifecycle, mask/pattern integration

Agent progress/tracking docs are in `docs/agent_progress/`.
