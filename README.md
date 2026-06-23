# CratesPlus

**Join the SQWARE Discord: [discord.sqware.gg](https://discord.sqware.gg).**

CratesPlus is a Minecraft crate plugin for Paper servers. It handles reward crates, crate keys, previews, cooldowns, weighted loot, and server store rewards.

Use it when you want configurable crates without hiding the important behavior behind a GUI-only setup.

## Features

- Config-defined crates.
- Weighted item and command rewards.
- Virtual keys and physical key items.
- Linked crate blocks.
- Reward previews and crate browser.
- Vault open costs.
- Cooldowns, mass opening, opening milestones, and reward limits.
- Per-crate particles.
- PlaceholderAPI placeholders.
- Open-history logging.
- API events for crate opens, rewards, and key changes.

## Requirements

- Paper `26.2+`
- Java `25+`
- Optional: Vault
- Optional: PlaceholderAPI
- Maven wrapper included

## Commands

```text
/crates
/crates preview <crate>
/crates open <crate> [amount|all]
/crates keys [crate]
/crates help

/cratesplus stats
/cratesplus reload
/cratesplus save
/cratesplus givekey <player> <crate> <amount> [virtual|physical]
/cratesplus takekey <player> <crate> <amount> [virtual|physical]
/cratesplus setkey <player> <crate> <amount>
/cratesplus giveall <crate> <amount> [virtual|physical]
/cratesplus openfor <player> <crate> [amount] [force]
/cratesplus givecrate <player> <crate> [amount]
/cratesplus setblock <crate>
/cratesplus removeblock
/cratesplus listblocks [crate]
/cratesplus info <crate>
/cratesplus simulate <crate> <rolls>
/cratesplus resetcooldown <player> [crate|all]
/cratesplus resetopenings <player> [crate|all]
/cratesplus resetlimit <player> <crate> [reward|all]
```

Aliases: `/crate`, `/keys`, `/cratesadmin`

## Permissions

```text
cratesplus.use          - browse crates and view keys
cratesplus.open         - open crates
cratesplus.preview      - preview rewards
cratesplus.admin        - manage crates, keys, and crate blocks
cratesplus.block.break  - break linked crate blocks
```

## Reward Example

```yaml
rewards:
  rare-sword:
    display-name: "Rare Sword"
    item:
      material: DIAMOND_SWORD
      amount: 1
      name: "Rare Sword"
      lore:
        - "Won from a crate."
      enchantments:
        sharpness: 4
    give-item: true
    weight: 5
    rarity: RARE
    commands: []
    broadcast: true
    requirements:
      permission: "cratesplus.reward.rare-sword"
      worlds:
        - world
      min-openings: 10
      one-time: true
      player-period-limit: 1
      player-period: 7d
      global-limit: 100
```

## PlaceholderAPI

Common placeholders:

```text
%cratesplus_keys_<crate>%
%cratesplus_virtual_keys_<crate>%
%cratesplus_physical_keys_<crate>%
%cratesplus_openings_<crate>%
%cratesplus_cooldown_<crate>%
%cratesplus_crate_count%
%cratesplus_block_count%
```

Reward limit and reset placeholders are also available for per-player and global limits.

## Build

```powershell
.\mvnw.cmd package
```

The jar is written to `target/CratesPlus-0.1.0.jar`.

## License

CratesPlus is licensed under the Apache License, Version 2.0.
