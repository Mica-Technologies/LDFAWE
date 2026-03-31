# Extent System and EditSession

This document describes the extent composition pattern and EditSession lifecycle used throughout LDFAWE.

## Overview

LDFAWE inherits and extends WorldEdit's **Extent** pattern — a functional composition system where world access is layered through a chain of delegates. Each layer can intercept, transform, filter, or record block operations.

## Extent Hierarchy

```
Extent (interface — com.sk89q.worldedit.extent)
├── AbstractDelegateExtent      # Base for extent wrappers (delegates to parent)
│   ├── MaskingExtent           # Filters set operations through a mask
│   ├── BlockTransformExtent    # Rotates/mirrors blocks during copy
│   ├── BlockBagExtent          # Consumes/produces items from player inventory
│   ├── HistoryExtent           # Records changes for undo/redo
│   └── [FAWE custom extents]   # object/extent/ — caching, lighting, processing, etc.
│
├── BlockArrayClipboard         # In-memory block storage (schematic clipboard)
└── World implementations       # Server world access
```

## EditSession Lifecycle

### Creation

`EditSessionBuilder` (`util/EditSessionBuilder.java`) constructs the extent chain:

1. Start with the world's `FaweQueue` as the base extent
2. Layer on history tracking (`HistoryExtent`) if undo is enabled
3. Layer on mask filtering (`MaskingExtent`) if a mask is set
4. Layer on block bag constraints if the player has a block bag
5. Wrap in the final `EditSession`

### Usage Pattern

```
Command/Brush
  → EditSession (top-level extent)
    → MaskingExtent (optional — filters by mask)
      → HistoryExtent (optional — records for undo)
        → FaweQueue (async block placement)
          → ForgeQueue_All (NMS chunk manipulation)
```

### Cleanup

When an EditSession completes:
1. Queue is flushed (pending changes applied)
2. History is finalized and stored (disk or memory)
3. Lighting is recalculated for affected chunks

## Key FAWE Extent Classes

Located in `object/extent/`:

| Class | Purpose |
|---|---|
| `FaweRegionExtent` | Base for region-bounded extents |
| `ProcessedWEExtent` | Applies FAWE's async processing pipeline |
| `HeightBoundExtent` | Limits operations to a Y-range |
| `SlowExtent` | Throttles operations to reduce server impact |
| `SourceMaskExtent` | Applies a mask based on source blocks (for copy operations) |
| `TemporalExtent` | Provides temporal block state during complex operations |

## Masks

Masks (`object/mask/`) filter which blocks an operation affects:

| Mask | Purpose |
|---|---|
| `AdjacentAnyMask` | Matches blocks adjacent to specific block types |
| `AngleMask` | Matches based on terrain slope angle |
| `DataMask` | Matches block data/metadata values |
| `IdMask` | Matches block IDs |
| `RadiusMask` | Matches within a radius of a point |
| `WallMask` | Matches blocks that form walls |
| `SurfaceMask` | Matches surface blocks (exposed to air) |

## Patterns

Patterns (`object/pattern/`) determine what blocks are placed:

| Pattern | Purpose |
|---|---|
| `PatternExtent` | Wraps a pattern as an extent |
| `ExpressionPattern` | Uses mathematical expressions to select blocks |
| `LinearBlockPattern` | Cycles through blocks in sequence |
| `RandomFullClipboardPattern` | Randomly pastes from clipboard |
| `SurfaceRandomOffsetPattern` | Applies random offset to surface placements |
| `ShufflePattern` | Randomized pattern selection |

## History / Undo System

- **`HistoryExtent`** — Records every block change (old state + new state)
- **`DiskStorageHistory`** — Persists change history to disk for large operations
- **`MemBlockChange`** / **`MutableBlockChange`** — Lightweight change records
- **`RollbackDatabase`** — SQLite database for queryable rollback by player/time/region

Changes are recorded as `(x, y, z, oldId, oldData, newId, newData)` tuples, compressed with ZSTD for disk storage.
