# User Testing Checklist

Consolidated list of changes that need user verification. Build a test jar from the current
`master` branch and test in the modpack environment.

---

## Completed Tests

- [x] **Block ID overflow (>4095)** — High-ID modded blocks work with WorldEdit operations
- [x] **`//setbiome` in singleplayer** — Biomes change correctly with REID present
- [x] **Biome client sync** — Biome changes visible immediately without world rejoin (singleplayer)

## Pending Tests

### High Priority

- [ ] **`//setbiome` on dedicated server**
  Test that `//setbiome` works on the dedicated server with REID. The fix sends a network
  packet (`BiomeChunkChangeMessage`) to all clients tracking the chunk, which is how REID
  syncs biomes on a server. Verify:
  1. Run `//setbiome` on the server
  2. Check F3 debug screen — biome should update
  3. Check if nearby players also see the change without relogging
  4. Check server console for any LDFAWE/REID errors

- [ ] **Clipboard copy/paste with high-ID blocks**
  The overflow persistence fix (P2-9) ensures high-ID blocks survive clipboard save/reload.
  Test:
  1. Select a region containing blocks with IDs > 4095
  2. `//copy` then `//paste` — should preserve the blocks correctly
  3. Save & quit, reopen the world, `//paste` again from the same clipboard — should still
     work (this tests the `.bd.overflow` sidecar file persistence)

### Medium Priority

- [ ] **Large async operations (threading fix)**
  The ThreadLocal section cache (P2-1) changed how block lookups are cached across threads.
  Test with large operations:
  1. `//set stone` on a large selection (e.g., 100x100x100)
  2. `//replace stone dirt` on the same area
  3. `//copy` and `//paste` of a large area
  4. Verify no block corruption, missing blocks, or errors in logs

- [ ] **Undo/redo after large operations**
  1. Perform a large `//set` operation
  2. `//undo` — should restore the area correctly
  3. `//redo` — should re-apply

### Low Priority

- [ ] **Lighting after block changes**
  After large `//set` operations, verify lighting updates correctly (no dark spots or
  light leaks). The NMSRelighter fixes from the codebase audit should help here.

- [ ] **P0 crash prevention under load**
  The P0 fixes added tick time budget enforcement and a deadlock timeout. Under heavy
  WorldEdit usage on a server with players, verify the server stays responsive and doesn't
  hang.

---

## How to Build a Test Jar

```bash
cd E:\gitRepos\LDFAWE
JAVA_HOME="C:/Users/ahawk/.jdks/azul-17.0.18" ./gradlew build
```

The jar will be in `build/libs/`. Swap it for the existing LDFAWE jar in the modpack's
mods folder.
