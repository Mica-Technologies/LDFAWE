# Codebase Audit — Bugs & Optimization Plan

## Resume Prompt

> We performed a deep audit of the LDFAWE codebase across three areas: (1) async queue/chunk
> system, (2) clipboard/history/undo, and (3) commands/config/utilities. This document catalogs
> all findings, prioritized by severity. Pick up where the checklist left off — fix items in
> priority order, verifying the build compiles after each batch.

---

## Priority 1: Critical Bugs

### P1-1. FaweChunk.equals() logic inversion
**File:** `object/FaweChunk.java:343`
```java
return longHash() != ((FaweChunk) obj).longHash();  // != should be ==
```
Breaks all HashMap/HashSet operations for chunks. Chunk deduplication silently fails.
- [ ] Fix `!=` to `==`

### P1-2. NMSRelighter bitset overflow for y > 63
**File:** `example/NMSRelighter.java:76`
```java
long value = m2[y >> 6] |= 1l << y;  // Should be 1L << (y & 63)
```
Light updates for blocks above y=63 are lost or corrupt. The `m2` array has length 4 (covering
y 0-255 in 64-bit longs), but the shift isn't masked, so `1L << 128` is undefined in Java (wraps
to `1L << 0`). This means lighting at y=64 overwrites y=0's entry, y=128 overwrites y=0, etc.
- [ ] Fix to `1L << (y & 63)`

### P1-3. RollbackDatabase.delete() never executes
**File:** `database/RollbackDatabase.java:102-109`
PreparedStatement is prepared but `stmt.executeUpdate()` is never called. Deletes silently do
nothing. The rollback database grows unbounded.
- [ ] Add `stmt.executeUpdate()` call

### P1-4. DiskStorageHistory.getSizeOnDisk() wrong file references
**File:** `object/changeset/DiskStorageHistory.java:237-259`
Three of six file size reads reference `entfFile.length()` instead of the correct file:
- `nbtfFile.exists()` → reads `entfFile.length()` (should be `nbtfFile`)
- `nbttFile.exists()` → reads `entfFile.length()` (should be `nbttFile`)
- `enttFile.exists()` → reads `entfFile.length()` (should be `enttFile`)
- [ ] Fix all three file references

### P1-5. ForgePlayer.java globally disables progress display
**File:** `forge/ForgePlayer.java:23-30`
```java
public void sendTitle(String head, String sub) {
    Settings.IMP.QUEUE.PROGRESS.DISPLAY = "false";  // Global setting!
}
```
Every call to `sendTitle()` or `resetTitle()` permanently disables progress display for ALL
players server-wide. These should be no-ops since Forge doesn't support title packets in the
same way.
- [ ] Remove the `Settings.IMP` mutation, make these true no-ops

### P1-6. Settings.java uses Math.max() for permission limits
**File:** `config/Settings.java:485-493`
```java
limit.MAX_ACTIONS = Math.max(limit.MAX_ACTIONS, newLimit.MAX_ACTIONS != -1 ? ...);
```
`Math.max()` means the HIGHEST (most permissive) limit wins when combining permissions.
Should be `Math.min()` so the most restrictive limit applies.
- [ ] Change `Math.max` to `Math.min` for all limit fields

### P1-7. DiskStorageHistory.flush() operator precedence bug
**File:** `object/changeset/DiskStorageHistory.java:175`
```java
boolean flushed = osBD != null || osBIO != null || osNBTF != null || osNBTT != null && osENTCF != null || osENTCT != null;
```
Due to `&&` binding tighter than `||`, this evaluates as:
`(osBD) || (osBIO) || (osNBTF) || ((osNBTT) && (osENTCF)) || (osENTCT)`
Should use explicit parentheses or just `||` throughout.
- [ ] Fix operator precedence with parentheses

---

## Priority 2: High-Severity Issues

### P2-1. MappedFaweQueue race condition on cache fields
**File:** `example/MappedFaweQueue.java:37-42`
Six public mutable fields (`lastSectionX/Y/Z`, `lastChunk`, `lastChunkSections`, `lastSection`)
are accessed from multiple threads without synchronization. Every method that calls
`ensureChunkLoaded()` is affected (~25 methods).
- [ ] Investigate thread access patterns — determine if queues are truly accessed from multiple
      threads or if the single-thread-per-queue model makes this safe in practice
- [ ] If multi-threaded: add synchronization or use ThreadLocal caches

### P2-2. NMSRelighter busy-wait spin lock
**File:** `example/NMSRelighter.java:305`
```java
while (!lightLock.compareAndSet(false, true));  // Busy-wait inside synchronized
```
CPU-wasting spin loop inside a synchronized block. Risk of deadlock if another thread holds
`lightLock` while trying to acquire `synchronized(lightQueue)`.
- [ ] Replace with `if (!lightLock.getAndSet(true))` or proper lock

### P2-3. NMSRelighter concurrentLightQueue updates may be lost
**File:** `example/NMSRelighter.java:80-102`
When the lightLock is taken, updates go to `concurrentLightQueue`. But `fixBlockLighting()`
only processes `lightQueue`, not `concurrentLightQueue`.
- [ ] Verify if `concurrentLightQueue` is merged elsewhere; if not, add merge step

### P2-4. ForgeChunk_All class-level synchronization
**File:** `forge/v112/ForgeChunk_All.java:279`
```java
synchronized (ForgeChunk_All.class) {  // Locks ALL instances
```
Tile entity trimming uses a class-level lock, blocking all concurrent chunk processing.
- [ ] Replace with per-instance or per-chunk lock

### P2-5. FaweForge.wrap() doesn't cache new player objects
**File:** `forge/FaweForge.java:110-111`
```java
return existing != null ? existing : new ForgePlayer(player);  // Not cached!
```
Creates duplicate ForgePlayer instances, causing memory leak and session state fragmentation.
- [ ] Register new ForgePlayer in the cache after creation

### P2-6. ForgeCommand.java NPE on null FawePlayer.wrap()
**File:** `forge/ForgeCommand.java:38`
No null check after `FawePlayer.wrap(player)` before calling `cmd.executeSafe(fp, args)`.
- [ ] Add null guard

### P2-7. TaskManager.parallel() array sizing bug
**File:** `util/TaskManager.java:112-114`
Integer division loses remainder, causing some tasks to never be queued.
- [ ] Fix array allocation or switch to List-based distribution

### P2-8. DiskOptimizedClipboard close/finalize race
**File:** `object/clipboard/DiskOptimizedClipboard.java:299-319`
`finalize()` calls `close()` without synchronization. `close()` checks `mbb != null` without
a lock. Concurrent access during GC causes corruption.
- [ ] Add synchronization to close(), remove finalize() (deprecated since Java 9)

### P2-9. DiskOptimizedClipboard overflowCombined not persisted
**File:** `object/clipboard/DiskOptimizedClipboard.java:64`
The overflow map for high block IDs is in-memory only. On disk reload, overflow blocks become
`0xFFFF` (block ID 4095, data 15) instead of their real value.
- [ ] Serialize overflow to a sidecar file or extend the disk format

---

## Priority 3: Medium-Severity Issues

### P3-1. HistoryExtent records changes before block is actually set
**File:** `object/HistoryExtent.java:56-91`
If `getExtent().setBlock()` fails after changeSet.add(), undo will revert a change that never
happened, corrupting the world.
- [ ] Record to changeSet only after successful setBlock, or add compensation

### P3-2. HistoryExtent swallows tile entity exceptions
**File:** `object/HistoryExtent.java:79-85`
Broad `catch (Throwable e)` with only `e.printStackTrace()`. Tile entity data silently lost
during undo/redo (chests, furnaces lose contents).
- [ ] Log properly via MainUtil, consider failing the operation instead

### P3-3. MappedFaweQueue.getCachedSection() ignores chunk parameter
**File:** `example/MappedFaweQueue.java:388`
Always returns `lastChunkSections` regardless of which chunk is passed.
- [ ] Investigate if this is intentional (caller always passes lastChunk) or a bug

### P3-4. TaskManager.wait() timeout parameter ignored
**File:** `util/TaskManager.java:292-307`
The `timout` parameter is passed to `wait()` but the loop only breaks based on
`DISCARD_AFTER_MS`, effectively ignoring the caller's requested timeout.
- [ ] Fix timeout logic to respect the parameter

### P3-5. MainUtil.handleError() dead code
**File:** `util/MainUtil.java:729-753`
Formatted error output code is unreachable (after a `return` statement). All errors just get
raw `e.printStackTrace()`.
- [ ] Either restore the formatted output or remove the dead code

### P3-6. CPUOptimizedClipboard.getAdd() NPE when add is null
**File:** `object/clipboard/CPUOptimizedClipboard.java:114-116`
`add[index]` accessed without null check on the `add` array. Called from `getBlock()` when
`add != null` check is done at the caller — but `getAdd()` is also public.
- [ ] Add null guard in `getAdd()` itself

### P3-7. MemoryOptimizedClipboard uninitialized cache fields
**File:** `object/clipboard/MemoryOptimizedClipboard.java:240-247`
`lastI`, `lastIMin`, `lastIMax` default to 0, which could match index 0 incorrectly on first
call.
- [ ] Initialize to -1 or use a separate `initialized` flag

### P3-8. DiskOptimizedClipboard.getIndex() cache not thread-safe
**File:** `object/clipboard/DiskOptimizedClipboard.java:336-442`
`ylast`/`zlast` cache fields are not synchronized. Concurrent clipboard access corrupts indices.
- [ ] Document single-threaded usage requirement or add synchronization

---

## Priority 4: Low-Severity / Code Quality

### P4-1. NMSRelighter.mutableBlockPos allocated but never used
**File:** `example/NMSRelighter.java:41`
- [ ] Remove or use it instead of allocating new IntegerTrio objects in the hot loop

### P4-2. Redundant System.gc() calls
**File:** `util/MemUtil.java:22-24, 68-69`
`System.gc()` called twice in sequence. One call is sufficient.
- [ ] Remove duplicate calls

### P4-3. ForgeQueue_All commented-out setMCA() method
**File:** `forge/v112/ForgeQueue_All.java:163-283`
Large block of dead code.
- [ ] Remove or document with TODO

### P4-4. MappedFaweQueue repeated cache-loading boilerplate
**File:** `example/MappedFaweQueue.java:461-596`
Same chunk/section loading pattern repeated ~20 times.
- [ ] Extract to helper method

### P4-5. NMSRelighter unnecessary allocations in hot loop
**File:** `example/NMSRelighter.java:188, 248`
New `IntegerTrio` objects created per light update. Should reuse mutableBlockPos.
- [ ] Reuse mutable objects

---

## Summary Statistics

| Severity | Count | Description |
|----------|-------|-------------|
| P1 Critical | 7 | Logic inversions, data corruption, security bypass |
| P2 High | 9 | Race conditions, resource leaks, NPEs |
| P3 Medium | 8 | Error handling, edge cases, thread safety |
| P4 Low | 5 | Dead code, allocations, code quality |
| **Total** | **29** | |
