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

### 2. FaweCache Block ID Limit (>65535 combined index)

- [ ] Investigate the root cause of the OOB cache hits in `FaweCache`
- [ ] Determine all call sites that feed into `getBlock(int index)` and `getItem(int index)`
- [ ] Understand the combined ID encoding: `(blockId << 4) + data`
- [ ] Determine the maximum block ID that modded Forge 1.12.2 can produce
- [ ] Design a fix that supports block IDs above the current `Character.MAX_VALUE` (65535) limit
- [ ] Implement the fix
- [ ] Test with a world containing high-ID modded blocks

**Context:** The `FaweCache` class pre-allocates fixed-size arrays using `Character.MAX_VALUE + 1`
(65536) as the size for `CACHE_BLOCK`, `CACHE_ITEM`, `CACHE_COLOR`, `CACHE_PASSTHROUGH`, and
`CACHE_TRANSLUSCENT`. The combined index is computed as `(id << 4) + data`, meaning block IDs above
4095 will produce indices beyond 65535.

Vanilla Minecraft 1.12.2 only uses block IDs 0–255, but **modded Forge extends the block registry
well beyond this**. Forge's `GameData` / `BlockStateIDMap` can assign numeric IDs up to 4095 for
blocks (12-bit limit in chunk storage), but with the 4-bit metadata shift (`id << 4`), the combined
index caps at `(4095 << 4) + 15 = 65535`. However, mods that register extremely large numbers of
blocks can cause Forge to assign IDs above 4095 when extended block ID support is in play.

The current workaround (commit `a526a2d0`) catches `ArrayIndexOutOfBoundsException` in
`getBlock(int index)` and returns `CACHE_BLOCK[0]` (air), which prevents crashes but silently
corrupts operations — blocks with high IDs are treated as air during WorldEdit operations, causing
incomplete or broken results.

**Key questions to investigate:**
- What is the actual maximum block ID that Forge 1.12.2 assigns in heavily modded environments?
- Is the `id << 4` encoding fundamental to FAWE's internal representation, or can it be changed?
- How does the queue system (`ForgeQueue_All`, `ForgeChunk_All`) encode/decode block IDs — do they
  also assume 12-bit block IDs?
- Would expanding the cache arrays be sufficient, or does the `char`-based encoding used in chunk
  sections (`char[]` in `CharFaweChunk`) impose a hard 16-bit ceiling?
- Are there other places in the codebase (beyond `FaweCache`) that assume the combined index fits
  in a `char`?

**Affected files (known so far):**
- `FaweCache.java` — Cache arrays and `getBlock(int index)` (the patched method)
- `ForgeQueue_All.java` — Chunk section block get/set (likely uses `char` encoding)
- `ForgeChunk_All.java` — Chunk data storage
- `CharFaweChunk.java` — Uses `char[][]` arrays for block storage (16-bit limit per element)
