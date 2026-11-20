# Slimefun 6 — Core de DrakesCraft

**Un core de Slimefun mantenido para Minecraft 1.21 → 26.2, hecho para correr con la mayor coleccion de addons de la comunidad que logramos cargar juntos.**

> 🎮 `mc.drakescraft.cl` (Java y Bedrock) · 💬 [Discord](https://discord.gg/rv3vtXZTk7) · 🌐 [web.drakescraft.cl](https://web.drakescraft.cl)

Version en ingles (completa, con compilacion y Maven): [README.md](README.md).

## Que es

Este repositorio es el fork de `Slimefun/Slimefun4` mantenido por [SlimefunNewHorizons](https://github.com/SlimefunNewHorizons). Contiene el core que corre en produccion, portado desde el codigo original, mas el trabajo de compatibilidad para que muchisimos addons de la comunidad carguen juntos en versiones actuales de Minecraft. El proyecto original esta acreditado en [`docs/UPSTREAM_ATTRIBUTION.md`](docs/UPSTREAM_ATTRIBUTION.md) y el codigo sigue bajo GPL-3.0.

La historia del repositorio anterior (`Slimefun4-Drake`) se conserva en la rama `legacy/slimefun4-drake-1.21`. Nuestra historia no desciende de la de upstream, asi que no se puede fusionar con ella; upstream queda en `experimental` como referencia.

## En produccion (Temporada 2, medido el 2026-11-20)

* Purpur 26.2 build 141, Java 25.
* Core: `Slimefun 11.0-Universal-26.x-SNAPSHOT` (rama `main`).
* Addons que reporta `/sf versions`: **110** (la lista completa, con versiones, esta en el README en ingles).
* 9 suites consolidadas ([Drakes-Suites](https://github.com/SlimefunNewHorizons/Drakes-Suites)) que agrupan ~90 modulos de addons mas.

## Versiones

La linea Universal 26.x es una sola base de codigo que se compila y carga desde Minecraft **1.21** hasta **26.2**: se compila contra la API Paper `1.21.11` con bytecode Java 21 y corre en Java 21+. No se soportan versiones anteriores a 1.21.

## Addons cargados juntos

Los 8 jars de suites: DrakesBio, DrakesCombat, DrakesGenerators, DrakesMagic, DrakesMultiverse, DrakesServer, DrakesTech, DrakesUtility.

Otros 102 addons y plugins que se registran con Slimefun: AdvancedTech, AlchimiaVitae, AuraSkills, AxGraves, BetterChests, BetterReactor, BreweryX, Bump, ChestTerminal, ColoredEnderChests, CompressionCraft, Coronalis, CringleBosses, CrystamaeHistoria, Cultivation, DankTech2, DemonicExpansion, DiosesDrakes, DrakesBosses, DrakesCrates, DrakesSlimeMarket, DrakesVotes, Drugfun, DyeBench, DyedBackpacks, DynaTech, EcoPower, ElectricSpawners, ElementManipulation, EMCTech, EquivalencyTech, ExoticGarden, ExtraGear, ExtraHeads, ExtraTools, FastMachines, FlowerPower, FluffyMachines, FNAmplifications, FoxyMachines, Galaxyfun, Gastronomicon, GeneticChickengineering, GeyserHeads, GlobiaMachines, GuizhanLibPlugin, HeadLimiter, HotbarPets, IDreamOfEasy, InfernalExpansion, InfinityExpansion, Liquid, LiteXpansion, Magic8Ball, MagicXpansion, MiniBlocks, MobCapturer, MultiverseCreatures, MultiverseNets, Netheopoiesis, NetworksV6-Drake, Nexcavate, NotEnoughAddons, Obsidian-Armor, ObsidianExpansion, Odysseia, PotionExpansion, PrivateStorage, Quaptics, RandomExpansion, RelicsOfCthonia, RykenSlimeCustomizer, SaneCrafting, SensibleToolbox, SFCalc, SfChunkInfo, SFMobDrops, SFPortalGunsExtension, SimpleMaterialGenerators, SimpleStorage, SimpleUtils, SlimeChem, SlimeFrame, Slimefun-Disc, SlimefunAdvancements, SlimefunLuckyBlocks, SlimefunOreChunks, SlimefunTranslation, SlimefunWarfare, SlimeHUD, SlimeTinker, SlimyBees, SlimyRepair, SlimyTreeTaps, SmallSpace, SoulJars, SpiritsUnchained, Supreme, TranscEndence, VillagerTrade, VillagerUtil, WorldEditSlimefun.
