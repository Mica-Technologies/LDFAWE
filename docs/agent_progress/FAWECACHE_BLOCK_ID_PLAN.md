# FaweCache Block ID Limit — Investigation & Attack Plan

## Resume Prompt

> We are working on LDFAWE, a Forge 1.12.2 fork of FastAsyncWorldEdit. The FaweCache system uses a
> 16-bit `char` encoding `(blockId << 4) | metadata` to represent blocks, which limits block IDs to
> 0-4095. In heavily modded environments, Forge can assign block IDs > 4095, causing
> ArrayIndexOutOfBoundsException. The current workaround (commit a526a2d0) returns air for
> out-of-range blocks, silently corrupting WorldEdit operations. This plan outlines a strategy to
> support high block IDs without massively increasing memory usage. Pick up where the checklist
> left off.

---

## Root Cause Analysis

### The Encoding

FAWE stores every block as a single `char` (16-bit unsigned, 0-65535):

```
Combined ID = (blockId << 4) | metadata
              ├── bits 15-4: block ID (12 bits → 0-4095)
              └── bits 3-0:  metadata  (4 bits  → 0-15)
```

This encoding is used **everywhere** — it's not just FaweCache, it's the fundamental data type
for the entire block processing pipeline.

### Where It's Baked In

| Layer | File | How char is used |
|-------|------|-----------------|
| **Cache** | `FaweCache.java` | `CACHE_BLOCK[65536]`, `CACHE_ITEM[65536]`, `CACHE_COLOR[65536]`, `CACHE_PASSTHROUGH[65535]`, `CACHE_TRANSLUSCENT[65535]` |
| **Chunk Storage** | `CharFaweChunk.java` | `char[][] ids` — every block in every pending chunk edit is a single `char` |
| **Forge Read** | `ForgeQueue_All.java:487-496` | `getCombinedId4Data()` — reads block from Minecraft, encodes as `(id << 4) + meta`, returned as `int` but stored as `char` |
| **Forge Write** | `ForgeChunk_All.java:138-139` | `combinedId >> 4` and `combinedId & 0xF` to decompose back to block + meta |
| **Forge Previous** | `ForgeQueue_All.java:526-535` | `previousLayer[j] = (char) combined` — explicit cast truncates high bits |
| **Clipboard** | `DiskOptimizedClipboard.java` | `mbb.putChar(index, (char) combined)` — 16-bit storage on disk |
| **Schematic I/O** | `FaweFormat.java:301` | `out.writeShort((short) getCombined())` — 16-bit in file format |
| **Patterns** | `ExpressionPattern.java:57` | `FaweCache.getBlock((char) combined)` |
| **Remap** | `ClipboardRemapper.java:480` | `remapCombined[combinedFrom] = (char) combinedTo` |

### Why Block IDs > 4095 Happen

- **Vanilla Minecraft 1.12.2:** Block IDs 0-255 only.
- **Forge 1.12.2:** Extends the block registry. `Block.getIdFromBlock()` can return values > 4095
  in heavily modded environments (hundreds of mods adding blocks).
- **WorldEdit itself:** `BaseBlock.MAX_ID = 65535` — WorldEdit *expects* high block IDs.
- **The overflow:** When `id > 4095`, `(id << 4) + meta > 65535`, which overflows `char` and
  exceeds the cache array bounds.

### Current Failure Mode

1. `ForgeQueue_All.getCombinedId4Data()` reads a block with ID > 4095 from the world
2. Computes `combined = (id << 4) + meta` → value > 65535
3. This flows to `FaweCache.getBlock(combined)` → `ArrayIndexOutOfBoundsException`
4. Catch block (commit a526a2d0) returns `CACHE_BLOCK[0]` (air)
5. **Result:** High-ID blocks silently become air in all WorldEdit operations

### Additional Bug: CACHE_PASSTHROUGH/TRANSLUSCENT Off-By-One

```java
private final static boolean[] CACHE_PASSTHROUGH = new boolean[65535];  // size 65535, max index 65534
private final static boolean[] CACHE_TRANSLUSCENT = new boolean[65535]; // same
```

Even for vanilla-range IDs, combined index `(4095 << 4) + 15 = 65535` is out of bounds for these
arrays. Should be `65536` like the other caches.

---

## Design Constraints

1. **No massive memory increase** — can't just make all arrays `int`-sized (millions of entries)
2. **Minimal code churn** — the `char` encoding touches 50+ files; rewriting all of them is risky
3. **Hot path performance** — block lookups happen millions of times per operation; must stay fast
4. **Backward compatibility** — schematic files and clipboard formats use 16-bit storage

---

## Attack Plan

### Strategy: Hybrid Sparse Cache + int Pipeline

Keep the `char[]` chunk storage for the common case (98%+ of blocks have IDs 0-4095), but add an
overflow path for high-ID blocks. The key insight is that high-ID blocks are **rare** — most chunks
in even heavily modded worlds contain only common blocks.

### Phase 1: Fix the Immediate Bugs (Low Risk) — DONE

- [x] **1a.** Fix `CACHE_PASSTHROUGH` and `CACHE_TRANSLUSCENT` array sizes from `65535` to `65536`
- [x] **1b.** `getCombinedId4Data()` already returns `int` — no change needed, it faithfully
      returns the full value. The truncation happened downstream in `getPrevious()` and char storage.
- [x] **1c.** `ForgeQueue_All.getPrevious()` — overflow blocks now use `CharFaweChunk.setBlock()`
      to store in the overflow map instead of truncating via `(char) combined`

### Phase 2: FaweCache Overflow Support (Medium Risk) — DONE

- [x] **2a.** Added `ConcurrentHashMap<Integer, BaseBlock> OVERFLOW_BLOCK` and `OVERFLOW_ITEM`
- [x] **2b.** `getBlock(int index)` — fast path for index < 65536, lazy HashMap for overflow
- [x] **2c.** `getItem()`, `getColor()`, `canPassThrough()`, `isTranslucent()` all handle overflow
- [x] **2d.** `hasNBT()`, `hasData()`, etc. already have safe `default:` cases for high IDs

### Phase 3: CharFaweChunk Overflow Support (Higher Risk) — DONE

- [x] **3a.** Added `HashMap<Integer, Integer> overflowIds` to CharFaweChunk (keyed by
      `(section << 12) | positionInSection`). Only allocated when overflow blocks exist.
- [x] **3b.** Both `setBlock()` overloads check `combined > Character.MAX_VALUE` and route to
      overflow map with sentinel `0xFFFF` in the char array
- [x] **3c.** `getBlockCombinedId()` checks for sentinel and looks up overflow map
- [x] **3d.** `ForgeChunk_All` — both `optimize()` and the block application loop check for
      overflow sentinel and resolve from the overflow map before writing to Minecraft
- [x] **3e.** `ForgeQueue_All.getPrevious()` — overflow blocks stored via `setBlock()` which
      routes to the overflow map

**Note:** `NMSMappedFaweQueue.getId()` used for relighting still reads raw char arrays without
overflow awareness. Overflow blocks will be treated as block ID 4095 (sentinel >> 4) for lighting
purposes. This is acceptable — they'll be treated as solid opaque blocks, which is correct for
most modded blocks and far better than the previous behavior of treating them as air.

### Phase 4: Clipboard & I/O Compatibility (Medium Risk) — DONE

- [x] **4a.** `DiskOptimizedClipboard` — added in-memory `overflowCombined` HashMap. All read
      paths (`getBlock`, `forEach`, `streamIds`, `streamDatas`) use `getCombinedIdAt()` which
      resolves overflow. All write paths (`setBlock`, `setId`, `setCombined`, `setAdd`) route
      overflow blocks to the map with sentinel `0xFFFF` on disk.
- [x] **4b.** `FaweFormat` — added mode 5 with `writeInt`/`readInt` (4 bytes per block) for
      full combined ID support. Modes 0-4 (16-bit) are clamped to `Character.MAX_VALUE` on write
      to avoid silent corruption. Mode 5 read path uses `readInt()`.
- [x] **4c.** Backward compat: modes 0-4 read path is unchanged. Old files load fine.
      `CPUOptimizedClipboard` and `MemoryOptimizedClipboard` already support high IDs via their
      `add` byte array (`id = base + (add << 8)`) — no changes needed.

### Phase 5: Validation & Testing

- [ ] **5a.** Create a test world with blocks at ID boundaries (4095, 4096, 8191, etc.)
- [ ] **5b.** Test core operations: select, copy, paste, set, replace, undo
- [ ] **5c.** Test clipboard save/load round-trip with high-ID blocks
- [ ] **5d.** Memory profiling: verify overflow maps don't leak

---

## Memory Impact Analysis

**Current:** `65536 × (BaseBlock ref + BaseItem ref + Color ref) + 65535 × 2 booleans`
≈ 65536 × 24 bytes + 131070 bytes ≈ **1.7 MB** (fixed, always allocated)

**With overflow HashMap:** Same fixed cost + ~48 bytes per unique high-ID block/data combination
actually encountered. In practice, even a heavily modded world with 200 blocks above ID 4095
would add only ~200 × 48 ≈ **9.6 KB**.

**CharFaweChunk overflow:** Only allocated for chunks containing high-ID blocks. Each overflow
entry is ~48 bytes (HashMap entry). A chunk with 100 high-ID blocks adds ~4.8 KB. Chunks with
no high-ID blocks (the vast majority) have zero overhead.

**Total impact:** Negligible. The approach scales with actual high-ID block usage, not with the
theoretical maximum block ID space.

---

## Risk Assessment

| Phase | Risk | Reason |
|-------|------|--------|
| 1 | Low | Bug fixes, no architectural changes |
| 2 | Low-Medium | FaweCache is centralized, changes are contained |
| 3 | Medium-High | CharFaweChunk is hot path, sentinel value must be handled everywhere |
| 4 | Medium | File format changes need backward compat |
| 5 | Low | Testing only |

**The sentinel value (Phase 3) is the riskiest part.** Every code path that reads from
`char[][] ids` must be aware that `0xFFFF` means "look in the overflow map." Missing even one
read site would cause `0xFFFF` (block ID 4095, data 15) to be treated as the actual block.

An alternative to the sentinel approach: use `int[][]` only for sections that contain overflow
blocks, with a boolean flag per section. This avoids the sentinel problem but requires all read
paths to check the flag and branch to the right array type.

---

## Files to Modify (Complete List)

### Phase 1-2 (FaweCache + bug fixes)
- `FaweCache.java` — overflow maps, array size fix, updated accessors

### Phase 3 (Chunk storage)
- `CharFaweChunk.java` — overflow map, setBlock/getBlock modifications
- `ForgeChunk_All.java` — overflow-aware block application
- `ForgeQueue_All.java` — overflow-aware getCombinedId4Data and getPrevious
- `NMSMappedFaweQueue.java` — getId() overflow handling
- `MappedFaweQueue.java` — any direct char[][] access

### Phase 4 (I/O)
- `DiskOptimizedClipboard.java` — 32-bit block storage path
- `FaweFormat.java` — format version for high-ID support
- `CPUOptimizedClipboard.java` — if it uses char[] storage

### Call sites that pass combined IDs to FaweCache (must handle > 65535)
- `EditSession.java` — ~15 call sites using getCombinedId4Data
- `FaweQueue.java` — getBlock/getLazyBlock with combined IDs
- `FaweChunk.java` — getBlock from combined arrays
- `FastWorldEditExtent.java` — getBlock/getLazyBlock
- `HistoryExtent.java` — previous block lookup
- `BlockMask.java` — combined ID matching
- `HeightMapMCAGenerator.java` — terrain generation with combined IDs
