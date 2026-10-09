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

- **Main server thread** — `SetQueue` dispatches chunks from here. Everything that touches world-wide
  state runs here too: `ForgeChunk_All.start()` settles the UUIDs of entities to spawn, and
  `ForgeQueue_All.end()` calls `ForgeChunk_All.applyOnMainThread()`, which invalidates replaced tile
  entities, removes and spawns entities, loads tile-entity NBT and recomputes the heightmap before the
  chunk is sent. Chunk packets are always built and sent on this thread.
- **ForkJoin workers** (`QUEUE.PARALLEL_THREADS`) — `ForgeChunk_All.call()` writes only chunk-local
  storage (block sections, light) and *collects* the world-wide work above; it must never spawn,
  remove or invalidate anything itself, because several chunks run at once.
- **Async command threads** — Pattern evaluation, mask checking, region iteration, history recording.
  `FaweQueue.flush()` blocks the command thread until the queue has left the ACTIVE stage, because the
  edit's change set is closed as soon as it returns. When too many queues are active, `SetQueue.enqueue()`
  makes the producer wait (never on the main thread) rather than applying a queue off-thread.

### Block updates

Edits made through the queue never cause block updates: sections are written directly and the chunk
is resent, so no `neighborChanged`, `onBlockAdded`, `breakBlock` or redstone/physics recalculation
runs. This is relied on in practice, for example to remove Railcraft or Charset wire networks without
the network recalculation crashing the server. The only per-block hook that still runs is
`TileEntity#invalidate()` for tile entities that are replaced or removed (on the main thread), which is
how mods learn their tile entity is gone. Keep it that way: do not add neighbour notifications to the
queue path.

### Block ids above 4095

With RoughlyEnoughIDs, block ids can exceed 4095, so combined ids (`id << 4 | data`) exceed 16 bits.
`CharFaweChunk` keeps 16-bit `char` sections and stores such blocks as the placeholder `0xFFFF` with the
real value in a sparse `overflowIds` map; anything reading the raw arrays must resolve the placeholder
through `getBlockCombinedId()` (a real 4095:15 block is `0xFFFF` without a map entry). History streams
store 32-bit ids (`FaweStreamChangeSet.MODE_WIDE_IDS`), the disk clipboard has an `.overflow` sidecar,
`.schematic` files carry an extra `AddBlocksFull` array, and snapshot restore decodes REID's palette
sections (`ChunkSectionDecoder`). Lookup tables indexed by id must bounds-check or use sets/maps.

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
