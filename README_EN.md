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
*   **Command**: Control Tool (middle-click to select a unit, right-click to move / attack, look down and right-click = make selected units follow you), R key command wheel, V key RTS mode (box-select troops and right-click to issue orders, number keys to change formation, Z / C to mount / dismount).
*   **Unit types**: Melee, archer, gunner, thrower, mage, dual-form, and flying.
*   **Guns and tactical equipment**: Rifle / Shotgun / Sniper Rifle / Rocket Launcher and their respective ammunition (scoping, bullet drop, rocket splash); Tactical Equipment Slots can hold the Bulletproof Vest, Warm Clothes, Ammo Box, and Backpack (the Backpack can also hold bullets directly).
*   **War horse dyeing**: Hold a dye and sneak-right-click a war horse or a cavalry unit's mount to apply that dye color. Cavalry retain the color across dismounting and mounting again until the horse is dyed another color.
*   **Monster Conquest Auto Battler**: See the [Auto Battler Gameplay Guide](怪物征伐自走棋玩法图鉴.md) for the complete flow, economy, units, equipment, traits and status rules.
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
| Z / C    |RTS: Mount / Dismount                                   |
| X        |Open Auto Battler Lobby                                 |
| H        |Open Tactical Equipment Slots                           |
| B        |Open Backpack (when placed in Tactical Equipment Slots) |

## Commands

- `/monsterc faction GUI`：Open the faction relations interface.
- `/monsterc spawnpoints（on/off）`：A global switch to make all spawn points generate monsters.
- `/monsterc arena shutdown`：OP-only force stop for the auto battler child server; also releases the active room state.
- `/monsterc arena history clear <player>`: OP-only clear of a player's Monster Conquest auto battler match history, with player-name completion and offline-player support.

## Configuration

On first launch, modular configs are generated under `config/monster_conquest/`, where recruitment prices, faction relations, unit stats, AI and other rules can be adjusted in the corresponding TOML files.

Auto Battler settings are in `arena-common.toml`; `[childServer].enabled` controls whether the arena child server starts.
To expose only one TCP port, enable the embedded gateway:

```toml
[network]
embeddedGatewayRouting = true
publicHost = "public IP or domain"

[network.gateway]
gatewayPort = 26565
gatewayMaxConnections = 256

[network.arena]
arenaServerPort = 24565
maxPlayers = 128
```

The gateway uses Netty event loops. `gatewayMaxConnections` limits concurrent TCP connections through the gateway instead of creating one forwarding thread pair per player. Size it for the main and arena traffic that actually passes through the gateway, including reconnects and status requests.
With the gateway enabled, players connect to `public IP or domain:26565`, and only `gatewayPort` must be allowed through the firewall. Without it, expose the main server and arena child ports separately. Any NAT mapping must preserve the same public and internal gateway port.
For public, forwarded or cross-network deployments, set `publicHost` to an address players can reach. Do not use loopback or LAN-only addresses.
Arena transfers use 60-second retryable leases. The main server generates a session secret and a nonce per player; the child acknowledges the actual successful login with an HMAC receipt. A confirmed lease accepts normal-login reconnects while a transfer back to the main server is routed to main. Offline servers use the offline UUID produced by the actual main-server login rather than the client's Mojang UUID.

Set `updateCheckEnabled` to `false` in `general-common.toml` to disable update checks.

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

Free play, video recording / streaming, free redistribution (including inclusion in modpacks), and modification are permitted, but **any form of commercial use is prohibited**. Modified versions must also remain non-commercial.

Anyone who studies or learns from this mod must credit Monster Conquest as the source and ohhapple as the original author.

Any distribution of this mod or modified versions by anyone other than the original author must:

*   Clearly credit: author ohhapple · [https://github.com/ohhapple](https://github.com/ohhapple) · source Monster Conquest;
*   Prominently label as "redistributed version / unofficial distribution", whether modified or not;
*   Prominently include the official Monster Conquest download link.

Modified versions must also be prominently labeled as "modified version / unofficial version" and must not imply endorsement by the original author. When distributing a modified version, you must also publicly provide the complete source code for free, retain this notice in full in a prominent location, include the official download link, and state the modifier's information and modification date. Modified versions and derivative works, no matter how many times modified, must continue to comply with this notice and must not adopt terms that permit commercial use.

This mod is provided "as is", and the author assumes no responsibility for any liability arising from downloading, installing, or using this mod. Use at your own risk. This project is not an official Minecraft product and is not affiliated with Mojang / Microsoft.

This description is for ease of reading only; if there is any ambiguity or doubt, please refer to the full statement in [LICENSE](LICENSE).

Author: ohhapple · [https://github.com/ohhapple](https://github.com/ohhapple)
