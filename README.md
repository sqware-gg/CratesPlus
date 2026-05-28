# CratesPlus

CratesPlus adds configurable reward crates for Paper servers.

Crates are defined in `config.yml`. Each crate supports weighted rewards, custom item and command rewards, virtual keys, physical key items, linked crate blocks, per-crate particles, cooldowns, Vault open costs, reward previews, mass opening, opening milestones, reward limits, PlaceholderAPI placeholders, and open-history logging.

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

## Permissions

```text
cratesplus.use          - browse crates and view keys
cratesplus.open         - open crates
cratesplus.preview      - preview crate rewards
cratesplus.admin        - manage crates, keys, and crate blocks
cratesplus.block.break  - break linked crate blocks
```

## Reward Options

## Per-Crate Particles

Crates inherit the global `effects` particle settings unless a crate overrides them:

```yaml
crates:
  gold:
    particles:
      enabled: true
      type: FLAME
      count: 6
      offset-x: 0.35
      offset-y: 0.25
      offset-z: 0.35
      speed: 0.01
```

Flat crate keys are also accepted, such as `particles-enabled`, `particle`, `particle-count`, and `particle-speed`.

Rewards can use simple `material`/`amount` fields or an `item` section with name, lore, enchantments, flags, unbreakable state, and custom model data.

```yaml
rewards:
  rare-sword:
    display-name: "&#57F287Rare Sword"
    item:
      material: DIAMOND_SWORD
      amount: 1
      name: "&#57F287Rare Sword"
      lore:
        - "&7Won from a crate."
      enchantments:
        sharpness: 4
      flags:
        - HIDE_ENCHANTS
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

`player-period-limit: 1` with `player-period: 7d` means the player can win that specific reward once every 7 days. `global-period-limit` and `global-period` apply the same idea server-wide.

## Placeholders

Requires PlaceholderAPI.

```text
%cratesplus_keys_<crate>%
%cratesplus_virtual_keys_<crate>%
%cratesplus_physical_keys_<crate>%
%cratesplus_openings_<crate>%
%cratesplus_cooldown_<crate>%
%cratesplus_cooldown_seconds_<crate>%
%cratesplus_next_milestone_<crate>%
%cratesplus_reward_remaining_<crate>_<reward>%
%cratesplus_reward_reset_<crate>_<reward>%
%cratesplus_reward_reset_seconds_<crate>_<reward>%
%cratesplus_reward_global_remaining_<crate>_<reward>%
%cratesplus_reward_global_reset_<crate>_<reward>%
%cratesplus_reward_global_reset_seconds_<crate>_<reward>%
%cratesplus_crate_count%
%cratesplus_block_count%
```
