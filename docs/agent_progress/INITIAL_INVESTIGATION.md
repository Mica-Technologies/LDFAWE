# Initial Investigation & Setup

## Resume Prompt

> We are working on LDFAWE, a Forge 1.12.2 fork of FastAsyncWorldEdit. Progress so far:
>
> 1. **Build system** — RESOLVED. Updated to GregTech Buildscripts v1762213476, RFG 1.4.0,
>    Gradle 8.9. Added `buildscript.properties`.
>
> 2. **Block ID overflow (>4095)** — COMPLETE. User-tested and confirmed working with high-ID
>    modded blocks. Hybrid sparse cache with HashMap overflow. See `FAWECACHE_BLOCK_ID_PLAN.md`.
>
> 3. **Codebase audit** — 25 of 39 items FIXED. All P0 crash risks and P1 critical bugs resolved.
>    Most P2/P3 done. 5 architectural items remain. See `CODEBASE_AUDIT_PLAN.md`.
>
> 4. **setbiome with REID** — FIXED in LDFAWE. REID replaces vanilla biome storage with int[];
>    FAWE was writing to a dummy byte[] that REID ignores. Added `REIDBiomeHelper.java` that
>    uses reflection to call REID's BiomeApi for reads/writes and sends network sync packets.
>    User-tested and confirmed working in singleplayer. Client-side network sync added but
>    not yet tested (should eliminate need to rejoin world to see changes).
>    Dedicated server testing still pending.
>
> 5. **GitHub Actions** — Updated workflows to latest action versions (checkout v6,
>    setup-java v5, setup-gradle v6, action-gh-release v2). CSM update plan created at
>    `E:\gitRepos\minecraft-city-super-mod\assets\docs\agent_progress\GITHUB_ACTIONS_UPDATE_STEPS.md`.
>
> 6. **Git hygiene** — Removed `.claude/settings.local.json` from history via rebase,
>    added `.claude/` to `.gitignore`.
>
> **What to do next:** Test (a) setbiome on dedicated server, (b) whether biome changes are
> visible to clients immediately (without rejoin) thanks to the network sync. Remaining audit
> items in `CODEBASE_AUDIT_PLAN.md` are architectural and can be tackled incrementally.
> The 5 open items are: P2-1 (MappedFaweQueue threading), P2-9 (overflow persistence to disk),
> P3-1 (HistoryExtent ordering), P4-4 (boilerplate extraction), Perf P2-2/P2-4 (section cache
> optimizations).

## Checklist

### 1. Build System Investigation — RESOLVED

- [x] Attempt a full `./gradlew build` and document any errors
- [x] Investigate and resolve build failures (dependency resolution, toolchain issues, etc.)
- [x] If build issues persist due to the legacy Forge MDK setup, investigate migrating to
      GregTechCEu Buildscripts (RetroFuturaGradle)

**Root cause:** RetroFuturaGradle 1.3.28 was removed from both the GTNH Maven and Gradle Plugin
Portal. The project was already using the GregTech Buildscripts, just an outdated version.

**Fix applied:** Copied the working `build.gradle` (buildscript version 1762213476, RFG 1.4.0),
`settings.gradle`, and `gradle-wrapper.properties` (Gradle 8.9) from the sister project
(minecraft-city-super-mod). Added `buildscript.properties` and trimmed `gradle.properties` to
match the intended buildscript framework layout. Updated wrapper files.

**Result:** Build succeeds cleanly. Only warnings are expected sun.reflect usage in
ReflectionUtils (normal for this type of mod).

---

### 2. FaweCache Block ID Limit (>65535 combined index) — PHASES 1-4 COMPLETE

- [x] Investigate the root cause of the OOB cache hits in `FaweCache`
- [x] Determine all call sites that feed into `getBlock(int index)` and `getItem(int index)`
- [x] Understand the combined ID encoding: `(blockId << 4) + data`
- [x] Determine the maximum block ID that modded Forge 1.12.2 can produce
- [x] Design a fix that supports block IDs above the current `Character.MAX_VALUE` (65535) limit
- [x] Implement the fix — Phases 1-4 complete (see `FAWECACHE_BLOCK_ID_PLAN.md`)
- [ ] **Phase 5:** Test with a world containing high-ID modded blocks (USER TESTING PENDING)

**Full investigation and attack plan:** See `docs/agent_progress/FAWECACHE_BLOCK_ID_PLAN.md`

**Summary:** The 16-bit `char` encoding `(id << 4) | metadata` was baked into the entire pipeline
— FaweCache, CharFaweChunk, ForgeQueue_All, ForgeChunk_All, clipboard I/O, and 50+ call sites.
The fix uses a hybrid sparse cache approach: keep the existing `char[]` fast path for IDs 0-4095
(98%+ of blocks), add HashMap-backed overflow for high-ID blocks. Memory impact is negligible
(scales with actual high-ID usage, not theoretical max). Also fixed an off-by-one bug in
`CACHE_PASSTHROUGH`/`CACHE_TRANSLUSCENT` (sized 65535 instead of 65536). Clipboard I/O
(DiskOptimizedClipboard, FaweFormat mode 5) also supports overflow.

---

### 3. Codebase Audit — Bugs & Optimization Opportunities — 25/39 FIXED

- [x] Deep-dive analysis of the full LDFAWE codebase
- [x] Document obvious bugs (crashes, silent failures, data corruption, edge cases)
- [x] Document optimization opportunities (performance, memory, code quality) without losing
      functionality
- [x] Create a new progress plan (`CODEBASE_AUDIT_PLAN.md`) with findings and actionable items
- [x] Implement P0 crash fixes (3/3): deadlock timeout, PREVENT_CRASHES default, tick time budget
- [x] Implement P1 critical fixes (7/7): equals(), relighter bitset, rollback delete, file refs,
      progress display, permission limits, flush precedence
- [x] Implement P2 high fixes (7/9): relighter spin lock + concurrent merge, player cache,
      command NPE, clipboard close race, parallel() distribution, class-level lock, progress throttle
- [x] Implement P3 medium fixes (5/8): timeout, error handling, NPE guard, cache init, tile logging
- [x] Implement P4 low fixes (3/5): redundant GC, dead code removal, thread cap
- [ ] 5 remaining architectural items (see `CODEBASE_AUDIT_PLAN.md`)

**Full plan:** See `docs/agent_progress/CODEBASE_AUDIT_PLAN.md`

---

### 4. Investigate `setbiome` Command Failure — FIXED IN LDFAWE

- [x] Trace the command execution path from input through to biome application
- [x] Investigate REID PR #53 for biome-related fixes
- [x] Test with REID 2.3.0 in singleplayer — broken (biomes not applied)
- [x] Root-cause analysis — FAWE writes to dummy byte[] that REID ignores
- [x] Implement REID-compatible biome writes via reflection-based BiomeApi bridge
- [x] User testing confirms fix works in singleplayer
- [x] Add client-side network sync via REID's MessageManager
- [x] Test that client sees biome change immediately without world rejoin — CONFIRMED
- [ ] Test on dedicated server (PENDING)

**Root Cause:**

REID replaces vanilla's `byte[256]` biome storage with an internal `int[256]` via
`BiomeContainer`. It makes `Chunk.getBiomeArray()` return a **dummy error array** and
**cancels** `Chunk.setBiomeArray(byte[])` entirely. FAWE's async pipeline bypasses
WorldEdit's normal biome path (which REID's `MixinForgeWorld` intercepts) and writes
directly to `nmsChunk.getBiomeArray()` — which under REID is a throwaway array. Biome
writes silently went nowhere.

REID's `MixinBiomeCommands` intercepts WorldEdit's `BiomeCommands.setBiome()`, but FAWE
handles the command before WorldEdit does, so REID's mixin never fires for FAWE operations.

**Fix (implemented in LDFAWE — no REID fork needed):**

New file `REIDBiomeHelper.java` — reflection-based bridge to REID's public API:
- Detects REID at runtime by checking for `tff.reid.api.BiomeApi`
- `applyBiomes()` reads current `int[]` from REID, overlays FAWE's changes, writes back
  via `BiomeApi.replaceBiomes()`, then sends `BiomeChunkChangeMessage` to tracking clients
- `getBiomeId()` reads biome at a position via `BiomeAccessor`
- Falls back gracefully when REID is not installed

Modified files:
- `ForgeChunk_All.call()` — biome write uses REID API when available, vanilla fallback
- `ForgeQueue_All.getBiome()` — biome read uses REID API when available, vanilla fallback

**Known limitations:**
- FAWE's internal biome storage is `byte[256]`, so biome IDs > 255 are truncated. This is
  a separate enhancement (would require widening `CharFaweChunk.biomes` to `int[]`).
- Biome ID 0 uses a sentinel value (-1) internally; round-trips correctly but is fragile.

**Server considerations:**
- `sendClientsBiomeChunkChange()` uses `sendToAllTracking()` which sends to all players
  who have the chunk loaded — works correctly on dedicated servers.
- FAWE's `ForgeChunk_All.call()` can run from a ForkJoinPool thread; Forge's
  `SimpleNetworkWrapper` sends through Netty which is thread-safe.

**References:**
- REID source: E:\gitRepos\RoughlyEnoughIDs
- REID BiomeApi: `tff.reid.api.BiomeApi` (public API)
- REID BiomeContainer: `org.dimdev.jeid.impl.type.BiomeContainer` (internal int[] storage)
- REID MixinChunk: cancels `setBiomeArray()`, returns dummy from `getBiomeArray()`
- REID MessageManager: `sendClientsBiomeChunkChange(World, BlockPos, int[])`
- LDWE source: E:\gitRepos\LDWE

---

## Session Statistics

**Session 1 commits:** 20 (from `a64af8ef` through `c1be6ff6`, now rebased)

**Session 2 commits:** 3 (GitHub Actions update, REID biome fix, client sync)

**Key deliverables:**
- Agent documentation baseline (CLAUDE.md, docs/)
- Build system updated to latest GregTech Buildscripts
- Block ID overflow support (FaweCache, CharFaweChunk, ForgeChunk_All, ForgeQueue_All,
  DiskOptimizedClipboard, FaweFormat) — user-tested, confirmed working
- 25 bug fixes and optimizations across the codebase
- REID biome compatibility (`REIDBiomeHelper.java`) — fixes `//setbiome` with REID present
- GitHub Actions updated to latest versions
- Git history cleaned (removed `.claude/` from commits, added to `.gitignore`)
- 3 progress/plan documents in docs/agent_progress/
