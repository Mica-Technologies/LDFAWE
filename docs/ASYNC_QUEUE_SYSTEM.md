# Async Queue System

This document describes the asynchronous queue architecture that is the core performance feature of LDFAWE.

## Overview

LDFAWE's primary value over standard WorldEdit is asynchronous, non-blocking world editing. Instead of modifying blocks synchronously on the server thread (which causes lag spikes for large operations), LDFAWE queues changes and processes them in batches across multiple threads.

## Architecture

### Queue Hierarchy

```
FaweQueue (interface)
└── MappedFaweQueue (abstract base)
    └── NMSMappedFaweQueue (NMS-specific base)
        └── ForgeQueue_All (Forge 1.12.2 implementation)
```

### Key Classes

| Class | Location | Purpose |
|---|---|---|
| `FaweQueue` | `object/FaweQueue.java` | Core queue interface — defines chunk get/set, flush, progress |
| `MappedFaweQueue` | `example/MappedFaweQueue.java` | Base implementation with chunk caching and batch processing |
| `NMSMappedFaweQueue` | `example/NMSMappedFaweQueue.java` | NMS-level chunk access (direct Minecraft server internals) |
| `ForgeQueue_All` | `forge/v112/ForgeQueue_All.java` | Forge 1.12.2 queue — chunk section manipulation, lighting, tile entities |
| `FaweChunk` | `object/FaweChunk.java` | Represents a chunk's worth of pending changes |
| `ForgeChunk_All` | `forge/v112/ForgeChunk_All.java` | Forge-specific chunk wrapper |
| `SetQueue` | `util/SetQueue.java` | Global queue manager — schedules and dispatches queues |
| `TaskManager` | `util/TaskManager.java` | Thread pool management, sync/async task scheduling |

### Processing Flow

1. **Edit initiated** — Player runs a WorldEdit command (e.g., `//set stone`)
2. **EditSession created** — `EditSessionBuilder` creates an `EditSession` wrapping a `FaweQueue`
3. **Blocks queued** — Block changes are batched into `FaweChunk` objects (one per 16x16 chunk column)
4. **Queue flushed** — `SetQueue` dispatches chunks to the server thread for application
5. **Chunk updated** — `ForgeQueue_All` applies changes via NMS, updates lighting, sends chunk packets to players

### Threading Model

- **Main server thread** — Chunk application (NMS operations must happen here)
- **Async worker threads** — Pattern evaluation, mask checking, region iteration, history recording
- **`Settings.IMP.QUEUE.PARALLEL_THREADS`** — Controls worker thread count (set to 1 when Sponge is detected)

### Chunk Sections

`ForgeQueue_All` operates at the chunk section level (16x16x16 blocks). It directly manipulates:
- Block ID/data arrays (`ExtendedBlockStorage`)
- Sky and block light arrays
- Tile entity maps
- Height maps

### Relighting

`NMSRelighter` handles light recalculation after block changes:
- Tracks which chunks need relighting
- Processes sky light and block light separately
- Batches light updates to minimize recalculation passes

## Configuration

Key settings in `Settings.java` (`config/Settings.java`):
- `QUEUE.PARALLEL_THREADS` — Number of async worker threads
- `QUEUE.TARGET_SIZE` — Target number of chunks to process per tick
- `QUEUE.MAX_WAIT_MS` — Maximum time to spend applying changes per tick
- `QUEUE.DISCARD_AFTER_MS` — Timeout for discarding stale queue entries

## Error Handling

- Out-of-memory conditions trigger queue flushing via `MemUtil`
- Failed chunk operations are logged and skipped rather than crashing the server
- Player disconnect during an operation triggers cleanup via `ForgeMain.handleQuit()`
