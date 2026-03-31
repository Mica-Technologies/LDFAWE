# Initial Investigation & Setup

## Resume Prompt

> We are working on LDFAWE, a Forge 1.12.2 fork of FastAsyncWorldEdit. We created baseline agent
> documentation (CLAUDE.md, docs/, .claude/settings.local.json). There are two priority investigation
> items below. Pick up where the checklist left off.

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
- [ ] Implement the fix (see `FAWECACHE_BLOCK_ID_PLAN.md`)
- [ ] Test with a world containing high-ID modded blocks

**Full investigation and attack plan:** See `docs/agent_progress/FAWECACHE_BLOCK_ID_PLAN.md`

**Summary:** The 16-bit `char` encoding `(id << 4) | metadata` is baked into the entire pipeline
— FaweCache, CharFaweChunk, ForgeQueue_All, ForgeChunk_All, clipboard I/O, and 50+ call sites.
The fix uses a hybrid sparse cache approach: keep the existing `char[]` fast path for IDs 0-4095
(98%+ of blocks), add HashMap-backed overflow for high-ID blocks. Memory impact is negligible
(scales with actual high-ID usage, not theoretical max). Also found an off-by-one bug in
`CACHE_PASSTHROUGH`/`CACHE_TRANSLUSCENT` (sized 65535 instead of 65536).
