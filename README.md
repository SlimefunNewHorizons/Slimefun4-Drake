
> [!IMPORTANT]
> **dough is SHADED inside Slimefun, not shipped as a standalone jar.** The source
> lives in the `dough-core` repo, but Slimefun consumes it as a Maven dependency
> (`com.github.drakescraft_labs:dough-core`) and relocates it when packaging to
> `com.github.drakescraft_labs.slimefun4.libraries.dough`. **There is no
> `dough.jar` in production.** Therefore, any change to `dough-core` requires:
> `mvn install` in dough-core -> **rebuild Slimefun** (and every plugin that
> shades it) -> upload those jars. Uploading dough on its own has no effect.

<div align="center">

<img src="banner.svg" alt="Slimefun4-Drake banner" width="920" />

# Slimefun4-Drake Core

**The Java 21 Slimefun core maintained for DrakesCraft on Paper/Purpur 1.21.11.**

</div>

> ### 🏰 Join the Official DrakesCraft Community!
> 
> * 🎮 **Server IP**: `mc.drakescraft.cl` *(Java 1.21.11 & Bedrock)*
> * 💬 **Official Discord**: [discord.gg/drakescraft](https://discord.gg/rv3vtXZTk7)
> * 🌐 **Web & Guide**: [web.drakescraft.cl](https://web.drakescraft.cl) — 🛒 **Store**: [web.drakescraft.cl/store](https://web.drakescraft.cl/store.html)
> 
> *Play with this addon and 80+ more optimized expansions live on our technical survival network!*

---

## Purpose

Slimefun4-Drake owns the reliable base layer for recipes, categories, guides, research, block storage, cargo networks, energy networks and addon loading. It is the compatibility boundary for the DrakesCraft Slimefun ecosystem.

The survival guide includes native per-player bookmarks. Players can right-click
an unlocked item in a category or search result to save it, then open the star
button in the guide header for a paginated quick-access list. Bookmarks persist
as stable Slimefun item IDs in `guide-bookmarks.yml`, so addon recipe or texture
updates do not serialize stale `ItemStack` data. The feature is controlled by
`guide.bookmarks` and defaults to enabled for existing installations.

The runtime is hybrid. `Slimefun-Rust` is the shared acceleration layer for
deterministic work across this core, addons and DrakesCraft-owned plugins. Java
remains authoritative for Bukkit state and supplies a transparent fallback.

## Version compatibility

| Line | Branch | Minecraft | Java | Compile-time Paper API | Status |
|---|---|---|---|---|---|
| Production | `main` | Paper/Purpur 1.21.11 | 21 | `1.21.11-R0.1-SNAPSHOT` | production |
| Universal 26.x | `port-26x` | Paper/Purpur 26.2 (same codebase as production 1.21.11) | 21 bytecode (major 65); runs on 21+ | `1.21.11-R0.1-SNAPSHOT` (`build-parent/pom.xml`, `paper.version`) | test (verified on Paper/Purpur 26.2) |

The Universal core is `11.0-Universal-26.x-SNAPSHOT` (package `io.github.thebusybiscuit.slimefun4`, `plugin.yml` `api-version: 1.21`). Production (`main`) uses the package `com.github.drakescraft_labs.slimefun4`.

**Minecraft versions older than 1.21.11 are not supported and not tested.** The `MinecraftVersion` enum inherits 1.16+ logic from upstream, but nothing below 1.21.11 is guaranteed to work.

Addons for the 26.x line are obtained with `ai-hub/scripts/portar_addons.py`.

## Runtime contract

- Target: **Java 21** and **Paper/Purpur 1.21.11**.
- Artifact: `Slimefun-1.2.DEV v11.0-Drake-1.21.11-SNAPSHOT.jar`.
- Storage: the normal Slimefun storage contract remains authoritative.
- Addons: addons load through the public Slimefun API; the core does not silently rewrite their data or recipes.
- Native acceleration: ABI 1 loads `native/libslimefun_ffi.so`, accelerates
  EnergyNet aggregation and exposes a Bukkit service for compatible plugins.
  `/sf native` reports calls, fallbacks and failures.

## Controlled SFMaster path

The guide delivery path has one authority: `CheatPolicy` in this Java core.
Category pages and search results both pass through the same checks.

- Staff with the bypass remains unrestricted.
- A player with `odysseia.sfmaster.active` receives one item per claim.
- The rolling quota is persisted in the player's PDC and survives restarts.
- Every delivered item is marked with its owner before inventory insertion.
- Addons are default-deny. Only configured addons or exact item IDs are eligible,
  and equipment/endgame blocklists still apply afterward.
- `/sf give` remains a staff operation. Odysseia blocks paid pass holders from
  using it and owns guide expiry, transfer prevention, and legacy audits.

## Universal 26.x line (branch `port-26x`)

> `main` is production **1.21.11** and does **not** include anything from this section. `port-26x` is the Universal line for Paper/Purpur 26.2 (see [Version compatibility](#version-compatibility)).

- **Opt-in storage backends (`SfStore`)**: `storage.backend` = `file` (disk with fsync, default), `sqlite`, `mysql` or `postgres`. Keys: `sqlite-file`, `jdbc-url`, `jdbc-user`, `jdbc-password`. SQL backends are wrapped in `CachedSfStore`: writes are asynchronous and everything is flushed on shutdown. The first time SQL is used, data is imported from disk (the files are not touched); if SQL fails, it falls back to disk. Changing backend requires a restart.
- **New commands**: `/sf tick` (tick freeze), `/sf storage [status|export]`, `/sf balance [addon]`, `/sf itemid`, `/sf addons` (versions and item count per addon).
- **`core/balance`**: `BalanceService`, taken from Slimefun5.
- **Crafter Smart Port** (`CRAFTER_SMART_PORT`): interactor placed under an autocrafter; idea from SlimefunGuguProject/Slimefun4.
- **Cargo**: `ItemStackWrapper` with a single meta read (from Slimefun5).
- **Tests**: the test suite (1834 tests) is green.
- **dough-core** `1.3.1-Universal-26.x-SNAPSHOT` (namespace `io.github.bakedlibs`; fixes `CustomGameProfile` against the final Authlib), published at `maven.drakescraft.cl`.
- **Attribution (GPL-3.0)**: SlimefunGuguProject/Slimefun4, Slimefun5/Slimefun5 and Slimefun-United.

## Ported improvements

Fixes ported from other Slimefun forks and unmerged upstream PRs (all GPL-3, each covered by JUnit tests):

| Change | Origin | Commit |
| --- | --- | --- |
| Cargo frequency parsing without regex for single digits, overflow-safe, invalid nodes logged once | wickidcow/Slimefun-Legacy `f7c4ce9` | `fcef7af58` |
| Piglin bartering drops shuffled (no iteration-order bias) | Slimefun/Slimefun4 PR #4337 | `1ab50213c` |
| Swapping hands (F) blocked while a backpack is open | Slimefun/Slimefun4 PR #4275 | `0be7a9257` |
| Pickaxe of Vein Mining no longer breaks Slimefun blocks | Slimefun/Slimefun4 PR #4343 | `bc60de004` |
| Research placeholders/stats ignore researches without enabled items, no division by zero | Slimefun/Slimefun4 PR #4320 | `9ac85bf20` |
| Cargo Grid Monitor GUI on the Cargo Manager (plain click opens it; sneak+click keeps the visualizer toggle). Compiles and passes the suite; **not yet tried in-game** | SlimeCore-MC/Slimefun4Core (GPL-3, R2Ksanu / RuTwoK Labs) | this commit |

## Build and validation

```bash
mvn package -DskipTests
mvn test -DlegacyMockBukkitTests \
  -Dtest=TestColorCodes,TestSlimefunSpelling,TestDoubleRangeSetting,TestEnumSetting,TestIntRangeSetting,TestItemSettings,TestMaterialTagSetting,TestNetworkManager,TestResearches,TestUpdaterService,TestRechargeableItems
```

GitHub Actions publishes the exact production JAR from a successful Java CI run and performs a Paper 1.21.11 end-to-end boot check for pull requests. Prefer that artifact to an unverified local build.

The isolated `ClaimWindowTest` does not require MockBukkit. The legacy MockBukkit
suite currently requires registry fixtures compatible with Paper 1.21.11; a
registry bootstrap failure is infrastructure debt, not a valid SFMaster result.

## Deployment discipline

1. Back up the active Slimefun JAR and storage before a core update.
2. Keep exactly one active Slimefun core JAR in `plugins/`.
3. Do not hot-reload Slimefun or replace its JAR while Paper is running.
4. Restart during a maintenance window and verify the startup log, addon load list, BlockStorage load and no duplicate-plugin warning.
5. Smoke-test the Slimefun Guide, a machine, a CargoNet operation and an EnergyNet operation before calling the update complete.

## DrakesCraft guides

- [Progression and automation](docs/GUIA_DE_PROGRESION_Y_AUTOMATIZACION.md)
- [Network integrity](https://github.com/DrakesCraft-Labs/NetworksV6-drake/blob/main/docs/INTEGRIDAD_DE_RED_DRAKESCRAFT.md)

## Maintainers

DrakesCraft Labs / JackStar. Upstream Slimefun licensing remains GPL-3.0.

## Support

This repository is the operational reference for the DrakesCraft core. Attach
generated error reports to the relevant DrakesCraft repository issue tracker;
do not publish them to public paste services because they can contain server
paths, plugin inventories or other operational metadata.
