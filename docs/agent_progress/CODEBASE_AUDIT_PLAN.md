# Codebase Audit — Bugs & Optimization Plan

## Resume Prompt

> We performed a deep audit of the LDFAWE codebase across three areas: (1) async queue/chunk
> system, (2) clipboard/history/undo, and (3) commands/config/utilities. This document catalogs
> all findings, prioritized by severity. Pick up where the checklist left off — fix items in
> priority order, verifying the build compiles after each batch.

---

## Priority 1: Critical Bugs — ALL FIXED

### P1-1. FaweChunk.equals() logic inversion — FIXED
**File:** `object/FaweChunk.java:343` — Changed `!=` to `==`

### P1-2. NMSRelighter bitset overflow for y > 63 — FIXED
**File:** `example/NMSRelighter.java:76` — Changed `1l << y` to `1L << (y & 63)`

### P1-3. RollbackDatabase.delete() never executes — FIXED
**File:** `database/RollbackDatabase.java:105` — Added `stmt.executeUpdate()`

### P1-4. DiskStorageHistory.getSizeOnDisk() wrong file references — FIXED
**File:** `object/changeset/DiskStorageHistory.java:246-256` — Fixed all three file references

### P1-5. ForgePlayer.java globally disables progress display — FIXED
**File:** `forge/ForgePlayer.java:23-30` — Removed `Settings.IMP` mutation, made true no-ops

### P1-6. Settings.java uses Math.max() for permission limits — FIXED
**File:** `config/Settings.java:485-493` — Changed all 9 `Math.max` to `Math.min`

### P1-7. DiskStorageHistory.flush() operator precedence bug — FIXED
**File:** `object/changeset/DiskStorageHistory.java:175` — Changed `&& osENTCF` to `|| osENTCF`

---

## Priority 2: High-Severity Issues

### P2-1. MappedFaweQueue race condition on cache fields — FIXED
**File:** `example/MappedFaweQueue.java:37-42`
Six public mutable cache fields wrapped in a `ThreadLocal<SectionCache>` so each thread gets
its own cache. Also extracted the repeated 15-line cache pattern into `ensureSectionCached()`
and `ensureChunkCached()` helpers (addresses P4-4).

### P2-2. NMSRelighter busy-wait spin lock — FIXED
**File:** `example/NMSRelighter.java:305` — Replaced busy-wait `while` with single
`compareAndSet()` check. Also added concurrentLightQueue merge (fixes P2-3).

### P2-3. NMSRelighter concurrentLightQueue updates may be lost — FIXED
**File:** `example/NMSRelighter.java:303+` — `fixBlockLighting()` now merges
`concurrentLightQueue` into `lightQueue` before processing, with bitwise OR for overlapping entries.

### P2-4. ForgeChunk_All class-level synchronization — FIXED
**File:** `forge/v112/ForgeChunk_All.java:279` — Changed `synchronized(ForgeChunk_All.class)` to
`synchronized(this)` for per-instance locking.

### P2-5. FaweForge.wrap() doesn't cache new player objects — FIXED
**File:** `forge/FaweForge.java:110-114` — New ForgePlayer now registered via `Fawe.get().register()`

### P2-6. ForgeCommand.java NPE on null FawePlayer.wrap() — FIXED
**File:** `forge/ForgeCommand.java:38` — Added null guard before `executeSafe()`

### P2-7. TaskManager.parallel() array sizing bug — FIXED
**File:** `util/TaskManager.java:112-140` — Replaced fixed 2D Runnable array with List-based
round-robin distribution. Also added null-skip for empty thread groups in join loop.

### P2-8. DiskOptimizedClipboard close/finalize race — FIXED
**File:** `object/clipboard/DiskOptimizedClipboard.java:304` — `close()` is now `synchronized`,
removed `finalize()` override.

### P2-9. DiskOptimizedClipboard overflowCombined not persisted — FIXED
**File:** `object/clipboard/DiskOptimizedClipboard.java:64`
Overflow HashMap now persisted to `.bd.overflow` sidecar file on flush/close and reloaded
when opening existing clipboards.

---

## Priority 3: Medium-Severity Issues

### P3-1. HistoryExtent records changes before block is actually set — FIXED
**File:** `object/HistoryExtent.java:56-91`
Old state (combined ID + tile entity) is now captured before set, then recorded to changeSet
only after `getExtent().setBlock()` succeeds. Also fixed `setBiome` methods for same issue.

### P3-2. HistoryExtent swallows tile entity exceptions — FIXED
**File:** `object/HistoryExtent.java:79-85` — Replaced raw `e.printStackTrace()` with
`MainUtil.handleError(e, false)` for proper error handling.

### P3-3. MappedFaweQueue.getCachedSection() ignores chunk parameter — BY DESIGN
**File:** `example/MappedFaweQueue.java:388` — The base class implementation ignores the
`chunk` parameter, but both concrete subclasses (`ForgeQueue_All`, `MCAQueue`) override the
method and correctly use it. 20 of 21 call sites in the base class pass `lastChunkSections`
anyway. The one outlier (`getLocalCombinedId4Data`) is itself dead-code-like. Not harmful.

### P3-4. TaskManager.wait() timeout parameter ignored — FIXED
**File:** `util/TaskManager.java:292-307` — Fixed typo (`timout`→`timeout`), loop now breaks
when elapsed time exceeds the caller's requested timeout.

### P3-5. MainUtil.handleError() dead code — FIXED
**File:** `util/MainUtil.java:733-753` — Restored formatted debug output; `debug=false` gets
raw stack trace, `debug=true` gets formatted FAWE header with truncated trace.

### P3-6. CPUOptimizedClipboard.getAdd() NPE when add is null — FIXED
**File:** `object/clipboard/CPUOptimizedClipboard.java:114` — Returns 0 when `add` is null.

### P3-7. MemoryOptimizedClipboard uninitialized cache fields — FIXED
**File:** `object/clipboard/MemoryOptimizedClipboard.java:55-57` — Initialized `lastI`,
`lastIMin`, `lastIMax` to -1 so first access always recalculates.

### P3-8. DiskOptimizedClipboard.getIndex() cache not thread-safe — SAFE BY DESIGN
**File:** `object/clipboard/DiskOptimizedClipboard.java:336-442` — Each clipboard instance is
tied to a single player's LocalSession. All operations (copy, paste, rotate, flip) execute
synchronously via Operations.complete*(). No concurrent access occurs in practice.

---

## Priority 4: Low-Severity / Code Quality

### P4-1. NMSRelighter.mutableBlockPos allocated but never used — NOT A BUG
**File:** `example/NMSRelighter.java:41` — Actually IS used as a reusable lookup key in
`containsKey()` calls (lines 247, 254, 269). New IntegerTrio allocations are necessary for
map storage since mutable objects can't be HashMap keys. No change needed.

### P4-2. Redundant System.gc() calls — FIXED
**File:** `util/MemUtil.java:22, 68` — Removed duplicate `System.gc()` calls.

### P4-3. ForgeQueue_All commented-out setMCA() method — FIXED
**File:** `forge/v112/ForgeQueue_All.java` — Removed 120 lines of dead commented-out code.

### P4-4. MappedFaweQueue repeated cache-loading boilerplate — FIXED
**File:** `example/MappedFaweQueue.java:461-596`
Extracted into `ensureSectionCached()` and `ensureChunkCached()` helpers as part of P2-1 fix.

### P4-5. NMSRelighter unnecessary allocations in hot loop — NOT A BUG
**File:** `example/NMSRelighter.java:188, 248` — New IntegerTrio objects are stored as HashMap
keys and queue entries; mutable objects can't be used for this. The existing `mutableBlockPos`
is correctly used only for transient `containsKey()` lookups. No change needed.

---

## Summary Statistics

| Severity | Count | Description |
|----------|-------|-------------|
| P1 Critical | 7 | Logic inversions, data corruption, security bypass |
| P2 High | 9 | Race conditions, resource leaks, NPEs |
| P3 Medium | 8 | Error handling, edge cases, thread safety |
| P4 Low | 5 | Dead code, allocations, code quality |
| **Subtotal** | **29** | |

---

## Performance & Server Stability (Large Operations)

A focused audit on what happens with massive operations (10M+ blocks). These are separate from
the correctness bugs above — they address crash prevention, throttling, and throughput.

### P0: Server Crash / Deadlock Risks

### P0-1. SetQueue.awaitQuiescence() blocks forever — FIXED
**File:** `util/SetQueue.java:177` — Replaced `Long.MAX_VALUE` with `DISCARD_AFTER_MS`

### P0-2. PREVENT_CRASHES defaults to false — FIXED
**File:** `config/Settings.java:44` — Changed default to `true`

### P0-3. ForgeTaskMan.onServerTick() has no time budget — FIXED
**File:** `forge/ForgeTaskMan.java:67-78` — Added 40ms time budget with early break

### P1: Queue Management & Memory

### P1-1. Unbounded activeQueues accumulation — FIXED
**File:** `util/SetQueue.java:210-221`
Added `MAX_ACTIVE_QUEUES` config (default 64). When the limit is reached, the oldest queue
is force-flushed before a new one is added, providing natural back-pressure.

### P1-2. MemUtil.calculateMemory() doesn't detect pressure early enough — FIXED
**File:** `util/MemUtil.java:39-52`
Replaced the `totalMemory < maxMemory` shortcut with `(maxMemory - usedBytes)` to calculate
true free memory across the full heap capacity. Catches pressure while heap is still growing.

### P1-3. FaweQueue.flush() wait loop only executes once — FIXED
**File:** `object/FaweQueue.java:496-504`
Now tracks a deadline and polls in 50ms intervals. Respects total timeout and detects
completion promptly instead of sleeping the full duration each iteration.

### P2: Performance Bottlenecks

### P2-1. Progress callback fires per-chunk (62,500 times for 1M blocks) — FIXED
**File:** `example/DefaultFaweQueueMap.java:34-39` — Throttled to at most once per 100ms.

### P2-2. Sky light lookup loops up to 16 sections per block
**File:** `example/MappedFaweQueue.java:585-590`
- [ ] Cache the "first non-null section above" per chunk column

### P2-3. PARALLEL_THREADS defaults to CPU count (no cap) — FIXED
**File:** `config/Settings.java:267` — Capped at `Math.min(8, availableProcessors())`

### P2-4. Section cache thrashes on random access patterns — FIXED
**File:** `example/MappedFaweQueue.java:37-42, 461-596`
Added 4-entry chunk ring buffer to SectionCache. On chunk miss, the ring is checked before
falling back to a full chunk load. Sequential iteration is unaffected (hits primary cache).

---

## Updated Summary Statistics

| Severity | Count | Description |
|----------|-------|-------------|
| P0 Crash/Deadlock | 3 | Server-killing issues |
| P1 Critical | 7+3 | Logic inversions, data corruption, security, memory |
| P2 High | 9+4 | Race conditions, resource leaks, performance |
| P3 Medium | 8 | Error handling, edge cases, thread safety |
| P4 Low | 5 | Dead code, allocations, code quality |
| **Total** | **39** | |
