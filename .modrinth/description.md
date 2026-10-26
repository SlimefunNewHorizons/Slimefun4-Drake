# Slimefun4-Drake Core

**A modernized, hardened Slimefun 4 core for Java 21 and Paper/Purpur 1.21.11 — the foundation of the DrakesCraft Slimefun ecosystem.**

![Java 21](https://img.shields.io/badge/Java-21-orange?style=for-the-badge) ![Paper/Purpur 1.21.11](https://img.shields.io/badge/Paper%20%2F%20Purpur-1.21.11-blue?style=for-the-badge) ![License GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-green?style=for-the-badge)

---

## 🎮 Play it live

See this core running with **80+ optimized expansions** on the DrakesCraft technical survival network.

- **Server IP:** `mc.drakescraft.cl` *(Java 1.21.11 & Bedrock)*
- **Website & guide:** [web.drakescraft.cl](https://web.drakescraft.cl)
- **Community:** join our Discord from the links on this page.

---

## ✨ What it is

Slimefun4-Drake owns the reliable base layer of Slimefun: recipes, categories, guides, research, block storage, cargo networks, energy networks and addon loading. It is the compatibility boundary every DrakesCraft addon builds on.

It is a sovereign fork, modernized for high concurrency, asynchronous safety and exploit/duplication prevention.

## 🚀 Highlights

### ⭐ Guide bookmarks
- Right-click any unlocked item in a category or search result to save it.
- Open the **star button** in the guide header for a paginated quick-access list.
- Stored as stable Slimefun item IDs in `guide-bookmarks.yml`, so addon recipe or texture updates never leave stale data behind.
- Toggle with `guide.bookmarks` (enabled by default).

### ⚡ Hybrid native acceleration
- Optional **Slimefun-Rust** layer speeds up deterministic work such as EnergyNet aggregation.
- Java stays authoritative for Bukkit state, with a transparent fallback if the native library is missing.
- `/sf native` reports native calls, fallbacks and failures.

### 🛡️ Controlled SFMaster guide delivery
- One authority (`CheatPolicy`) checks both category pages and search results.
- Players with `odysseia.sfmaster.active` receive one item per claim, on a rolling quota that survives restarts.
- Every delivered item is tagged with its owner before it reaches the inventory.
- Addons are **default-deny**: only configured addons or exact item IDs are eligible, and equipment/endgame blocklists still apply.
- Staff bypass and `/sf give` remain staff-only operations.

### 🧩 Addon-friendly by design
- Addons load through the public Slimefun API.
- The core never silently rewrites addon data or recipes.
- The standard Slimefun storage contract remains authoritative.

---

## 📋 Requirements

| | |
|---|---|
| **Java** | 21 |
| **Server** | Paper or Purpur 1.21.11 |
| **Native library** *(optional)* | `native/libslimefun_ffi.so` (ABI 1) |

## 📦 Installation & updates

1. Back up your current Slimefun jar and storage.
2. Keep **exactly one** Slimefun core jar in `plugins/`.
3. Never hot-reload Slimefun or replace its jar while the server is running.
4. Restart during a maintenance window. Check the startup log, the addon load list, BlockStorage loading and that there is no duplicate-plugin warning.
5. Smoke-test the Slimefun Guide, a machine, a CargoNet operation and an EnergyNet operation.

---

## 🐞 Support

Found a bug? Open a ticket on the **Issues** link of this page and attach the generated error report there.

> Do not post error reports on public paste services: they can contain server paths, plugin lists and other operational metadata.

## 📄 License & credits

- Maintained by **JackStar** under **DrakesCraft Labs**.
- Based on Slimefun 4 by its upstream authors and the open-source community.
- Distributed under the **GNU General Public License v3.0**.
