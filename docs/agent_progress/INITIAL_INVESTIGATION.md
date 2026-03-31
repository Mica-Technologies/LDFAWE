# Initial Investigation & Setup

## Resume Prompt

> We are working on LDFAWE, a Forge 1.12.2 fork of FastAsyncWorldEdit. We created baseline agent
> documentation (CLAUDE.md, docs/, .claude/settings.local.json). The build system and FaweCache
> block ID limit are resolved (Phases 1-4 implemented). Remaining items below include testing the
> block ID fix, a full codebase audit for bugs and optimizations, and investigating the setbiome
> command issue. Pick up where the checklist left off.

## Checklist

### 1. Build System Investigation -- RESOLVED

- [x] Attempt a full `./gradlew build` and document any errors
- [x] Investigate and resolve build failures (dependency resolution, toolchain issues, etc.)
- [x] If build issues persist due to the legacy Forge MDK setup, investigate migrating to
      GregTechCEu Buildscripts (RetroFuturaGradle)

**Root cause:** RetroFuturaGradle 1.3.28 was removed from both the GTNH Maven and Gradle Plugin
Portal. The project was already using the GregTech Buildscripts, just an outdated version.

**Fix applied:** Copied the working `build.gradle` (buildscript version 1762213476, RFG 1.4.0),
`settings.gradle`, and `gradle-wrapper.properties` (Gradle 8.9) from the sister project
(minecraft-city-super-mod). These are boilerplate GregTech buildscript files explicitly designed
to be replaceable — project-specific configuration lives in `gradle.properties`,
`dependencies.gradle`, `addon.gradle`, and `repositories.gradle`, which were untouched.

**Result:** Build succeeds cleanly. Only warnings are expected sun.misc.Unsafe / sun.reflect usage
in ZSTD/LZ4 compression code and ReflectionUtils (normal for this type of mod).

**Note for the future:** The GregTech buildscript has a built-in `./gradlew updateBuildScript` task
that can be used to stay current. However, it requires the current version to resolve first, so
when the plugin artifact is deleted from Maven (as happened here), a manual copy from a working
project or the GregTech Buildscripts repo is needed.

---

### 2. FaweCache Block ID Limit (>65535 combined index) — INVESTIGATION COMPLETE

- [x] Investigate the root cause of the OOB cache hits in `FaweCache`
- [x] Determine all call sites that feed into `getBlock(int index)` and `getItem(int index)`
- [x] Understand the combined ID encoding: `(blockId << 4) + data`
- [x] Determine the maximum block ID that modded Forge 1.12.2 can produce
- [x] Design a fix that supports block IDs above the current `Character.MAX_VALUE` (65535) limit
- [x] Implement the fix — Phases 1-4 complete (see `FAWECACHE_BLOCK_ID_PLAN.md`)
- [ ] **Phase 5:** Test with a world containing high-ID modded blocks

**Full investigation and attack plan:** See `docs/agent_progress/FAWECACHE_BLOCK_ID_PLAN.md`

**Summary:** The 16-bit `char` encoding `(id << 4) | metadata` was baked into the entire pipeline
— FaweCache, CharFaweChunk, ForgeQueue_All, ForgeChunk_All, clipboard I/O, and 50+ call sites.
The fix uses a hybrid sparse cache approach: keep the existing `char[]` fast path for IDs 0-4095
(98%+ of blocks), add HashMap-backed overflow for high-ID blocks. Memory impact is negligible
(scales with actual high-ID usage, not theoretical max). Also fixed an off-by-one bug in
`CACHE_PASSTHROUGH`/`CACHE_TRANSLUSCENT` (sized 65535 instead of 65536). Clipboard I/O
(DiskOptimizedClipboard, FaweFormat mode 5) also supports overflow.

---

### 3. Codebase Audit — Bugs & Optimization Opportunities

- [ ] Deep-dive analysis of the full LDFAWE codebase
- [ ] Document obvious bugs (crashes, silent failures, data corruption, edge cases)
- [ ] Document optimization opportunities (performance, memory, code quality) without losing
      functionality
- [ ] Create a new progress plan (`CODEBASE_AUDIT_PLAN.md`) with findings and actionable items

**Goal:** Systematically review the mod's major subsystems (queue, chunk, clipboard, commands,
NBT/anvil, relighting, etc.) for correctness and performance issues. The output should be a
prioritized list of fixes and improvements that can be tackled incrementally.

---

### 4. Investigate `setbiome` Command Failure

- [ ] Determine whether `setbiome` fails in LDFAWE or in LDWE (the LD fork of WorldEdit itself)
- [ ] Trace the command execution path from input through to biome application
- [ ] If the issue is in WorldEdit's biome handling, document it and note that the fix belongs in
      LDWE, not LDFAWE
- [ ] If LDFAWE's async biome path is the culprit, document and fix

**Context:** The `setbiome` command doesn't work. This may be an LDFAWE issue (async biome writes
not being applied correctly) or a WorldEdit issue (the command itself not generating the right
biome data). Need to determine which layer is responsible so the fix lands in the right repo.
