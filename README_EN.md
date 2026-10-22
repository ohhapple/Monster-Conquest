# Monster Conquest

**语言 / Language:** [中文](README.md) | [English](README_EN.md)]

[![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/ohhapple/Monster-Conquest/total?color=161616&label=Github%20downloads)](https://github.com/ohhapple/Monster-Conquest/releases)
[![CurseForge](https://img.shields.io/curseforge/dt/1745982?label=CurseForge%20Downloads&color=orange)](https://www.curseforge.com/minecraft/mc-mods/monster-conquest)
[![Modrinth](https://img.shields.io/modrinth/dt/AUNtGJFE?label=Modrinth%20Downloads)](https://modrinth.com/mod/monster-conquest)

A faction warfare mod for Minecraft 1.21.1 / NeoForge: recruit soldiers, build armies, and lead troops into battle with command tools and an RTS view.

## Requirements

*   Minecraft 1.21.1
*   NeoForge 21.1.251 or higher (21.x series)
*   Required on both client and server

## Gameplay Overview

*   **Factions**: Undead / Villager / Illager / Nether / End / Monster / Neutral, plus a separate player faction for each player. Factions can be allied or hostile (default relations are written in the config and can also be changed in-game); units of the same faction support each other, and when attacked, the entire army locks onto the target together.
*   **Recruitment and upkeep**: Right-click a Recruitment Station block to open its interface, recruit / upgrade soldiers by tier, with prices configured per unit type; the roster also includes fixed-tier flying soldiers such as Vex and Allay. A Trade Station block sells ammunition, guns, and tactical equipment. Both blocks can directly read items from the player's inventory.
*   **Command**: Control Tool (middle-click to select a unit, right-click to move / attack, look down and right-click = make selected units follow you), R key command wheel, V key RTS mode (box-select troops and right-click to issue orders, number keys to change formation, Z / X to mount / dismount).
*   **Unit types**: Melee, archer, gunner, thrower, mage, dual-form, and flying.
*   **Guns and tactical equipment**: Rifle / Shotgun / Sniper Rifle / Rocket Launcher and their respective ammunition (scoping, bullet drop, rocket splash); Tactical Equipment Slots can hold the Bulletproof Vest, Warm Clothes, Ammo Box, and Backpack (the Backpack can also hold bullets directly).
*   **Unit AI**: A custom pathfinding and formation system handles formation arrangement, marching in column, maze bypassing, door opening, ladder climbing, and wading through water;
*   **Guide book**: Craft a guide book that explains command, recruitment, trading, and more in chapters.

## Default Keybindings

All can be changed in vanilla "Options → Controls".

| Key      |Action                                                  |
| -------- |------------------------------------------------------- |
| N        |Open Faction Relations GUI                              |
| R (hold) |Command Wheel                                           |
| V        |Toggle RTS mode                                         |
| 1 ~ 5    |RTS: Line / Shield Wall / Wedge / Skirmish / Free Roam  |
| Z / X    |RTS: Mount / Dismount                                   |
| H        |Open Tactical Equipment Slots                           |
| B        |Open Backpack (when placed in Tactical Equipment Slots) |

## Commands

- `/monsterc faction GUI`：Open the faction relations interface.
- `/monsterc spawnpoints（on/off）`：A global switch to make all spawn points generate monsters.

## Configuration

On first launch, modular configs are generated under `config/monster_conquest/` (general / factions / combat / player / recruit, etc.), where recruitment prices, faction relations, unit stats, AI behavior, and more can be fine-tuned. The build records the source Git branch in the JAR, and the client reads `gradle.properties` from that same branch. It validates `minecraft_version` and `mod_loader`, and does not depend on release titles, tags or asset names. Each launch makes one attempt with no retries. The version is still changed only in `gradle.properties` via `mod_version`. Set `updateCheckEnabled` to `false` in `general-common.toml` to disable all external requests.

## Build

Requires JDK 21. Detached-HEAD builds may pass `-PgitBranch=<branch>`; if the branch cannot be determined, it is recorded as `unknown`, the build still succeeds, and update checks are disabled.

```
# Build
./gradlew build          # Windows: gradlew.bat build
# Output: build/libs/

# Development run
./gradlew runClient
./gradlew runServer
```

## Inspiration and Credits

The creative direction and design inspiration for this mod come in part from games, mods, datapacks, modpacks, and other works the author has played or seen. These works serve only as sources of inspiration and do not imply any affiliation, cooperation, sponsorship, or endorsement between them and this mod.

## License and Notes

Free play, video recording / streaming, free redistribution (including inclusion in modpacks), and modification are permitted, but **any form of commercial use is prohibited**. Video recording or streaming must also not be monetized through advertising, sponsorship, memberships, tips, donations, affiliate links, paid promotions, or any other commercial means. Modified versions must also remain non-commercial.

Any distribution of this mod or modified versions by anyone other than the original author must:

*   Clearly credit: author ohhapple · [https://github.com/ohhapple](https://github.com/ohhapple) · source Monster Conquest;
*   Prominently label as "redistributed version / unofficial distribution", whether modified or not;
*   Prominently include the official Monster Conquest download link.

Modified versions must also be prominently labeled as "modified version / unofficial version" and must not imply endorsement by the original author. When distributing a modified version, you must also publicly provide the complete source code for free, retain this notice in full in a prominent location, include the official download link, and state the modifier's information and modification date. Modified versions and derivative works, no matter how many times modified, must continue to comply with this notice and must not adopt terms that permit commercial use.

This mod is provided "as is", and the author assumes no responsibility for any liability arising from downloading, installing, or using this mod. Use at your own risk. This project is not an official Minecraft product and is not affiliated with Mojang / Microsoft.

This description is for ease of reading only; if there is any ambiguity or doubt, please refer to the full statement in [LICENSE](LICENSE).

Author: ohhapple · [https://github.com/ohhapple](https://github.com/ohhapple)
