<p align="center">
  <img src="https://i.imgur.com/Fog5fDB.png">
</p>

---

# LDFAWE (Limitless Development Fast Async World Edit)

LDFAWE is a fork of the legacy [FAWE](https://github.com/boy0001/FastAsyncWorldedit) mod which aims to improve upon
WorldEdit with new features and large performance improvements.

Unlike the legacy [FAWE](https://github.com/boy0001/FastAsyncWorldedit) mod which supported versions 1.8 - 1.12 for
Bukkit, Forge, Sponge, and Nukkit, the development of Mica Minecraft LDFAWE is focused primarily on Forge 1.12.

### Other Minecraft Versions

#### 1.7.10

For those who are looking for Forge 1.7.10 support, user [KAMKEEL](https://github.com/KAMKEEL) has made a fork for
1.7.10 called KAWE (Kam's AsyncWorldEdit).
You can find it at [https://github.com/KAMKEEL/KAWE](https://github.com/KAMKEEL/KAWE).

## Documentation

Documentation for LDFAWE is currently available via the legacy [FAWE](https://github.com/boy0001/FastAsyncWorldedit)
mod's wiki: https://github.com/boy0001/FastAsyncWorldedit/wiki

* A standalone, dedicated Wiki for the LDFAWE mod is planned. If and when the feature set or commands diverge from the
  upstream version, the Wiki will be created to ensure accurate, up-to-date documentation.

## Server Notes

These differ from the upstream FAWE documentation.

### Rollback database (SQLite)

`/frb` (rollback), the inspect tool's history lookups, and edit logging use a small SQLite database per world
(`config/FastAsyncWorldEdit/history/<world>/summary.db`). LDFAWE bundles the SQLite JDBC driver, so no extra
download is needed. It is enabled by `history.use-database: true` in `config/FastAsyncWorldEdit/config.yml`.

- **Supported platforms:** Windows, macOS and Linux (glibc and musl/Alpine), each on x64 and arm64. Other platforms
  (32-bit systems, 32-bit ARM, FreeBSD) are not bundled; there the database is switched off with one console message
  and everything else keeps working.
- **Linux hosts with a `noexec` `/tmp`:** the driver unpacks its native library into Java's temporary folder at
  startup, which fails if that folder does not allow running programs (common on hardened hosts and some containers).
  Point it at a writable folder that does, by adding this to the server's Java start command (before `-jar`):

  ```
  -Dorg.sqlite.tmpdir=/path/to/writable/folder
  ```

### Block ids above 4095 (RoughlyEnoughIDs)

Edits, undo/redo, masks, rotation, schematics (`.schematic`, `.nbt`, `.fawe`) and snapshot `//restore` all support
block ids above 4095. History recorded by older LDFAWE versions cannot be repaired retroactively.

### LDFAWE-specific commands and behaviour

- `//set -p <pattern>` and `//replace -p [from] <to>` preview an edit: they report how many blocks would change, by
  block, without changing anything.
- `//history [player] [world]` lists a player's stored edits, newest first (works for offline players and from the
  console). `//undo [times] <player>` then rolls back that player's edits.
- Edits never cause block updates or neighbour notifications (sections are written directly), so they are safe for
  redstone, wire networks and physics-sensitive blocks.

## Official FAWE Mod

Information about official versions of the FAWE mod can be found
at https://intellectualsites.gitbook.io/fastasyncworldedit/.

Legacy versions of the official FAWE mod which support Forge 1.12 are no longer supported.

## Contributing

Looking to contribute? Let us know!

- Pull requests (PRs) are always welcome
- Let us know what has or has not been tested
- GitHub issues are available for additional assistance should you need it

## Disclaimer

LDFAWE is not affiliated with the original, official FAWE mod, Forge, or Minecraft. LDFAWE has been developed to improve
upon and extend support for Forge 1.12. Other developers may maintain LDFAWE (or derivative) support for other versions
of Forge through their own forks or contributions. Support for other versions of Forge is not guaranteed.